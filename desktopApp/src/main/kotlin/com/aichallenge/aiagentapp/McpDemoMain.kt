package com.aichallenge.aiagentapp

import com.aichallenge.aiagentapp.mcp.McpConnectionConfig
import com.aichallenge.aiagentapp.mcp.McpToolExplorer
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) = runBlocking {
    val config = McpConnectionConfig.parse(args)
    val explorer = McpToolExplorer()
    try {
        when (config) {
            is McpConnectionConfig.Stdio -> {
                println("Connecting via stdio: ${config.command.joinToString(" ")}")
            }
            is McpConnectionConfig.Http -> {
                println("Connecting via HTTP: ${config.url}")
            }
        }

        explorer.connect(config)
        println("MCP connected")

        val tools = explorer.listTools()
        println("Tools (${tools.size}):")
        tools.forEach { tool ->
            val description = tool.description?.takeIf { it.isNotBlank() } ?: "(no description)"
            println("  - ${tool.name}: $description")
        }
        require(tools.isNotEmpty()) { "No tools returned" }
    } finally {
        explorer.close()
    }
}
