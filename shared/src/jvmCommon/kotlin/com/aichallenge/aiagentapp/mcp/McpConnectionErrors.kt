package com.aichallenge.aiagentapp.mcp

import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpError

actual fun formatConnectionError(error: Throwable): String {
    val chain = generateSequence(error as Throwable?) { it.cause }.toList()
    chain.filterIsInstance<StreamableHttpError>().firstOrNull()?.let { httpError ->
        val code = httpError.code
        val body = httpError.message
            ?.removePrefix("Streamable HTTP error:")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        return when {
            code != null && body != null -> "HTTP $code: $body"
            code == 406 -> "HTTP 406: сервер не принял запрос (несовместимый Accept или протокол MCP)"
            code != null -> "HTTP $code — не удалось подключиться к MCP-серверу"
            else -> httpError.message ?: "Streamable HTTP error"
        }
    }

    val message = chain
        .mapNotNull { it.message?.trim()?.takeIf { m -> m.isNotEmpty() } }
        .distinct()
        .joinToString(" → ")

    return message.ifBlank { error::class.simpleName ?: "Connection failed" }
}
