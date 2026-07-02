package com.aichallenge.aiagentapp.mcp

class McpToolExecutorImpl(
    private val registry: McpRegistry,
) : McpToolExecutor {

    override suspend fun listAvailableTools(): Result<List<McpToolInfo>> = runCatching {
        registry.listAgentTools().getOrThrow().map { it.toMcpToolInfo() }
    }

    override suspend fun listAgentToolUsages(): Result<List<McpToolUsage>> = runCatching {
        registry.listAgentTools().getOrThrow().map {
            McpToolUsage(serverName = it.serverName, toolName = it.name)
        }
    }

    override suspend fun resolveToolUsage(keyOrName: String): McpToolUsage {
        val tools = registry.listAgentTools().getOrNull().orEmpty()
        parseMcpToolKey(keyOrName)?.let { (serverId, toolName) ->
            tools.find { it.serverId == serverId && it.name == toolName }?.let {
                return McpToolUsage(serverName = it.serverName, toolName = it.name)
            }
        }
        tools.find { it.name == keyOrName }?.let {
            return McpToolUsage(serverName = it.serverName, toolName = it.name)
        }
        return McpToolUsage(serverName = "MCP", toolName = keyOrName)
    }

    override suspend fun callTool(name: String, argumentsJson: String): Result<McpToolCallResult> =
        registry.callTool(name, argumentsJson)
}
