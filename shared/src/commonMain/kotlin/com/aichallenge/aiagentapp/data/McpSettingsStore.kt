package com.aichallenge.aiagentapp.data

enum class McpConnectionMode {
    HTTP,
    STDIO,
}

data class McpSettings(
    val connectionMode: McpConnectionMode = McpConnectionMode.HTTP,
    val httpUrl: String = "http://127.0.0.1:3000/mcp",
    val stdioCommand: String = "npx -y @modelcontextprotocol/server-everything",
)

interface McpSettingsStore {
    fun load(): McpSettings
    fun save(settings: McpSettings)
}
