package com.aichallenge.aiagentapp.data

import com.aichallenge.aiagentapp.agent.memory.LongTermMemory
import com.aichallenge.aiagentapp.platform.platformAppDataDir
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class LongTermMemoryRepository(
    dataDir: File = platformAppDataDir()
) : LongTermMemoryStore {

    private val file = File(dataDir, FILE_NAME)
    private val cache = AtomicReference(LongTermMemory())

    init {
        loadToCache()
    }

    override fun load(): LongTermMemory = cache.get()

    override fun save(memory: LongTermMemory) {
        cache.set(memory)
        writeToFile(memory)
    }

    private fun loadToCache() {
        if (!file.exists()) {
            cache.set(LongTermMemory())
            return
        }
        try {
            val json = file.readText(Charsets.UTF_8)
            cache.set(parseLongTermMemoryJson(json))
        } catch (_: Exception) {
            cache.set(LongTermMemory())
        }
    }

    private fun writeToFile(memory: LongTermMemory) {
        try {
            file.parentFile?.mkdirs()
            file.writeText(encodeLongTermMemoryJson(memory), Charsets.UTF_8)
        } catch (_: Exception) { }
    }

    companion object {
        private const val FILE_NAME = "long_term_memory.json"
    }
}
