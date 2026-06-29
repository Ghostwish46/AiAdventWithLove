package com.aichallenge.aiagentapp.data

import com.aichallenge.aiagentapp.agent.invariant.InvariantBlock
import com.aichallenge.aiagentapp.platform.platformAppDataDir
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class InvariantsRepository(
    dataDir: File = platformAppDataDir()
) : InvariantsStore {

    private val file = File(dataDir, FILE_NAME)
    private val cache = AtomicReference<List<InvariantBlock>>(emptyList())

    init {
        loadToCache()
        if (cache.get().isEmpty()) {
            val preset = listOf(InvariantBlock.defaultAndroidStack())
            cache.set(preset)
            writeToFile(preset)
        }
    }

    override fun loadBlocks(): List<InvariantBlock> = cache.get()

    override fun saveBlocks(blocks: List<InvariantBlock>) {
        cache.set(blocks)
        writeToFile(blocks)
    }

    private fun loadToCache() {
        if (!file.exists()) {
            cache.set(emptyList())
            return
        }
        try {
            cache.set(parseInvariantsJson(file.readText(Charsets.UTF_8)))
        } catch (_: Exception) {
            cache.set(emptyList())
        }
    }

    private fun writeToFile(blocks: List<InvariantBlock>) {
        file.parentFile?.mkdirs()
        file.writeText(encodeInvariantsJson(blocks), Charsets.UTF_8)
    }

    companion object {
        private const val FILE_NAME = "invariants.json"
    }
}
