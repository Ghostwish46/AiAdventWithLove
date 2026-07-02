package com.aichallenge.aiagentapp.data

import com.aichallenge.aiagentapp.mcp.McpToolInfo

data class LlmToolDefinition(
    val name: String,
    val description: String?,
    val parametersJson: String,
)

data class LlmToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

data class LlmToolResponse(
    val content: String?,
    val toolCalls: List<LlmToolCall>,
    val usage: Usage?,
    val elapsedMs: Long,
    val finishReason: String?,
)

data class ToolChatMessage(
    val role: String,
    val content: String? = null,
    val toolCalls: List<LlmToolCall>? = null,
    val toolCallId: String? = null,
    val name: String? = null,
) {
    companion object {
        fun system(content: String) = ToolChatMessage(role = "system", content = content)
        fun user(content: String) = ToolChatMessage(role = "user", content = content)
        fun assistant(content: String?) = ToolChatMessage(role = "assistant", content = content)
        fun tool(toolCallId: String, name: String, content: String) = ToolChatMessage(
            role = "tool",
            content = content,
            toolCallId = toolCallId,
            name = name,
        )
    }
}

fun LlmToolResponse.toAssistantToolMessage(): ToolChatMessage = ToolChatMessage(
    role = "assistant",
    content = content,
    toolCalls = toolCalls.takeIf { it.isNotEmpty() },
)

fun ChatMessage.toToolChatMessage(): ToolChatMessage =
    ToolChatMessage(role = role, content = content)

fun List<ChatMessage>.toToolChatMessages(): List<ToolChatMessage> = map { it.toToolChatMessage() }

fun McpToolInfo.toLlmToolDefinition(): LlmToolDefinition {
    val parameters = inputSchemaJson?.takeIf { it.isNotBlank() }
        ?: """{"type":"object","properties":{}}"""
    return LlmToolDefinition(
        name = name,
        description = description,
        parametersJson = parameters,
    )
}
