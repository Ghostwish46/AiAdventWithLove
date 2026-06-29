package com.aichallenge.aiagentapp.agent.invariant

object InvariantBlocksFormatter {

    fun formatForClassifier(blocks: List<InvariantBlock>): String {
        if (blocks.isEmpty()) return "(нет активных блоков)"
        return blocks.joinToString("\n\n") { block -> formatBlock(block) }
    }

    fun formatBlock(block: InvariantBlock): String = buildString {
        append("id: ${block.id}")
        append("\ntitle: ${block.title}")
        if (block.domainHint.isNotBlank()) {
            append("\ndomainHint: ${block.domainHint}")
        }
        append("\nrules:")
        if (block.rules.isEmpty()) {
            append("\n  (нет правил)")
        } else {
            block.rules.forEach { rule ->
                append("\n  • ${rule.text}")
            }
        }
    }
}
