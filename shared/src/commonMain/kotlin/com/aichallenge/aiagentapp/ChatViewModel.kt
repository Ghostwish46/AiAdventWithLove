package com.aichallenge.aiagentapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichallenge.aiagentapp.agent.ContextStrategy
import com.aichallenge.aiagentapp.agent.SimpleAgent
import com.aichallenge.aiagentapp.agent.memory.MemorySnapshot
import com.aichallenge.aiagentapp.agent.profile.AssistantProfile
import com.aichallenge.aiagentapp.agent.invariant.InvariantBlock
import com.aichallenge.aiagentapp.agent.invariant.InvariantConflictRefusal
import com.aichallenge.aiagentapp.agent.task.TaskState
import com.aichallenge.aiagentapp.agent.validation.ValidationResult
import com.aichallenge.aiagentapp.data.BranchingUiSnapshot
import com.aichallenge.aiagentapp.data.ChatMessage
import com.aichallenge.aiagentapp.data.Conversation
import com.aichallenge.aiagentapp.data.ConversationStore
import com.aichallenge.aiagentapp.data.SavedMessage
import com.aichallenge.aiagentapp.data.StreamEvent
import com.aichallenge.aiagentapp.data.Usage
import com.aichallenge.aiagentapp.data.encodeBranchingUiSnapshot
import com.aichallenge.aiagentapp.data.encodeStickyFactsJson
import com.aichallenge.aiagentapp.data.encodeTaskStatePayload
import com.aichallenge.aiagentapp.data.encodeWorkingMemoryJson
import com.aichallenge.aiagentapp.mcp.McpToolUsage
import com.aichallenge.aiagentapp.mcp.displaySummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.datetime.Clock
import kotlin.random.Random

data class UiMessage(
    val role: String,
    val content: String,
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val usage: Usage? = null,
    val elapsedMs: Long = 0,
    val estimatedCostRub: Double? = null,
    val mcpToolsUsed: List<McpToolUsage> = emptyList(),
    val mcpToolsAvailable: List<McpToolUsage> = emptyList(),
)

data class BranchOption(val id: String, val label: String)

data class ChatUiState(
    val input: String = "",
    val messages: List<UiMessage> = emptyList(),
    val isLoading: Boolean = false,
    val streamingContent: String = "",
    val contextStrategy: ContextStrategy = ContextStrategy.SLIDING_WINDOW,
    val stickyFactsSummary: String = "",
    val isBranched: Boolean = false,
    val activeBranchId: String? = null,
    val branches: List<BranchOption> = emptyList(),
    val canCreateCheckpoint: Boolean = false,
    val memorySnapshot: MemorySnapshot? = null,
    val lastRoutingLog: List<String> = emptyList(),
    val assistantProfile: AssistantProfile = AssistantProfile.NEUTRAL,
    val taskState: TaskState = TaskState.inactive(),
    val activeInvariantBlocks: List<InvariantBlock> = emptyList(),
    val relevanceReason: String = "",
    val reworkStatus: String = "",
    val blockedTransitionMessage: String? = null,
    val mcpToolsUsedLive: List<McpToolUsage> = emptyList(),
    val mcpToolsAvailable: List<McpToolUsage> = emptyList(),
    val mcpAvailabilityHint: String = "",
)

