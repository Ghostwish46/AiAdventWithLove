package com.aichallenge.aiagentapp.data

import com.google.gson.JsonParser

fun ChatMessage.toApiChatMessage(): ApiChatMessage =
    ApiChatMessage(role = role, content = content)

fun List<ChatMessage>.toApiChatMessages(): List<ApiChatMessage> =
    map { it.toApiChatMessage() }

fun ToolChatMessage.toApiChatMessage(): ApiChatMessage = ApiChatMessage(
    role = role,
    content = content,
    toolCalls = toolCalls?.map { call ->
        ApiToolCall(
            id = call.id,
            function = ApiToolFunction(
                name = call.name,
                arguments = call.argumentsJson,
            ),
        )
    },
    toolCallId = toolCallId,
    name = name,
)

fun List<ToolChatMessage>.toApiToolChatMessages(): List<ApiChatMessage> =
    map { it.toApiChatMessage() }

fun LlmToolDefinition.toApiToolDefinition(): ToolDefinition = ToolDefinition(
    function = FunctionDefinition(
        name = name,
        description = description,
        parameters = JsonParser.parseString(parametersJson).asJsonObject,
    ),
)

fun List<LlmToolDefinition>.toApiToolDefinitions(): List<ToolDefinition> =
    map { it.toApiToolDefinition() }

fun Message.toLlmToolResponseMessage(): Pair<String?, List<LlmToolCall>> {
    val calls = toolCalls.orEmpty().map { call ->
        LlmToolCall(
            id = call.id,
            name = call.function.name,
            argumentsJson = call.function.arguments,
        )
    }
    return content to calls
}
