package com.aichallenge.aiagentapp.agent.memory

/**
 * Рабочая память: данные текущей задачи (per-chat).
 */
data class WorkingMemory(
    val facts: Map<String, String> = emptyMap(),
    val taskSummary: String? = null
) {
    fun merge(updates: Map<String, String>, newSummary: String? = null): WorkingMemory {
        if (updates.isEmpty() && newSummary == null) return this
        val merged = LinkedHashMap(facts)
        updates.forEach { (k, v) ->
            if (v.isBlank()) merged.remove(k) else merged[k] = v
        }
        val summary = newSummary?.takeIf { it.isNotBlank() } ?: taskSummary
        return copy(facts = merged, taskSummary = summary)
    }

    fun displayText(): String {
        if (facts.isEmpty() && taskSummary.isNullOrBlank()) return "(пусто)"
        return buildString {
            if (!taskSummary.isNullOrBlank()) {
                appendLine("Сводка: $taskSummary")
            }
            facts.forEach { (k, v) -> appendLine("$k: $v") }
        }.trim()
    }

    fun isEmpty(): Boolean = facts.isEmpty() && taskSummary.isNullOrBlank()
}
