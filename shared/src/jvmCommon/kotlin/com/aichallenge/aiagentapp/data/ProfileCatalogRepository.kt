package com.aichallenge.aiagentapp.data

import com.aichallenge.aiagentapp.agent.profile.AssistantProfile
import com.aichallenge.aiagentapp.platform.platformAppDataDir
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class ProfileCatalogRepository(
    dataDir: File = platformAppDataDir()
) : ProfileCatalogStore {

    private val file = File(dataDir, FILE_NAME)
    private val cache = AtomicReference<List<AssistantProfile>>(emptyList())

    init {
        loadToCache()
    }

    override fun loadCustomProfiles(): List<AssistantProfile> = cache.get()

    override fun saveCustomProfiles(profiles: List<AssistantProfile>) {
        cache.set(profiles)
        writeToFile(profiles)
    }

    private fun loadToCache() {
        if (!file.exists()) {
            cache.set(emptyList())
            return
        }
        try {
            cache.set(parseProfileCatalogJson(file.readText(Charsets.UTF_8)))
        } catch (_: Exception) {
            cache.set(emptyList())
        }
    }

    private fun writeToFile(profiles: List<AssistantProfile>) {
        try {
            file.parentFile?.mkdirs()
            file.writeText(encodeProfileCatalogJson(profiles), Charsets.UTF_8)
        } catch (_: Exception) { }
    }

    companion object {
        private const val FILE_NAME = "profile_catalog.json"
    }
}
