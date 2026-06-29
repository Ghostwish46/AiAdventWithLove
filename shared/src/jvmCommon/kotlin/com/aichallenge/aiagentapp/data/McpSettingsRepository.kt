package com.aichallenge.aiagentapp.data

import com.aichallenge.aiagentapp.platform.platformAppDataDir
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class McpSettingsRepository(
    dataDir: File = platformAppDataDir()
) : McpSettingsStore {

    private val file = File(dataDir, FILE_NAME)
    private val cache = AtomicReference(loadFromFile())

    override fun load(): McpSettings = cache.get()

    override fun save(settings: McpSettings) {
        cache.set(settings)
        file.parentFile?.mkdirs()
        file.writeText(encode(settings), Charsets.UTF_8)
    }

    private fun loadFromFile(): McpSettings {
        if (!file.exists()) return McpSettings()
        return try {
            decode(file.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            McpSettings()
        }
    }

    private fun encode(settings: McpSettings): String = buildString {
        appendLine("mode=${settings.connectionMode.name}")
        appendLine("httpUrl=${settings.httpUrl}")
        appendLine("stdioCommand=${settings.stdioCommand}")
    }

    private fun decode(text: String): McpSettings {
        val values = text.lineSequence()
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
            }
            .toMap()
        val mode = values["mode"]?.let { runCatching { McpConnectionMode.valueOf(it) }.getOrNull() }
            ?: McpConnectionMode.HTTP
        return McpSettings(
            connectionMode = mode,
            httpUrl = values["httpUrl"]?.trim().orEmpty().ifBlank { McpSettings().httpUrl },
            stdioCommand = values["stdioCommand"]?.trim().orEmpty().ifBlank { McpSettings().stdioCommand },
        )
    }

    companion object {
        private const val FILE_NAME = "mcp_settings.txt"
    }
}
