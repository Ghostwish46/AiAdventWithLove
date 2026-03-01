package com.aichallenge.aiagentapp.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
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
}

data class AgentTurnResult(
    val content: String,
    val usage: Usage?,
    val elapsedMs: Long
)
