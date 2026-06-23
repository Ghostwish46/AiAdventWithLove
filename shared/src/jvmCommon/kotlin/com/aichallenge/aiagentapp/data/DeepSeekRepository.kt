package com.aichallenge.aiagentapp.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.FieldNamingPolicy
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.IOException

data class ErrorBody(
    @SerializedName("error") val error: ErrorDetail? = null
)

data class ErrorDetail(
    @SerializedName("message") val message: String? = null,
    @SerializedName("type") val type: String? = null
)

class DeepSeekRepository(private val routerAiApi: DeepSeekApi) : LlmClient {

    override suspend fun sendMessages(
        messages: List<ChatMessage>,
        modelId: String
    ): Result<AgentTurnResult> = withContext(Dispatchers.IO) {
        if (messages.isEmpty()) return@withContext Result.failure(IllegalArgumentException("No messages"))

        val request = DeepSeekRequest(
            model = modelId,
            messages = messages,
            stream = false
        )

        val startMs = System.currentTimeMillis()
        try {
            val response = routerAiApi.createChatCompletion(request)
            val elapsedMs = System.currentTimeMillis() - startMs

            if (response.isSuccessful) {
                val body = response.body()
                val content = body?.choices?.firstOrNull()?.message?.content
                if (content != null) {
                    val usage = body?.usage?.toUsage()?.takeIf { it.isMeaningful() }
                        ?: estimateUsage(messages, content)
                    Result.success(AgentTurnResult(content, usage, elapsedMs))
                } else {
                    Result.failure(Exception("Empty response from API"))
                }
            } else {
                val errorMsg = response.errorBody()?.string()?.let { raw ->
                    try {
                        Gson().fromJson(raw, ErrorBody::class.java)?.error?.message ?: raw
                    } catch (_: Exception) {
                        raw
                    }
                } ?: "Error: ${response.code()} ${response.message()}"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: IOException) {
            Result.failure(Exception("Network error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun mergeStickyFacts(
        existingFacts: Map<String, String>,
        newUserMessage: String,
        recentContext: List<ChatMessage>,
        modelId: String
    ): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        val gsonLocal = Gson()
        val mapType = object : TypeToken<Map<String, String>>() {}.type
        val contextLines = recentContext.takeLast(6).joinToString("\n") { m ->
            val label = if (m.role == "user") "П" else "А"
            "$label: ${m.content.take(600)}"
        }
        val system = (
            "Извлекай и обновляй факты диалога: цели, ограничения, предпочтения, решения, договорённости, важные имена и даты, кодовые слова. " +
                "На вход — текущие факты JSON, последние реплики и новое сообщение пользователя. " +
                "Верни ТОЛЬКО один валидный JSON-объект с ключами и строковыми значениями, без markdown, без ```, без пояснений. " +
                "Обнови и дополни; устаревшие ключи удали."
            )
        val userPayload = buildString {
            appendLine("Текущие факты (JSON):")
            appendLine(gsonLocal.toJson(existingFacts))
            appendLine("Последние реплики:")
            appendLine(contextLines.ifBlank { "(нет)" })
            appendLine("Новое сообщение пользователя:")
            appendLine(newUserMessage)
        }
        val request = DeepSeekRequest(
            model = modelId,
            messages = listOf(
                ChatMessage(role = "system", content = system),
                ChatMessage(role = "user", content = userPayload)
            ),
            stream = false,
            maxTokens = 512,
            temperature = 0.2
        )
        try {
            val response = routerAiApi.createChatCompletion(request)
            if (response.isSuccessful) {
                val raw = response.body()?.choices?.firstOrNull()?.message?.content?.trim()
                    ?: return@withContext Result.failure(Exception("Empty facts response"))
                val parsed = parseFactsJsonResponse(raw, gsonLocal, mapType)
                if (parsed != null) Result.success(parsed)
                else Result.failure(Exception("Failed to parse facts JSON"))
            } else {
                val err = response.errorBody()?.string() ?: response.message()
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun classifyMemoryUpdate(
        existingWorking: Map<String, String>,
        existingLongTerm: Map<String, String>,
        newUserMessage: String,
        recentContext: List<ChatMessage>,
        forceLongTerm: Boolean,
        modelId: String
    ): Result<MemoryClassificationResult> = withContext(Dispatchers.IO) {
        val gsonLocal = Gson()
        val contextLines = recentContext.takeLast(6).joinToString("\n") { m ->
            val label = if (m.role == "user") "П" else "А"
            "$label: ${m.content.take(600)}"
        }
        val system = buildString {
            append(
                "Ты классификатор памяти ассистента. Раздели информацию из сообщения пользователя по слоям:\n" +
                    "- working: цели, ограничения, решения и статус ТЕКУЩЕЙ задачи (только этот чат)\n" +
                    "- longTerm: профиль пользователя, устойчивые предпочтения, знания на будущее (между сессиями)\n" +
                    "- routingLog: массив строк с явным объяснением, куда и почему попала каждая запись\n\n" +
                    "Верни ТОЛЬКО один валидный JSON без markdown:\n" +
                    "{\"working\": {\"ключ\": \"значение\"}, \"longTerm\": {\"ключ\": \"значение\"}, \"routingLog\": [\"ключ → working: причина\"]}\n" +
                    "Если обновлений нет — верни пустые объекты и пустой routingLog."
            )
            if (forceLongTerm) {
                append("\n\nПользователь явно просит запомнить надолго — извлеки в longTerm.")
            }
        }
        val userPayload = buildString {
            appendLine("Текущая рабочая память (JSON):")
            appendLine(gsonLocal.toJson(existingWorking))
            appendLine("Текущая долговременная память (JSON):")
            appendLine(gsonLocal.toJson(existingLongTerm))
            appendLine("Последние реплики:")
            appendLine(contextLines.ifBlank { "(нет)" })
            appendLine("Новое сообщение пользователя:")
            appendLine(newUserMessage)
        }
        val request = DeepSeekRequest(
            model = modelId,
            messages = listOf(
                ChatMessage(role = "system", content = system),
                ChatMessage(role = "user", content = userPayload)
            ),
            stream = false,
            maxTokens = 768,
            temperature = 0.2
        )
        try {
            val response = routerAiApi.createChatCompletion(request)
            if (response.isSuccessful) {
                val raw = response.body()?.choices?.firstOrNull()?.message?.content?.trim()
                    ?: return@withContext Result.failure(Exception("Empty memory classification response"))
                val parsed = parseMemoryClassificationResponse(raw, gsonLocal)
                if (parsed != null) Result.success(parsed)
                else Result.failure(Exception("Failed to parse memory classification JSON"))
            } else {
                val err = response.errorBody()?.string() ?: response.message()
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseMemoryClassificationResponse(raw: String, gson: Gson): MemoryClassificationResult? {
        var t = raw.trim()
        if (t.startsWith("```")) {
            t = t.removePrefix("```json").removePrefix("```JSON").removePrefix("```").trim()
            val endFence = t.lastIndexOf("```")
            if (endFence >= 0) t = t.substring(0, endFence).trim()
        }
        return try {
            val dto = gson.fromJson(t, MemoryClassificationDto::class.java) ?: return null
            MemoryClassificationResult(
                working = dto.working ?: emptyMap(),
                longTerm = dto.longTerm ?: emptyMap(),
                routingLog = dto.routingLog ?: emptyList()
            )
        } catch (_: Exception) {
            null
        }
    }

    private data class MemoryClassificationDto(
        val working: Map<String, String>? = null,
        val longTerm: Map<String, String>? = null,
        val routingLog: List<String>? = null
    )

    private fun parseFactsJsonResponse(
        raw: String,
        gson: Gson,
        mapType: java.lang.reflect.Type
    ): Map<String, String>? {
        var t = raw.trim()
        if (t.startsWith("```")) {
            t = t.removePrefix("```json").removePrefix("```JSON").removePrefix("```").trim()
            val endFence = t.lastIndexOf("```")
            if (endFence >= 0) t = t.substring(0, endFence).trim()
        }
        return try {
            gson.fromJson<Map<String, String>>(t, mapType)
        } catch (_: Exception) {
            null
        }
    }

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    override fun sendMessagesStreaming(
        messages: List<ChatMessage>,
        modelId: String
    ): Flow<StreamEvent> = flow {
        if (messages.isEmpty()) return@flow
        val request = DeepSeekRequest(
            model = modelId,
            messages = messages,
            stream = true,
            streamOptions = StreamOptions(includeUsage = true)
        )
        try {
            val response = routerAiApi.createChatCompletionStream(request)
            if (!response.isSuccessful) {
                val errorMsg = response.errorBody()?.string()?.let { raw ->
                    try {
                        Gson().fromJson(raw, ErrorBody::class.java)?.error?.message ?: raw
                    } catch (_: Exception) {
                        raw
                    }
                } ?: "Error: ${response.code()} ${response.message()}"
                emit(StreamEvent.Error(errorMsg))
                return@flow
            }
            val body = response.body() ?: run {
                emit(StreamEvent.Error("Empty response body"))
                return@flow
            }
            var usage: Usage? = null
            val accumulated = StringBuilder()
            body.byteStream().bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    val trimmed = line.trim()
                    if (!trimmed.startsWith("data: ")) continue
                    val data = trimmed.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val chunk = gson.fromJson(data, StreamChunk::class.java)
                        chunk.usage?.toUsage()?.let { parsed ->
                            if (parsed.isMeaningful()) usage = parsed
                        }
                        val delta = chunk.choices?.firstOrNull()?.delta ?: continue
                        val content = delta.content
                        if (!content.isNullOrEmpty()) {
                            accumulated.append(content)
                            emit(StreamEvent.Chunk(content))
                        }
                    } catch (_: Exception) {
                        // skip unparseable chunk
                    }
                }
            }
            val finalUsage = usage?.takeIf { it.isMeaningful() }
                ?: estimateUsage(messages, accumulated.toString())
            emit(StreamEvent.Done(finalUsage))
        } catch (e: Exception) {
            emit(StreamEvent.Error(e.message ?: "Stream error"))
        }
    }.flowOn(Dispatchers.IO)
}