class ChatViewModel(
    private val agent: SimpleAgent,
    conversationId: String?,
    private val conversationRepository: ConversationStore,
    initialMessages: List<UiMessage> = emptyList(),
    initialBranching: BranchingUiSnapshot? = null,
    val contextLength: Int? = null
) : ViewModel() {

    private var currentConversationId: String? = conversationId

    private var trunkUiMessages: List<UiMessage> = initialBranching?.trunk ?: emptyList()
    private val branchUiMessages = mutableMapOf<String, MutableList<UiMessage>>()

    init {
        initialBranching?.branchMessages?.forEach { (id, msgs) ->
            branchUiMessages[id] = msgs.toMutableList()
        }
        viewModelScope.launch {
            refreshMcpAvailability()
        }
    }

    private val _uiState = MutableStateFlow(
        buildInitialState(initialMessages, initialBranching)
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private fun buildInitialState(
        initialMessages: List<UiMessage>,
        initialBranching: BranchingUiSnapshot?
    ): ChatUiState {
        val strategy = agent.getContextStrategy()
        val branched = initialBranching != null || agent.isBranched()
        val messages = if (initialBranching != null) {
            val activeId = initialBranching.activeBranchId
            trunkUiMessages + (branchUiMessages[activeId] ?: emptyList())
        } else {
            initialMessages
        }
        return ChatUiState(
            messages = messages,
            contextStrategy = strategy,
            stickyFactsSummary = stickyFactsDisplayForState(),
            isBranched = branched,
            activeBranchId = initialBranching?.activeBranchId ?: agent.getBranchingState()?.activeBranchId,
            branches = branchOptions(),
            canCreateCheckpoint = canCreateCheckpoint(messages, branched),
            memorySnapshot = memorySnapshotForState(),
            lastRoutingLog = agent.getMemorySnapshot()?.routingLog ?: emptyList(),
            assistantProfile = agent.getAssistantProfile(),
            taskState = agent.getTaskState()
        )
    }

    private fun memorySnapshotForState(): MemorySnapshot? =
        if (agent.getContextStrategy() == ContextStrategy.MEMORY_LAYERS) {
            agent.getMemorySnapshot()
        } else {
            null
        }

    fun updateInput(text: String) {
        _uiState.value = _uiState.value.copy(input = text)
    }

    private fun stickyFactsDisplayForState(): String =
        if (agent.getContextStrategy() == ContextStrategy.FACTS_KV) {
            agent.getStickyFactsDisplay()
        } else {
            ""
        }

    private fun branchOptions(): List<BranchOption> =
        agent.getBranchingState()?.branches?.map { BranchOption(it.id, it.label) }
            ?: listOf(BranchOption("a", "Ветка A"), BranchOption("b", "Ветка B"))

    private fun canCreateCheckpoint(messages: List<UiMessage>, branched: Boolean): Boolean =
        agent.getContextStrategy() == ContextStrategy.BRANCHING &&
            !branched &&
            messages.any { it.role == "user" && !it.isLoading && !it.isError }

    fun createBranchCheckpoint() {
        if (!_uiState.value.canCreateCheckpoint) return
        val trunk = _uiState.value.messages.filter { !it.isLoading && !it.isError }
        if (!agent.createBranchCheckpoint()) return
        trunkUiMessages = trunk
        branchUiMessages.clear()
        branchUiMessages["a"] = mutableListOf()
        branchUiMessages["b"] = mutableListOf()
        _uiState.value = _uiState.value.copy(
            messages = trunkUiMessages,
            isBranched = true,
            activeBranchId = "a",
            branches = branchOptions(),
            canCreateCheckpoint = false
        )
        persistConversation()
    }

    fun switchBranch(branchId: String) {
        if (!_uiState.value.isBranched) return
        val currentActive = _uiState.value.activeBranchId ?: return
        if (currentActive == branchId) return

        val trunkLen = trunkUiMessages.size
        val currentTail = _uiState.value.messages.drop(trunkLen)
        branchUiMessages[currentActive] = currentTail.toMutableList()

        if (!agent.switchBranch(branchId)) return

        val newTail = branchUiMessages[branchId]?.toList() ?: emptyList()
        _uiState.value = _uiState.value.copy(
            messages = trunkUiMessages + newTail,
            activeBranchId = branchId
        )
        persistConversation()
    }

    fun pinToLongTerm(messageContent: String) {
        if (agent.getContextStrategy() != ContextStrategy.MEMORY_LAYERS) return
        val log = agent.pinMessageToLongTerm(messageContent)
        _uiState.value = _uiState.value.copy(
            memorySnapshot = agent.getMemorySnapshot(),
            lastRoutingLog = log
        )
    }

    fun send() {
        val text = _uiState.value.input.trim()
        if (text.isBlank() || _uiState.value.isLoading) return

        val userMsg = UiMessage(role = "user", content = text)
        val loadingMsg = UiMessage(role = "assistant", content = "", isLoading = true)
        _uiState.value = _uiState.value.copy(
            input = "",
            messages = _uiState.value.messages + userMsg + loadingMsg,
            isLoading = true,
            mcpToolsUsedLive = emptyList(),
        )
        viewModelScope.launch {
            executeSend(text, isRetry = false)
        }
    }

    fun retryLastFailedSend() {
        if (_uiState.value.isLoading) return
        val messages = _uiState.value.messages
        val last = messages.lastOrNull() ?: return
        if (!last.isError) return
        val userText = messages.dropLast(1).lastOrNull { it.role == "user" && !it.isError }?.content
            ?: return

        _uiState.value = _uiState.value.copy(
            messages = messages.dropLast(1),
            isLoading = true
        )
        viewModelScope.launch {
            executeSend(userText, isRetry = true)
        }
    }

    private suspend fun executeSend(text: String, isRetry: Boolean) {
        val strategy = _uiState.value.contextStrategy

        if (isRetry) {
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + UiMessage(
                    role = "assistant",
                    content = "",
                    isLoading = true
                ),
                isLoading = true,
                reworkStatus = ""
            )
        }

        val prior = conversationChatMessagesForPreTurn(includePendingUser = isRetry)

        agent.prepareTurn(text, prior)
        val turnCtx = agent.getTurnContext()
        _uiState.value = _uiState.value.copy(
            taskState = agent.getTaskState(),
            activeInvariantBlocks = turnCtx.relevantBlocks,
            relevanceReason = turnCtx.promptContext.relevanceReason,
            blockedTransitionMessage = agent.getTaskState().blockedTransition?.reason
        )

        turnCtx.invariantConflict?.takeIf { it.hasConflict }?.let { conflict ->
            if (!isRetry) {
                agent.appendUserMessage(text)
            }
            val refusal = InvariantConflictRefusal.buildMessage(conflict)
            agent.addAssistantMessage(refusal)
            agent.clearTurnContext()
            clearStreamingContent()
            replaceLastMessage(
                UiMessage(
                    role = "assistant",
                    content = refusal,
                    isLoading = false
                )
            )
            _uiState.value = _uiState.value.copy(isLoading = false, reworkStatus = "")
            persistConversation()
            return
        }

        if (strategy == ContextStrategy.FACTS_KV) {
            agent.mergeStickyFactsForUserMessage(text, prior)
            _uiState.value = _uiState.value.copy(
                stickyFactsSummary = agent.getStickyFactsDisplay()
            )
        }
        if (strategy == ContextStrategy.MEMORY_LAYERS) {
            val routingLog = agent.updateMemoryForUserMessage(text, prior)
            _uiState.value = _uiState.value.copy(
                memorySnapshot = agent.getMemorySnapshot(),
                lastRoutingLog = routingLog
            )
        }

        if (!isRetry) {
            agent.appendUserMessage(text)
        } else {
            val last = agent.displayMessages().lastOrNull()
            if (last?.role != "user" || last.content != text) {
                agent.appendUserMessage(text)
            }
        }

        val startMs = Clock.System.now().toEpochMilliseconds()
        var finalContent: String? = null
        var finalUsage: Usage? = null
        val mcpToolsUsed = mutableListOf<McpToolUsage>()
        val mcpToolsAvailable = agent.listMcpToolsForAgent()
        _uiState.value = _uiState.value.copy(
            mcpToolsAvailable = mcpToolsAvailable,
            mcpAvailabilityHint = buildMcpAvailabilityHint(mcpToolsAvailable),
        )

        val useMcpTools = mcpToolsAvailable.isNotEmpty()
        runCatching {
            if (useMcpTools) {
                collectToolStreamingResponse(
                    onToolUsed = { call ->
                        mcpToolsUsed.add(call)
                        _uiState.value = _uiState.value.copy(
                            mcpToolsUsedLive = mcpToolsUsed.toList(),
                        )
                    },
                    onDone = { content, usage ->
                        finalContent = content
                        finalUsage = usage
                    },
                )
            } else {
                collectStreamingResponse { content, usage ->
                    finalContent = content
                    finalUsage = usage
                }
            }
        }.onFailure { e ->
            clearStreamingContent()
            agent.rollbackLastUserMessage()
            agent.clearTurnContext()
            replaceLastMessage(
                UiMessage(
                    role = "assistant",
                    content = e.message ?: "Unknown error",
                    isError = true
                )
            )
            return
        }

        var content = finalContent?.trim().orEmpty()
        if (content.isBlank()) {
            content = if (useMcpTools) {
                "Пустой ответ от модели после MCP-хода. Проверьте сервер и попробуйте снова."
            } else if (mcpToolsAvailable.isEmpty()) {
                "Пустой ответ от модели. MCP tools недоступны — откройте Настройки → MCP Tools и обновите сервер."
            } else {
                "Пустой ответ от модели. Попробуйте ещё раз."
            }
        }
        var usage = finalUsage
        var reworkAttempt = 0

        while (true) {
            when (val validation = agent.validateResponse(content)) {
                is ValidationResult.Pass -> {
                    content = validation.response
                    break
                }
                is ValidationResult.Fail -> {
                    if (reworkAttempt >= SimpleAgent.MAX_REWORK_ATTEMPTS) {
                        content = validation.violations.joinToString("\n") { "⚠ $it" } +
                            "\n\n" + content
                        break
                    }
                    reworkAttempt++
                    _uiState.value = _uiState.value.copy(
                        reworkStatus = "↺ Переработка ответа (${validation.violations.firstOrNull() ?: "нарушение"})"
                    )
                    agent.setReworkViolations(validation.violations)
                    val regen = agent.generateNonStreaming().getOrElse { throw it }
                    content = regen.content
                    usage = regen.usage ?: usage
                }
            }
        }

        val elapsedMs = Clock.System.now().toEpochMilliseconds() - startMs
        clearStreamingContent()
        val last = _uiState.value.messages.lastOrNull() ?: return
        agent.addAssistantMessage(content)
        agent.clearTurnContext()
        val cost = usage?.let { u ->
            u.promptTokens.toDouble() / 1_000_000 * agent.getModelInfo().inputPricePerM +
                u.completionTokens.toDouble() / 1_000_000 * agent.getModelInfo().outputPricePerM
        }
        replaceLastMessage(
            last.copy(
                content = content,
                usage = usage,
                elapsedMs = elapsedMs,
                estimatedCostRub = cost,
                isLoading = false,
                mcpToolsUsed = mcpToolsUsed.toList(),
                mcpToolsAvailable = mcpToolsAvailable,
            )
        )
        syncBranchCacheFromDisplay()
        if (strategy == ContextStrategy.MEMORY_LAYERS) {
            _uiState.value = _uiState.value.copy(
                memorySnapshot = agent.getMemorySnapshot(),
                reworkStatus = "",
                taskState = agent.getTaskState(),
                mcpToolsUsedLive = emptyList(),
            )
        } else {
            _uiState.value = _uiState.value.copy(
                reworkStatus = "",
                taskState = agent.getTaskState(),
                blockedTransitionMessage = null,
                mcpToolsUsedLive = emptyList(),
            )
        }
        persistConversation()
    }

    private suspend fun refreshMcpAvailability() {
        val available = agent.listMcpToolsForAgent()
        _uiState.value = _uiState.value.copy(
            mcpToolsAvailable = available,
            mcpAvailabilityHint = buildMcpAvailabilityHint(available),
        )
    }

    private fun buildMcpAvailabilityHint(available: List<McpToolUsage>): String = when {
        available.isEmpty() ->
            "MCP: нет доступных tools — добавьте сервер в Настройки → MCP Tools и нажмите обновить"
        else ->
            "MCP готов: ${available.displaySummary()}"
    }

    private suspend fun collectStreamingResponse(onDone: (String, Usage?) -> Unit) {
        val accumulated = StringBuilder()
        var usage: Usage? = null
        agent.processQueryStreaming().collect { event ->
            when (event) {
                is StreamEvent.Chunk -> {
                    accumulated.append(event.text)
                    appendStreamingContent(event.text)
                    yield()
                }
                is StreamEvent.Done -> {
                    usage = event.usage
                }
                is StreamEvent.Error -> throw Exception(event.message)
            }
        }
        onDone(accumulated.toString(), usage)
    }

    private suspend fun collectToolStreamingResponse(
        onToolUsed: suspend (McpToolUsage) -> Unit,
        onDone: (String, Usage?) -> Unit,
    ) {
        val accumulated = StringBuilder()
        var usage: Usage? = null
        agent.processTurnWithTools(onToolUsed = onToolUsed).collect { event ->
            when (event) {
                is StreamEvent.Chunk -> {
                    accumulated.append(event.text)
                    appendStreamingContent(event.text)
                    yield()
                }
                is StreamEvent.Done -> {
                    usage = event.usage
                }
                is StreamEvent.Error -> throw Exception(event.message)
            }
        }
        onDone(accumulated.toString(), usage)
    }

    private fun conversationChatMessagesForPreTurn(includePendingUser: Boolean): List<ChatMessage> {
        val msgs = _uiState.value.messages.filter { !it.isLoading && !it.isError }
        val relevant = if (!includePendingUser && msgs.lastOrNull()?.role == "user") {
            msgs.dropLast(1)
        } else {
            msgs
        }
        return relevant.map { ChatMessage(role = it.role, content = it.content) }
    }

    private fun conversationChatMessages(): List<ChatMessage> =
        _uiState.value.messages
            .filter { !it.isLoading && !it.isError }
            .map { ChatMessage(role = it.role, content = it.content) }

    private fun syncBranchCacheFromDisplay() {
        if (!_uiState.value.isBranched) return
        val activeId = _uiState.value.activeBranchId ?: return
        val trunkLen = trunkUiMessages.size
        branchUiMessages[activeId] = _uiState.value.messages.drop(trunkLen).toMutableList()
    }

    private fun appendStreamingContent(text: String) {
        _uiState.value = _uiState.value.copy(
            streamingContent = _uiState.value.streamingContent + text
        )
    }

    private fun clearStreamingContent() {
        _uiState.value = _uiState.value.copy(streamingContent = "")
    }

    fun clearChat() {
        agent.clearHistory()
        trunkUiMessages = emptyList()
        branchUiMessages.clear()
        _uiState.value = ChatUiState(
            contextStrategy = _uiState.value.contextStrategy,
            stickyFactsSummary = "",
            branches = branchOptions(),
            canCreateCheckpoint = false,
            memorySnapshot = memorySnapshotForState(),
            assistantProfile = agent.getAssistantProfile(),
            taskState = TaskState.inactive()
        )
        currentConversationId = null
    }

    fun estimateFullHistoryPromptTokens(): Int {
        val msgs = conversationChatMessages()
        return agent.estimatePromptTokensIfFullHistory(msgs)
    }

    private fun persistConversation() {
        val messages = _uiState.value.messages.filter { !it.isLoading }
        if (messages.isEmpty()) return
        val id = currentConversationId ?: newConversationId().also { currentConversationId = it }
        val title = messages.asSequence()
            .filter { it.role == "user" }
            .map { it.content.trim().take(50).ifBlank { null } }
            .firstOrNull() ?: "Новая тема"
        val savedMessages = messages.map { m ->
            SavedMessage(
                role = m.role,
                content = m.content,
                promptTokens = m.usage?.promptTokens,
                completionTokens = m.usage?.completionTokens,
                totalTokens = m.usage?.totalTokens,
                elapsedMs = m.elapsedMs.takeIf { it > 0 },
                estimatedCostRub = m.estimatedCostRub,
                mcpToolsUsed = m.mcpToolsUsed,
            )
        }
        val strategy = agent.getContextStrategy()
        val stickyFactsJson =
            if (strategy == ContextStrategy.FACTS_KV) {
                encodeStickyFactsJson(agent.getStickyFactsMap())
            } else {
                null
            }
        val branchingJson =
            if (strategy == ContextStrategy.BRANCHING && _uiState.value.isBranched) {
                val activeId = _uiState.value.activeBranchId ?: "a"
                syncBranchCacheFromDisplay()
                encodeBranchingUiSnapshot(
                    trunk = trunkUiMessages,
                    branchMessages = branchUiMessages.mapValues { it.value.toList() },
                    branches = _uiState.value.branches.map { it.id to it.label },
                    activeBranchId = activeId
                )
            } else {
                null
            }
        val workingMemoryJson =
            if (strategy == ContextStrategy.MEMORY_LAYERS) {
                encodeWorkingMemoryJson(agent.getWorkingMemory())
            } else {
                null
            }
        val conversation = Conversation(
            id = id,
            title = title,
            messages = savedMessages,
            updatedAtMillis = Clock.System.now().toEpochMilliseconds(),
            contextStrategy = strategy.name,
            rollingSummary = null,
            stickyFactsJson = stickyFactsJson,
            branchingJson = branchingJson,
            workingMemoryJson = workingMemoryJson,
            profileId = agent.getAssistantProfile().id.takeIf { it != AssistantProfile.ID_NEUTRAL },
            taskStateJson = encodeTaskStatePayload(agent.getTaskState(), agent.getTaskFsmScope())
        )
        conversationRepository.save(conversation)
    }

    private fun replaceLastMessage(msg: UiMessage) {
        val current = _uiState.value.messages.toMutableList()
        if (current.isNotEmpty()) {
            current[current.lastIndex] = msg
        }
        _uiState.value = _uiState.value.copy(
            messages = current,
            isLoading = false,
            canCreateCheckpoint = canCreateCheckpoint(current, _uiState.value.isBranched)
        )
    }

    private fun newConversationId(): String =
        (1..32).map { "0123456789abcdef"[Random.nextInt(16)] }.joinToString("")
}
