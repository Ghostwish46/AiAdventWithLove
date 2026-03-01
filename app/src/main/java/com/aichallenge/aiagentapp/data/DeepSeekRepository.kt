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

    suspend fun sendWithStrategy(
        userMessage: String,
        strategy: PromptStrategy
    ): Result<String> = withContext(Dispatchers.IO) {
        if (userMessage.isBlank()) return@withContext Result.failure(IllegalArgumentException("Empty message"))

        val messages = when (strategy) {
            PromptStrategy.DIRECT -> listOf(
                ChatMessage(role = "user", content = userMessage.trim())
            )
            PromptStrategy.STEP_BY_STEP -> listOf(
                ChatMessage(
                    role = "system",
                    content = "Реши задачу пошагово, объясняя каждый шаг рассуждения. " +
                            "Нумеруй шаги. В конце дай итоговый ответ."
                ),
                ChatMessage(role = "user", content = userMessage.trim())
            )
            PromptStrategy.SELF_PROMPT -> listOf(
                ChatMessage(
                    role = "system",
                    content = "Ты — эксперт по prompt engineering. " +
                            "Составь оптимальный промпт для решения задачи пользователя. " +
                            "Выведи ТОЛЬКО готовый промпт, без решения задачи и без пояснений."
                ),
                ChatMessage(role = "user", content = userMessage.trim())
            )
            PromptStrategy.EXPERTS -> listOf(
                ChatMessage(
                    role = "system",
                    content = "Ты — группа экспертов (от 3 до 5 человек), имеющих прямое отношение к вопросу пользователя. " +
                            "Сначала перечисли выбранных экспертов (имя и роль/специализация). " +
                            "Затем каждый эксперт даёт свой развёрнутый ответ, подписывая имя и роль. " +
                            "Эксперты могут дополнять или не соглашаться друг с другом."
                ),
                ChatMessage(role = "user", content = userMessage.trim())
            )
        }

        val request = DeepSeekRequest(
            model = "deepseek-chat",
            messages = messages,
            stream = false
        )

        executeRequest(request)
    }

    private suspend fun executeRequest(request: DeepSeekRequest): Result<String> {
        return try {
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
