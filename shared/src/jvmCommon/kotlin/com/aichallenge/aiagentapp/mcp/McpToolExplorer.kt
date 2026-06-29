package com.aichallenge.aiagentapp.mcp

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

class McpToolExplorer {
    private var client: Client? = null
    private var httpClient: HttpClient? = null
    private var stdioProcess: Process? = null

    suspend fun connect(config: McpConnectionConfig) {
        require(client == null) { "Already connected" }

        val mcpClient = Client(
            clientInfo = Implementation(
                name = "AiAgentApp",
                version = "1.0.0",
            ),
        )

        val transport = when (config) {
            is McpConnectionConfig.Stdio -> {
                val process = ProcessBuilder(config.command)
                    .redirectError(ProcessBuilder.Redirect.PIPE)
                    .start()
                stdioProcess = process
                StdioClientTransport(
                    input = process.inputStream.asSource().buffered(),
                    output = process.outputStream.asSink().buffered(),
                    error = process.errorStream.asSource().buffered(),
                )
            }
            is McpConnectionConfig.Http -> {
                val ktorClient = HttpClient(CIO) { install(SSE) }
                httpClient = ktorClient
                StreamableHttpClientTransport(
                    client = ktorClient,
                    url = config.url,
                )
            }
        }

        mcpClient.connect(transport)
        client = mcpClient
    }

    suspend fun listTools(): List<McpToolInfo> {
        val mcpClient = client ?: error("Not connected")
        return mcpClient.listTools().tools.map { tool ->
            McpToolInfo(
                name = tool.name,
                description = tool.description,
            )
        }
    }

    suspend fun close() {
        client?.close()
        client = null
        httpClient?.close()
        httpClient = null
        stdioProcess?.destroyForcibly()
        stdioProcess = null
    }
}
