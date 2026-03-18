package com.aichallenge.aiagentapp.data

data class Conversation(
    val id: String,
    val title: String,
    val messages: List<SavedMessage>,
    val updatedAtMillis: Long,
    /** Сжатая сводка старых реплик для запросов к API; экран хранит полные messages */
    val rollingSummary: String? = null
)
