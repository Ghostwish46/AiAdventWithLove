package com.aichallenge.aiagentapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichallenge.aiagentapp.agent.SimpleAgent
import com.aichallenge.aiagentapp.data.Usage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiMessage(
    val role: String,
    val content: String,
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val usage: Usage? = null,
    val elapsedMs: Long = 0
)

data class ChatUiState(
    val input: String = "",
    val messages: List<UiMessage> = emptyList(),
    val isLoading: Boolean = false
)

class ChatViewModel(
    private val agent: SimpleAgent,
    val modelInfo: com.aichallenge.aiagentapp.data.ModelInfo
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
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
                            elapsedMs = turn.elapsedMs
                        )
                    )
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
    }

    private fun replaceLastMessage(msg: UiMessage) {
        val current = _uiState.value.messages.toMutableList()
        if (current.isNotEmpty()) {
            current[current.lastIndex] = msg
        }
        _uiState.value = _uiState.value.copy(messages = current, isLoading = false)
    }
}
