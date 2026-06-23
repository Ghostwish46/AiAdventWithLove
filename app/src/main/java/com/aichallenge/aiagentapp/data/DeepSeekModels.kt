package com.aichallenge.aiagentapp.data

import com.google.gson.annotations.SerializedName

// --- Model catalogue ---

data class ModelInfo(
    val id: String,
    val label: String,
    val tier: String,
    val inputPricePerM: Double,
    val outputPricePerM: Double,
    val contextLength: Int? = null
)

val AVAILABLE_MODELS = listOf(
    ModelInfo("liquid/lfm-2-24b-a2b", "LFM2 2B", "Слабая", 3.0, 12.0),
    ModelInfo("qwen/qwen3.5-flash-02-23", "Qwen Flash", "Быстрая", 10.0, 40.0),
    ModelInfo("qwen/qwen3.5-27b", "Qwen3.5 27B", "Средняя", 30.0, 241.0),
    ModelInfo("openai/gpt-5.2", "GPT-5.2", "Сильная", 175.0, 1406.0)
)

data class ModelResponse(
    val content: String,
    val usage: Usage?,
    val elapsedMs: Long
)

// --- Request ---

data class DeepSeekRequest(
    val model: String = "deepseek-chat",
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    val temperature: Double? = null,
    @SerializedName("max_tokens") val maxTokens: Int? = null,
    val stop: List<String>? = null,
    @SerializedName("response_format") val responseFormat: ResponseFormat? = null,
    @SerializedName("stream_options") val streamOptions: StreamOptions? = null
)

data class StreamOptions(
    @SerializedName("include_usage") val includeUsage: Boolean = true
)

data class ChatMessage(
    val role: String,
    val content: String
)

data class ResponseFormat(
    val type: String // "text" | "json_object"
)

// --- Response ---

data class DeepSeekResponse(
    val id: String? = null,
    val choices: List<Choice>? = null,
    val usage: Usage? = null
)

data class Choice(
    val index: Int = 0,
    val message: Message? = null,
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class Message(
    val role: String? = null,
    val content: String? = null,
    @SerializedName("reasoning_content") val reasoningContent: String? = null
)

data class Usage(
    @SerializedName("prompt_tokens") val promptTokens: Int = 0,
    @SerializedName("completion_tokens") val completionTokens: Int = 0,
    @SerializedName("total_tokens") val totalTokens: Int = 0,
    @SerializedName("input_tokens") val inputTokens: Int = 0,
    @SerializedName("output_tokens") val outputTokens: Int = 0
) {
    fun normalized(): Usage {
        val prompt = when {
            promptTokens > 0 -> promptTokens
            inputTokens > 0 -> inputTokens
            else -> 0
        }
        val completion = when {
            completionTokens > 0 -> completionTokens
            outputTokens > 0 -> outputTokens
            else -> 0
        }
        val total = when {
            totalTokens > 0 -> totalTokens
            prompt + completion > 0 -> prompt + completion
            else -> 0
        }
        return copy(
            promptTokens = prompt,
            completionTokens = completion,
            totalTokens = total,
            inputTokens = 0,
            outputTokens = 0
        )
    }

    fun isMeaningful(): Boolean = totalTokens > 0 || promptTokens > 0 || completionTokens > 0
}

fun estimateTokenCount(text: String): Int =
    if (text.isEmpty()) 0 else kotlin.math.ceil(text.length / 3.0).toInt()

fun estimateUsage(promptMessages: List<ChatMessage>, completionText: String): Usage {
    val promptText = promptMessages.joinToString("\n") { "${it.role}: ${it.content}" }
    val promptTokens = estimateTokenCount(promptText)
    val completionTokens = estimateTokenCount(completionText)
    return Usage(
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        totalTokens = promptTokens + completionTokens
    )
}

// --- Streaming (SSE) ---

data class StreamChunk(
    val choices: List<StreamChoice>? = null,
    val usage: Usage? = null
)

data class StreamChoice(
    val delta: StreamDelta? = null,
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class StreamDelta(
    val content: String? = null,
    val reasoning: String? = null
)
