package com.aichallenge.aiagentapp.agent.invariant

data class InvariantRule(
    val id: String,
    val text: String
) {
    fun isProhibition(): Boolean {
        val lower = text.trim().lowercase()
        return lower.startsWith("не использовать") ||
            lower.startsWith("запрещено") ||
            lower.startsWith("без ") ||
            lower.startsWith("не применять") ||
            lower.startsWith("не рекомендовать")
    }
}
