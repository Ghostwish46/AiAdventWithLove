package com.aichallenge.aiagentapp.data

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class ModelResponse(
    val content: String,
    val usage: Usage?,
    val elapsedMs: Long
)

data class DeepSeekRequest(
    val model: String = "deepseek-chat",
    val messages: List<ApiChatMessage>,
    val stream: Boolean = false,
    val temperature: Double? = null,
    @SerializedName("max_tokens") val maxTokens: Int? = null,
    val stop: List<String>? = null,
    @SerializedName("response_format") val responseFormat: ResponseFormat? = null,
    @SerializedName("stream_options") val streamOptions: StreamOptions? = null,
    val tools: List<ToolDefinition>? = null,
    @SerializedName("tool_choice") val toolChoice: String? = null,
)

data class ApiChatMessage(
    val role: String,
    val content: String? = null,
    @SerializedName("tool_calls") val toolCalls: List<ApiToolCall>? = null,
    @SerializedName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null,
)

data class ApiToolCall(
    val id: String,
    val type: String = "function",
    val function: ApiToolFunction,
)

data class ApiToolFunction(
    val name: String,
    val arguments: String,
)

data class ToolDefinition(
    val type: String = "function",
    val function: FunctionDefinition,
)

data class FunctionDefinition(
    val name: String,
    val description: String? = null,
    val parameters: JsonObject,
)

data class StreamOptions(
    @SerializedName("include_usage") val includeUsage: Boolean = true
)

data class ResponseFormat(
    val type: String
)

/** JSON DTO for OpenAI-compatible usage blocks (`prompt_tokens`, etc.). */
data class UsageJson(
    @SerializedName("prompt_tokens") val promptTokens: Int = 0,
    @SerializedName("completion_tokens") val completionTokens: Int = 0,
    @SerializedName("total_tokens") val totalTokens: Int = 0,
    @SerializedName("input_tokens") val inputTokens: Int = 0,
    @SerializedName("output_tokens") val outputTokens: Int = 0
) {
    fun toUsage(): Usage {
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
        return Usage(promptTokens = prompt, completionTokens = completion, totalTokens = total)
    }
}

data class DeepSeekResponse(
    val id: String? = null,
    val choices: List<Choice>? = null,
    val usage: UsageJson? = null
)

data class Choice(
    val index: Int = 0,
    val message: Message? = null,
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class Message(
    val role: String? = null,
    val content: String? = null,
    @SerializedName("reasoning_content") val reasoningContent: String? = null,
    @SerializedName("tool_calls") val toolCalls: List<ApiToolCall>? = null,
    @SerializedName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null,
)

data class StreamChunk(
    val choices: List<StreamChoice>? = null,
    val usage: UsageJson? = null
)

data class StreamChoice(
    val delta: StreamDelta? = null,
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class StreamDelta(
    val content: String? = null,
    val reasoning: String? = null
)
