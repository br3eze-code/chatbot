package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.domain.model.ApiPlugin
import com.example.domain.model.PromptTemplate
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class AtelierRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun userProfile_and_chatSession_crud_succeedsForOwner() = runBlocking {
        val aliceUid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
        val repo = AtelierRepository(firestore)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserProfile(aliceUid, "Alice", "alice@test.com", null)
            val profile = repo.observeUserProfile(aliceUid).first { it != null }
            assertEquals("Alice", profile?.displayName)

            val chatId = repo.createChat(
                userId = aliceUid,
                title = "System Design",
                provider = "deepmind",
                modelId = "gemini-3.8-flash"
            )
            val msgId = repo.addMessage(
                userId = aliceUid,
                chatId = chatId,
                role = "user",
                content = "Explain Raft consensus.",
                provider = "deepmind",
                modelId = "gemini-3.8-flash",
                tokenCountEstimate = 5L,
                latencyMs = 0L
            )
            assertTrue(msgId.isNotBlank())

            val chats = repo.observeChats(aliceUid).first { list -> list.any { it.id == chatId } }
            assertTrue(chats.any { it.id == chatId && it.title == "System Design" })

            val promptId = repo.savePrompt(
                aliceUid,
                PromptTemplate(
                    title = "Prompt A",
                    description = "Desc",
                    content = "Summarize carefully.",
                    category = "Writing"
                )
            )
            assertTrue(promptId.isNotBlank())

            val pluginId = repo.savePlugin(
                aliceUid,
                ApiPlugin(
                    name = "Test Gateway",
                    description = "Desc",
                    kind = "custom_endpoint",
                    baseUrl = "https://example.com",
                    endpointPath = "/v1/chat/completions",
                    httpMethod = "POST",
                    authHeaderName = "Authorization",
                    authScheme = "Bearer",
                    customModelId = "custom-1",
                    enabled = true
                )
            )
            assertTrue(pluginId.isNotBlank())
        }
    }

    @Test
    fun crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_cross_${UUID.randomUUID()}@test.com")
        val repo = AtelierRepository(firestore)
        val chatId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserProfile(aliceUid, "Alice", null, null)
            repo.createChat(aliceUid, "Private Chat", "anthropic", "claude-sonnet-4-5-20250929")
        }

        signInTestUser("bob_cross_${UUID.randomUUID()}@test.com")
        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeChats(aliceUid).first()
            }
        } catch (e: Exception) {
            caught = e
        }
        val firestoreException = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected PERMISSION_DENIED for cross-user chat list, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
        assertTrue(chatId.isNotBlank())
    }

    @Test
    fun unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = AtelierRepository(firestore)
        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeChats("some_uid").first()
            }
        } catch (e: Exception) {
            caught = e
        }
        val firestoreException = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected PERMISSION_DENIED for unauthenticated caller, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
