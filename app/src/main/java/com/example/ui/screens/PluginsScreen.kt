package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ApiKeyStore
import com.example.data.remote.EndpointTestResult
import com.example.domain.model.ApiPlugin
import com.example.ui.theme.JetBrainsMonoFontFamily

private enum class PluginTab(val label: String) {
    ALL("All"),
    ENDPOINTS("Custom Models"),
    CONNECTORS("Connectors")
}

@Composable
fun PluginsScreen(
    plugins: List<ApiPlugin>,
    pluginKeys: Map<String, String>,
    isTesting: Boolean,
    testResult: EndpointTestResult?,
    onSavePlugin: (ApiPlugin, String?) -> Unit,
    onToggleEnabled: (ApiPlugin) -> Unit,
    onDeletePlugin: (String) -> Unit,
    onTestPlugin: (ApiPlugin, String?) -> Unit,
    onClearTestResult: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(PluginTab.ALL) }
    var editingPlugin by remember { mutableStateOf<ApiPlugin?>(null) }

    val filtered = remember(plugins, selectedTab) {
        when (selectedTab) {
            PluginTab.ALL -> plugins
            PluginTab.ENDPOINTS -> plugins.filter { it.kind == "custom_endpoint" }
            PluginTab.CONNECTORS -> plugins.filter { it.kind != "custom_endpoint" }
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
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text("Plugins & Extensions", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Connect custom OpenAI-compatible APIs, local gateways, or live REST data connectors.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PluginTab.entries.forEach { tab ->
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(tab.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No plugins in this category", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Tap + to integrate a custom LLM API endpoint or live JSON connector.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 84.dp)
                ) {
                    items(filtered, key = { it.id }) { plugin ->
                        val hasKey = !pluginKeys[plugin.id].isNullOrBlank()
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(plugin.name, style = MaterialTheme.typography.titleMedium)
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (plugin.kind == "custom_endpoint") MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Text(
                                                    text = if (plugin.kind == "custom_endpoint") "Model API" else "Connector",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (plugin.description.isNotBlank()) {
                                            Text(
                                                plugin.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = plugin.enabled,
                                        onCheckedChange = { onToggleEnabled(plugin) }
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "${plugin.httpMethod} ${plugin.baseUrl}${plugin.endpointPath}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = buildString {
                                                if (plugin.customModelId.isNotBlank()) append("Model: ${plugin.customModelId} · ")
                                                append(if (hasKey) "Key: ${ApiKeyStore.maskKey(pluginKeys[plugin.id].orEmpty())}" else "No auth token")
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row {
                                        IconButton(onClick = {
                                            onClearTestResult()
                                            editingPlugin = plugin
                                        }) {
                                            Icon(Icons.Outlined.Edit, contentDescription = "Edit extension")
                                        }
                                        IconButton(onClick = { onDeletePlugin(plugin.id) }) {
                                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete extension")
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { onTestPlugin(plugin, null) },
                                        enabled = !isTesting
                                    ) {
                                        Icon(Icons.Outlined.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Test Endpoint")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                onClearTestResult()
                editingPlugin = ApiPlugin(
                    kind = "custom_endpoint",
                    baseUrl = "https://",
                    endpointPath = "/v1/chat/completions",
                    httpMethod = "POST",
                    authHeaderName = "Authorization",
                    authScheme = "Bearer",
                    responseJsonPath = "choices[0].message.content",
                    enabled = true
                )
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_plugin_fab")
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "Add custom extension")
        }
    }

    editingPlugin?.let { initial ->
        PluginEditorDialog(
            initial = initial,
            initialKey = pluginKeys[initial.id].orEmpty(),
            isTesting = isTesting,
            testResult = testResult,
            onTest = { draft, key -> onTestPlugin(draft, key) },
            onDismiss = {
                onClearTestResult()
                editingPlugin = null
            },
            onSave = { saved, key ->
                onSavePlugin(saved, key)
                onClearTestResult()
                editingPlugin = null
            }
        )
    }

    if (editingPlugin == null && testResult != null) {
        AlertDialog(
            onDismissRequest = onClearTestResult,
            title = {
                Text(if (testResult.success) "Connection Succeeded (${testResult.statusCode})" else "Connection Failed (${testResult.statusCode})")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Latency: ${testResult.latencyMs}ms", style = MaterialTheme.typography.labelMedium)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = testResult.preview,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onClearTestResult) { Text("OK") }
            }
        )
    }
}

@Composable
private fun PluginEditorDialog(
    initial: ApiPlugin,
    initialKey: String,
    isTesting: Boolean,
    testResult: EndpointTestResult?,
    onTest: (ApiPlugin, String?) -> Unit,
    onDismiss: () -> Unit,
    onSave: (ApiPlugin, String?) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var description by remember { mutableStateOf(initial.description) }
    var kind by remember { mutableStateOf(initial.kind) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var endpointPath by remember { mutableStateOf(initial.endpointPath) }
    var httpMethod by remember { mutableStateOf(initial.httpMethod) }
    var authHeaderName by remember { mutableStateOf(initial.authHeaderName) }
    var authScheme by remember { mutableStateOf(initial.authScheme) }
    var customModelId by remember { mutableStateOf(initial.customModelId) }
    var apiKey by remember { mutableStateOf(initialKey) }
    var showKey by remember { mutableStateOf(false) }
    var requestTemplate by remember { mutableStateOf(initial.requestTemplate) }
    var responseJsonPath by remember { mutableStateOf(initial.responseJsonPath) }

    fun buildDraft(): ApiPlugin = initial.copy(
        name = name,
        description = description,
        kind = kind,
        baseUrl = baseUrl,
        endpointPath = endpointPath,
        httpMethod = httpMethod,
        authHeaderName = authHeaderName,
        authScheme = authScheme,
        customModelId = customModelId,
        requestTemplate = requestTemplate,
        responseJsonPath = responseJsonPath
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Api, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(if (initial.id.isBlank()) "New API Integration" else "Edit Integration")
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = kind == "custom_endpoint",
                        onClick = { kind = "custom_endpoint" },
                        label = { Text("Custom Model") }
                    )
                    FilterChip(
                        selected = kind == "connector",
                        onClick = { kind = "connector" },
                        label = { Text("Data Connector") }
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Extension Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL (https://...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = endpointPath,
                    onValueChange = { endpointPath = it },
                    label = { Text("Endpoint Path (e.g. /v1/chat/completions)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = httpMethod == "POST",
                        onClick = { httpMethod = "POST" },
                        label = { Text("POST") }
                    )
                    FilterChip(
                        selected = httpMethod == "GET",
                        onClick = { httpMethod = "GET" },
                        label = { Text("GET") }
                    )
                }

                if (kind == "custom_endpoint") {
                    OutlinedTextField(
                        value = customModelId,
                        onValueChange = { customModelId = it },
                        label = { Text("Model ID (e.g. llama-3.3-70b)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = authHeaderName,
                        onValueChange = { authHeaderName = it },
                        label = { Text("Auth Header") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = authScheme,
                        onValueChange = { authScheme = it },
                        label = { Text("Prefix (Bearer)") },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                }

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key / Token (stored locally)") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = "Toggle key visibility"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = responseJsonPath,
                    onValueChange = { responseJsonPath = it },
                    label = { Text("Response JSONPath (e.g. choices[0].message.content)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = requestTemplate,
                    onValueChange = { requestTemplate = it },
                    label = { Text("Optional JSON Body Template ({{prompt}})") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = { onTest(buildDraft(), apiKey) },
                    enabled = !isTesting && baseUrl.startsWith("http"),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing...")
                    } else {
                        Icon(Icons.Outlined.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Connection")
                    }
                }

                testResult?.let { res ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (res.success) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "HTTP ${res.statusCode} (${res.latencyMs}ms): ${res.preview}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && baseUrl.isNotBlank(),
                onClick = { onSave(buildDraft(), apiKey) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
