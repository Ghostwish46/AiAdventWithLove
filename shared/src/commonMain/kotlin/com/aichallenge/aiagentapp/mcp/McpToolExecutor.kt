package com.aichallenge.aiagentapp.mcp

interface McpToolExecutor {
    suspend fun listAvailableTools(): Result<List<McpToolInfo>>
    suspend fun listAgentToolUsages(): Result<List<McpToolUsage>>
    suspend fun resolveToolUsage(keyOrName: String): McpToolUsage
    suspend fun callTool(name: String, argumentsJson: String): Result<McpToolCallResult>
}
