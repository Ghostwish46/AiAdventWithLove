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

class DeepSeekRepository(private val api: DeepSeekApi) {

    data class ControlParams(
        val formatDescription: String,
        val maxChars: Int?,
        val stopSequence: String
    )

    suspend fun sendMessageRaw(userMessage: String): Result<String> = withContext(Dispatchers.IO) {
        if (userMessage.isBlank()) return@withContext Result.failure(IllegalArgumentException("Empty message"))
        val request = DeepSeekRequest(
            model = "deepseek-chat",
            messages = listOf(ChatMessage(role = "user", content = userMessage.trim())),
            stream = false
        )
        try {
            val response = api.createChatCompletion(request)
            if (response.isSuccessful) {
                val body = response.body()
                val content = body?.choices?.firstOrNull()?.message?.content
                if (content != null) {
                    Result.success(content)
                } else {
                    Result.failure(Exception("Empty response from API"))
                }
            } else {
                val errorMsg = response.errorBody()?.string()?.let { body ->
                    try {
                        Gson().fromJson(body, ErrorBody::class.java)?.error?.message ?: body
                    } catch (_: Exception) {
                        body
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

    suspend fun sendMessageControlled(
        userMessage: String,
        params: ControlParams
    ): Result<String> = withContext(Dispatchers.IO) {
        if (userMessage.isBlank()) return@withContext Result.failure(IllegalArgumentException("Empty message"))

        val maxChars = params.maxChars?.takeIf { it > 0 }
        val maxTokens = maxChars?.let { chars ->
            // Rough heuristic: 1 token ~= 3-4 chars in many cases; keep a safe minimum.
            (chars / 4).coerceAtLeast(16)
        }

        val systemParts = buildList {
            if (params.formatDescription.isNotBlank()) {
                add("FORMAT:\n${params.formatDescription.trim()}")
            }
            if (maxChars != null) {
                add("LIMIT: Answer must be no more than $maxChars characters.")
            }
            add("STOP: End your answer by outputting exactly the stop sequence: ${params.stopSequence}")
        }

        val request = DeepSeekRequest(
            model = "deepseek-chat",
            messages = listOf(
                ChatMessage(role = "system", content = systemParts.joinToString("\n\n")),
                ChatMessage(role = "user", content = userMessage.trim())
            ),
            stream = false,
            temperature = 0.2,
            maxTokens = maxTokens,
            stop = listOf(params.stopSequence)
        )

        try {
            val response = api.createChatCompletion(request)
            if (response.isSuccessful) {
                val body = response.body()
                val content = body?.choices?.firstOrNull()?.message?.content
                if (content != null) {
                    Result.success(content)
                } else {
                    Result.failure(Exception("Empty response from API"))
                }
            } else {
                val errorMsg = response.errorBody()?.string()?.let { body ->
                    try {
                        Gson().fromJson(body, ErrorBody::class.java)?.error?.message ?: body
                    } catch (_: Exception) {
                        body
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
