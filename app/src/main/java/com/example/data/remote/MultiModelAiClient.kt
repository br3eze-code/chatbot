package com.example.data.remote

import android.util.Log
import com.example.domain.model.AiProvider
import com.example.domain.model.ApiPlugin
import com.example.domain.model.ChatMessage
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class AiStreamEvent(
    val accumulatedText: String,
    val isComplete: Boolean = false
)

data class EndpointTestResult(
    val success: Boolean,
    val statusCode: Int,
    val latencyMs: Long,
    val preview: String
)

class MultiModelAiClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun streamCompletion(
        provider: AiProvider,
        modelId: String,
        apiKey: String,
        history: List<ChatMessage>,
        userPrompt: String,
        systemPrompt: String,
        temperature: Double,
        streamEnabled: Boolean,
        customPlugin: ApiPlugin? = null
    ): Flow<AiStreamEvent> = flow {
        when (provider) {
            AiProvider.DEEPMIND -> {
                val model = Firebase.ai.generativeModel(
                    modelName = modelId.ifBlank { "gemini-3.8-flash" },
                    generationConfig = generationConfig {
                        this.temperature = temperature.toFloat().coerceIn(0f, 2f)
                    },
                    systemInstruction = systemPrompt.takeIf { it.isNotBlank() }?.let { sys ->
                        content { text(sys) }
                    }
                )
                val chatHistory: List<Content> = history
                    .filter { !it.isError && it.content.isNotBlank() }
                    .takeLast(20)
                    .map { msg ->
                        val role = if (msg.role == "user") "user" else "model"
                        content(role) { text(msg.content) }
                    }
                val chat = model.startChat(history = chatHistory)
                val sb = StringBuilder()
                withTimeout(60_000L) {
                    if (streamEnabled) {
                        chat.sendMessageStream(userPrompt).collect { chunk ->
                            val piece = chunk.text.orEmpty()
                            if (piece.isNotEmpty()) {
                                sb.append(piece)
                                emit(AiStreamEvent(sb.toString(), isComplete = false))
                            }
                        }
                    } else {
                        val response = chat.sendMessage(userPrompt)
                        sb.append(response.text.orEmpty())
                    }
                }
                val finalText = sb.toString().ifBlank {
                    throw IllegalStateException("DeepMind returned an empty response.")
                }
                emit(AiStreamEvent(finalText, isComplete = true))
            }

            AiProvider.ANTHROPIC -> {
                require(apiKey.isNotBlank()) {
                    "Anthropic API key is not configured. Add it in Settings → API Keys."
                }
                streamAnthropic(
                    apiKey = apiKey,
                    modelId = modelId,
                    history = history,
                    userPrompt = userPrompt,
                    systemPrompt = systemPrompt,
                    temperature = temperature,
                    streamEnabled = streamEnabled,
                    emitChunk = { emit(it) }
                )
            }

            AiProvider.OPENAI, AiProvider.DEEPSEEK, AiProvider.MOONSHOT -> {
                require(apiKey.isNotBlank()) {
                    "${provider.displayName} API key is not configured. Add it in Settings → API Keys."
                }
                val endpoint = when (provider) {
                    AiProvider.OPENAI -> "https://api.openai.com/v1/chat/completions"
                    AiProvider.DEEPSEEK -> "https://api.deepseek.com/chat/completions"
                    AiProvider.MOONSHOT -> "https://api.moonshot.cn/v1/chat/completions"
                    else -> ""
                }
                streamOpenAiCompatible(
                    endpointUrl = endpoint,
                    authHeaderName = "Authorization",
                    authHeaderValue = "Bearer $apiKey",
                    modelId = modelId,
                    history = history,
                    userPrompt = userPrompt,
                    systemPrompt = systemPrompt,
                    temperature = temperature,
                    streamEnabled = streamEnabled,
                    emitChunk = { emit(it) }
                )
            }

            AiProvider.CUSTOM -> {
                val plugin = requireNotNull(customPlugin) {
                    "No active custom endpoint plugin selected. Configure one in Plugins."
                }
                val url = buildUrl(plugin.baseUrl, plugin.endpointPath)
                require(url.startsWith("http://") || url.startsWith("https://")) {
                    "Invalid custom endpoint URL: $url"
                }
                val headerName = plugin.authHeaderName.ifBlank { "Authorization" }
                val headerVal = if (apiKey.isBlank()) "" else {
                    if (plugin.authScheme.isBlank()) apiKey else "${plugin.authScheme} $apiKey"
                }

                if (plugin.requestTemplate.isBlank()) {
                    streamOpenAiCompatible(
                        endpointUrl = url,
                        authHeaderName = headerName,
                        authHeaderValue = headerVal,
                        modelId = plugin.customModelId.ifBlank { modelId },
                        history = history,
                        userPrompt = userPrompt,
                        systemPrompt = systemPrompt,
                        temperature = temperature,
                        streamEnabled = false,
                        responseJsonPath = plugin.responseJsonPath,
                        emitChunk = { emit(it) }
                    )
                } else {
                    val result = executeCustomTemplate(
                        plugin = plugin,
                        url = url,
                        authHeaderName = headerName,
                        authHeaderValue = headerVal,
                        prompt = userPrompt,
                        systemPrompt = systemPrompt
                    )
                    emit(AiStreamEvent(result, isComplete = true))
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun streamAnthropic(
        apiKey: String,
        modelId: String,
        history: List<ChatMessage>,
        userPrompt: String,
        systemPrompt: String,
        temperature: Double,
        streamEnabled: Boolean,
        emitChunk: suspend (AiStreamEvent) -> Unit
    ) {
        val messagesJson = JSONArray()
        history.filter { !it.isError && it.content.isNotBlank() && it.role != "system" }
            .takeLast(20)
            .forEach { msg ->
                messagesJson.put(JSONObject().apply {
                    put("role", if (msg.role == "user") "user" else "assistant")
                    put("content", msg.content)
                })
            }
        messagesJson.put(JSONObject().apply {
            put("role", "user")
            put("content", userPrompt)
        })

        val bodyJson = JSONObject().apply {
            put("model", modelId)
            put("max_tokens", 4096)
            put("temperature", temperature.coerceIn(0.0, 1.0))
            put("stream", streamEnabled)
            if (systemPrompt.isNotBlank()) put("system", systemPrompt)
            put("messages", messagesJson)
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        withTimeout(60_000L) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    throw IllegalStateException(parseApiError("Anthropic", response.code, errBody))
                }
                val body = response.body ?: throw IllegalStateException("Empty Anthropic response")
                if (!streamEnabled) {
                    val raw = body.string()
                    val json = JSONObject(raw)
                    val contentArr = json.optJSONArray("content")
                    val text = buildString {
                        if (contentArr != null) {
                            for (i in 0 until contentArr.length()) {
                                val item = contentArr.optJSONObject(i)
                                if (item?.optString("type") == "text") {
                                    append(item.optString("text"))
                                }
                            }
                        }
                    }
                    emitChunk(AiStreamEvent(text.ifBlank { raw }, isComplete = true))
                    return@use
                }

                val reader = BufferedReader(InputStreamReader(body.byteStream()))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val current = line ?: continue
                    if (current.startsWith("data:")) {
                        val data = current.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        runCatching {
                            val eventJson = JSONObject(data)
                            when (eventJson.optString("type")) {
                                "content_block_delta" -> {
                                    val delta = eventJson.optJSONObject("delta")
                                    val text = delta?.optString("text").orEmpty()
                                    if (text.isNotEmpty()) {
                                        sb.append(text)
                                        emitChunk(AiStreamEvent(sb.toString(), isComplete = false))
                                    }
                                }
                                "error" -> {
                                    val err = eventJson.optJSONObject("error")?.optString("message")
                                        ?: "Anthropic stream error"
                                    throw IllegalStateException(err)
                                }
                            }
                        }
                    }
                }
                emitChunk(AiStreamEvent(sb.toString().ifBlank { "No text returned." }, isComplete = true))
            }
        }
    }

    private suspend fun streamOpenAiCompatible(
        endpointUrl: String,
        authHeaderName: String,
        authHeaderValue: String,
        modelId: String,
        history: List<ChatMessage>,
        userPrompt: String,
        systemPrompt: String,
        temperature: Double,
        streamEnabled: Boolean,
        responseJsonPath: String = "choices[0].message.content",
        emitChunk: suspend (AiStreamEvent) -> Unit
    ) {
        val messagesJson = JSONArray()
        if (systemPrompt.isNotBlank()) {
            messagesJson.put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
        }
        history.filter { !it.isError && it.content.isNotBlank() && it.role != "system" }
            .takeLast(20)
            .forEach { msg ->
                messagesJson.put(JSONObject().apply {
                    put("role", if (msg.role == "user") "user" else "assistant")
                    put("content", msg.content)
                })
            }
        messagesJson.put(JSONObject().apply {
            put("role", "user")
            put("content", userPrompt)
        })

        val bodyJson = JSONObject().apply {
            put("model", modelId)
            put("messages", messagesJson)
            put("temperature", temperature.coerceIn(0.0, 2.0))
            put("stream", streamEnabled)
        }

        val reqBuilder = Request.Builder()
            .url(endpointUrl)
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody(jsonMediaType))

        if (authHeaderValue.isNotBlank()) {
            reqBuilder.addHeader(authHeaderName, authHeaderValue)
        }

        withTimeout(60_000L) {
            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    throw IllegalStateException(parseApiError("API", response.code, errBody))
                }
                val body = response.body ?: throw IllegalStateException("Empty response body")
                if (!streamEnabled) {
                    val raw = body.string()
                    val extracted = extractJsonPath(raw, responseJsonPath)
                        ?: extractJsonPath(raw, "choices[0].message.content")
                        ?: raw
                    emitChunk(AiStreamEvent(extracted, isComplete = true))
                    return@use
                }

                val reader = BufferedReader(InputStreamReader(body.byteStream()))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val current = line ?: continue
                    if (current.startsWith("data:")) {
                        val data = current.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        runCatching {
                            val json = JSONObject(data)
                            val choices = json.optJSONArray("choices")
                            val first = choices?.optJSONObject(0)
                            val delta = first?.optJSONObject("delta")
                            val contentPiece = delta?.optString("content").orEmpty()
                            val reasoningPiece = delta?.optString("reasoning_content").orEmpty()
                            if (reasoningPiece.isNotEmpty() && sb.isEmpty()) {
                                sb.append("> *Reasoning:* $reasoningPiece")
                                emitChunk(AiStreamEvent(sb.toString(), isComplete = false))
                            } else if (contentPiece.isNotEmpty()) {
                                sb.append(contentPiece)
                                emitChunk(AiStreamEvent(sb.toString(), isComplete = false))
                            }
                        }
                    }
                }
                emitChunk(AiStreamEvent(sb.toString().ifBlank { "No text returned." }, isComplete = true))
            }
        }
    }

    private suspend fun executeCustomTemplate(
        plugin: ApiPlugin,
        url: String,
        authHeaderName: String,
        authHeaderValue: String,
        prompt: String,
        systemPrompt: String
    ): String {
        val escapedPrompt = JSONObject.quote(prompt).drop(1).dropLast(1)
        val escapedSys = JSONObject.quote(systemPrompt).drop(1).dropLast(1)
        val escapedModel = JSONObject.quote(plugin.customModelId).drop(1).dropLast(1)

        val resolvedUrl = url
            .replace("{{prompt}}", java.net.URLEncoder.encode(prompt, "UTF-8"))

        val reqBuilder = Request.Builder().url(resolvedUrl)
        if (authHeaderValue.isNotBlank()) {
            reqBuilder.addHeader(authHeaderName, authHeaderValue)
        }
        if (plugin.httpMethod == "GET") {
            reqBuilder.get()
        } else {
            val payload = plugin.requestTemplate
                .replace("{{prompt}}", escapedPrompt)
                .replace("{{system}}", escapedSys)
                .replace("{{model}}", escapedModel)
            reqBuilder.addHeader("Content-Type", "application/json")
                .post(payload.toRequestBody(jsonMediaType))
        }

        return withTimeout(45_000L) {
            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Custom endpoint HTTP ${response.code}: ${raw.take(300)}")
                }
                extractJsonPath(raw, plugin.responseJsonPath) ?: raw
            }
        }
    }

    suspend fun runConnectorPlugin(
        plugin: ApiPlugin,
        apiKey: String,
        userPrompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = buildUrl(plugin.baseUrl, plugin.endpointPath)
            val headerName = plugin.authHeaderName.ifBlank { "Authorization" }
            val headerVal = if (apiKey.isBlank()) "" else {
                if (plugin.authScheme.isBlank()) apiKey else "${plugin.authScheme} $apiKey"
            }
            val result = executeCustomTemplate(
                plugin = plugin.copy(
                    requestTemplate = plugin.requestTemplate.ifBlank { """{"query":"{{prompt}}"}""" }
                ),
                url = url,
                authHeaderName = headerName,
                authHeaderValue = headerVal,
                prompt = userPrompt,
                systemPrompt = ""
            )
            Result.success(result.take(2000))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("MultiModelAiClient", "Connector ${plugin.name} failed", e)
            Result.failure(e)
        }
    }

    suspend fun testPluginConnection(
        plugin: ApiPlugin,
        apiKey: String
    ): EndpointTestResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val url = buildUrl(plugin.baseUrl, plugin.endpointPath)
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return@withContext EndpointTestResult(false, 0, 0L, "URL must start with http:// or https://")
            }
            val reqBuilder = Request.Builder().url(url)
            if (apiKey.isNotBlank()) {
                val headerName = plugin.authHeaderName.ifBlank { "Authorization" }
                val headerVal = if (plugin.authScheme.isBlank()) apiKey else "${plugin.authScheme} $apiKey"
                reqBuilder.addHeader(headerName, headerVal)
            }
            if (plugin.httpMethod == "GET") {
                reqBuilder.get()
            } else {
                val payload = if (plugin.requestTemplate.isNotBlank()) {
                    plugin.requestTemplate
                        .replace("{{prompt}}", "ping")
                        .replace("{{system}}", "")
                        .replace("{{model}}", plugin.customModelId)
                } else {
                    JSONObject().apply {
                        put("model", plugin.customModelId.ifBlank { "test-model" })
                        put("messages", JSONArray().put(JSONObject().apply {
                            put("role", "user")
                            put("content", "Respond with OK.")
                        }))
                        put("max_tokens", 16)
                    }.toString()
                }
                reqBuilder.addHeader("Content-Type", "application/json")
                    .post(payload.toRequestBody(jsonMediaType))
            }

            withTimeout(20_000L) {
                httpClient.newCall(reqBuilder.build()).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    val latency = System.currentTimeMillis() - start
                    val preview = extractJsonPath(raw, plugin.responseJsonPath) ?: raw.take(240)
                    EndpointTestResult(
                        success = response.isSuccessful,
                        statusCode = response.code,
                        latencyMs = latency,
                        preview = preview.ifBlank { "HTTP ${response.code}" }
                    )
                }
            }
        } catch (e: TimeoutCancellationException) {
            EndpointTestResult(false, 408, System.currentTimeMillis() - start, "Connection timed out after 20s")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            EndpointTestResult(
                success = false,
                statusCode = 0,
                latencyMs = System.currentTimeMillis() - start,
                preview = e.localizedMessage ?: "Connection failed"
            )
        }
    }

    private fun buildUrl(baseUrl: String, endpointPath: String): String {
        val base = baseUrl.trim().trimEnd('/')
        val path = endpointPath.trim()
        return when {
            path.isEmpty() -> base
            path.startsWith("http://") || path.startsWith("https://") -> path
            path.startsWith("/") -> "$base$path"
            else -> "$base/$path"
        }
    }

    private fun parseApiError(label: String, code: Int, body: String): String {
        val extracted = runCatching {
            val json = JSONObject(body)
            json.optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
        }.getOrNull()
        return "$label error ($code): ${extracted ?: body.take(250).ifBlank { "Request failed" }}"
    }

    private fun extractJsonPath(rawJson: String, path: String): String? {
        if (path.isBlank()) return null
        return runCatching {
            var current: Any = if (rawJson.trimStart().startsWith("[")) {
                JSONArray(rawJson)
            } else {
                JSONObject(rawJson)
            }
            val tokens = path.split('.')
            for (token in tokens) {
                val bracketIdx = token.indexOf('[')
                if (bracketIdx >= 0 && token.endsWith(']')) {
                    val key = token.substring(0, bracketIdx)
                    val index = token.substring(bracketIdx + 1, token.length - 1).toInt()
                    if (key.isNotEmpty() && current is JSONObject) {
                        current = current.get(key)
                    }
                    if (current is JSONArray) {
                        current = current.get(index)
                    }
                } else if (current is JSONObject) {
                    current = current.get(token)
                }
            }
            current.toString()
        }.getOrNull()
    }
}
