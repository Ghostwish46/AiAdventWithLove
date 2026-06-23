package com.aichallenge.aiagentapp.data

data class Conversation(
    val id: String,
    val title: String,
    val messages: List<SavedMessage>,
    val updatedAtMillis: Long,
    /** SLIDING_WINDOW | FACTS_KV | BRANCHING */
    val contextStrategy: String? = null,
    /** Устарело; не используется. */
    val rollingSummary: String? = null,
    val stickyFactsJson: String? = null,
    /** JSON snapshot веток для BRANCHING. */
    val branchingJson: String? = null,
    /** Рабочая память для MEMORY_LAYERS (per-chat). */
    val workingMemoryJson: String? = null
)
