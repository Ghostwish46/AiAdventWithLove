package com.aichallenge.aiagentapp.data

data class Conversation(
    val id: String,
    val title: String,
    val messages: List<SavedMessage>,
    val updatedAtMillis: Long,
    /** FULL | SLIDING_WINDOW (фаза 1) */
    val contextStrategy: String? = null,
    /** Устарело для фазы 1; не используется в промпте при FULL/SLIDING. */
    val rollingSummary: String? = null
)
