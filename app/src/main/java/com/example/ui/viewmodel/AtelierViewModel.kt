package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ApiKeyStore
import com.example.data.remote.EndpointTestResult
import com.example.data.remote.MultiModelAiClient
import com.example.data.repository.AtelierRepository
import com.example.domain.model.AiProvider
import com.example.domain.model.ApiExecutionLog
import com.example.domain.model.ApiPlugin
import com.example.domain.model.ChatMessage
import com.example.domain.model.ChatSession
import com.example.domain.model.ModelCatalog
import com.example.domain.model.PromptTemplate
import com.example.domain.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AtelierUiState(
    val activeChatId: String? = null,
    val selectedProvider: AiProvider = AiProvider.DEEPMIND,
    val selectedModelId: String = "gemini-3.8-flash",
    val composerText: String = "",
    val streamingContent: String? = null,
    val isGenerating: Boolean = false,
    val activePluginIds: Set<String> = emptySet(),
    val statusBanner: String? = null,
    val errorBanner: String? = null,
    val endpointTestResult: EndpointTestResult? = null,
    val isTestingEndpoint: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class AtelierViewModel(
    private val repository: AtelierRepository,
    private val apiKeyStore: ApiKeyStore,
    private val aiClient: MultiModelAiClient,
    private val currentUserId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(AtelierUiState())
    val uiState: StateFlow<AtelierUiState> = _uiState.asStateFlow()

    private var activeGenerationJob: Job? = null
    private var hasSyncedInitialDefaults = false

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { error ->
            handleStreamError("profile", error)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val chats: StateFlow<List<ChatSession>> = repository.observeChats(currentUserId)
        .catch { error ->
            handleStreamError("chats", error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val activeMessages: StateFlow<List<ChatMessage>> = _uiState
        .flatMapLatest { state ->
            val chatId = state.activeChatId
            if (chatId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                repository.observeMessages(currentUserId, chatId)
                    .catch { error ->
                        handleStreamError("messages", error)
                        emit(emptyList())
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val prompts: StateFlow<List<PromptTemplate>> = repository.observePrompts(currentUserId)
        .catch { error ->
            handleStreamError("prompts", error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val plugins: StateFlow<List<ApiPlugin>> = repository.observePlugins(currentUserId)
        .catch { error ->
            handleStreamError("plugins", error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val logs: StateFlow<List<ApiExecutionLog>> = repository.observeLogs(currentUserId)
        .catch { error ->
            handleStreamError("logs", error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val providerKeys: StateFlow<Map<String, String>> = apiKeyStore.allProviderKeysFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyMap())

    val pluginKeys: StateFlow<Map<String, String>> = apiKeyStore.allPluginKeysFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyMap())

    init {
        viewModelScope.launch {
            val authUser = FirebaseAuth.getInstance().currentUser
            try {
                repository.ensureUserProfile(
                    userId = currentUserId,
                    displayName = authUser?.displayName,
                    email = authUser?.email,
                    photoUrl = authUser?.photoUrl?.toString()
                )
                repository.seedStarterPromptsAndPluginsIfEmpty(currentUserId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Profile initialization warning", e)
            }
        }

        viewModelScope.launch {
            userProfile.collect { profile ->
                if (profile != null && !hasSyncedInitialDefaults && _uiState.value.activeChatId == null) {
                    hasSyncedInitialDefaults = true
                    _uiState.update {
                        it.copy(
                            selectedProvider = AiProvider.fromWireId(profile.defaultProvider),
                            selectedModelId = profile.defaultModelId
                        )
                    }
                }
            }
        }
    }

    private fun handleStreamError(tag: String, error: Throwable) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val root = firestoreError ?: error
        Log.w(TAG, "Error observing $tag", root)
        if (FirebaseAuth.getInstance().currentUser?.uid == currentUserId) {
            _uiState.update { it.copy(errorBanner = root.localizedMessage ?: "Sync error on $tag") }
        }
    }

    fun clearBanners() {
        _uiState.update { it.copy(statusBanner = null, errorBanner = null) }
    }

    fun updateComposerText(text: String) {
        _uiState.update { it.copy(composerText = text) }
    }

    fun startNewChat() {
        activeGenerationJob?.cancel()
        val profile = userProfile.value
        val defaultProvider = AiProvider.fromWireId(profile?.defaultProvider)
        val defaultModel = profile?.defaultModelId?.ifBlank { "gemini-3.8-flash" } ?: "gemini-3.8-flash"
        _uiState.update {
            it.copy(
                activeChatId = null,
                selectedProvider = defaultProvider,
                selectedModelId = defaultModel,
                streamingContent = null,
                isGenerating = false,
                errorBanner = null
            )
        }
    }

    fun openChat(chat: ChatSession) {
        activeGenerationJob?.cancel()
        _uiState.update {
            it.copy(
                activeChatId = chat.id,
                selectedProvider = AiProvider.fromWireId(chat.provider),
                selectedModelId = chat.modelId,
                activePluginIds = chat.activePluginIds.toSet(),
                streamingContent = null,
                isGenerating = false,
                errorBanner = null
            )
        }
    }

    fun selectModel(provider: AiProvider, modelId: String) {
        _uiState.update {
            it.copy(
                selectedProvider = provider,
                selectedModelId = modelId,
                errorBanner = null
            )
        }
        val chatId = _uiState.value.activeChatId
        if (!chatId.isNullOrBlank()) {
            viewModelScope.launch {
                runCatching {
                    repository.updateChatMetadata(
                        userId = currentUserId,
                        chatId = chatId,
                        provider = provider.wireId,
                        modelId = modelId
                    )
                }
            }
        }
    }

    fun toggleChatPlugin(pluginId: String) {
        val updated = _uiState.value.activePluginIds.toMutableSet().apply {
            if (contains(pluginId)) remove(pluginId) else add(pluginId)
        }
        _uiState.update { it.copy(activePluginIds = updated) }
        val chatId = _uiState.value.activeChatId
        if (!chatId.isNullOrBlank()) {
            viewModelScope.launch {
                runCatching {
                    repository.updateChatMetadata(
                        userId = currentUserId,
                        chatId = chatId,
                        activePluginIds = updated.toList()
                    )
                }
            }
        }
    }

    fun stopGeneration() {
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        _uiState.update {
            it.copy(
                isGenerating = false,
                streamingContent = null
            )
        }
    }

    fun sendMessage(overrideText: String? = null) {
        val rawPrompt = (overrideText ?: _uiState.value.composerText).trim()
        if (rawPrompt.isBlank() || _uiState.value.isGenerating) return

        val currentState = _uiState.value
        val provider = currentState.selectedProvider
        val modelId = currentState.selectedModelId
        val profile = userProfile.value
        val systemPrompt = profile?.defaultSystemPrompt.orEmpty()
        val temperature = profile?.temperature ?: 0.7
        val streamEnabled = profile?.streamResponses ?: true

        _uiState.update {
            it.copy(
                composerText = if (overrideText == null) "" else it.composerText,
                isGenerating = true,
                streamingContent = "",
                errorBanner = null
            )
        }

        activeGenerationJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            var chatId = currentState.activeChatId
            try {
                val allPlugins = plugins.value
                val activeCustomPlugin = if (provider == AiProvider.CUSTOM) {
                    allPlugins.firstOrNull {
                        it.kind == "custom_endpoint" &&
                            (it.customModelId == modelId || it.id == modelId)
                    }
                } else null

                val apiKey = when (provider) {
                    AiProvider.DEEPMIND -> ""
                    AiProvider.CUSTOM -> activeCustomPlugin?.let { apiKeyStore.getPluginKey(it.id) }.orEmpty()
                    else -> apiKeyStore.getProviderKey(provider.wireId)
                }

                if (provider.requiresApiKey && apiKey.isBlank()) {
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            streamingContent = null,
                            errorBanner = "${provider.displayName} API key is missing. Configure it in Settings → API Keys, or switch to Google DeepMind."
                        )
                    }
                    return@launch
                }

                if (chatId.isNullOrBlank()) {
                    val autoTitle = rawPrompt.lineSequence().firstOrNull().orEmpty()
                        .take(48)
                        .ifBlank { "New conversation" }
                    chatId = repository.createChat(
                        userId = currentUserId,
                        title = autoTitle,
                        provider = provider.wireId,
                        modelId = modelId,
                        systemPrompt = systemPrompt,
                        activePluginIds = currentState.activePluginIds.toList()
                    )
                    _uiState.update { it.copy(activeChatId = chatId) }
                }

                val priorMessages = activeMessages.value
                val userTokenEst = (rawPrompt.length / 4L).coerceAtLeast(1L)
                repository.addMessage(
                    userId = currentUserId,
                    chatId = chatId,
                    role = "user",
                    content = rawPrompt,
                    provider = provider.wireId,
                    modelId = modelId,
                    tokenCountEstimate = userTokenEst,
                    latencyMs = 0L
                )

                // Execute any enabled tool/connector plugins attached to this chat
                val connectorContext = buildString {
                    val attachedConnectors = allPlugins.filter {
                        it.enabled && it.kind != "custom_endpoint" && currentState.activePluginIds.contains(it.id)
                    }
                    attachedConnectors.forEach { connector ->
                        val cKey = apiKeyStore.getPluginKey(connector.id)
                        val result = aiClient.runConnectorPlugin(connector, cKey, rawPrompt)
                        result.onSuccess { data ->
                            appendLine("[Connector: ${connector.name}]")
                            appendLine(data)
                            appendLine()
                        }
                        result.onFailure { err ->
                            repository.appendLog(
                                userId = currentUserId,
                                chatId = chatId,
                                provider = "connector",
                                modelId = connector.name,
                                status = "error",
                                latencyMs = System.currentTimeMillis() - startTime,
                                summary = err.localizedMessage ?: "Connector failed"
                            )
                        }
                    }
                }

                val effectivePrompt = if (connectorContext.isNotBlank()) {
                    "Live connector context:\n$connectorContext\nUser request:\n$rawPrompt"
                } else {
                    rawPrompt
                }

                var finalReply = ""
                aiClient.streamCompletion(
                    provider = provider,
                    modelId = modelId,
                    apiKey = apiKey,
                    history = priorMessages,
                    userPrompt = effectivePrompt,
                    systemPrompt = systemPrompt,
                    temperature = temperature,
                    streamEnabled = streamEnabled,
                    customPlugin = activeCustomPlugin
                ).collect { event ->
                    finalReply = event.accumulatedText
                    _uiState.update { it.copy(streamingContent = event.accumulatedText) }
                }

                val latency = System.currentTimeMillis() - startTime
                val assistantTokens = (finalReply.length / 4L).coerceAtLeast(1L)
                repository.addMessage(
                    userId = currentUserId,
                    chatId = chatId,
                    role = "assistant",
                    content = finalReply,
                    provider = provider.wireId,
                    modelId = modelId,
                    tokenCountEstimate = assistantTokens,
                    latencyMs = latency
                )

                val totalMessages = (priorMessages.size + 2).toLong()
                repository.updateChatMetadata(
                    userId = currentUserId,
                    chatId = chatId,
                    lastMessagePreview = finalReply.replace("\n", " ").take(140),
                    messageCount = totalMessages
                )

                repository.appendLog(
                    userId = currentUserId,
                    chatId = chatId,
                    provider = provider.wireId,
                    modelId = modelId,
                    status = "success",
                    latencyMs = latency,
                    summary = "Completed (~$assistantTokens est. tokens)"
                )

                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        streamingContent = null
                    )
                }
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isGenerating = false, streamingContent = null) }
                throw e
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - startTime
                val errMsg = e.localizedMessage ?: "Generation failed."
                Log.e(TAG, "Completion error", e)
                if (!chatId.isNullOrBlank()) {
                    runCatching {
                        repository.addMessage(
                            userId = currentUserId,
                            chatId = chatId,
                            role = "assistant",
                            content = "**Error:** $errMsg",
                            provider = provider.wireId,
                            modelId = modelId,
                            tokenCountEstimate = 0L,
                            latencyMs = latency,
                            isError = true
                        )
                        repository.appendLog(
                            userId = currentUserId,
                            chatId = chatId,
                            provider = provider.wireId,
                            modelId = modelId,
                            status = "error",
                            latencyMs = latency,
                            summary = errMsg
                        )
                    }
                }
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        streamingContent = null,
                        errorBanner = errMsg
                    )
                }
            }
        }
    }

    fun toggleChatPinned(chat: ChatSession) {
        viewModelScope.launch {
            runCatching {
                repository.updateChatMetadata(
                    userId = currentUserId,
                    chatId = chat.id,
                    pinned = !chat.pinned
                )
            }
        }
    }

    fun toggleChatArchived(chat: ChatSession) {
        viewModelScope.launch {
            runCatching {
                repository.updateChatMetadata(
                    userId = currentUserId,
                    chatId = chat.id,
                    archived = !chat.archived
                )
            }
        }
    }

    fun renameChat(chatId: String, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            runCatching {
                repository.updateChatMetadata(
                    userId = currentUserId,
                    chatId = chatId,
                    title = newTitle
                )
            }
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            runCatching {
                repository.deleteChat(currentUserId, chatId)
                if (_uiState.value.activeChatId == chatId) {
                    startNewChat()
                }
            }
        }
    }

    suspend fun loadMessagesForExport(chatId: String): List<ChatMessage> {
        return repository.getMessagesOnce(currentUserId, chatId)
    }

    // --- Prompt Library ---

    fun savePrompt(prompt: PromptTemplate) {
        viewModelScope.launch {
            runCatching {
                repository.savePrompt(currentUserId, prompt)
                _uiState.update { it.copy(statusBanner = "Prompt saved to library") }
            }.onFailure { err ->
                _uiState.update { it.copy(errorBanner = err.localizedMessage ?: "Failed to save prompt") }
            }
        }
    }

    fun togglePromptFavorite(prompt: PromptTemplate) {
        viewModelScope.launch {
            runCatching {
                repository.togglePromptFavorite(currentUserId, prompt.id, !prompt.isFavorite)
            }
        }
    }

    fun deletePrompt(promptId: String) {
        viewModelScope.launch {
            runCatching {
                repository.deletePrompt(currentUserId, promptId)
            }
        }
    }

    fun usePromptInComposer(prompt: PromptTemplate, startFreshChat: Boolean) {
        if (startFreshChat) {
            startNewChat()
        }
        _uiState.update {
            val merged = if (it.composerText.isBlank()) prompt.content
            else "${it.composerText}\n\n${prompt.content}"
            it.copy(composerText = merged)
        }
        viewModelScope.launch {
            runCatching { repository.incrementPromptUsage(currentUserId, prompt.id) }
        }
    }

    // --- Plugins & Custom API Extensions ---

    fun savePlugin(plugin: ApiPlugin, apiKey: String?) {
        viewModelScope.launch {
            runCatching {
                val savedId = repository.savePlugin(currentUserId, plugin)
                if (apiKey != null) {
                    apiKeyStore.savePluginKey(savedId, apiKey)
                }
                _uiState.update { it.copy(statusBanner = "Extension '${plugin.name}' saved") }
            }.onFailure { err ->
                _uiState.update { it.copy(errorBanner = err.localizedMessage ?: "Failed to save extension") }
            }
        }
    }

    fun togglePluginEnabled(plugin: ApiPlugin) {
        viewModelScope.launch {
            runCatching {
                repository.togglePlugin(currentUserId, plugin.id, !plugin.enabled)
            }
        }
    }

    fun deletePlugin(pluginId: String) {
        viewModelScope.launch {
            runCatching {
                repository.deletePlugin(currentUserId, pluginId)
                apiKeyStore.savePluginKey(pluginId, "")
            }
        }
    }

    fun testPluginConnection(plugin: ApiPlugin, apiKeyOverride: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingEndpoint = true, endpointTestResult = null) }
            val key = apiKeyOverride ?: if (plugin.id.isNotBlank()) apiKeyStore.getPluginKey(plugin.id) else ""
            val result = aiClient.testPluginConnection(plugin, key)
            repository.appendLog(
                userId = currentUserId,
                chatId = "",
                provider = plugin.kind,
                modelId = plugin.name,
                status = if (result.success) "success" else "error",
                latencyMs = result.latencyMs,
                summary = "HTTP ${result.statusCode}: ${result.preview.take(120)}"
            )
            _uiState.update {
                it.copy(isTestingEndpoint = false, endpointTestResult = result)
            }
        }
    }

    fun clearEndpointTestResult() {
        _uiState.update { it.copy(endpointTestResult = null) }
    }

    // --- Settings & API Keys ---

    fun saveProviderApiKey(provider: AiProvider, key: String) {
        viewModelScope.launch {
            apiKeyStore.saveProviderKey(provider.wireId, key)
            _uiState.update {
                it.copy(
                    statusBanner = if (key.isBlank()) "${provider.displayName} key cleared"
                    else "${provider.displayName} API key saved"
                )
            }
        }
    }

    fun updateSettings(
        displayName: String? = null,
        themeMode: String? = null,
        defaultProvider: String? = null,
        defaultModelId: String? = null,
        defaultSystemPrompt: String? = null,
        temperature: Double? = null,
        streamResponses: Boolean? = null,
        syncEnabled: Boolean? = null
    ) {
        viewModelScope.launch {
            runCatching {
                repository.updateUserProfile(
                    userId = currentUserId,
                    displayName = displayName,
                    themeMode = themeMode,
                    defaultProvider = defaultProvider,
                    defaultModelId = defaultModelId,
                    defaultSystemPrompt = defaultSystemPrompt,
                    temperature = temperature,
                    streamResponses = streamResponses,
                    syncEnabled = syncEnabled
                )
                _uiState.update { it.copy(statusBanner = "Preferences synced") }
            }.onFailure { err ->
                _uiState.update { it.copy(errorBanner = err.localizedMessage ?: "Failed to update settings") }
            }
        }
    }

    fun deleteLog(logId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteLog(currentUserId, logId) }
        }
    }

    fun getAvailableModelsForProvider(provider: AiProvider) =
        ModelCatalog.modelsForProvider(provider, plugins.value)

    private companion object {
        const val TAG = "AtelierViewModel"
    }
}
