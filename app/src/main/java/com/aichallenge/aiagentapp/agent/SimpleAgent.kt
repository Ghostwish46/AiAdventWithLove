package com.aichallenge.aiagentapp.agent

import com.aichallenge.aiagentapp.data.AgentTurnResult
import com.aichallenge.aiagentapp.data.ChatMessage
import com.aichallenge.aiagentapp.data.DeepSeekRepository
import com.aichallenge.aiagentapp.data.ModelInfo
import com.aichallenge.aiagentapp.data.StreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SimpleAgent(
    private val repository: DeepSeekRepository,
    private val modelInfo: ModelInfo,
    private val systemPrompt: String = "Ты полезный AI-ассистент. Отвечай чётко и по делу.",
    initialHistory: List<ChatMessage> = emptyList(),
    initialRollingSummary: String? = null,
    compressionEnabled: Boolean = true
) {
    companion object {
        const val KEEP_LAST_MESSAGES = 8
        /** Меньше 10 — первая суммаризация раньше (успевает захватить ранние «запомни»). */
        const val COMPRESS_BATCH_SIZE = 5
    }

    private var compressionEnabledInternal = compressionEnabled

    /** Полная история для режима без сжатия */
    private val fullHistory = mutableListOf<ChatMessage>()

    /** Режим сжатия: окно для API, буфер на суммаризацию, накопленная сводка */
    private var rollingSummary = initialRollingSummary?.trim().orEmpty()
    private val recentForApi = mutableListOf<ChatMessage>()
    private val pendingCompressBuffer = mutableListOf<ChatMessage>()
    private val summarizeMutex = Mutex()

    init {
        if (compressionEnabledInternal) {
            when {
                initialRollingSummary != null && initialRollingSummary.isNotBlank() -> {
                    recentForApi.addAll(initialHistory.takeLast(KEEP_LAST_MESSAGES))
                }
                initialHistory.size > KEEP_LAST_MESSAGES -> {
                    recentForApi.addAll(initialHistory.takeLast(KEEP_LAST_MESSAGES))
                }
                else -> {
                    recentForApi.addAll(initialHistory)
                }
            }
        } else {
            fullHistory.addAll(initialHistory)
        }
    }

    fun isCompressionEnabled(): Boolean = compressionEnabledInternal

    fun setCompressionEnabled(enabled: Boolean, fullMessagesFromUi: List<ChatMessage>) {
        compressionEnabledInternal = enabled
        if (enabled) {
            fullHistory.clear()
            rollingSummary = ""
            pendingCompressBuffer.clear()
            recentForApi.clear()
            if (fullMessagesFromUi.size > KEEP_LAST_MESSAGES) {
                recentForApi.addAll(fullMessagesFromUi.takeLast(KEEP_LAST_MESSAGES))
            } else {
                recentForApi.addAll(fullMessagesFromUi)
            }
        } else {
            recentForApi.clear()
            pendingCompressBuffer.clear()
            rollingSummary = ""
            fullHistory.clear()
            fullHistory.addAll(fullMessagesFromUi)
        }
    }

    fun getRollingSummary(): String = rollingSummary

    suspend fun processQuery(userMessage: String): Result<AgentTurnResult> {
        if (userMessage.isBlank()) {
            return Result.failure(IllegalArgumentException("Empty message"))
        }
        val trimmed = userMessage.trim()
        if (compressionEnabledInternal) {
            recentForApi.add(ChatMessage(role = "user", content = trimmed))
        } else {
            fullHistory.add(ChatMessage(role = "user", content = trimmed))
        }
        val allMessages = buildMessagesForApi()
        val result = repository.sendMessages(allMessages, modelInfo.id)
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
        if (compressionEnabledInternal) recentForApi.toList() else fullHistory.toList()

    fun getModelInfo(): ModelInfo = modelInfo

    fun addAssistantMessage(content: String) {
        addAssistantMessageInternal(content)
    }

    private fun addAssistantMessageInternal(content: String) {
        if (compressionEnabledInternal) {
            recentForApi.add(ChatMessage(role = "assistant", content = content))
            while (recentForApi.size > KEEP_LAST_MESSAGES) {
                pendingCompressBuffer.add(recentForApi.removeAt(0))
            }
        } else {
            fullHistory.add(ChatMessage(role = "assistant", content = content))
        }
    }

    /**
     * После ошибки запроса убрать последний user, если ответа не было.
     */
    fun rollbackLastUserMessage() {
        if (compressionEnabledInternal) {
            if (recentForApi.isNotEmpty() && recentForApi.last().role == "user") {
                recentForApi.removeAt(recentForApi.lastIndex)
            }
        } else {
            if (fullHistory.isNotEmpty() && fullHistory.last().role == "user") {
                fullHistory.removeAt(fullHistory.lastIndex)
            }
        }
    }

    fun processQueryStreaming(userMessage: String): Flow<StreamEvent> {
        if (userMessage.isBlank()) return flowOf(StreamEvent.Error("Empty message"))
        val trimmed = userMessage.trim()
        if (compressionEnabledInternal) {
            recentForApi.add(ChatMessage(role = "user", content = trimmed))
        } else {
            fullHistory.add(ChatMessage(role = "user", content = trimmed))
        }
        return repository.sendMessagesStreaming(buildMessagesForApi(), modelInfo.id)
    }

    private fun buildMessagesForApi(): List<ChatMessage> = buildList {
        add(ChatMessage(role = "system", content = systemPrompt))
        if (compressionEnabledInternal && rollingSummary.isNotBlank()) {
            add(
                ChatMessage(
                    role = "user",
                    content = "Сводка ранее в диалоге:\n$rollingSummary"
                )
            )
        }
        addAll(if (compressionEnabledInternal) recentForApi else fullHistory)
    }

    /** Оценка токенов промпта «как без сжатия» (грубо по символам). */
    fun estimatePromptTokensIfFullHistory(fullUiMessages: List<ChatMessage>): Int {
        val chars = systemPrompt.length + fullUiMessages.sumOf { it.content.length + 8 }
        return kotlin.math.ceil(chars / 3.0).toInt()
    }

    /**
     * Суммаризация буфера. Батч из очереди удаляется только после успешной сводки или резервной выжимки
     * (раньше при ошибке API батч выбрасывался — контекст терялся полностью).
     */
    suspend fun flushPendingSummarization() {
        if (!compressionEnabledInternal) return
        summarizeMutex.withLock {
            while (pendingCompressBuffer.size >= COMPRESS_BATCH_SIZE) {
                val batch = pendingCompressBuffer.take(COMPRESS_BATCH_SIZE).toList()
                val summary = repository.summarizeDialogFragment(batch, modelInfo.id).getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: buildFallbackSummary(batch)
                repeat(COMPRESS_BATCH_SIZE) { pendingCompressBuffer.removeAt(0) }
                rollingSummary = if (rollingSummary.isBlank()) summary else "$rollingSummary\n$summary"
            }
        }
    }

    /** Если API суммаризации недоступен — сжатые цитаты, чтобы не потерять кодовые слова и факты. */
    private fun buildFallbackSummary(batch: List<ChatMessage>): String {
        val lines = batch.map { m ->
            val tag = if (m.role == "user") "П" else "А"
            "$tag: ${m.content.take(320).trim()}"
        }
        return lines.joinToString("\n")
    }

    fun clearHistory() {
        fullHistory.clear()
        recentForApi.clear()
        pendingCompressBuffer.clear()
        rollingSummary = ""
    }
}
