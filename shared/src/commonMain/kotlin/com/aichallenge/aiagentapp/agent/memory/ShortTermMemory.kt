package com.aichallenge.aiagentapp.agent.memory

import com.aichallenge.aiagentapp.data.ChatMessage

/**
 * Краткосрочная память: окно последних реплик текущего диалога.
 * Не персистится отдельно — восстанавливается из полной ленты сообщений.
 */
class ShortTermMemory(
    private val maxMessages: Int
) {
    private val messages = mutableListOf<ChatMessage>()

    fun append(message: ChatMessage) {
        messages.add(message)
        trim()
    }

    fun replaceAll(from: List<ChatMessage>) {
        messages.clear()
        if (from.size > maxMessages) {
            messages.addAll(from.takeLast(maxMessages))
        } else {
            messages.addAll(from)
        }
    }

    fun snapshot(): List<ChatMessage> = messages.toList()

    fun displayLines(): List<String> =
        messages.map { msg ->
            val label = if (msg.role == "user") "П" else "А"
            "$label: ${msg.content.take(120)}${if (msg.content.length > 120) "…" else ""}"
        }

    fun clear() {
        messages.clear()
    }

    fun removeLast() {
        if (messages.isNotEmpty()) {
            messages.removeAt(messages.lastIndex)
        }
    }

    private fun trim() {
        while (messages.size > maxMessages) {
            messages.removeAt(0)
        }
    }
}
