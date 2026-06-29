package com.aichallenge.aiagentapp.agent.invariant

import com.aichallenge.aiagentapp.data.InvariantConflictResult

object InvariantConflictRefusal {

    fun buildMessage(conflict: InvariantConflictResult): String = buildString {
        appendLine("Не могу выполнить запрос.")
        if (conflict.conflictSummary.isNotBlank()) {
            appendLine()
            appendLine(conflict.conflictSummary)
        }
        if (conflict.violatedRuleTexts.isNotEmpty()) {
            appendLine()
            appendLine("Нарушенные правила:")
            conflict.violatedRuleTexts.forEach { appendLine("• $it") }
        }
        if (conflict.suggestedAlternative.isNotBlank()) {
            appendLine()
            appendLine(conflict.suggestedAlternative)
        }
    }.trim()
}
