package com.aichallenge.aiagentapp.data

import com.aichallenge.aiagentapp.platform.platformAppDataDir
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class ConversationRepository(
    dataDir: File = platformAppDataDir()
) : ConversationStore {

    private val file = File(dataDir, FILE_NAME)
    private val gson = Gson()
    private val type = object : TypeToken<List<Conversation>>() {}.type
    private val cache = AtomicReference<List<Conversation>>(emptyList())

    init {
        loadToCache()
    }

    override fun getAll(): List<Conversation> = cache.get().sortedByDescending { it.updatedAtMillis }

    override fun getById(id: String): Conversation? = cache.get().find { it.id == id }

    override fun save(conversation: Conversation) {
        val list = cache.get().toMutableList()
        val index = list.indexOfFirst { it.id == conversation.id }
        if (index >= 0) {
            list[index] = conversation
        } else {
            list.add(conversation)
        }
        cache.set(list)
        writeToFile(list)
    }

    override fun delete(id: String) {
        val list = cache.get().filter { it.id != id }
        cache.set(list)
        writeToFile(list)
    }

    private fun loadToCache() {
        if (!file.exists()) {
            cache.set(emptyList())
            return
        }
        try {
            val json = file.readText(Charsets.UTF_8)
            if (json.isBlank()) {
                cache.set(emptyList())
                return
            }
            val list: List<Conversation> = gson.fromJson(json, type) ?: emptyList()
            cache.set(list)
        } catch (_: Exception) {
            cache.set(emptyList())
        }
    }

    private fun writeToFile(list: List<Conversation>) {
        try {
            file.parentFile?.mkdirs()
            file.writeText(gson.toJson(list), Charsets.UTF_8)
        } catch (_: Exception) { }
    }

    companion object {
        private const val FILE_NAME = "conversations.json"
    }
}
