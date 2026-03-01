package com.aichallenge.aiagentapp.data

import com.google.gson.annotations.SerializedName

// --- Model catalogue ---

data class ModelInfo(
    val id: String,
    val label: String,
    val tier: String,
    val inputPricePerM: Double,
    val outputPricePerM: Double
)

val AVAILABLE_MODELS = listOf(
    ModelInfo("liquid/lfm-2-24b-a2b", "LFM2 2B", "Слабая", 3.0, 12.0),
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
    @SerializedName("response_format") val responseFormat: ResponseFormat? = null
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
    @SerializedName("total_tokens") val totalTokens: Int = 0
)
