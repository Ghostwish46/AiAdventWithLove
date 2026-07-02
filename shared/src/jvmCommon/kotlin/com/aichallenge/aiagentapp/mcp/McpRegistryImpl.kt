package com.aichallenge.aiagentapp.mcp

import com.aichallenge.aiagentapp.data.McpConnectionMode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class McpRegistryImpl(
    private val catalogStore: McpCatalogStore,
) : McpRegistry {

    private val mutex = Mutex()
    private val runtimeStatus = ConcurrentHashMap<String, McpConnectionStatus>()
    private val runtimeErrors = ConcurrentHashMap<String, String?>()

    override fun getCatalog(): McpCatalog = buildCatalogView(catalogStore.load())

    override fun addServer(
        name: String,
        connectionMode: McpConnectionMode,
        httpUrl: String,
        stdioCommand: String,
    ): McpServerProfile {
        val profile = McpServerProfile(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "MCP Server" },
            connectionMode = connectionMode,
            httpUrl = httpUrl.trim(),
            stdioCommand = stdioCommand.trim(),
            enabled = true,
        )
        val persisted = catalogStore.load()
        catalogStore.save(persisted.copy(servers = persisted.servers + profile))
        runtimeStatus[profile.id] = McpConnectionStatus.UNKNOWN
        return profile
    }

    override fun updateServer(profile: McpServerProfile) {
        val persisted = catalogStore.load()
        catalogStore.save(
            persisted.copy(
                servers = persisted.servers.map { if (it.id == profile.id) profile else it },
            )
        )
    }

    override fun removeServer(id: String) {
        val persisted = catalogStore.load()
        catalogStore.save(
            persisted.copy(
                servers = persisted.servers.filterNot { it.id == id },
                tools = persisted.tools.filterNot { it.serverId == id },
            )
        )
        runtimeStatus.remove(id)
        runtimeErrors.remove(id)
    }

    override fun setServerEnabled(id: String, enabled: Boolean) {
        val persisted = catalogStore.load()
        catalogStore.save(
            persisted.copy(
                servers = persisted.servers.map {
                    if (it.id == id) it.copy(enabled = enabled) else it
                },
            )
        )
    }

    override fun setToolEnabled(serverId: String, toolName: String, enabled: Boolean) {
        val persisted = catalogStore.load()
        catalogStore.save(
            persisted.copy(
                tools = persisted.tools.map {
                    if (it.serverId == serverId && it.name == toolName) {
                        it.copy(enabled = enabled)
                    } else {
                        it
                    }
                },
            )
        )
    }

    override suspend fun refreshAll(): McpCatalog = mutex.withLock {
        val persisted = catalogStore.load()
        for (server in persisted.servers) {
            refreshServerLocked(server.id)
        }
        buildCatalogView(catalogStore.load())
    }

    override suspend fun refreshServer(id: String): McpCatalog = mutex.withLock {
        refreshServerLocked(id)
        buildCatalogView(catalogStore.load())
    }

    override suspend fun listAgentTools(): Result<List<McpAgentTool>> = mutex.withLock {
        ensureUnknownServersRefreshedLocked()
        Result.success(buildAgentToolsLocked())
    }

    private suspend fun ensureUnknownServersRefreshedLocked() {
        val persisted = catalogStore.load()
        for (server in persisted.servers.filter { it.enabled }) {
            val status = runtimeStatus[server.id] ?: McpConnectionStatus.UNKNOWN
            if (status == McpConnectionStatus.UNKNOWN) {
                refreshServerLocked(server.id)
            }
        }
    }

    private fun buildAgentToolsLocked(): List<McpAgentTool> {
        val catalog = buildCatalogView(catalogStore.load())
        return catalog.tools
            .filter { tool ->
                val server = catalog.servers.find { it.profile.id == tool.serverId }
                server != null &&
                    server.profile.enabled &&
                    tool.enabled &&
                    tool.status == McpConnectionStatus.ONLINE
            }
            .map { tool ->
                val serverName = catalog.servers.first { it.profile.id == tool.serverId }.profile.name
                tool.toAgentTool(serverName)
            }
    }

    override suspend fun callTool(toolKey: String, argumentsJson: String): Result<McpToolCallResult> =
        runCatching {
            val (serverId, toolName) = resolveToolRouting(toolKey)
            val server = catalogStore.load().servers.find { it.id == serverId }
                ?: error("Server not found: $serverId")
            withExplorer(server) { explorer ->
                explorer.callTool(toolName, argumentsJson)
            }
        }

    private suspend fun resolveToolRouting(keyOrName: String): Pair<String, String> {
        parseMcpToolKey(keyOrName)?.let { return it }
        val agentTools = listAgentTools().getOrThrow()
        val matches = agentTools.filter { it.name == keyOrName }
        return when {
            matches.isEmpty() -> error("Invalid tool key: $keyOrName")
            matches.size > 1 -> error(
                "Ambiguous tool '$keyOrName' on ${matches.size} servers — use serverId::toolName",
            )
            else -> matches.first().serverId to matches.first().name
        }
    }

    private suspend fun refreshServerLocked(serverId: String) {
        val persisted = catalogStore.load()
        val server = persisted.servers.find { it.id == serverId } ?: return

        runtimeStatus[serverId] = McpConnectionStatus.CHECKING
        runtimeErrors.remove(serverId)

        val result = runCatching {
            withExplorer(server) { it.listTools() }
        }

        result.fold(
            onSuccess = { remoteTools ->
                runtimeStatus[serverId] = McpConnectionStatus.ONLINE
                val existingByKey = persisted.tools
                    .filter { it.serverId == serverId }
                    .associateBy { it.name }
                val mergedTools = remoteTools.map { remote ->
                    val existing = existingByKey[remote.name]
                    McpToolEntryPersisted(
                        serverId = serverId,
                        name = remote.name,
                        description = remote.description,
                        inputSchemaJson = remote.inputSchemaJson,
                        enabled = existing?.enabled ?: true,
                    )
                }
                val otherTools = persisted.tools.filter { it.serverId != serverId }
                catalogStore.save(persisted.copy(tools = otherTools + mergedTools))
            },
            onFailure = { error ->
                runtimeStatus[serverId] = McpConnectionStatus.OFFLINE
                runtimeErrors[serverId] = formatConnectionError(error)
                val updatedTools = persisted.tools.map { tool ->
                    if (tool.serverId == serverId) {
                        tool.copy(description = tool.description)
                    } else {
                        tool
                    }
                }
                catalogStore.save(persisted.copy(tools = updatedTools))
            },
        )
    }

    private fun buildCatalogView(persisted: McpCatalogPersisted): McpCatalog {
        val serverViews = persisted.servers.map { server ->
            McpServerView(
                profile = server,
                status = runtimeStatus[server.id] ?: McpConnectionStatus.UNKNOWN,
                lastError = runtimeErrors[server.id],
            )
        }
        val tools = persisted.tools.map { persistedTool ->
            val serverStatus = runtimeStatus[persistedTool.serverId] ?: McpConnectionStatus.UNKNOWN
            val toolStatus = when (serverStatus) {
                McpConnectionStatus.ONLINE -> McpConnectionStatus.ONLINE
                McpConnectionStatus.CHECKING -> McpConnectionStatus.CHECKING
                McpConnectionStatus.OFFLINE, McpConnectionStatus.ERROR -> McpConnectionStatus.OFFLINE
                McpConnectionStatus.UNKNOWN -> McpConnectionStatus.UNKNOWN
            }
            McpToolEntry(
                serverId = persistedTool.serverId,
                name = persistedTool.name,
                description = persistedTool.description,
                inputSchemaJson = persistedTool.inputSchemaJson,
                enabled = persistedTool.enabled,
                status = toolStatus,
                lastError = runtimeErrors[persistedTool.serverId],
            )
        }
        return McpCatalog(servers = serverViews, tools = tools)
    }

    private suspend fun <T> withExplorer(
        server: McpServerProfile,
        block: suspend (McpToolExplorer) -> T,
    ): T {
        val config = when (server.connectionMode) {
            McpConnectionMode.HTTP -> {
                val url = server.httpUrl.trim()
                require(url.isNotEmpty()) { "Укажите URL MCP-сервера" }
                McpConnectionConfig.Http(url)
            }
            McpConnectionMode.STDIO -> {
                val command = McpConnectionConfig.parseStdioCommandLine(server.stdioCommand)
                McpConnectionConfig.Stdio(command)
            }
        }
        val explorer = McpToolExplorer()
        try {
            explorer.connect(config)
            return block(explorer)
        } finally {
            explorer.close()
        }
    }
}
