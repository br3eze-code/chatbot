package com.example.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AiProvider(
    val wireId: String,
    val displayName: String,
    val badge: String,
    val requiresApiKey: Boolean,
    val defaultBaseUrl: String,
    val keyHint: String
) {
    DEEPMIND(
        wireId = "deepmind",
        displayName = "Google DeepMind",
        badge = "Built-in",
        requiresApiKey = false,
        defaultBaseUrl = "firebase-ai-logic",
        keyHint = "Ready via Firebase AI Logic"
    ),
    ANTHROPIC(
        wireId = "anthropic",
        displayName = "Anthropic",
        badge = "Claude",
        requiresApiKey = true,
        defaultBaseUrl = "https://api.anthropic.com",
        keyHint = "sk-ant-..."
    ),
    OPENAI(
        wireId = "openai",
        displayName = "OpenAI",
        badge = "GPT",
        requiresApiKey = true,
        defaultBaseUrl = "https://api.openai.com",
        keyHint = "sk-..."
    ),
    DEEPSEEK(
        wireId = "deepseek",
        displayName = "DeepSeek",
        badge = "V3 / R1",
        requiresApiKey = true,
        defaultBaseUrl = "https://api.deepseek.com",
        keyHint = "sk-..."
    ),
    MOONSHOT(
        wireId = "moonshot",
        displayName = "Moonshot AI",
        badge = "Kimi",
        requiresApiKey = true,
        defaultBaseUrl = "https://api.moonshot.cn",
        keyHint = "sk-..."
    ),
    CUSTOM(
        wireId = "custom",
        displayName = "Custom Extension",
        badge = "API",
        requiresApiKey = false,
        defaultBaseUrl = "",
        keyHint = "Configured per plugin"
    );

    companion object {
        fun fromWireId(id: String?): AiProvider =
            entries.firstOrNull { it.wireId == id } ?: DEEPMIND
    }
}

data class ModelSpec(
    val id: String,
    val displayName: String,
    val provider: AiProvider,
    val contextLabel: String,
    val speedLabel: String
)

object ModelCatalog {
    val models: List<ModelSpec> = listOf(
        // Google DeepMind via Firebase AI Logic (zero key required)
        ModelSpec("gemini-3.8-flash", "Gemini 3.8 Flash", AiProvider.DEEPMIND, "1M ctx", "Balanced"),
        ModelSpec("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", AiProvider.DEEPMIND, "1M ctx", "Ultra Fast"),
        ModelSpec("gemini-4-argon", "Gemini 4 Argon", AiProvider.DEEPMIND, "1M ctx", "Deep Reasoning"),
        // Anthropic
        ModelSpec("claude-sonnet-4-5-20250929", "Claude Sonnet 4.5", AiProvider.ANTHROPIC, "200K ctx", "Balanced"),
        ModelSpec("claude-opus-4-1-20250805", "Claude Opus 4.1", AiProvider.ANTHROPIC, "200K ctx", "Frontier"),
        ModelSpec("claude-haiku-4-5-20251001", "Claude Haiku 4.5", AiProvider.ANTHROPIC, "200K ctx", "Fast"),
        // OpenAI
        ModelSpec("gpt-4.1", "GPT-4.1", AiProvider.OPENAI, "1M ctx", "Flagship"),
        ModelSpec("gpt-4o", "GPT-4o", AiProvider.OPENAI, "128K ctx", "Multimodal"),
        ModelSpec("o3-mini", "o3-mini", AiProvider.OPENAI, "200K ctx", "Reasoning"),
        // DeepSeek
        ModelSpec("deepseek-chat", "DeepSeek V3", AiProvider.DEEPSEEK, "64K ctx", "Fast"),
        ModelSpec("deepseek-reasoner", "DeepSeek R1", AiProvider.DEEPSEEK, "64K ctx", "Reasoning"),
        // Moonshot
        ModelSpec("moonshot-v1-8k", "Moonshot v1 8K", AiProvider.MOONSHOT, "8K ctx", "Fast"),
        ModelSpec("moonshot-v1-32k", "Moonshot v1 32K", AiProvider.MOONSHOT, "32K ctx", "Balanced"),
        ModelSpec("kimi-latest", "Kimi Latest", AiProvider.MOONSHOT, "128K ctx", "Flagship")
    )

    fun modelsForProvider(provider: AiProvider, customPlugins: List<ApiPlugin> = emptyList()): List<ModelSpec> {
        if (provider == AiProvider.CUSTOM) {
            return customPlugins
                .filter { it.kind == "custom_endpoint" && it.enabled }
                .map { plugin ->
                    ModelSpec(
                        id = plugin.customModelId.ifBlank { plugin.id },
                        displayName = plugin.name,
                        provider = AiProvider.CUSTOM,
                        contextLabel = "Custom",
                        speedLabel = plugin.httpMethod
                    )
                }
        }
        return models.filter { it.provider == provider }
    }

    fun findDisplayName(providerId: String, modelId: String, customPlugins: List<ApiPlugin> = emptyList()): String {
        val provider = AiProvider.fromWireId(providerId)
        if (provider == AiProvider.CUSTOM) {
            return customPlugins.firstOrNull {
                it.customModelId == modelId || it.id == modelId
            }?.name ?: modelId
        }
        return models.firstOrNull { it.id == modelId }?.displayName ?: modelId
    }
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Studio Member",
    val email: String? = null,
    val photoUrl: String? = null,
    val themeMode: String = "system",
    val defaultProvider: String = AiProvider.DEEPMIND.wireId,
    val defaultModelId: String = "gemini-3.8-flash",
    val defaultSystemPrompt: String = "You are Atelier, a thoughtful, precise assistant. Format answers cleanly in Markdown.",
    val temperature: Double = 0.7,
    val streamResponses: Boolean = true,
    val syncEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "New conversation",
    val provider: String = AiProvider.DEEPMIND.wireId,
    val modelId: String = "gemini-3.8-flash",
    val systemPrompt: String = "",
    val lastMessagePreview: String = "",
    val messageCount: Long = 0L,
    val archived: Boolean = false,
    val pinned: Boolean = false,
    val activePluginIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val chatId: String = "",
    val role: String = "user",
    val content: String = "",
    val provider: String = AiProvider.DEEPMIND.wireId,
    val modelId: String = "gemini-3.8-flash",
    val tokenCountEstimate: Long = 0L,
    val latencyMs: Long = 0L,
    val isError: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PromptTemplate(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val content: String = "",
    val category: String = "General",
    val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val usageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ApiPlugin(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val kind: String = "custom_endpoint", // "connector", "custom_endpoint", "tool"
    val baseUrl: String = "",
    val endpointPath: String = "/v1/chat/completions",
    val httpMethod: String = "POST",
    val authHeaderName: String = "Authorization",
    val authScheme: String = "Bearer",
    val customModelId: String = "",
    val enabled: Boolean = true,
    val requestTemplate: String = "",
    val responseJsonPath: String = "choices[0].message.content",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ApiExecutionLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val chatId: String = "",
    val provider: String = "",
    val modelId: String = "",
    val status: String = "success",
    val latencyMs: Long = 0L,
    val summary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
