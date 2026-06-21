package com.aichallenge.aiagentapp.data

import com.google.gson.annotations.SerializedName

data class ModelResponse(
    val content: String,
    val usage: Usage?,
    val elapsedMs: Long
)

data class DeepSeekRequest(
    val model: String = "deepseek-chat",
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    val temperature: Double? = null,
    @SerializedName("max_tokens") val maxTokens: Int? = null,
    val stop: List<String>? = null,
    @SerializedName("response_format") val responseFormat: ResponseFormat? = null
)

data class ResponseFormat(
    val type: String
)

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
