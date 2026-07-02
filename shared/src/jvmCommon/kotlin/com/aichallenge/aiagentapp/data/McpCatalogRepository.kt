package com.aichallenge.aiagentapp.data

import com.aichallenge.aiagentapp.mcp.McpCatalogPersisted
import com.aichallenge.aiagentapp.mcp.McpCatalogStore
import com.aichallenge.aiagentapp.mcp.McpServerProfile
import com.aichallenge.aiagentapp.mcp.McpToolEntryPersisted
import com.aichallenge.aiagentapp.platform.platformAppDataDir
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

class McpCatalogRepository(
    private val legacySettingsStore: McpSettingsStore = McpSettingsRepository(),
    dataDir: File = platformAppDataDir(),
) : McpCatalogStore {

    private val file = File(dataDir, FILE_NAME)
    private val legacyFile = File(dataDir, LEGACY_FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val cache = AtomicReference(loadInitial())

    override fun load(): McpCatalogPersisted = cache.get()

    override fun save(catalog: McpCatalogPersisted) {
        cache.set(catalog)
        writeToFile(catalog)
    }

    private fun writeToFile(catalog: McpCatalogPersisted) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(McpCatalogPersisted.serializer(), catalog), Charsets.UTF_8)
    }

    private fun loadInitial(): McpCatalogPersisted {
        if (file.exists()) {
            return try {
                json.decodeFromString(McpCatalogPersisted.serializer(), file.readText(Charsets.UTF_8))
            } catch (_: Exception) {
                McpCatalogPersisted()
            }
        }
        return migrateFromLegacySettings()
    }

    private fun migrateFromLegacySettings(): McpCatalogPersisted {
        if (!legacyFile.exists()) {
            return McpCatalogPersisted()
        }
        val legacy = legacySettingsStore.load()
        val id = UUID.randomUUID().toString()
        val server = McpServerProfile(
            id = id,
            name = "Default",
            connectionMode = legacy.connectionMode,
            httpUrl = legacy.httpUrl,
            stdioCommand = legacy.stdioCommand,
            enabled = true,
        )
        val catalog = McpCatalogPersisted(servers = listOf(server), tools = emptyList())
        writeToFile(catalog)
        return catalog
    }

    companion object {
        private const val FILE_NAME = "mcp_catalog.json"
        private const val LEGACY_FILE_NAME = "mcp_settings.txt"
    }
}
