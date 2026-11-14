package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.ChatExportHelper
import com.example.domain.model.AiProvider
import com.example.domain.model.ApiPlugin
import com.example.domain.model.ChatMessage
import com.example.domain.model.ChatSession
import com.example.domain.model.ModelCatalog
import com.example.domain.model.PromptTemplate
import com.example.ui.components.MarkdownText
import com.example.ui.viewmodel.AtelierUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: AtelierUiState,
    activeChat: ChatSession?,
    messages: List<ChatMessage>,
    prompts: List<PromptTemplate>,
    plugins: List<ApiPlugin>,
    providerKeys: Map<String, String>,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onSelectModel: (AiProvider, String) -> Unit,
    onComposerChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onStopGeneration: () -> Unit,
    onTogglePlugin: (String) -> Unit,
    onUsePrompt: (PromptTemplate) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToPlugins: () -> Unit,
    onDismissError: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    var showModelSheet by remember { mutableStateOf(false) }
    var showPluginSheet by remember { mutableStateOf(false) }

    val modelLabel = remember(uiState.selectedProvider, uiState.selectedModelId, plugins) {
        ModelCatalog.findDisplayName(uiState.selectedProvider.wireId, uiState.selectedModelId, plugins)
    }

    LaunchedEffect(messages.size, uiState.streamingContent) {
        val total = messages.size + (if (uiState.streamingContent != null) 1 else 0)
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        // Editorial Top Bar
        Surface(
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier.testTag("open_drawer_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = "Open conversations"
                    )
                }

                // Model Switcher Pill
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(50))
                        .clickable { showModelSheet = true }
                        .testTag("model_switcher_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = modelLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowDown,
                            contentDescription = "Switch model",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (activeChat != null && messages.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                val md = ChatExportHelper.formatChatAsMarkdown(activeChat, messages)
                                ChatExportHelper.shareText(context, activeChat.title, md)
                            },
                            modifier = Modifier.testTag("export_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.IosShare,
                                contentDescription = "Export conversation"
                            )
                        }
                    }
                    IconButton(
                        onClick = onNewChat,
                        modifier = Modifier.testTag("new_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AddComment,
                            contentDescription = "New chat"
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        // Error Banner
        AnimatedVisibility(visible = !uiState.errorBanner.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = uiState.errorBanner.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    if (uiState.errorBanner.orEmpty().contains("API key", ignoreCase = true)) {
                        TextButton(onClick = onNavigateToSettings) {
                            Text("Keys")
                        }
                    }
                    IconButton(onClick = onDismissError, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "Dismiss error", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Main Chat Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (messages.isEmpty() && uiState.streamingContent == null) {
                EmptyChatState(
                    provider = uiState.selectedProvider,
                    modelLabel = modelLabel,
                    prompts = prompts.take(3),
                    onUsePrompt = onUsePrompt,
                    onOpenLibrary = onNavigateToLibrary
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        if (msg.role == "user") {
                            UserMessageBubble(msg)
                        } else {
                            AssistantMessageBlock(
                                message = msg,
                                onCopy = {
                                    clipboard.setText(AnnotatedString(msg.content))
                                    Toast.makeText(context, "Copied response", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }

                    if (uiState.streamingContent != null) {
                        item(key = "streaming_bubble") {
                            StreamingAssistantBlock(
                                provider = uiState.selectedProvider,
                                modelLabel = modelLabel,
                                content = uiState.streamingContent
                            )
                        }
                    }
                }
            }
        }

        // Floating Pill Composer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                // Quick tools row above composer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val enabledConnectorsCount = plugins.count {
                        it.enabled && it.kind != "custom_endpoint" && uiState.activePluginIds.contains(it.id)
                    }
                    FilterChip(
                        selected = enabledConnectorsCount > 0,
                        onClick = { showPluginSheet = true },
                        label = {
                            Text(
                                if (enabledConnectorsCount > 0) "$enabledConnectorsCount Connectors Active"
                                else "Connectors"
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.Extension, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                    AssistChip(
                        onClick = onNavigateToLibrary,
                        label = { Text("Prompt Library") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Book, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                    AssistChip(
                        onClick = { showModelSheet = true },
                        label = { Text("${uiState.selectedProvider.badge} · $modelLabel") }
                    )
                }

                // Composer Box
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    tonalElevation = 2.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        TextField(
                            value = uiState.composerText,
                            onValueChange = onComposerChange,
                            placeholder = {
                                Text(
                                    text = "Message $modelLabel...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            maxLines = 6,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_composer_input")
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        if (uiState.isGenerating) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier
                                    .padding(bottom = 4.dp)
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable { onStopGeneration() }
                                    .testTag("stop_generation_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Stop,
                                        contentDescription = "Stop generation",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        } else {
                            val canSend = uiState.composerText.isNotBlank()
                            Surface(
                                shape = CircleShape,
                                color = if (canSend) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .padding(bottom = 4.dp)
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = canSend) { onSendMessage() }
                                    .testTag("send_message_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.Send,
                                        contentDescription = "Send message",
                                        tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showModelSheet) {
        ModalBottomSheet(onDismissRequest = { showModelSheet = false }) {
            ModelSelectorSheetContent(
                selectedProvider = uiState.selectedProvider,
                selectedModelId = uiState.selectedModelId,
                plugins = plugins,
                providerKeys = providerKeys,
                onSelect = { provider, modelId ->
                    onSelectModel(provider, modelId)
                    showModelSheet = false
                },
                onConfigureKeys = {
                    showModelSheet = false
                    onNavigateToSettings()
                },
                onManagePlugins = {
                    showModelSheet = false
                    onNavigateToPlugins()
                }
            )
        }
    }

    if (showPluginSheet) {
        ModalBottomSheet(onDismissRequest = { showPluginSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Session Connectors",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Enabled connectors run before your prompt is sent and inject live API data into context.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val connectors = plugins.filter { it.kind != "custom_endpoint" && it.enabled }
                if (connectors.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("No enabled connectors yet.", style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = {
                                showPluginSheet = false
                                onNavigateToPlugins()
                            }) {
                                Text("Configure in Plugins & Extensions")
                            }
                        }
                    }
                } else {
                    connectors.forEach { plugin ->
                        val active = uiState.activePluginIds.contains(plugin.id)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTogglePlugin(plugin.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = active,
                                    onCheckedChange = { onTogglePlugin(plugin.id) }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(plugin.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        plugin.description.ifBlank { plugin.baseUrl },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChatState(
    provider: AiProvider,
    modelLabel: String,
    prompts: List<PromptTemplate>,
    onUsePrompt: (PromptTemplate) -> Unit,
    onOpenLibrary: () -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_atelier_studio_1794670239856),
                contentDescription = "Atelier welcome illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "What shall we explore today?",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Active engine: ${provider.displayName} · $modelLabel",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (prompts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quick Starters from Library",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onOpenLibrary) {
                    Text("View all")
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                prompts.forEach { prompt ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onUsePrompt(prompt) }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(prompt.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    prompt.description.ifBlank { prompt.content },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserMessageBubble(msg: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Text(
                text = msg.content,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun AssistantMessageBlock(
    message: ChatMessage,
    onCopy: () -> Unit
) {
    val provider = AiProvider.fromWireId(message.provider)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = "${provider.displayName} · ${message.modelId}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (message.latencyMs > 0L) {
                Text(
                    text = "${message.latencyMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        MarkdownText(
            markdown = message.content,
            textColor = if (message.isError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy response",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StreamingAssistantBlock(
    provider: AiProvider,
    modelLabel: String,
    content: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "stream_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "pulse_alpha"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .alpha(pulseAlpha)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Text(
                text = "${provider.displayName} · $modelLabel · Streaming…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (content.isNotBlank()) {
            MarkdownText(markdown = content)
        }
    }
}

@Composable
private fun ModelSelectorSheetContent(
    selectedProvider: AiProvider,
    selectedModelId: String,
    plugins: List<ApiPlugin>,
    providerKeys: Map<String, String>,
    onSelect: (AiProvider, String) -> Unit,
    onConfigureKeys: () -> Unit,
    onManagePlugins: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Switch Model & Provider", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onConfigureKeys) {
                Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("API Keys")
            }
        }

        AiProvider.entries.forEach { provider ->
            val models = ModelCatalog.modelsForProvider(provider, plugins)
            val hasKey = !provider.requiresApiKey || !providerKeys[provider.wireId].isNullOrBlank()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(provider.displayName, style = MaterialTheme.typography.titleMedium)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = provider.badge,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = when {
                            provider == AiProvider.DEEPMIND -> "Ready (Built-in)"
                            provider == AiProvider.CUSTOM -> if (models.isEmpty()) "None enabled" else "${models.size} active"
                            hasKey -> "Key configured"
                            else -> "Key required"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasKey) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                    )
                }

                if (models.isEmpty() && provider == AiProvider.CUSTOM) {
                    TextButton(onClick = onManagePlugins) {
                        Text("Create a custom API endpoint in Plugins →")
                    }
                } else {
                    models.forEach { spec ->
                        val isSelected = selectedProvider == provider && selectedModelId == spec.id
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelect(provider, spec.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(spec.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${spec.id} · ${spec.contextLabel} · ${spec.speedLabel}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
