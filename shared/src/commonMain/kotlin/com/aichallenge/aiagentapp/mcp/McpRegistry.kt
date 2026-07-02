package com.aichallenge.aiagentapp.mcp

import com.aichallenge.aiagentapp.data.McpConnectionMode

interface McpRegistry {
    fun getCatalog(): McpCatalog
    fun addServer(
        name: String,
        connectionMode: McpConnectionMode,
        httpUrl: String,
        stdioCommand: String,
    ): McpServerProfile
    fun updateServer(profile: McpServerProfile)
    fun removeServer(id: String)
    fun setServerEnabled(id: String, enabled: Boolean)
    fun setToolEnabled(serverId: String, toolName: String, enabled: Boolean)
    suspend fun refreshAll(): McpCatalog
    suspend fun refreshServer(id: String): McpCatalog
    suspend fun listAgentTools(): Result<List<McpAgentTool>>
    suspend fun callTool(toolKey: String, argumentsJson: String): Result<McpToolCallResult>
}
