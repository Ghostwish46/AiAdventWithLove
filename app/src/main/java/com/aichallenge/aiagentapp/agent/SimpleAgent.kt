package com.aichallenge.aiagentapp.agent

import com.aichallenge.aiagentapp.data.AgentTurnResult
import com.aichallenge.aiagentapp.data.ChatMessage
import com.aichallenge.aiagentapp.data.DeepSeekRepository
import com.aichallenge.aiagentapp.data.ModelInfo

class SimpleAgent(
    private val repository: DeepSeekRepository,
    private val modelInfo: ModelInfo,
    private val systemPrompt: String = "Ты полезный AI-ассистент. Отвечай чётко и по делу.",
    initialHistory: List<ChatMessage> = emptyList()
) {
    private val conversationHistory = mutableListOf<ChatMessage>().apply { addAll(initialHistory) }

    suspend fun processQuery(userMessage: String): Result<AgentTurnResult> {
        if (userMessage.isBlank()) {
            return Result.failure(IllegalArgumentException("Empty message"))
        }

        conversationHistory.add(ChatMessage(role = "user", content = userMessage.trim()))

        val allMessages = buildList {
            add(ChatMessage(role = "system", content = systemPrompt))
            addAll(conversationHistory)
        }

        val result = repository.sendMessages(allMessages, modelInfo.id)

        return result.mapCatching { turn ->
            val cost = turn.usage?.let { u ->
                u.promptTokens.toDouble() / 1_000_000 * modelInfo.inputPricePerM +
                    u.completionTokens.toDouble() / 1_000_000 * modelInfo.outputPricePerM
            }
            conversationHistory.add(ChatMessage(role = "assistant", content = turn.content))
            turn.copy(estimatedCostRub = cost)
        }
    }

    fun getHistory(): List<ChatMessage> = conversationHistory.toList()

    fun clearHistory() {
        conversationHistory.clear()
    }
}
