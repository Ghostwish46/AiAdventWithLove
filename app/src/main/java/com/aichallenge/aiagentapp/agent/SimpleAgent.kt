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
    initialContextStrategy: ContextStrategy = ContextStrategy.SLIDING_WINDOW,
    initialStickyFacts: Map<String, String> = emptyMap()
) {
    companion object {
        /** Сколько последних реплик (user+assistant) уходит в API при SLIDING_WINDOW и FACTS_KV. */
        const val KEEP_LAST_MESSAGES = 8
    }

    private var contextStrategyInternal = initialContextStrategy

    /** Полная история для [ContextStrategy.FULL]. */
    private val fullHistory = mutableListOf<ChatMessage>()

    /** Скользящее окно для SLIDING_WINDOW и FACTS_KV. */
    private val windowForApi = mutableListOf<ChatMessage>()

    /** Sticky facts для [ContextStrategy.FACTS_KV]. */
    private val stickyFacts = LinkedHashMap<String, String>().apply { putAll(initialStickyFacts) }

    init {
        when (contextStrategyInternal) {
            ContextStrategy.FULL -> fullHistory.addAll(initialHistory)
            ContextStrategy.SLIDING_WINDOW, ContextStrategy.FACTS_KV -> {
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
        stickyFacts.clear()
        when (strategy) {
            ContextStrategy.FULL -> fullHistory.addAll(fullMessagesFromUi)
            ContextStrategy.SLIDING_WINDOW, ContextStrategy.FACTS_KV -> {
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

    fun getStickyFactsMap(): Map<String, String> = stickyFacts.toMap()

    fun getStickyFactsDisplay(): String =
        if (stickyFacts.isEmpty()) ""
        else stickyFacts.entries.joinToString("\n") { "${it.key}: ${it.value}" }

    /**
     * Обновляет sticky facts перед добавлением сообщения пользователя в историю API.
     * При ошибке парсинга/сети прежние факты сохраняются.
     */
    suspend fun mergeStickyFactsForUserMessage(
        newUserMessage: String,
        conversationSoFar: List<ChatMessage>
    ) {
        repository.mergeStickyFacts(
            stickyFacts.toMap(),
            newUserMessage,
            conversationSoFar,
            modelInfo.id
        ).onSuccess { merged ->
            stickyFacts.clear()
            stickyFacts.putAll(merged)
        }
    }

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
            ContextStrategy.SLIDING_WINDOW, ContextStrategy.FACTS_KV -> windowForApi.toList()
        }

    fun getModelInfo(): ModelInfo = modelInfo

    fun addAssistantMessage(content: String) {
        addAssistantMessageInternal(content)
    }

    private fun appendUser(trimmed: String) {
        val msg = ChatMessage(role = "user", content = trimmed)
        when (contextStrategyInternal) {
            ContextStrategy.FULL -> fullHistory.add(msg)
            ContextStrategy.SLIDING_WINDOW, ContextStrategy.FACTS_KV -> windowForApi.add(msg)
        }
    }

    private fun addAssistantMessageInternal(content: String) {
        val msg = ChatMessage(role = "assistant", content = content)
        when (contextStrategyInternal) {
            ContextStrategy.FULL -> fullHistory.add(msg)
            ContextStrategy.SLIDING_WINDOW, ContextStrategy.FACTS_KV -> {
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
            ContextStrategy.SLIDING_WINDOW, ContextStrategy.FACTS_KV -> {
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
        val systemContent = if (
            contextStrategyInternal == ContextStrategy.FACTS_KV &&
            stickyFacts.isNotEmpty()
        ) {
            val block = stickyFacts.entries.joinToString("\n") { "${it.key}: ${it.value}" }
            "$systemPrompt\n\nЗафиксированные факты из диалога (ключ — значение):\n$block"
        } else {
            systemPrompt
        }
        add(ChatMessage(role = "system", content = systemContent))
        addAll(
            when (contextStrategyInternal) {
                ContextStrategy.FULL -> fullHistory
                ContextStrategy.SLIDING_WINDOW, ContextStrategy.FACTS_KV -> windowForApi
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
        stickyFacts.clear()
    }
}
