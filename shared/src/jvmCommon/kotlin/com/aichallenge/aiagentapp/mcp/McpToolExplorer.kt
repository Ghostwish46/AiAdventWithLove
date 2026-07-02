package com.aichallenge.aiagentapp.mcp

import com.google.gson.Gson
import com.google.gson.JsonParser
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.Json as KotlinxJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

class McpToolExplorer {
    private var client: Client? = null
    private var httpClient: HttpClient? = null
    private var stdioProcess: Process? = null

    suspend fun connect(config: McpConnectionConfig) {
        require(client == null) { "Already connected" }
        when (config) {
            is McpConnectionConfig.Stdio -> connectStdio(config)
            is McpConnectionConfig.Http -> connectHttp(config.url)
        }
    }

    private suspend fun connectStdio(config: McpConnectionConfig.Stdio) {
        val mcpClient = createMcpClient()
        val process = ProcessBuilder(config.command)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
        stdioProcess = process
        mcpClient.connect(
            StdioClientTransport(
                input = process.inputStream.asSource().buffered(),
                output = process.outputStream.asSink().buffered(),
                error = process.errorStream.asSource().buffered(),
            ),
        )
        client = mcpClient
    }

    private suspend fun connectHttp(url: String) {
        val ktorClient = HttpClient { install(SSE) }
        httpClient = ktorClient

        runCatching {
            val mcpClient = createMcpClient()
            mcpClient.connect(StreamableHttpClientTransport(client = ktorClient, url = url))
            client = mcpClient
        }.recoverCatching { error ->
            if (!shouldTryLegacySse(url)) throw error
            val mcpClient = createMcpClient()
            mcpClient.connect(
                SseClientTransport(
                    client = ktorClient,
                    urlString = legacySseUrl(url),
                ),
            )
            client = mcpClient
        }.getOrThrow()
    }

    private fun shouldTryLegacySse(url: String): Boolean {
        val lower = url.lowercase()
        val isLocalHost = "127.0.0.1" in lower || "localhost" in lower || "10.0.2.2" in lower
        return isLocalHost && lower.contains("/mcp")
    }

    private fun createMcpClient(): Client = Client(
        clientInfo = Implementation(
            name = "AiAgentApp",
            version = "1.0.0",
        ),
    )

    /** Local kotlin-sdk 0.10 AniList server uses legacy SSE at `/`; strip `/mcp` suffix. */
    private fun legacySseUrl(url: String): String {
        val trimmed = url.trim().removeSuffix("/")
        if (trimmed.endsWith("/mcp", ignoreCase = true)) {
            val base = trimmed.dropLast(4).removeSuffix("/")
            return if (base.isEmpty()) trimmed else "$base/"
        }
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    suspend fun listTools(): List<McpToolInfo> {
        val mcpClient = client ?: error("Not connected")
        return mcpClient.listTools().tools.map { tool ->
            McpToolInfo(
                name = tool.name,
                description = tool.description,
                inputSchemaJson = tool.inputSchema?.let { schema ->
                    val propertiesJson = KotlinxJson.encodeToString(
                        JsonObject.serializer(),
                        schema.properties ?: buildJsonObject {},
                    )
                    val requiredFields = schema.required
                    if (requiredFields.isNullOrEmpty()) {
                        """{"type":"object","properties":$propertiesJson}"""
                    } else {
                        val required = requiredFields.joinToString(",") { "\"$it\"" }
                        """{"type":"object","properties":$propertiesJson,"required":[$required]}"""
                    }
                },
            )
        }
    }

    suspend fun callTool(name: String, argumentsJson: String): McpToolCallResult {
        val mcpClient = client ?: error("Not connected")
        val arguments = parseArguments(argumentsJson)
        val result = mcpClient.callTool(name = name, arguments = arguments)
        val text = result.content
            .filterIsInstance<TextContent>()
            .joinToString("\n") { it.text ?: "" }
            .ifBlank { "(empty tool result)" }
        return McpToolCallResult(
            text = text,
            isError = result.isError == true,
        )
    }

    suspend fun close() {
        client?.close()
        client = null
        httpClient?.close()
        httpClient = null
        stdioProcess?.destroyForcibly()
        stdioProcess = null
    }

    private fun parseArguments(argumentsJson: String): Map<String, Any?> {
        val trimmed = argumentsJson.trim()
        if (trimmed.isEmpty() || trimmed == "{}") return emptyMap()
        val element = JsonParser.parseString(trimmed)
        if (!element.isJsonObject) return emptyMap()
        return element.asJsonObject.entrySet().associate { (key, value) ->
            key to when {
                value.isJsonNull -> null
                value.isJsonPrimitive -> {
                    val primitive = value.asJsonPrimitive
                    when {
                        primitive.isBoolean -> primitive.asBoolean
                        primitive.isNumber -> primitive.asNumber
                        else -> primitive.asString
                    }
                }
                else -> Gson().toJson(value)
            }
        }
    }
}
