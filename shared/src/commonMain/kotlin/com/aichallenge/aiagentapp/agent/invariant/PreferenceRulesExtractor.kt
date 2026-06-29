package com.aichallenge.aiagentapp.agent.invariant

/**
 * Извлекает ограничения вида «только X» из текста правил пользователя (не из захардкоженного списка).
 */
object PreferenceRulesExtractor {

    private val onlyPattern = Regex("""только\s+([^\n,.;]+)""", RegexOption.IGNORE_CASE)

    fun extractOnlyConstraints(blocks: List<InvariantBlock>): List<String> =
        blocks.flatMap { block -> block.rules.map { it.text } }
            .flatMap { ruleText -> onlyPattern.findAll(ruleText).map { it.groupValues[1].trim() } }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }

    fun formatForClassifier(blocks: List<InvariantBlock>): String {
        val constraints = extractOnlyConstraints(blocks)
        if (constraints.isEmpty()) return ""
        return constraints.joinToString("\n") { "• только $it" }
    }
}
