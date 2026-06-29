package com.aichallenge.aiagentapp.agent.invariant

/**
 * Извлекает строгие правила из блоков пользователя для классификаторов и проверок.
 */
object StrictRulesExtractor {

    fun mandatoryPreferences(blocks: List<InvariantBlock>): List<String> =
        blocks.flatMap { block -> block.rules.filter { !it.isProhibition() }.map { it.text.trim() } }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }

    fun prohibitionRules(blocks: List<InvariantBlock>): List<String> =
        blocks.flatMap { block -> block.rules.filter { it.isProhibition() }.map { it.text.trim() } }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }

    fun onlyConstraints(blocks: List<InvariantBlock>): List<String> =
        PreferenceRulesExtractor.extractOnlyConstraints(blocks)

    fun formatStrictSummary(blocks: List<InvariantBlock>): String {
        if (blocks.isEmpty()) return ""
        return buildString {
            appendLine(StrictInvariantPolicy.classifierRules)
            appendLine()
            val mandatory = mandatoryPreferences(blocks)
            val only = onlyConstraints(blocks)
            val prohibitions = prohibitionRules(blocks)
            if (mandatory.isNotEmpty()) {
                appendLine("Обязательные предпочтения (строго, без замен):")
                mandatory.forEach { appendLine("• $it") }
                appendLine()
            }
            if (only.isNotEmpty()) {
                appendLine("Исключительно только:")
                only.forEach { appendLine("• $it") }
                appendLine()
            }
            if (prohibitions.isNotEmpty()) {
                appendLine("Запреты:")
                prohibitions.forEach { appendLine("• $it") }
            }
        }.trimEnd()
    }
}
