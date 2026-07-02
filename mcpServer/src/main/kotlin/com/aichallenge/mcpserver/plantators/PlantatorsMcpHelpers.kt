package com.aichallenge.mcpserver.plantators

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal fun stringProp(description: String): JsonObject = buildJsonObject {
    put("type", "string")
    put("description", description)
}

internal fun intProp(description: String): JsonObject = buildJsonObject {
    put("type", "integer")
    put("description", description)
}

internal fun schema(vararg required: Pair<String, JsonObject>, requiredNames: List<String> = emptyList()): ToolSchema {
    return ToolSchema(
        properties = buildJsonObject {
            required.forEach { (name, prop) -> put(name, prop) }
        },
        required = requiredNames,
    )
}

internal fun JsonObject.stringArg(name: String): String? =
    this[name]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }

internal fun JsonObject.intArg(name: String): Int? =
    this[name]?.jsonPrimitive?.int

internal fun JsonObject.requireString(name: String): String {
    return stringArg(name) ?: error("'$name' parameter is required")
}

internal fun JsonObject.requireInt(name: String): Int {
    return intArg(name) ?: error("'$name' parameter is required")
}

internal suspend fun runPlantatorsTool(block: suspend () -> String): CallToolResult {
    return try {
        CallToolResult(content = listOf(TextContent(block())))
    } catch (e: PlantatorsAuthException) {
        CallToolResult(content = listOf(TextContent("Error: ${e.message}")), isError = true)
    } catch (e: IllegalArgumentException) {
        CallToolResult(content = listOf(TextContent("Error: ${e.message}")), isError = true)
    } catch (e: Exception) {
        CallToolResult(content = listOf(TextContent("Error: ${e.message ?: e.javaClass.simpleName}")), isError = true)
    }
}

internal fun PlantatorsClient.parseProductsJson(productsJson: String): List<Product> {
    val trimmed = productsJson.trim()
    if (trimmed.isEmpty()) return emptyList()
    return Json.decodeFromString<List<Product>>(trimmed)
}

internal inline fun <reified T> encodeResult(value: T): String = Json.encodeToString(value)

private val Json = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}
