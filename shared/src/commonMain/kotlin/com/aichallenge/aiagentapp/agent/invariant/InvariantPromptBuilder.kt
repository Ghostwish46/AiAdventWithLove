package com.aichallenge.aiagentapp.agent.invariant

import com.aichallenge.aiagentapp.agent.task.TaskState

object InvariantPromptBuilder {

    fun buildInvariantsBlock(blocks: List<InvariantBlock>): String {
        if (blocks.isEmpty()) return ""
        return buildString {
            append("[INVARIANTS — СТРОГИЕ ОГРАНИЧЕНИЯ]")
            append("\n")
            append(StrictInvariantPolicy.injectedPreamble)
            blocks.forEach { block ->
                append("\n\n--- ${block.title} ---")
                block.rules.forEach { rule ->
                    append("\n• ${rule.text}")
                }
            }
            append(
                "\n\nНарушение ЛЮБОГО правила ЗАПРЕЩЕНО. " +
                    "Предпочтения обязательны: это не «можно, но лучше», а единственно допустимый стек/жанр/подход."
            )
        }
    }

    fun buildConflictBlock(
        conflictSummary: String,
        violatedRules: List<String>,
        suggestedAlternative: String
    ): String = buildString {
        append("\n\n--- КОНФЛИКТ С ИНВАРИАНТОМ (СТРОГОЕ ОГРАНИЧЕНИЕ) ---")
        append("\n$conflictSummary")
        if (violatedRules.isNotEmpty()) {
            append("\nНарушенные правила:")
            violatedRules.forEach { append("\n• $it") }
        }
        if (suggestedAlternative.isNotBlank()) {
            append("\nДопустимая альтернатива (строго в рамках инвариантов): $suggestedAlternative")
        }
        append(
            "\nДействие: ОТКАЖИ выполнить запрос как написан. " +
                "Формат: «Не могу выполнить запрос» → какое правило → почему (строгое ограничение) → альтернатива в рамках инвариантов."
        )
    }

    fun buildReworkBlock(violations: List<String>): String = buildString {
        append("\n\n--- ПЕРЕРАБОТКА: НАРУШЕНЫ СТРОГИЕ ИНВАРИАНТЫ ---")
        append("\nПредыдущий ответ нарушил обязательные ограничения. Перегенерируй строго по правилам:")
        violations.forEach { append("\n• $it") }
        append(
            "\nПредпочтения блока обязательны. Не предлагай альтернативы из других языков, жанров, фреймворков."
        )
    }

    fun appendStateLine(taskState: TaskState): String {
        if (!taskState.isActive) return ""
        return buildString {
            append("\n\n[STATE] phase=${taskState.phase.name.lowercase()}")
            if (taskState.currentStep.isNotBlank()) append(", step=${taskState.currentStep}")
            if (!taskState.taskGoal.isNullOrBlank()) append(", goal=${taskState.taskGoal}")
        }
    }
}
