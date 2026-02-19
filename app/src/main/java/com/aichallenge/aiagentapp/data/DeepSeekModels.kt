package com.aichallenge.aiagentapp.data

import com.google.gson.annotations.SerializedName

// --- Request ---

data class DeepSeekRequest(
    val model: String = "deepseek-chat",
    val messages: List<ChatMessage>,
    val stream: Boolean = false
)

data class ChatMessage(
    val role: String,
    val content: String
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
