package com.aichallenge.aiagentapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichallenge.aiagentapp.agent.ContextStrategy
import com.aichallenge.aiagentapp.agent.SimpleAgent
import com.aichallenge.aiagentapp.data.ChatMessage
import com.aichallenge.aiagentapp.data.Conversation
import com.aichallenge.aiagentapp.data.ConversationRepository
import com.aichallenge.aiagentapp.data.SavedMessage
import com.aichallenge.aiagentapp.data.Usage
import com.aichallenge.aiagentapp.data.StreamEvent
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.util.UUID

data class UiMessage(
    val role: String,
    val content: String,
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val usage: Usage? = null,
    val elapsedMs: Long = 0,
    val estimatedCostRub: Double? = null
)

data class ChatUiState(
    val input: String = "",
    val messages: List<UiMessage> = emptyList(),
    val isLoading: Boolean = false,
    val streamingContent: String = "",
    val contextStrategy: ContextStrategy = ContextStrategy.SLIDING_WINDOW,
    /** Текст фактов для превью при FACTS_KV. */
    val stickyFactsSummary: String = ""
)

class ChatViewModel(
    private val agent: SimpleAgent,
    conversationId: String?,
    private val conversationRepository: ConversationRepository,
    initialMessages: List<UiMessage> = emptyList(),
    val contextLength: Int? = null
) : ViewModel() {

    private var currentConversationId: String? = conversationId

    private val gson = Gson()

    private val _uiState = MutableStateFlow(
        ChatUiState(
            messages = initialMessages,
            contextStrategy = agent.getContextStrategy(),
            stickyFactsSummary = stickyFactsDisplayForState()
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun updateInput(text: String) {
        _uiState.value = _uiState.value.copy(input = text)
    }

    private fun stickyFactsDisplayForState(): String =
        if (agent.getContextStrategy() == ContextStrategy.FACTS_KV) {
            agent.getStickyFactsDisplay()
        } else {
            ""
        }

    fun setContextStrategy(strategy: ContextStrategy) {
        val chatMessages = _uiState.value.messages
            .filter { !it.isLoading }
            .map { ChatMessage(role = it.role, content = it.content) }
        agent.setContextStrategy(strategy, chatMessages)
        _uiState.value = _uiState.value.copy(
            contextStrategy = strategy,
            stickyFactsSummary = if (strategy == ContextStrategy.FACTS_KV) {
                agent.getStickyFactsDisplay()
            } else {
                ""
            }
        )
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
                val prior = _uiState.value.messages
                    .filter { !it.isLoading && !it.isError }
                    .map { ChatMessage(role = it.role, content = it.content) }
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

            val startMs = System.currentTimeMillis()
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
                            val elapsedMs = System.currentTimeMillis() - startMs
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
        _uiState.value = ChatUiState(
            contextStrategy = _uiState.value.contextStrategy,
            stickyFactsSummary = ""
        )
        currentConversationId = null
    }

    fun estimateFullHistoryPromptTokens(): Int {
        val msgs = _uiState.value.messages
            .filter { !it.isLoading && !it.isError }
            .map { ChatMessage(it.role, it.content) }
        return agent.estimatePromptTokensIfFullHistory(msgs)
    }

    private fun persistConversation() {
        val messages = _uiState.value.messages
        if (messages.isEmpty()) return
        val id = currentConversationId ?: UUID.randomUUID().toString().also { currentConversationId = it }
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
        val stickyFactsJson =
            if (agent.getContextStrategy() == ContextStrategy.FACTS_KV) {
                gson.toJson(agent.getStickyFactsMap())
            } else {
                null
            }
        val conversation = Conversation(
            id = id,
            title = title,
            messages = savedMessages,
            updatedAtMillis = System.currentTimeMillis(),
            contextStrategy = agent.getContextStrategy().name,
            rollingSummary = null,
            stickyFactsJson = stickyFactsJson
        )
        conversationRepository.save(conversation)
    }

    private fun replaceLastMessage(msg: UiMessage) {
        val current = _uiState.value.messages.toMutableList()
        if (current.isNotEmpty()) {
            current[current.lastIndex] = msg
        }
        _uiState.value = _uiState.value.copy(messages = current, isLoading = false)
    }
}
