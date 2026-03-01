package com.aichallenge.aiagentapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichallenge.aiagentapp.data.DeepSeekRepository
import com.aichallenge.aiagentapp.data.PromptStrategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatUiState(
    val query: String = "",
    val selectedStrategy: PromptStrategy = PromptStrategy.DIRECT,
    val response: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

class ChatViewModel(private val repository: DeepSeekRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun updateQuery(text: String) {
        _uiState.value = _uiState.value.copy(query = text, error = null)
    }

    fun updateStrategy(strategy: PromptStrategy) {
        _uiState.value = _uiState.value.copy(selectedStrategy = strategy, error = null)
    }

    fun send() {
        val state = _uiState.value
        if (state.query.isBlank() || state.isLoading) return
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null, response = "")
            repository.sendWithStrategy(state.query, state.selectedStrategy)
                .onSuccess { content ->
                    _uiState.value = _uiState.value.copy(
                        response = content,
                        isLoading = false
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error"
                    )
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
