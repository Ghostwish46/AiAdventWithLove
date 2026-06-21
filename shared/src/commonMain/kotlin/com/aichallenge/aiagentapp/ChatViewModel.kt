package com.aichallenge.aiagentapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichallenge.aiagentapp.agent.ContextStrategy
import com.aichallenge.aiagentapp.agent.SimpleAgent
import com.aichallenge.aiagentapp.data.BranchingUiSnapshot
import com.aichallenge.aiagentapp.data.ChatMessage
import com.aichallenge.aiagentapp.data.Conversation
import com.aichallenge.aiagentapp.data.ConversationStore
import com.aichallenge.aiagentapp.data.SavedMessage
import com.aichallenge.aiagentapp.data.StreamEvent
import com.aichallenge.aiagentapp.data.Usage
import com.aichallenge.aiagentapp.data.encodeBranchingUiSnapshot
import com.aichallenge.aiagentapp.data.encodeStickyFactsJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
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
    val estimatedCostRub: Double? = null
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
    val canCreateCheckpoint: Boolean = false
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
            canCreateCheckpoint = canCreateCheckpoint(messages, branched)
        )
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

    fun send() {
        val text = _uiState.value.input.trim()
        if (text.isBlank() || _uiState.value.isLoading) return

        val strategy = _uiState.value.contextStrategy
        _uiState.value = _uiState.value.copy(
            input = "",
            isLoading = strategy == ContextStrategy.FACTS_KV
        )

        viewModelScope.launch {
            if (strategy == ContextStrategy.FACTS_KV) {
                val prior = conversationChatMessages()
                agent.mergeStickyFactsForUserMessage(text, prior)
                _uiState.value = _uiState.value.copy(
                    stickyFactsSummary = agent.getStickyFactsDisplay()
                )
            }

            val userMsg = UiMessage(role = "user", content = text)
            val loadingMsg = UiMessage(role = "assistant", content = "", isLoading = true)

            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + userMsg + loadingMsg,
                isLoading = true
            )

            val startMs = Clock.System.now().toEpochMilliseconds()
            agent.processQueryStreaming(text)
                .catch { e ->
                    clearStreamingContent()
                    agent.rollbackLastUserMessage()
                    replaceLastMessage(
                        UiMessage(
                            role = "assistant",
                            content = e.message ?: "Unknown error",
                            isError = true
                        )
                    )
                }
                .collect { event ->
                    when (event) {
                        is StreamEvent.Chunk -> {
                            appendStreamingContent(event.text)
                            yield()
                        }
                        is StreamEvent.Done -> {
                            val elapsedMs = Clock.System.now().toEpochMilliseconds() - startMs
                            val accumulated = _uiState.value.streamingContent
                            clearStreamingContent()
                            val last = _uiState.value.messages.lastOrNull() ?: return@collect
                            agent.addAssistantMessage(accumulated)
                            val cost = event.usage?.let { u ->
                                u.promptTokens.toDouble() / 1_000_000 * (agent.getModelInfo().inputPricePerM) +
                                    u.completionTokens.toDouble() / 1_000_000 * (agent.getModelInfo().outputPricePerM)
                            }
                            replaceLastMessage(
                                last.copy(
                                    content = accumulated,
                                    usage = event.usage,
                                    elapsedMs = elapsedMs,
                                    estimatedCostRub = cost,
                                    isLoading = false
                                )
                            )
                            syncBranchCacheFromDisplay()
                            persistConversation()
                        }
                        is StreamEvent.Error -> {
                            clearStreamingContent()
                            agent.rollbackLastUserMessage()
                            replaceLastMessage(
                                UiMessage(
                                    role = "assistant",
                                    content = event.message,
                                    isError = true
                                )
                            )
                        }
                    }
                }
        }
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
            canCreateCheckpoint = false
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
                estimatedCostRub = m.estimatedCostRub
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
        val conversation = Conversation(
            id = id,
            title = title,
            messages = savedMessages,
            updatedAtMillis = Clock.System.now().toEpochMilliseconds(),
            contextStrategy = strategy.name,
            rollingSummary = null,
            stickyFactsJson = stickyFactsJson,
            branchingJson = branchingJson
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
