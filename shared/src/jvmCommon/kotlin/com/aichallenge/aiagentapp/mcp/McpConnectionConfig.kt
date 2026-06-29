package com.aichallenge.aiagentapp.mcp

sealed class McpConnectionConfig {
    data class Stdio(val command: List<String>) : McpConnectionConfig()
    data class Http(val url: String) : McpConnectionConfig()

    companion object {
        private val defaultStdioCommand = listOf(
            "npx",
            "-y",
            "@modelcontextprotocol/server-everything",
        )

        fun parse(args: Array<String>): McpConnectionConfig {
            System.getenv("MCP_URL")?.takeIf { it.isNotBlank() }?.let { url ->
                return Http(url)
            }
            System.getenv("MCP_STDIO_COMMAND")?.takeIf { it.isNotBlank() }?.let { commandLine ->
                return Stdio(parseCommandLine(commandLine))
            }

            var index = 0
            while (index < args.size) {
                when (args[index]) {
                    "--url" -> {
                        val url = args.getOrNull(index + 1)
                            ?: error("Missing value for --url")
                        return Http(url)
                    }
                    "--stdio" -> {
                        val command = args.drop(index + 1).takeIf { it.isNotEmpty() }
                            ?: defaultStdioCommand
                        return Stdio(command)
                    }
                }
                index++
            }

            return Stdio(defaultStdioCommand)
        }

        fun parseStdioCommandLine(commandLine: String): List<String> {
            val tokens = Regex("""[^\s"]+|"[^"]*"""").findAll(commandLine.trim())
                .map { it.value.trim('"') }
                .filter { it.isNotEmpty() }
                .toList()
            require(tokens.isNotEmpty()) { "Укажите команду запуска MCP-сервера" }
            return tokens
        }

        private fun parseCommandLine(commandLine: String): List<String> = parseStdioCommandLine(commandLine)
    }
}
