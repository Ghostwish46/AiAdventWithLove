package com.aichallenge.aiagentapp.data

import com.aichallenge.aiagentapp.agent.memory.WorkingMemory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class WorkingMemoryDto(
    val facts: Map<String, String> = emptyMap(),
    val taskSummary: String? = null
)

private val workingMemoryJson = Json { ignoreUnknownKeys = true }

fun parseWorkingMemoryJson(json: String?): WorkingMemory {
    if (json.isNullOrBlank()) return WorkingMemory()
    return try {
        val dto = workingMemoryJson.decodeFromString(WorkingMemoryDto.serializer(), json)
        WorkingMemory(facts = dto.facts, taskSummary = dto.taskSummary)
    } catch (_: Exception) {
        WorkingMemory()
    }
}

fun encodeWorkingMemoryJson(memory: WorkingMemory): String {
    val dto = WorkingMemoryDto(facts = memory.facts, taskSummary = memory.taskSummary)
    return workingMemoryJson.encodeToString(WorkingMemoryDto.serializer(), dto)
}
