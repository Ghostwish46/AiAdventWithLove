package com.aichallenge.aiagentapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichallenge.aiagentapp.agent.SimpleAgent
import com.aichallenge.aiagentapp.data.Conversation
import com.aichallenge.aiagentapp.data.ConversationRepository
import com.aichallenge.aiagentapp.data.SavedMessage
import com.aichallenge.aiagentapp.data.Usage
import com.aichallenge.aiagentapp.data.StreamEvent
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
    /** Текст, который приходит чанками во время стриминга; отображается поверх последнего сообщения */
    val streamingContent: String = ""
)

class ChatViewModel(
    private val agent: SimpleAgent,
    conversationId: String?,
    private val conversationRepository: ConversationRepository,
    initialMessages: List<UiMessage> = emptyList(),
    val contextLength: Int? = null
) : ViewModel() {

    private var currentConversationId: String? = conversationId

    private val _uiState = MutableStateFlow(ChatUiState(messages = initialMessages))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun updateInput(text: String) {
        _uiState.value = _uiState.value.copy(input = text)
    }

    fun send() {
        val text = _uiState.value.input.trim()
        if (text.isBlank() || _uiState.value.isLoading) return

        val userMsg = UiMessage(role = "user", content = text)
        val loadingMsg = UiMessage(role = "assistant", content = "", isLoading = true)

        _uiState.value = _uiState.value.copy(
            input = "",
            messages = _uiState.value.messages + userMsg + loadingMsg,
            isLoading = true
        )

        val startMs = System.currentTimeMillis()
        viewModelScope.launch {
            agent.processQueryStreaming(text)
                .catch { e ->
                    clearStreamingContent()
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
                            yield() // даём UI перерисоваться до следующего чанка
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
        _uiState.value = ChatUiState()
        currentConversationId = null
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
        val conversation = Conversation(
            id = id,
            title = title,
            messages = savedMessages,
            updatedAtMillis = System.currentTimeMillis()
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
