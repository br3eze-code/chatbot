package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.ChatExportHelper
import com.example.domain.model.AiProvider
import com.example.domain.model.ApiExecutionLog
import com.example.domain.model.ChatMessage
import com.example.domain.model.ChatSession
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private enum class ArchiveFilter(val label: String) {
    ALL("Active"),
    PINNED("Pinned"),
    ARCHIVED("Archived"),
    LOGS("Exported Logs")
}

@Composable
fun HistoryArchiveScreen(
    chats: List<ChatSession>,
    logs: List<ApiExecutionLog>,
    onOpenChat: (ChatSession) -> Unit,
    onTogglePin: (ChatSession) -> Unit,
    onToggleArchive: (ChatSession) -> Unit,
    onRenameChat: (String, String) -> Unit,
    onDeleteChat: (String) -> Unit,
    onDeleteLog: (String) -> Unit,
    loadMessagesForExport: suspend (String) -> List<ChatMessage>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(ArchiveFilter.ALL) }
    var renamingChat by remember { mutableStateOf<ChatSession?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var exportTargetChat by remember { mutableStateOf<ChatSession?>(null) }
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    val filteredChats = remember(chats, searchQuery, selectedFilter) {
        val q = searchQuery.trim().lowercase()
        chats.filter { chat ->
            val matchesFilter = when (selectedFilter) {
                ArchiveFilter.ALL -> !chat.archived
                ArchiveFilter.PINNED -> chat.pinned && !chat.archived
                ArchiveFilter.ARCHIVED -> chat.archived
                ArchiveFilter.LOGS -> false
            }
            val matchesQuery = q.isEmpty() ||
                chat.title.lowercase().contains(q) ||
                chat.lastMessagePreview.lowercase().contains(q) ||
                chat.modelId.lowercase().contains(q) ||
                chat.provider.lowercase().contains(q)
            matchesFilter && matchesQuery
        }.sortedWith(compareByDescending<ChatSession> { it.pinned }.thenByDescending { it.updatedAt?.seconds ?: 0L })
    }

    val filteredLogs = remember(logs, searchQuery) {
        val q = searchQuery.trim().lowercase()
        logs.filter { log ->
            q.isEmpty() ||
                log.modelId.lowercase().contains(q) ||
                log.provider.lowercase().contains(q) ||
                log.summary.lowercase().contains(q) ||
                log.status.lowercase().contains(q)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("History & Archive", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${chats.size} synced threads · ${logs.size} execution logs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (selectedFilter == ArchiveFilter.LOGS && filteredLogs.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            val report = ChatExportHelper.formatLogsAsMarkdown(filteredLogs)
                            ChatExportHelper.shareText(context, "Atelier Execution Logs", report)
                        }
                    ) {
                        Icon(Icons.Outlined.IosShare, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export Logs")
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search conversations, models, or logs...") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("archive_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ArchiveFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedFilter == ArchiveFilter.LOGS) {
                if (filteredLogs.isEmpty()) {
                    ArchiveEmptyState(
                        icon = Icons.Outlined.Terminal,
                        title = "No execution logs yet",
                        subtitle = "Model completions and custom endpoint tests appear here for export."
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(filteredLogs, key = { it.id }) { log ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (log.status == "success") MaterialTheme.colorScheme.secondaryContainer
                                                else MaterialTheme.colorScheme.errorContainer
                                            ) {
                                                Text(
                                                    text = log.status.uppercase(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (log.status == "success") MaterialTheme.colorScheme.onSecondaryContainer
                                                    else MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text("${log.provider} · ${log.modelId}", style = MaterialTheme.typography.titleSmall)
                                        }
                                        Text(log.summary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        val dateLabel = log.createdAt?.toDate()?.let { dateFormat.format(it) } ?: "just now"
                                        Text(
                                            "$dateLabel · ${log.latencyMs}ms",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { onDeleteLog(log.id) }) {
                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete log")
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (filteredChats.isEmpty()) {
                ArchiveEmptyState(
                    icon = Icons.Outlined.History,
                    title = "No matching conversations",
                    subtitle = "Start a new chat or adjust your search query."
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredChats, key = { it.id }) { chat ->
                        val provider = AiProvider.fromWireId(chat.provider)
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { onOpenChat(chat) }
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = chat.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    val dateStr = chat.updatedAt?.toDate()?.let { dateFormat.format(it) } ?: ""
                                    Text(
                                        text = dateStr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (chat.lastMessagePreview.isNotBlank()) {
                                    Text(
                                        text = chat.lastMessagePreview,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = "${provider.badge} · ${chat.modelId} · ${chat.messageCount} msgs",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Row {
                                        IconButton(onClick = { onTogglePin(chat) }, modifier = Modifier.size(38.dp)) {
                                            Icon(
                                                imageVector = Icons.Outlined.PushPin,
                                                contentDescription = "Pin chat",
                                                tint = if (chat.pinned) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                renamingChat = chat
                                                renameInput = chat.title
                                            },
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = "Rename", modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(onClick = { exportTargetChat = chat }, modifier = Modifier.size(38.dp)) {
                                            Icon(Icons.Outlined.IosShare, contentDescription = "Export", modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(onClick = { onToggleArchive(chat) }, modifier = Modifier.size(38.dp)) {
                                            Icon(
                                                imageVector = if (chat.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                                                contentDescription = if (chat.archived) "Unarchive" else "Archive",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(onClick = { onDeleteChat(chat.id) }, modifier = Modifier.size(38.dp)) {
                                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    renamingChat?.let { target ->
        AlertDialog(
            onDismissRequest = { renamingChat = null },
            title = { Text("Rename Conversation") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    label = { Text("Title") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRenameChat(target.id, renameInput)
                    renamingChat = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renamingChat = null }) { Text("Cancel") }
            }
        )
    }

    exportTargetChat?.let { target ->
        AlertDialog(
            onDismissRequest = { exportTargetChat = null },
            title = { Text("Export '${target.title}'") },
            text = { Text("Choose export format to share or save this synced conversation.") },
            confirmButton = {
                TextButton(onClick = {
                    val c = target
                    exportTargetChat = null
                    scope.launch {
                        val msgs = loadMessagesForExport(c.id)
                        if (msgs.isEmpty()) {
                            Toast.makeText(context, "Conversation has no messages yet", Toast.LENGTH_SHORT).show()
                        } else {
                            ChatExportHelper.shareText(context, "${c.title}.md", ChatExportHelper.formatChatAsMarkdown(c, msgs))
                        }
                    }
                }) { Text("Markdown (.md)") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val c = target
                    exportTargetChat = null
                    scope.launch {
                        val msgs = loadMessagesForExport(c.id)
                        if (msgs.isEmpty()) {
                            Toast.makeText(context, "Conversation has no messages yet", Toast.LENGTH_SHORT).show()
                        } else {
                            ChatExportHelper.shareText(context, "${c.title}.json", ChatExportHelper.formatChatAsJson(c, msgs))
                        }
                    }
                }) { Text("JSON (.json)") }
            }
        )
    }
}

@Composable
private fun ArchiveEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
