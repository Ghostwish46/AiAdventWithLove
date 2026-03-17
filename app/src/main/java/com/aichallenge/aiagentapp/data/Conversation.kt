package com.aichallenge.aiagentapp.data

data class Conversation(
    val id: String,
    val title: String,
    val messages: List<SavedMessage>,
    val updatedAtMillis: Long
)
