package com.aichallenge.aiagentapp.data

data class SavedMessage(
    val role: String,
    val content: String,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val elapsedMs: Long? = null,
    val estimatedCostRub: Double? = null
) {
    fun toChatMessage(): ChatMessage = ChatMessage(role = role, content = content)

    fun toUsage(): Usage? {
        if (promptTokens == null && completionTokens == null && totalTokens == null) return null
        val usage = Usage(
            promptTokens = promptTokens ?: 0,
            completionTokens = completionTokens ?: 0,
            totalTokens = totalTokens ?: (promptTokens ?: 0) + (completionTokens ?: 0)
        )
        return usage.takeIf { it.isMeaningful() }
    }
}
