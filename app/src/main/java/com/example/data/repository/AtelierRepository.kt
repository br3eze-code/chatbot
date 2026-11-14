package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.domain.model.AiProvider
import com.example.domain.model.ApiExecutionLog
import com.example.domain.model.ApiPlugin
import com.example.domain.model.ChatMessage
import com.example.domain.model.ChatSession
import com.example.domain.model.PromptTemplate
import com.example.domain.model.UserProfile
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AtelierRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun userDoc(userId: String) = db.collection("users").document(userId)
    private fun chatsCol(userId: String) = userDoc(userId).collection("chats")
    private fun messagesCol(userId: String, chatId: String) =
        chatsCol(userId).document(chatId).collection("messages")
    private fun promptsCol(userId: String) = userDoc(userId).collection("prompts")
    private fun pluginsCol(userId: String) = userDoc(userId).collection("plugins")
    private fun logsCol(userId: String) = userDoc(userId).collection("logs")

    private fun unwrapAndLog(error: Throwable, op: OperationType, path: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        handleFirestoreError(firestoreError ?: error, op, path)
    }

    // --- User Profile & Synced Preferences ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        val docRef = userDoc(userId)
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else null
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.GET, docRef.path)
                throw error
            }
    }

    suspend fun ensureUserProfile(
        userId: String,
        displayName: String?,
        email: String?,
        photoUrl: String?
    ) {
        val docRef = userDoc(userId)
        try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val safeName = displayName?.takeIf { it.isNotBlank() }?.take(80) ?: "Studio Member"
                val payload = buildMap<String, Any> {
                    put("userId", userId)
                    put("displayName", safeName)
                    if (!email.isNullOrBlank()) put("email", email.take(120))
                    if (!photoUrl.isNullOrBlank()) put("photoUrl", photoUrl.take(500))
                    put("themeMode", "system")
                    put("defaultProvider", AiProvider.DEEPMIND.wireId)
                    put("defaultModelId", "gemini-3.8-flash")
                    put("defaultSystemPrompt", "You are Atelier, a thoughtful, precise assistant. Format answers cleanly in Markdown.")
                    put("temperature", 0.7)
                    put("streamResponses", true)
                    put("syncEnabled", true)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun updateUserProfile(
        userId: String,
        displayName: String? = null,
        themeMode: String? = null,
        defaultProvider: String? = null,
        defaultModelId: String? = null,
        defaultSystemPrompt: String? = null,
        temperature: Double? = null,
        streamResponses: Boolean? = null,
        syncEnabled: Boolean? = null
    ) {
        val docRef = userDoc(userId)
        val updates = buildMap<String, Any> {
            displayName?.let { put("displayName", it.trim().ifBlank { "Studio Member" }.take(80)) }
            themeMode?.let { put("themeMode", it) }
            defaultProvider?.let { put("defaultProvider", it) }
            defaultModelId?.let { put("defaultModelId", it.take(100)) }
            defaultSystemPrompt?.let { put("defaultSystemPrompt", it.take(4000)) }
            temperature?.let { put("temperature", it.coerceIn(0.0, 2.0)) }
            streamResponses?.let { put("streamResponses", it) }
            syncEnabled?.let { put("syncEnabled", it) }
            put("updatedAt", FieldValue.serverTimestamp())
        }
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- Chats & Searchable Archive ---

    fun observeChats(userId: String): Flow<List<ChatSession>> {
        val col = chatsCol(userId)
        return col.orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ChatSession::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, col.path)
                throw error
            }
    }

    suspend fun createChat(
        userId: String,
        title: String,
        provider: String,
        modelId: String,
        systemPrompt: String = "",
        activePluginIds: List<String> = emptyList()
    ): String {
        val chatId = UUID.randomUUID().toString()
        val docRef = chatsCol(userId).document(chatId)
        val payload = mapOf(
            "userId" to userId,
            "title" to title.trim().ifBlank { "New conversation" }.take(120),
            "provider" to provider,
            "modelId" to modelId.take(100),
            "systemPrompt" to systemPrompt.take(4000),
            "lastMessagePreview" to "",
            "messageCount" to 0L,
            "archived" to false,
            "pinned" to false,
            "activePluginIds" to activePluginIds.take(20),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            return chatId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateChatMetadata(
        userId: String,
        chatId: String,
        title: String? = null,
        provider: String? = null,
        modelId: String? = null,
        systemPrompt: String? = null,
        lastMessagePreview: String? = null,
        messageCount: Long? = null,
        archived: Boolean? = null,
        pinned: Boolean? = null,
        activePluginIds: List<String>? = null
    ) {
        val docRef = chatsCol(userId).document(chatId)
        val updates = buildMap<String, Any> {
            title?.let { put("title", it.trim().ifBlank { "Untitled" }.take(120)) }
            provider?.let { put("provider", it) }
            modelId?.let { put("modelId", it.take(100)) }
            systemPrompt?.let { put("systemPrompt", it.take(4000)) }
            lastMessagePreview?.let { put("lastMessagePreview", it.take(300)) }
            messageCount?.let { put("messageCount", it.coerceAtLeast(0L)) }
            archived?.let { put("archived", it) }
            pinned?.let { put("pinned", it) }
            activePluginIds?.let { put("activePluginIds", it.take(20)) }
            put("updatedAt", FieldValue.serverTimestamp())
        }
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteChat(userId: String, chatId: String) {
        val docRef = chatsCol(userId).document(chatId)
        try {
            val messagesSnap = messagesCol(userId, chatId).get().await()
            val batch = db.batch()
            messagesSnap.documents.forEach { batch.delete(it.reference) }
            batch.delete(docRef)
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- Messages ---

    fun observeMessages(userId: String, chatId: String): Flow<List<ChatMessage>> {
        val col = messagesCol(userId, chatId)
        return col.orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ChatMessage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, col.path)
                throw error
            }
    }

    suspend fun getMessagesOnce(userId: String, chatId: String): List<ChatMessage> {
        val col = messagesCol(userId, chatId)
        return try {
            col.orderBy("createdAt", Query.Direction.ASCENDING)
                .get()
                .await()
                .toObjects(
                    ChatMessage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, col.path)
            throw e
        }
    }

    suspend fun addMessage(
        userId: String,
        chatId: String,
        role: String,
        content: String,
        provider: String,
        modelId: String,
        tokenCountEstimate: Long,
        latencyMs: Long,
        isError: Boolean = false
    ): String {
        val msgId = UUID.randomUUID().toString()
        val docRef = messagesCol(userId, chatId).document(msgId)
        val payload = mapOf(
            "userId" to userId,
            "chatId" to chatId,
            "role" to role,
            "content" to content.ifBlank { "..." }.take(100000),
            "provider" to provider.take(40),
            "modelId" to modelId.take(100),
            "tokenCountEstimate" to tokenCountEstimate.coerceAtLeast(0L),
            "latencyMs" to latencyMs.coerceAtLeast(0L),
            "isError" to isError,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            return msgId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    // --- Prompt Library ---

    fun observePrompts(userId: String): Flow<List<PromptTemplate>> {
        val col = promptsCol(userId)
        return col.orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    PromptTemplate::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, col.path)
                throw error
            }
    }

    suspend fun savePrompt(userId: String, prompt: PromptTemplate): String {
        val isNew = prompt.id.isBlank()
        val promptId = if (isNew) UUID.randomUUID().toString() else prompt.id
        val docRef = promptsCol(userId).document(promptId)
        try {
            if (isNew) {
                val payload = mapOf(
                    "userId" to userId,
                    "title" to prompt.title.trim().ifBlank { "Untitled Prompt" }.take(100),
                    "description" to prompt.description.trim().take(300),
                    "content" to prompt.content.trim().ifBlank { "..." }.take(10000),
                    "category" to prompt.category.trim().ifBlank { "General" }.take(50),
                    "tags" to prompt.tags.take(10),
                    "isFavorite" to prompt.isFavorite,
                    "usageCount" to prompt.usageCount.coerceAtLeast(0L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val updates = mapOf(
                    "title" to prompt.title.trim().ifBlank { "Untitled Prompt" }.take(100),
                    "description" to prompt.description.trim().take(300),
                    "content" to prompt.content.trim().ifBlank { "..." }.take(10000),
                    "category" to prompt.category.trim().ifBlank { "General" }.take(50),
                    "tags" to prompt.tags.take(10),
                    "isFavorite" to prompt.isFavorite,
                    "usageCount" to prompt.usageCount.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            }
            return promptId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isNew) OperationType.CREATE else OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun togglePromptFavorite(userId: String, promptId: String, isFavorite: Boolean) {
        val docRef = promptsCol(userId).document(promptId)
        try {
            docRef.update(
                mapOf(
                    "isFavorite" to isFavorite,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun incrementPromptUsage(userId: String, promptId: String) {
        val docRef = promptsCol(userId).document(promptId)
        try {
            docRef.update(
                mapOf(
                    "usageCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deletePrompt(userId: String, promptId: String) {
        val docRef = promptsCol(userId).document(promptId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- Plugins & Custom API Extensions ---

    fun observePlugins(userId: String): Flow<List<ApiPlugin>> {
        val col = pluginsCol(userId)
        return col.orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ApiPlugin::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, col.path)
                throw error
            }
    }

    suspend fun savePlugin(userId: String, plugin: ApiPlugin): String {
        val isNew = plugin.id.isBlank()
        val pluginId = if (isNew) UUID.randomUUID().toString() else plugin.id
        val docRef = pluginsCol(userId).document(pluginId)
        try {
            if (isNew) {
                val payload = mapOf(
                    "userId" to userId,
                    "name" to plugin.name.trim().ifBlank { "Custom Extension" }.take(80),
                    "description" to plugin.description.trim().take(300),
                    "kind" to plugin.kind,
                    "baseUrl" to plugin.baseUrl.trim().take(500),
                    "endpointPath" to plugin.endpointPath.trim().take(200),
                    "httpMethod" to plugin.httpMethod,
                    "authHeaderName" to plugin.authHeaderName.trim().take(80),
                    "authScheme" to plugin.authScheme.trim().take(40),
                    "customModelId" to plugin.customModelId.trim().take(100),
                    "enabled" to plugin.enabled,
                    "requestTemplate" to plugin.requestTemplate.take(4000),
                    "responseJsonPath" to plugin.responseJsonPath.trim().take(120),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val updates = mapOf(
                    "name" to plugin.name.trim().ifBlank { "Custom Extension" }.take(80),
                    "description" to plugin.description.trim().take(300),
                    "kind" to plugin.kind,
                    "baseUrl" to plugin.baseUrl.trim().take(500),
                    "endpointPath" to plugin.endpointPath.trim().take(200),
                    "httpMethod" to plugin.httpMethod,
                    "authHeaderName" to plugin.authHeaderName.trim().take(80),
                    "authScheme" to plugin.authScheme.trim().take(40),
                    "customModelId" to plugin.customModelId.trim().take(100),
                    "enabled" to plugin.enabled,
                    "requestTemplate" to plugin.requestTemplate.take(4000),
                    "responseJsonPath" to plugin.responseJsonPath.trim().take(120),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            }
            return pluginId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isNew) OperationType.CREATE else OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun togglePlugin(userId: String, pluginId: String, enabled: Boolean) {
        val docRef = pluginsCol(userId).document(pluginId)
        try {
            docRef.update(
                mapOf(
                    "enabled" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deletePlugin(userId: String, pluginId: String) {
        val docRef = pluginsCol(userId).document(pluginId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- Exportable API Execution Logs ---

    fun observeLogs(userId: String): Flow<List<ApiExecutionLog>> {
        val col = logsCol(userId)
        return col.orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ApiExecutionLog::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, col.path)
                throw error
            }
    }

    suspend fun appendLog(
        userId: String,
        chatId: String,
        provider: String,
        modelId: String,
        status: String,
        latencyMs: Long,
        summary: String
    ) {
        val logId = UUID.randomUUID().toString()
        val docRef = logsCol(userId).document(logId)
        val payload = mapOf(
            "userId" to userId,
            "chatId" to chatId.take(128),
            "provider" to provider.ifBlank { "unknown" }.take(40),
            "modelId" to modelId.ifBlank { "unknown" }.take(100),
            "status" to status,
            "latencyMs" to latencyMs.coerceAtLeast(0L),
            "summary" to summary.take(500),
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
        }
    }

    suspend fun deleteLog(userId: String, logId: String) {
        val docRef = logsCol(userId).document(logId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- Starter Library & Connector Seeding (First Sign-In) ---

    suspend fun seedStarterPromptsAndPluginsIfEmpty(userId: String) {
        try {
            val existingPrompts = promptsCol(userId).limit(1).get().await()
            if (existingPrompts.isEmpty) {
                val starters = listOf(
                    PromptTemplate(
                        title = "Architecture Critic",
                        description = "Deep systems review with trade-offs and failure modes",
                        content = "Review this technical design critically. Identify hidden coupling, concurrency hazards, data integrity gaps, and propose a cleaner alternative with concrete trade-offs.",
                        category = "Engineering",
                        tags = listOf("architecture", "review"),
                        isFavorite = true
                    ),
                    PromptTemplate(
                        title = "Executive Brief",
                        description = "Distill dense notes into an editorial 3-part summary",
                        content = "Synthesize the following notes into: (1) Key Decisions, (2) Open Risks & Blockers, and (3) Concrete Next Steps with owners. Keep prose crisp and scannable.",
                        category = "Writing",
                        tags = listOf("summary", "clarity"),
                        isFavorite = true
                    ),
                    PromptTemplate(
                        title = "Socratic Tutor",
                        description = "Step-by-step guided reasoning without giving away the answer immediately",
                        content = "Act as a patient Socratic mentor. Help me understand the following concept by asking one diagnostic question at a time and building on my intuition.",
                        category = "Research",
                        tags = listOf("learning", "reasoning"),
                        isFavorite = false
                    ),
                    PromptTemplate(
                        title = "SQL & Schema Optimizer",
                        description = "Analyze query plans, indexing strategies, and normalization",
                        content = "Analyze this database schema and query workload. Recommend composite indexes, denormalization trade-offs, and rewritten SQL or Firestore queries.",
                        category = "Engineering",
                        tags = listOf("database", "performance"),
                        isFavorite = false
                    )
                )
                starters.forEach { savePrompt(userId, it) }
            }

            val existingPlugins = pluginsCol(userId).limit(1).get().await()
            if (existingPlugins.isEmpty) {
                val starterPlugins = listOf(
                    ApiPlugin(
                        name = "OpenRouter / Ollama Bridge",
                        description = "OpenAI-compatible custom endpoint template for local or gateway models",
                        kind = "custom_endpoint",
                        baseUrl = "https://openrouter.ai/api",
                        endpointPath = "/v1/chat/completions",
                        httpMethod = "POST",
                        authHeaderName = "Authorization",
                        authScheme = "Bearer",
                        customModelId = "anthropic/claude-3.5-sonnet",
                        enabled = false,
                        requestTemplate = "",
                        responseJsonPath = "choices[0].message.content"
                    ),
                    ApiPlugin(
                        name = "Live JSON Context Connector",
                        description = "Fetches structured JSON context from a REST endpoint before each reply",
                        kind = "connector",
                        baseUrl = "https://jsonplaceholder.typicode.com",
                        endpointPath = "/todos/1",
                        httpMethod = "GET",
                        authHeaderName = "Authorization",
                        authScheme = "",
                        customModelId = "",
                        enabled = false,
                        requestTemplate = "",
                        responseJsonPath = "title"
                    )
                )
                starterPlugins.forEach { savePlugin(userId, it) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$userId")
        }
    }
}
