package com.aichallenge.aiagentapp.data

import com.aichallenge.aiagentapp.agent.memory.LongTermMemory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class LongTermMemoryDto(
    val profile: Map<String, String> = emptyMap(),
    val preferences: Map<String, String> = emptyMap(),
    val knowledge: Map<String, String> = emptyMap()
)

private val longTermJson = Json { ignoreUnknownKeys = true }

fun parseLongTermMemoryJson(json: String?): LongTermMemory {
    if (json.isNullOrBlank()) return LongTermMemory()
    return try {
        val dto = longTermJson.decodeFromString(LongTermMemoryDto.serializer(), json)
        LongTermMemory(
            profile = dto.profile,
            preferences = dto.preferences,
            knowledge = dto.knowledge
        )
    } catch (_: Exception) {
        LongTermMemory()
    }
}

fun encodeLongTermMemoryJson(memory: LongTermMemory): String {
    val dto = LongTermMemoryDto(
        profile = memory.profile,
        preferences = memory.preferences,
        knowledge = memory.knowledge
    )
    return longTermJson.encodeToString(LongTermMemoryDto.serializer(), dto)
}
