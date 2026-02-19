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

    suspend fun sendMessage(userMessage: String): Result<String> = withContext(Dispatchers.IO) {
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
}
