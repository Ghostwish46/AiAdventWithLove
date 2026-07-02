package com.aichallenge.aiagentapp.mcp

import com.aichallenge.aiagentapp.data.McpConnectionMode
import kotlinx.serialization.Serializable

@Serializable
data class McpServerProfile(
    val id: String,
    val name: String,
    val connectionMode: McpConnectionMode = McpConnectionMode.HTTP,
    val httpUrl: String = "",
    val stdioCommand: String = "",
    val enabled: Boolean = true,
)

enum class McpConnectionStatus {
    UNKNOWN,
    CHECKING,
    ONLINE,
    OFFLINE,
    ERROR,
}

@Serializable
data class McpToolEntryPersisted(
    val serverId: String,
    val name: String,
    val description: String? = null,
    val inputSchemaJson: String? = null,
    val enabled: Boolean = true,
)

@Serializable
data class McpCatalogPersisted(
    val servers: List<McpServerProfile> = emptyList(),
    val tools: List<McpToolEntryPersisted> = emptyList(),
)

data class McpToolEntry(
    val serverId: String,
    val name: String,
    val description: String? = null,
    val inputSchemaJson: String? = null,
    val enabled: Boolean = true,
    val status: McpConnectionStatus = McpConnectionStatus.UNKNOWN,
    val lastError: String? = null,
) {
    val toolKey: String get() = mcpToolKey(serverId, name)
}

data class McpServerView(
    val profile: McpServerProfile,
    val status: McpConnectionStatus = McpConnectionStatus.UNKNOWN,
    val lastError: String? = null,
)

data class McpCatalog(
    val servers: List<McpServerView> = emptyList(),
    val tools: List<McpToolEntry> = emptyList(),
)

data class McpAgentTool(
    val toolKey: String,
    val serverId: String,
    val serverName: String,
    val name: String,
    val description: String?,
    val inputSchemaJson: String?,
)

fun mcpToolKey(serverId: String, toolName: String): String = "$serverId::$toolName"

fun parseMcpToolKey(toolKey: String): Pair<String, String>? {
    val index = toolKey.indexOf("::")
    if (index <= 0 || index >= toolKey.lastIndex) return null
    return toolKey.substring(0, index) to toolKey.substring(index + 2)
}

fun McpToolEntry.toAgentTool(serverName: String): McpAgentTool = McpAgentTool(
    toolKey = toolKey,
    serverId = serverId,
    serverName = serverName,
    name = name,
    description = description,
    inputSchemaJson = inputSchemaJson,
)

fun McpAgentTool.toMcpToolInfo(): McpToolInfo = McpToolInfo(
    name = name,
    description = description?.let { "[$serverName] $it" } ?: "[$serverName] $name",
    inputSchemaJson = inputSchemaJson,
)

fun McpToolInfo.toAgentToolFromKey(): McpAgentTool? {
    val (serverId, name) = parseMcpToolKey(this.name) ?: return null
    return McpAgentTool(
        toolKey = this.name,
        serverId = serverId,
        serverName = serverId,
        name = name,
        description = description,
        inputSchemaJson = inputSchemaJson,
    )
}

fun McpAgentTool.toLlmToolDefinition(): com.aichallenge.aiagentapp.data.LlmToolDefinition {
    val parameters = inputSchemaJson?.takeIf { it.isNotBlank() }
        ?: """{"type":"object","properties":{}}"""
    val desc = buildString {
        append("[$serverName] ")
        append(description?.takeIf { it.isNotBlank() } ?: name)
    }
    return com.aichallenge.aiagentapp.data.LlmToolDefinition(
        name = name,
        description = desc,
        parametersJson = parameters,
    )
}
