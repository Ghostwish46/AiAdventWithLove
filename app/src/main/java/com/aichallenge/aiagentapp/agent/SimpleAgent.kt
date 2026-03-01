package com.aichallenge.aiagentapp.agent

import com.aichallenge.aiagentapp.data.AgentTurnResult
import com.aichallenge.aiagentapp.data.ChatMessage
import com.aichallenge.aiagentapp.data.DeepSeekRepository

class SimpleAgent(
    private val repository: DeepSeekRepository,
    private val systemPrompt: String = "Ты полезный AI-ассистент. Отвечай чётко и по делу.",
    private val modelId: String = "qwen/qwen3.5-flash-02-23"
) {
    private val conversationHistory = mutableListOf<ChatMessage>()

    suspend fun processQuery(userMessage: String): Result<AgentTurnResult> {
        if (userMessage.isBlank()) {
            return Result.failure(IllegalArgumentException("Empty message"))
        }

        conversationHistory.add(ChatMessage(role = "user", content = userMessage.trim()))

        val allMessages = buildList {
            add(ChatMessage(role = "system", content = systemPrompt))
            addAll(conversationHistory)
        }

        val result = repository.sendMessages(allMessages, modelId)

        result.onSuccess { turn ->
            conversationHistory.add(ChatMessage(role = "assistant", content = turn.content))
        }

        return result
    }

    fun getHistory(): List<ChatMessage> = conversationHistory.toList()

    fun clearHistory() {
        conversationHistory.clear()
    }
}
