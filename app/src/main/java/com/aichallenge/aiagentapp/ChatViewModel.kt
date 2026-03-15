package com.aichallenge.aiagentapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichallenge.aiagentapp.agent.SimpleAgent
import com.aichallenge.aiagentapp.data.Conversation
import com.aichallenge.aiagentapp.data.ConversationRepository
import com.aichallenge.aiagentapp.data.Usage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    val isLoading: Boolean = false
)

class ChatViewModel(
    private val agent: SimpleAgent,
    conversationId: String?,
    private val conversationRepository: ConversationRepository,
    initialMessages: List<UiMessage> = emptyList()
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

        viewModelScope.launch {
            agent.processQuery(text)
                .onSuccess { turn ->
                    replaceLastMessage(
                        UiMessage(
                            role = "assistant",
                            content = turn.content,
                            usage = turn.usage,
                            elapsedMs = turn.elapsedMs,
                            estimatedCostRub = turn.estimatedCostRub
                        )
                    )
                    persistConversation()
                }
                .onFailure { e ->
                    replaceLastMessage(
                        UiMessage(
                            role = "assistant",
                            content = e.message ?: "Unknown error",
                            isError = true
                        )
                    )
                }
        }
    }

    fun clearChat() {
        agent.clearHistory()
        _uiState.value = ChatUiState()
        currentConversationId = null
    }

    private fun persistConversation() {
        val history = agent.getHistory()
        if (history.isEmpty()) return
        val id = currentConversationId ?: UUID.randomUUID().toString().also { currentConversationId = it }
        val title = history.asSequence()
            .filter { it.role == "user" }
            .map { it.content.trim().take(50).ifBlank { null } }
            .firstOrNull() ?: "Новая тема"
        val conversation = Conversation(
            id = id,
            title = title,
            messages = history,
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
