package com.aichallenge.aiagentapp.mcp

interface McpConnectionTester {
    suspend fun testHttp(url: String): Result<List<McpToolInfo>>
    suspend fun testStdio(commandLine: String): Result<List<McpToolInfo>>
}
