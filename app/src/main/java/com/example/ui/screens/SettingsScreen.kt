package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.ApiKeyStore
import com.example.domain.model.AiProvider
import com.example.domain.model.ModelCatalog
import com.example.domain.model.UserProfile
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    profile: UserProfile?,
    providerKeys: Map<String, String>,
    onSaveApiKey: (AiProvider, String) -> Unit,
    onUpdateSettings: (
        displayName: String?,
        themeMode: String?,
        defaultProvider: String?,
        defaultModelId: String?,
        defaultSystemPrompt: String?,
        temperature: Double?,
        streamResponses: Boolean?,
        syncEnabled: Boolean?
    ) -> Unit,
    onSignOut: () -> Unit
) {
    var editingKeyProvider by remember { mutableStateOf<AiProvider?>(null) }
    var editingProfileName by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf(profile?.displayName ?: "") }
    var systemPromptDraft by remember { mutableStateOf(profile?.defaultSystemPrompt ?: "") }
    var tempDraft by remember { mutableDoubleStateOf(profile?.temperature ?: 0.7) }

    LaunchedEffect(profile) {
        if (profile != null) {
            nameDraft = profile.displayName
            systemPromptDraft = profile.defaultSystemPrompt
            tempDraft = profile.temperature
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("Settings & Account", style = MaterialTheme.typography.headlineMedium)

            // 1. User Profile Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (!profile?.photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = profile?.photoUrl,
                                contentDescription = "Profile photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (profile?.displayName?.firstOrNull() ?: 'A').uppercase(),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile?.displayName ?: "Studio Member",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = profile?.email ?: "Google Account",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { editingProfileName = true }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit profile name")
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Outlined.CloudDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text("Cross-Platform Sync", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Chats, prompts, plugins & settings synced",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = profile?.syncEnabled ?: true,
                            onCheckedChange = { enabled ->
                                onUpdateSettings(null, null, null, null, null, null, null, enabled)
                            }
                        )
                    }
                }
            }

            // 2. Appearance & Dark Mode
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Appearance & Dark Mode", style = MaterialTheme.typography.titleMedium)
                    }
                    val currentTheme = profile?.themeMode ?: "system"
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (mode, label) ->
                            FilterChip(
                                selected = currentTheme == mode,
                                onClick = { onUpdateSettings(null, mode, null, null, null, null, null, null) },
                                label = { Text(label) },
                                leadingIcon = {
                                    when (mode) {
                                        "light" -> Icon(Icons.Outlined.LightMode, null, Modifier.size(16.dp))
                                        "dark" -> Icon(Icons.Outlined.DarkMode, null, Modifier.size(16.dp))
                                        else -> Icon(Icons.Outlined.SettingsSuggest, null, Modifier.size(16.dp))
                                    }
                                },
                                modifier = Modifier.testTag("theme_chip_$mode")
                            )
                        }
                    }
                }
            }

            // 3. Model & Inference Preferences
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Model & Inference Preferences", style = MaterialTheme.typography.titleMedium)
                    }

                    Text("Default Provider", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val activeDefProvider = AiProvider.fromWireId(profile?.defaultProvider)
                        listOf(AiProvider.DEEPMIND, AiProvider.ANTHROPIC, AiProvider.OPENAI).forEach { p ->
                            FilterChip(
                                selected = activeDefProvider == p,
                                onClick = {
                                    val firstModel = ModelCatalog.modelsForProvider(p).firstOrNull()?.id ?: "gemini-3.8-flash"
                                    onUpdateSettings(null, null, p.wireId, firstModel, null, null, null, null)
                                },
                                label = { Text(p.badge) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val activeDefProvider = AiProvider.fromWireId(profile?.defaultProvider)
                        listOf(AiProvider.DEEPSEEK, AiProvider.MOONSHOT).forEach { p ->
                            FilterChip(
                                selected = activeDefProvider == p,
                                onClick = {
                                    val firstModel = ModelCatalog.modelsForProvider(p).firstOrNull()?.id ?: "deepseek-chat"
                                    onUpdateSettings(null, null, p.wireId, firstModel, null, null, null, null)
                                },
                                label = { Text(p.displayName) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Streaming Responses", style = MaterialTheme.typography.titleSmall)
                        Switch(
                            checked = profile?.streamResponses ?: true,
                            onCheckedChange = { stream ->
                                onUpdateSettings(null, null, null, null, null, null, stream, null)
                            }
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Temperature", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = String.format("%.1f", tempDraft),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Slider(
                            value = tempDraft.toFloat(),
                            onValueChange = { tempDraft = (it * 10f).roundToInt() / 10.0 },
                            onValueChangeFinished = {
                                onUpdateSettings(null, null, null, null, null, tempDraft, null, null)
                            },
                            valueRange = 0f..1.5f
                        )
                    }

                    OutlinedTextField(
                        value = systemPromptDraft,
                        onValueChange = { systemPromptDraft = it },
                        label = { Text("Global System Prompt") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            onUpdateSettings(null, null, null, null, systemPromptDraft, null, null, null)
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save System Prompt")
                    }
                }
            }

            // 4. API Key Configuration
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("API Key Configuration", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Stored privately on-device for direct provider requests.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // DeepMind built-in card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Google DeepMind (Gemini)", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Authenticated automatically via Firebase AI Logic",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = "Ready",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    listOf(
                        AiProvider.ANTHROPIC,
                        AiProvider.OPENAI,
                        AiProvider.DEEPSEEK,
                        AiProvider.MOONSHOT
                    ).forEach { provider ->
                        val rawKey = providerKeys[provider.wireId].orEmpty()
                        val configured = rawKey.isNotBlank()
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { editingKeyProvider = provider }
                                .testTag("api_key_row_${provider.wireId}")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(provider.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = if (configured) ApiKeyStore.maskKey(rawKey) else "Tap to add API key",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                        color = if (configured) MaterialTheme.colorScheme.secondary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { editingKeyProvider = provider }) {
                                    Text(if (configured) "Edit" else "Configure")
                                }
                            }
                        }
                    }
                }
            }

            // 5. Sign Out
            OutlinedButton(
                onClick = onSignOut,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    editingKeyProvider?.let { provider ->
        var keyInput by remember(provider) { mutableStateOf(providerKeys[provider.wireId].orEmpty()) }
        var showPlain by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { editingKeyProvider = null },
            title = { Text("${provider.displayName} API Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Endpoint: ${provider.defaultBaseUrl}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("API Key (${provider.keyHint})") },
                        singleLine = true,
                        visualTransformation = if (showPlain) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPlain = !showPlain }) {
                                Icon(
                                    imageVector = if (showPlain) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = "Toggle key visibility"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSaveApiKey(provider, keyInput)
                    editingKeyProvider = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                Row {
                    if (!providerKeys[provider.wireId].isNullOrBlank()) {
                        TextButton(onClick = {
                            onSaveApiKey(provider, "")
                            editingKeyProvider = null
                        }) {
                            Text("Clear", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { editingKeyProvider = null }) { Text("Cancel") }
                }
            }
        )
    }

    if (editingProfileName) {
        AlertDialog(
            onDismissRequest = { editingProfileName = false },
            title = { Text("Update Display Name") },
            text = {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    label = { Text("Display Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateSettings(nameDraft, null, null, null, null, null, null, null)
                    editingProfileName = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingProfileName = false }) { Text("Cancel") }
            }
        )
    }
}
