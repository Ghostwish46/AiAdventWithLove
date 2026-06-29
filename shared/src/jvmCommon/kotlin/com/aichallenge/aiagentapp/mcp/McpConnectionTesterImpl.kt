package com.aichallenge.aiagentapp.mcp

class McpConnectionTesterImpl : McpConnectionTester {

    override suspend fun testHttp(url: String): Result<List<McpToolInfo>> = runCatching {
        val trimmedUrl = url.trim()
        require(trimmedUrl.isNotEmpty()) { "Укажите URL MCP-сервера" }
        connectAndListTools(McpConnectionConfig.Http(trimmedUrl))
    }

    override suspend fun testStdio(commandLine: String): Result<List<McpToolInfo>> = runCatching {
        val command = McpConnectionConfig.parseStdioCommandLine(commandLine)
        connectAndListTools(McpConnectionConfig.Stdio(command))
    }

    private suspend fun connectAndListTools(config: McpConnectionConfig): List<McpToolInfo> {
        val explorer = McpToolExplorer()
        try {
            explorer.connect(config)
            val tools = explorer.listTools()
            require(tools.isNotEmpty()) { "MCP-сервер не вернул инструменты" }
            return tools
        } finally {
            explorer.close()
        }
    }
}
