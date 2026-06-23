package com.aichallenge.aiagentapp.agent.memory

/**
 * Сборка блоков system-промпта из рабочей и долговременной памяти.
 * Краткосрочная память идёт отдельно как messages.
 */
object MemoryPromptBuilder {

    fun buildSystemPrompt(
        basePrompt: String,
        working: WorkingMemory,
        longTerm: LongTermMemory
    ): String = buildString {
        append(basePrompt)
        if (!working.isEmpty()) {
            append("\n\n--- РАБОЧАЯ ПАМЯТЬ (текущая задача) ---")
            if (!working.taskSummary.isNullOrBlank()) {
                append("\nСводка задачи: ${working.taskSummary}")
            }
            working.facts.forEach { (k, v) -> append("\n$k: $v") }
        }
        if (!longTerm.isEmpty()) {
            append("\n\n--- ДОЛГОВРЕМЕННАЯ ПАМЯТЬ ---")
            if (longTerm.profile.isNotEmpty()) {
                append("\nПрофиль:")
                longTerm.profile.forEach { (k, v) -> append("\n  $k: $v") }
            }
            if (longTerm.preferences.isNotEmpty()) {
                append("\nПредпочтения:")
                longTerm.preferences.forEach { (k, v) -> append("\n  $k: $v") }
            }
            if (longTerm.knowledge.isNotEmpty()) {
                append("\nЗнания:")
                longTerm.knowledge.forEach { (k, v) -> append("\n  $k: $v") }
            }
        }
    }
}
