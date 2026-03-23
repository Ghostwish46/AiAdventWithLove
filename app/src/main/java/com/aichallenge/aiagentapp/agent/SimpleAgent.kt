package com.aichallenge.aiagentapp.agent

import com.aichallenge.aiagentapp.data.AgentTurnResult
import com.aichallenge.aiagentapp.data.ChatMessage
import com.aichallenge.aiagentapp.data.DeepSeekRepository
import com.aichallenge.aiagentapp.data.ModelInfo
import com.aichallenge.aiagentapp.data.StreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class SimpleAgent(
    private val repository: DeepSeekRepository,
    private val modelInfo: ModelInfo,
    private val systemPrompt: String = "Ты полезный AI-ассистент. Отвечай чётко и по делу.",
    initialHistory: List<ChatMessage> = emptyList(),
    initialContextStrategy: ContextStrategy = ContextStrategy.SLIDING_WINDOW
) {
    companion object {
        /** Сколько последних реплик (user+assistant) уходит в API при [ContextStrategy.SLIDING_WINDOW]. */
        const val KEEP_LAST_MESSAGES = 8
    }

    private var contextStrategyInternal = initialContextStrategy

    /** Полная история для [ContextStrategy.FULL]. */
    private val fullHistory = mutableListOf<ChatMessage>()

    /** Скользящее окно для [ContextStrategy.SLIDING_WINDOW] (без summary). */
    private val windowForApi = mutableListOf<ChatMessage>()

    init {
        when (contextStrategyInternal) {
            ContextStrategy.FULL -> fullHistory.addAll(initialHistory)
            ContextStrategy.SLIDING_WINDOW -> {
                if (initialHistory.size > KEEP_LAST_MESSAGES) {
                    windowForApi.addAll(initialHistory.takeLast(KEEP_LAST_MESSAGES))
                } else {
                    windowForApi.addAll(initialHistory)
                }
            }
        }
    }

    fun getContextStrategy(): ContextStrategy = contextStrategyInternal

    fun setContextStrategy(strategy: ContextStrategy, fullMessagesFromUi: List<ChatMessage>) {
        contextStrategyInternal = strategy
        fullHistory.clear()
        windowForApi.clear()
        when (strategy) {
            ContextStrategy.FULL -> fullHistory.addAll(fullMessagesFromUi)
            ContextStrategy.SLIDING_WINDOW -> {
                if (fullMessagesFromUi.size > KEEP_LAST_MESSAGES) {
                    windowForApi.addAll(fullMessagesFromUi.takeLast(KEEP_LAST_MESSAGES))
                } else {
                    windowForApi.addAll(fullMessagesFromUi)
                }
            }
        }
    }

    /** Фаза 1: сводка не используется; для совместимости persist — всегда пусто. */
    fun getRollingSummary(): String = ""

    suspend fun processQuery(userMessage: String): Result<AgentTurnResult> {
        if (userMessage.isBlank()) {
            return Result.failure(IllegalArgumentException("Empty message"))
        }
        val trimmed = userMessage.trim()
        appendUser(trimmed)
        val result = repository.sendMessages(buildMessagesForApi(), modelInfo.id)
        return result.mapCatching { turn ->
            val cost = turn.usage?.let { u ->
                u.promptTokens.toDouble() / 1_000_000 * modelInfo.inputPricePerM +
                    u.completionTokens.toDouble() / 1_000_000 * modelInfo.outputPricePerM
            }
            addAssistantMessageInternal(turn.content)
            turn.copy(estimatedCostRub = cost)
        }
    }

    fun getHistory(): List<ChatMessage> =
        when (contextStrategyInternal) {
            ContextStrategy.FULL -> fullHistory.toList()
            ContextStrategy.SLIDING_WINDOW -> windowForApi.toList()
        }

    fun getModelInfo(): ModelInfo = modelInfo

    fun addAssistantMessage(content: String) {
        addAssistantMessageInternal(content)
    }

    private fun appendUser(trimmed: String) {
        val msg = ChatMessage(role = "user", content = trimmed)
        when (contextStrategyInternal) {
            ContextStrategy.FULL -> fullHistory.add(msg)
            ContextStrategy.SLIDING_WINDOW -> windowForApi.add(msg)
        }
    }

    private fun addAssistantMessageInternal(content: String) {
        val msg = ChatMessage(role = "assistant", content = content)
        when (contextStrategyInternal) {
            ContextStrategy.FULL -> fullHistory.add(msg)
            ContextStrategy.SLIDING_WINDOW -> {
                windowForApi.add(msg)
                while (windowForApi.size > KEEP_LAST_MESSAGES) {
                    windowForApi.removeAt(0)
                }
            }
        }
    }

    fun rollbackLastUserMessage() {
        when (contextStrategyInternal) {
            ContextStrategy.FULL -> {
                if (fullHistory.isNotEmpty() && fullHistory.last().role == "user") {
                    fullHistory.removeAt(fullHistory.lastIndex)
                }
            }
            ContextStrategy.SLIDING_WINDOW -> {
                if (windowForApi.isNotEmpty() && windowForApi.last().role == "user") {
                    windowForApi.removeAt(windowForApi.lastIndex)
                }
            }
        }
    }

    fun processQueryStreaming(userMessage: String): Flow<StreamEvent> {
        if (userMessage.isBlank()) return flowOf(StreamEvent.Error("Empty message"))
        appendUser(userMessage.trim())
        return repository.sendMessagesStreaming(buildMessagesForApi(), modelInfo.id)
    }

    private fun buildMessagesForApi(): List<ChatMessage> = buildList {
        add(ChatMessage(role = "system", content = systemPrompt))
        addAll(
            when (contextStrategyInternal) {
                ContextStrategy.FULL -> fullHistory
                ContextStrategy.SLIDING_WINDOW -> windowForApi
            }
        )
    }

    fun estimatePromptTokensIfFullHistory(fullUiMessages: List<ChatMessage>): Int {
        val chars = systemPrompt.length + fullUiMessages.sumOf { it.content.length + 8 }
        return kotlin.math.ceil(chars / 3.0).toInt()
    }

    /** Зарезервировано для будущих стратегий со сводкой; фаза 1 — пустая реализация. */
    suspend fun flushPendingSummarization() = Unit

    fun clearHistory() {
        fullHistory.clear()
        windowForApi.clear()
    }
}
