package com.example

import com.example.core.ChatExportHelper
import com.example.data.local.ApiKeyStore
import com.example.domain.model.AiProvider
import com.example.domain.model.ApiPlugin
import com.example.domain.model.ChatMessage
import com.example.domain.model.ChatSession
import com.example.domain.model.ModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AtelierUnitTest {

    @Test
    fun modelCatalog_listsAllRequestedProviders() {
        assertTrue(ModelCatalog.modelsForProvider(AiProvider.DEEPMIND).isNotEmpty())
        assertTrue(ModelCatalog.modelsForProvider(AiProvider.ANTHROPIC).isNotEmpty())
        assertTrue(ModelCatalog.modelsForProvider(AiProvider.OPENAI).isNotEmpty())
        assertTrue(ModelCatalog.modelsForProvider(AiProvider.DEEPSEEK).isNotEmpty())
        assertTrue(ModelCatalog.modelsForProvider(AiProvider.MOONSHOT).isNotEmpty())
    }

    @Test
    fun modelCatalog_includesEnabledCustomEndpointPlugins() {
        val custom = ApiPlugin(
            id = "p1",
            name = "My Local Llama",
            kind = "custom_endpoint",
            customModelId = "llama-3.3",
            enabled = true
        )
        val disabled = ApiPlugin(
            id = "p2",
            name = "Disabled",
            kind = "custom_endpoint",
            customModelId = "x",
            enabled = false
        )
        val models = ModelCatalog.modelsForProvider(AiProvider.CUSTOM, listOf(custom, disabled))
        assertEquals(1, models.size)
        assertEquals("llama-3.3", models.first().id)
        assertEquals("My Local Llama", models.first().displayName)
    }

    @Test
    fun apiKeyStore_maskKey_hidesMiddleCharacters() {
        assertEquals("Not configured", ApiKeyStore.maskKey(""))
        assertEquals("••••••••", ApiKeyStore.maskKey("123456"))
        assertEquals("sk-a••••wxyz", ApiKeyStore.maskKey("sk-ant-12345678wxyz"))
    }

    @Test
    fun chatExportHelper_formatsMarkdownAndJson() {
        val chat = ChatSession(
            id = "c1",
            title = "Design Review",
            provider = "anthropic",
            modelId = "claude-sonnet-4-5-20250929"
        )
        val msgs = listOf(
            ChatMessage(id = "m1", chatId = "c1", role = "user", content = "Hello"),
            ChatMessage(id = "m2", chatId = "c1", role = "assistant", content = "**Hi!**", modelId = "claude-sonnet-4-5-20250929")
        )
        val md = ChatExportHelper.formatChatAsMarkdown(chat, msgs)
        assertTrue(md.contains("# Design Review"))
        assertTrue(md.contains("**Hi!**"))

        val json = ChatExportHelper.formatChatAsJson(chat, msgs)
        assertTrue(json.contains("\"title\": \"Design Review\""))
        assertTrue(json.contains("\"role\": \"assistant\""))
    }
}
