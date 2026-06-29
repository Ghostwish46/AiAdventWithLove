package com.aichallenge.aiagentapp.agent.validation

import com.aichallenge.aiagentapp.agent.invariant.ForbiddenTermsExtractor
import com.aichallenge.aiagentapp.agent.invariant.InvariantBlock
import com.aichallenge.aiagentapp.agent.invariant.StrictRulesExtractor
import com.aichallenge.aiagentapp.agent.task.TaskPhase
import com.aichallenge.aiagentapp.agent.task.TaskState

object ResponseValidator {

    private val codeFencePattern = Regex("```[\\s\\S]*?```")
    private val donePhrases = listOf(
        "задача завершена",
        "работа завершена",
        "финальный результат принят",
        "переходим к done"
    )

    fun validateLocally(
        response: String,
        relevantBlocks: List<InvariantBlock>,
        taskState: TaskState,
        includeTaskState: Boolean
    ): ValidationResult {
        val violations = mutableListOf<String>()

        val forbiddenTerms = ForbiddenTermsExtractor.extractFromBlocks(relevantBlocks)
        forbiddenTerms.forEach { term ->
            if (ForbiddenTermsExtractor.responseContainsForbiddenTerm(response, term)) {
                violations += "Ответ упоминает запрещённое: $term"
            }
        }

        violations += validateStrictMandatoryPreferences(response, relevantBlocks)

        if (taskState.isActive) {
            violations += validatePhaseRules(response, taskState)
        }

        return if (violations.isEmpty()) {
            ValidationResult.Pass(response)
        } else {
            ValidationResult.Fail(violations.distinct())
        }
    }

    private fun validatePhaseRules(response: String, taskState: TaskState): List<String> {
        val violations = mutableListOf<String>()
        val lower = response.lowercase()
        val hasCode = codeFencePattern.containsMatchIn(response) ||
            lower.contains("fun ") ||
            lower.contains("class ") ||
            lower.contains("val ") ||
            lower.contains("import ")

        when (taskState.phase) {
            TaskPhase.PLANNING -> {
                if (hasCode) {
                    violations += "На этапе planning запрещено выдавать код или реализацию"
                }
                val prematureClosure = listOf(
                    "рад что смог помочь", "рад что помог", "рад был помочь",
                    "рад, что смог", "обращайся если", "задача выполнена", "всё готово"
                )
                if (prematureClosure.any { lower.contains(it) }) {
                    violations += "На этапе planning нельзя завершать диалог как выполненную задачу — нужны execution и validation"
                }
            }
            TaskPhase.EXECUTION -> {
                if (donePhrases.any { lower.contains(it) }) {
                    violations += "На этапе execution нельзя завершать задачу без validation"
                }
                if (lower.contains("отчёт о проверке") || lower.contains("отчет о проверке")) {
                    violations += "На этапе execution нельзя выдавать validation-отчёт"
                }
            }
            TaskPhase.VALIDATION -> {
                val hasReport = lower.contains("отчёт о проверке") || lower.contains("отчет о проверке")
                if (!hasReport && donePhrases.any { lower.contains(it) }) {
                    violations += "Нельзя завершать задачу без блока «Отчёт о проверке»"
                }
            }
            TaskPhase.DONE -> Unit
        }
        return violations
    }

    /**
     * Проверяет явные нарушения обязательных предпочтений из текста правил пользователя.
     * Срабатывает только когда правило явно задаёт единственный допустимый язык (Kotlin и т.п.).
     */
    private fun validateStrictMandatoryPreferences(
        response: String,
        blocks: List<InvariantBlock>
    ): List<String> {
        val violations = mutableListOf<String>()
        val mandatory = StrictRulesExtractor.mandatoryPreferences(blocks)
        val only = StrictRulesExtractor.onlyConstraints(blocks)
        val languageConstraints = (mandatory + only.map { "только $it" })
            .filter { rule ->
                val lower = rule.lowercase()
                lower.contains("kotlin") || lower.contains("язык")
            }

        if (languageConstraints.isEmpty()) return violations

        val lower = response.lowercase()
        val codeFenceLangs = Regex("```(\\w+)")
            .findAll(lower)
            .map { it.groupValues[1] }
            .filter { it.isNotBlank() && it !in setOf("text", "bash", "shell", "json", "xml", "yaml", "md") }
            .toSet()

        val requiresKotlin = languageConstraints.any { it.lowercase().contains("kotlin") }
        if (requiresKotlin) {
            val foreignLangs = codeFenceLangs.filter { it !in setOf("kotlin", "kt") }
            if (foreignLangs.isNotEmpty()) {
                violations += "Ответ содержит код на ${foreignLangs.joinToString(", ")} — нарушение строгого инварианта (Kotlin)"
            }
            if (lower.contains("using system") || lower.contains("console.writeline") ||
                lower.contains("```csharp") || lower.contains("```cs") ||
                (lower.contains("public static void main") && lower.contains("system.out"))
            ) {
                violations += "Ответ содержит код не на Kotlin — нарушение строгого инварианта"
            }
        }
        return violations
    }
}
