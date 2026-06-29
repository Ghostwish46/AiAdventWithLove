package com.aichallenge.aiagentapp.agent.invariant

object ForbiddenTermsExtractor {

    fun extractFromRule(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return emptyList()
        val lower = trimmed.lowercase()
        val prefixes = listOf(
            "не использовать ",
            "запрещено ",
            "без ",
            "не применять ",
            "не рекомендовать "
        )
        for (prefix in prefixes) {
            if (lower.startsWith(prefix)) {
                val rest = trimmed.drop(prefix.length).trim()
                if (rest.isNotBlank()) return listOf(rest)
            }
        }
        return emptyList()
    }

    fun extractFromBlock(block: InvariantBlock): List<String> =
        block.rules.flatMap { extractFromRule(it.text) }.distinctBy { it.lowercase() }

    fun extractFromBlocks(blocks: List<InvariantBlock>): List<String> =
        blocks.flatMap { extractFromBlock(it) }.distinctBy { it.lowercase() }

    fun responseContainsForbiddenTerm(response: String, term: String): Boolean {
        if (term.isBlank()) return false
        return Regex("\\b${Regex.escape(term)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(response)
    }
}
