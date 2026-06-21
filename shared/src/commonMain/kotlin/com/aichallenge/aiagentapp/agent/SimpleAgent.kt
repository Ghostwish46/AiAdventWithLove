package com.aichallenge.aiagentapp.agent

import com.aichallenge.aiagentapp.data.AgentTurnResult
import com.aichallenge.aiagentapp.data.ChatMessage
import com.aichallenge.aiagentapp.data.LlmClient
import com.aichallenge.aiagentapp.data.ModelInfo
import com.aichallenge.aiagentapp.data.StreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class SimpleAgent(
    private val repository: LlmClient,
    private val modelInfo: ModelInfo,
    private val systemPrompt: String = "Ты полезный AI-ассистент. Отвечай чётко и по делу.",
    initialHistory: List<ChatMessage> = emptyList(),
    initialContextStrategy: ContextStrategy = ContextStrategy.SLIDING_WINDOW,
    initialStickyFacts: Map<String, String> = emptyMap(),
    initialBranching: BranchingState? = null
) {
    companion object {
        const val KEEP_LAST_MESSAGES = 8
    }

    private val contextStrategyInternal = initialContextStrategy

    /** Линейная лента до checkpoint (или вся лента для SLIDING/FACTS). */
    private val linearMessages = mutableListOf<ChatMessage>()

    /** Окно API для SLIDING / FACTS (до ветвления у BRANCHING — то же). */
    private val windowForApi = mutableListOf<ChatMessage>()

    private val stickyFacts = LinkedHashMap<String, String>().apply { putAll(initialStickyFacts) }

    private var branchingState: BranchingState? = initialBranching

    init {
        when (contextStrategyInternal) {
            ContextStrategy.BRANCHING -> {
                if (branchingState != null) {
                    // восстановление: linear не используется
                } else {
                    linearMessages.addAll(initialHistory)
                    syncWindowFromLinear()
                }
            }
            ContextStrategy.SLIDING_WINDOW, ContextStrategy.FACTS_KV -> {
                linearMessages.addAll(initialHistory)
                syncWindowFromLinear()
            }
        }
    }

    fun getContextStrategy(): ContextStrategy = contextStrategyInternal

    fun getRollingSummary(): String = ""

    fun getStickyFactsMap(): Map<String, String> = stickyFacts.toMap()

    fun getStickyFactsDisplay(): String =
        if (stickyFacts.isEmpty()) "" else stickyFacts.entries.joinToString("\n") { "${it.key}: ${it.value}" }

    fun getBranchingState(): BranchingState? = branchingState

    fun isBranched(): Boolean = branchingState?.isActive == true

    fun createBranchCheckpoint(): Boolean {
        if (contextStrategyInternal != ContextStrategy.BRANCHING || branchingState != null) return false
        val trunk = linearMessages.toList()
        if (trunk.isEmpty()) return false
        branchingState = BranchingState.createCheckpoint(trunk)
        linearMessages.clear()
        windowForApi.clear()
        return true
    }

    fun switchBranch(branchId: String): Boolean {
        val state = branchingState ?: return false
        if (state.branches.none { it.id == branchId }) return false
        branchingState = state.copy(activeBranchId = branchId)
        return true
    }

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
        if (userMessage.isBlank()) return Result.failure(IllegalArgumentException("Empty message"))
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

    fun getHistory(): List<ChatMessage> = displayMessages()

    fun displayMessages(): List<ChatMessage> =
        when {
            contextStrategyInternal == ContextStrategy.BRANCHING && branchingState != null ->
                branchingState!!.displayMessages()
            contextStrategyInternal == ContextStrategy.BRANCHING ->
                linearMessages.toList()
            else -> linearMessages.toList()
        }

    fun getModelInfo(): ModelInfo = modelInfo

    fun addAssistantMessage(content: String) {
        addAssistantMessageInternal(content)
    }

    private fun appendUser(trimmed: String) {
        val msg = ChatMessage(role = "user", content = trimmed)
        when {
            contextStrategyInternal == ContextStrategy.BRANCHING && branchingState != null ->
                appendToActiveBranch(msg)
            contextStrategyInternal == ContextStrategy.BRANCHING -> {
                linearMessages.add(msg)
            }
            else -> {
                linearMessages.add(msg)
                windowForApi.add(msg)
            }
        }
    }

    private fun addAssistantMessageInternal(content: String) {
        val msg = ChatMessage(role = "assistant", content = content)
        when {
            contextStrategyInternal == ContextStrategy.BRANCHING && branchingState != null ->
                appendToActiveBranch(msg)
            contextStrategyInternal == ContextStrategy.BRANCHING -> {
                linearMessages.add(msg)
            }
            else -> {
                linearMessages.add(msg)
                windowForApi.add(msg)
                trimWindow()
            }
        }
    }

    private fun appendToActiveBranch(msg: ChatMessage) {
        val state = branchingState ?: return
        val branches = state.branches.map { branch ->
            if (branch.id == state.activeBranchId) {
                branch.copy(messages = branch.messages + msg)
            } else {
                branch
            }
        }
        branchingState = state.copy(branches = branches)
    }

    fun rollbackLastUserMessage() {
        when {
            contextStrategyInternal == ContextStrategy.BRANCHING && branchingState != null -> {
                val state = branchingState ?: return
                val branches = state.branches.map { branch ->
                    if (branch.id == state.activeBranchId &&
                        branch.messages.isNotEmpty() &&
                        branch.messages.last().role == "user"
                    ) {
                        branch.copy(messages = branch.messages.dropLast(1))
                    } else {
                        branch
                    }
                }
                branchingState = state.copy(branches = branches)
            }
            contextStrategyInternal == ContextStrategy.BRANCHING -> {
                if (linearMessages.isNotEmpty() && linearMessages.last().role == "user") {
                    linearMessages.removeAt(linearMessages.lastIndex)
                }
            }
            else -> {
                if (linearMessages.isNotEmpty() && linearMessages.last().role == "user") {
                    linearMessages.removeAt(linearMessages.lastIndex)
                }
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
        addAll(apiPayloadMessages())
    }

    private fun apiPayloadMessages(): List<ChatMessage> =
        when (contextStrategyInternal) {
            ContextStrategy.SLIDING_WINDOW, ContextStrategy.FACTS_KV -> windowForApi.toList()
            ContextStrategy.BRANCHING -> branchingApiMessages()
        }

    private fun branchingApiMessages(): List<ChatMessage> {
        val state = branchingState
        if (state == null) {
            return linearMessages.takeLast(KEEP_LAST_MESSAGES)
        }
        val activeTail = state.activeBranch()?.messages?.takeLast(KEEP_LAST_MESSAGES) ?: emptyList()
        return state.trunk + activeTail
    }

    private fun syncWindowFromLinear() {
        windowForApi.clear()
        if (linearMessages.size > KEEP_LAST_MESSAGES) {
            windowForApi.addAll(linearMessages.takeLast(KEEP_LAST_MESSAGES))
        } else {
            windowForApi.addAll(linearMessages)
        }
    }

    private fun trimWindow() {
        while (windowForApi.size > KEEP_LAST_MESSAGES) {
            windowForApi.removeAt(0)
        }
    }

    fun estimatePromptTokensIfFullHistory(fullUiMessages: List<ChatMessage>): Int {
        val chars = systemPrompt.length + fullUiMessages.sumOf { it.content.length + 8 }
        return kotlin.math.ceil(chars / 3.0).toInt()
    }

    suspend fun flushPendingSummarization() = Unit

    fun clearHistory() {
        linearMessages.clear()
        windowForApi.clear()
        stickyFacts.clear()
        branchingState = null
    }
}
