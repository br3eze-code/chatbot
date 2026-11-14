package com.example.core

import android.content.Context
import android.content.Intent
import com.example.domain.model.ApiExecutionLog
import com.example.domain.model.ChatMessage
import com.example.domain.model.ChatSession
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ChatExportHelper {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun formatChatAsMarkdown(chat: ChatSession, messages: List<ChatMessage>): String {
        return buildString {
            appendLine("# ${chat.title}")
            appendLine()
            appendLine("- **Provider:** ${chat.provider}")
            appendLine("- **Model:** `${chat.modelId}`")
            appendLine("- **Messages:** ${messages.size}")
            chat.updatedAt?.toDate()?.let {
                appendLine("- **Updated:** ${isoFormat.format(it)}")
            }
            if (chat.systemPrompt.isNotBlank()) {
                appendLine("- **System Prompt:** ${chat.systemPrompt}")
            }
            appendLine()
            appendLine("---")
            appendLine()
            messages.forEach { msg ->
                val author = when (msg.role) {
                    "user" -> "You"
                    "assistant" -> "Assistant (${msg.modelId})"
                    else -> "System"
                }
                val timeStr = msg.createdAt?.toDate()?.let { isoFormat.format(it) } ?: "pending"
                appendLine("### $author · $timeStr")
                appendLine()
                appendLine(msg.content)
                appendLine()
            }
        }
    }

    fun formatChatAsJson(chat: ChatSession, messages: List<ChatMessage>): String {
        val root = JSONObject().apply {
            put("id", chat.id)
            put("title", chat.title)
            put("provider", chat.provider)
            put("modelId", chat.modelId)
            put("systemPrompt", chat.systemPrompt)
            put("archived", chat.archived)
            put("exportedAt", isoFormat.format(Date()))
            put("messages", JSONArray().apply {
                messages.forEach { msg ->
                    put(JSONObject().apply {
                        put("id", msg.id)
                        put("role", msg.role)
                        put("provider", msg.provider)
                        put("modelId", msg.modelId)
                        put("content", msg.content)
                        put("tokenCountEstimate", msg.tokenCountEstimate)
                        put("latencyMs", msg.latencyMs)
                        put("isError", msg.isError)
                        put("createdAt", msg.createdAt?.toDate()?.let { isoFormat.format(it) } ?: "")
                    })
                }
            })
        }
        return root.toString(2)
    }

    fun formatLogsAsMarkdown(logs: List<ApiExecutionLog>): String {
        return buildString {
            appendLine("# Atelier API Execution & Interaction Logs")
            appendLine("Exported: ${isoFormat.format(Date())}")
            appendLine()
            logs.forEach { log ->
                val ts = log.createdAt?.toDate()?.let { isoFormat.format(it) } ?: "pending"
                appendLine("- **[$ts]** `${log.provider}` / `${log.modelId}` · **${log.status.uppercase()}** (${log.latencyMs}ms) — ${log.summary}")
            }
        }
    }

    fun shareText(context: Context, subject: String, content: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, content)
        }
        val chooser = Intent.createChooser(sendIntent, "Export via")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
