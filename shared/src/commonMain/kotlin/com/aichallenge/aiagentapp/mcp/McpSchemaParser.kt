package com.aichallenge.aiagentapp.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class McpSchemaParam(
    val name: String,
    val type: String,
    val description: String?,
    val required: Boolean,
)

private val schemaJson = Json { ignoreUnknownKeys = true }

fun parseInputSchema(inputSchemaJson: String?): List<McpSchemaParam> {
    if (inputSchemaJson.isNullOrBlank()) return emptyList()
    return try {
        val root = schemaJson.parseToJsonElement(inputSchemaJson).jsonObject
        val properties = root["properties"]?.jsonObject ?: return emptyList()
        val required = root["required"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty()
        properties.map { (name, element) ->
            val obj = element.jsonObject
            McpSchemaParam(
                name = name,
                type = obj["type"]?.jsonPrimitive?.content ?: "any",
                description = obj["description"]?.jsonPrimitive?.content,
                required = name in required,
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun McpConnectionStatus.displayLabel(): String = when (this) {
    McpConnectionStatus.UNKNOWN -> "Не проверен"
    McpConnectionStatus.CHECKING -> "Подключение…"
    McpConnectionStatus.ONLINE -> "Online"
    McpConnectionStatus.OFFLINE -> "Offline"
    McpConnectionStatus.ERROR -> "Ошибка"
}

fun McpConnectionStatus.statusHint(): String = when (this) {
    McpConnectionStatus.UNKNOWN -> "Нажмите Refresh, чтобы проверить подключение"
    McpConnectionStatus.CHECKING -> "Проверяем доступность сервера…"
    McpConnectionStatus.ONLINE -> "Сервер доступен"
    McpConnectionStatus.OFFLINE -> "Сервер недоступен"
    McpConnectionStatus.ERROR -> "Ошибка при подключении"
}
