package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.configureAppCheck
import com.example.data.local.ApiKeyStore
import com.example.data.remote.MultiModelAiClient
import com.example.data.repository.AtelierRepository
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.HistoryArchiveScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PluginsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.signOutUser
import com.example.ui.theme.AtelierTheme
import com.example.ui.viewmodel.AtelierViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

private enum class StudioTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    CHAT("Chat", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    ARCHIVE("Archive", Icons.Filled.History, Icons.Outlined.History),
    LIBRARY("Library", Icons.Filled.Book, Icons.Outlined.Book),
    PLUGINS("Plugins", Icons.Filled.Extension, Icons.Outlined.Extension),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Mandatory: Configure App Check with launch intent BEFORE UI, ViewModels, or Firebase ops
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        setContent {
            AppRoot()
        }
    }
}

@Composable
private fun AppRoot() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    if (currentUser == null) {
        AtelierTheme(themeMode = "system") {
            AuthScreen(
                onAuthSuccess = { currentUser = Firebase.auth.currentUser }
            )
        }
    } else {
        val uid = currentUser!!.uid
        key(uid) {
            AuthenticatedStudioShell(currentUserId = uid)
        }
    }
}

@Composable
private fun AuthenticatedStudioShell(currentUserId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModel: AtelierViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                AtelierViewModel(
                    repository = AtelierRepository(db),
                    apiKeyStore = ApiKeyStore(app),
                    aiClient = MultiModelAiClient(),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val messages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val prompts by viewModel.prompts.collectAsStateWithLifecycle()
    val plugins by viewModel.plugins.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val providerKeys by viewModel.providerKeys.collectAsStateWithLifecycle()
    val pluginKeys by viewModel.pluginKeys.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(StudioTab.CHAT) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.statusBanner) {
        val msg = uiState.statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanners()
        }
    }

    if (currentTab != StudioTab.CHAT) {
        BackHandler { currentTab = StudioTab.CHAT }
    }

    val activeChat = remember(chats, uiState.activeChatId) {
        chats.firstOrNull { it.id == uiState.activeChatId }
    }

    AtelierTheme(themeMode = profile?.themeMode ?: "system") {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Atelier",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Button(
                            onClick = {
                                viewModel.startNewChat()
                                currentTab = StudioTab.CHAT
                                scope.launch { drawerState.close() }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.AddComment, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("New Chat")
                        }

                        HorizontalDivider()

                        Text(
                            text = "Recent Conversations",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val recent = chats.filter { !it.archived }.take(25)
                            items(recent, key = { it.id }) { chat ->
                                NavigationDrawerItem(
                                    label = {
                                        Text(
                                            text = chat.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    selected = chat.id == uiState.activeChatId && currentTab == StudioTab.CHAT,
                                    onClick = {
                                        viewModel.openChat(chat)
                                        currentTab = StudioTab.CHAT
                                        scope.launch { drawerState.close() }
                                    }
                                )
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentTab = StudioTab.SETTINGS
                                    scope.launch { drawerState.close() }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    profile?.displayName ?: "Studio Member",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    profile?.email ?: "Synced Account",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                        }
                    }
                }
            }
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useRail = maxWidth >= 700.dp
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        if (!useRail) {
                            NavigationBar(
                                windowInsets = WindowInsets.navigationBars,
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                StudioTab.entries.forEach { tab ->
                                    val selected = currentTab == tab
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { currentTab = tab },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = { Text(tab.label) },
                                        modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useRail) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                Spacer(modifier = Modifier.height(12.dp))
                                StudioTab.entries.forEach { tab ->
                                    val selected = currentTab == tab
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { currentTab = tab },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = { Text(tab.label) }
                                    )
                                }
                            }
                        }

                        when (currentTab) {
                            StudioTab.CHAT -> ChatScreen(
                                uiState = uiState,
                                activeChat = activeChat,
                                messages = messages,
                                prompts = prompts,
                                plugins = plugins,
                                providerKeys = providerKeys,
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                onNewChat = { viewModel.startNewChat() },
                                onSelectModel = { p, m -> viewModel.selectModel(p, m) },
                                onComposerChange = { viewModel.updateComposerText(it) },
                                onSendMessage = { viewModel.sendMessage() },
                                onStopGeneration = { viewModel.stopGeneration() },
                                onTogglePlugin = { viewModel.toggleChatPlugin(it) },
                                onUsePrompt = { prompt ->
                                    viewModel.usePromptInComposer(prompt, startFreshChat = false)
                                },
                                onNavigateToSettings = { currentTab = StudioTab.SETTINGS },
                                onNavigateToLibrary = { currentTab = StudioTab.LIBRARY },
                                onNavigateToPlugins = { currentTab = StudioTab.PLUGINS },
                                onDismissError = { viewModel.clearBanners() }
                            )

                            StudioTab.ARCHIVE -> HistoryArchiveScreen(
                                chats = chats,
                                logs = logs,
                                onOpenChat = { chat ->
                                    viewModel.openChat(chat)
                                    currentTab = StudioTab.CHAT
                                },
                                onTogglePin = { viewModel.toggleChatPinned(it) },
                                onToggleArchive = { viewModel.toggleChatArchived(it) },
                                onRenameChat = { id, title -> viewModel.renameChat(id, title) },
                                onDeleteChat = { viewModel.deleteChat(it) },
                                onDeleteLog = { viewModel.deleteLog(it) },
                                loadMessagesForExport = { chatId -> viewModel.loadMessagesForExport(chatId) }
                            )

                            StudioTab.LIBRARY -> LibraryScreen(
                                prompts = prompts,
                                onSavePrompt = { viewModel.savePrompt(it) },
                                onToggleFavorite = { viewModel.togglePromptFavorite(it) },
                                onDeletePrompt = { viewModel.deletePrompt(it) },
                                onUseInNewChat = { prompt ->
                                    viewModel.usePromptInComposer(prompt, startFreshChat = true)
                                    currentTab = StudioTab.CHAT
                                },
                                onInsertInCurrentChat = { prompt ->
                                    viewModel.usePromptInComposer(prompt, startFreshChat = false)
                                    currentTab = StudioTab.CHAT
                                }
                            )

                            StudioTab.PLUGINS -> PluginsScreen(
                                plugins = plugins,
                                pluginKeys = pluginKeys,
                                isTesting = uiState.isTestingEndpoint,
                                testResult = uiState.endpointTestResult,
                                onSavePlugin = { plugin, key -> viewModel.savePlugin(plugin, key) },
                                onToggleEnabled = { viewModel.togglePluginEnabled(it) },
                                onDeletePlugin = { viewModel.deletePlugin(it) },
                                onTestPlugin = { plugin, key -> viewModel.testPluginConnection(plugin, key) },
                                onClearTestResult = { viewModel.clearEndpointTestResult() }
                            )

                            StudioTab.SETTINGS -> SettingsScreen(
                                profile = profile,
                                providerKeys = providerKeys,
                                onSaveApiKey = { p, key -> viewModel.saveProviderApiKey(p, key) },
                                onUpdateSettings = { name, theme, defProv, defModel, sysPrompt, temp, stream, sync ->
                                    viewModel.updateSettings(
                                        displayName = name,
                                        themeMode = theme,
                                        defaultProvider = defProv,
                                        defaultModelId = defModel,
                                        defaultSystemPrompt = sysPrompt,
                                        temperature = temp,
                                        streamResponses = stream,
                                        syncEnabled = sync
                                    )
                                },
                                onSignOut = {
                                    signOutUser(credentialManager, scope)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
