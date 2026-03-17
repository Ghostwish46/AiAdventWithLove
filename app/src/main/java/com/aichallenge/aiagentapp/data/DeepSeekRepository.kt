package com.aichallenge.aiagentapp.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
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

class DeepSeekRepository(private val routerAiApi: DeepSeekApi) {

    suspend fun sendMessages(
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
                    Result.success(AgentTurnResult(content, body.usage, elapsedMs))
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

    private val gson = Gson()

    fun sendMessagesStreaming(
        messages: List<ChatMessage>,
        modelId: String
    ): Flow<StreamEvent> = flow {
        if (messages.isEmpty()) return@flow
        val request = DeepSeekRequest(
            model = modelId,
            messages = messages,
            stream = true
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
            body.byteStream().bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    val trimmed = line.trim()
                    if (!trimmed.startsWith("data: ")) continue
                    val data = trimmed.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val chunk = gson.fromJson(data, StreamChunk::class.java)
                        chunk.usage?.let { usage = it }
                        val delta = chunk.choices?.firstOrNull()?.delta ?: continue
                        val content = delta.content
                        // Показываем только финальный ответ (content), без reasoning («думки» модели)
                        if (!content.isNullOrEmpty()) emit(StreamEvent.Chunk(content))
                    } catch (_: Exception) {
                        // skip unparseable chunk
                    }
                }
            }
            emit(StreamEvent.Done(usage))
        } catch (e: Exception) {
            emit(StreamEvent.Error(e.message ?: "Stream error"))
        }
    }.flowOn(Dispatchers.IO)
}

sealed class StreamEvent {
    data class Chunk(val text: String) : StreamEvent()
    data class Done(val usage: Usage?) : StreamEvent()
    data class Error(val message: String) : StreamEvent()
}

data class AgentTurnResult(
    val content: String,
    val usage: Usage?,
    val elapsedMs: Long,
    val estimatedCostRub: Double? = null
)
