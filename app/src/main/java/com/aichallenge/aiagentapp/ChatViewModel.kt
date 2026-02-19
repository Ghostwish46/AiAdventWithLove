package com.aichallenge.aiagentapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichallenge.aiagentapp.data.DeepSeekRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatUiState(
    val query: String = "",
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

    fun sendMessage() {
        val query = _uiState.value.query
        if (query.isBlank() || _uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )
            repository.sendMessage(query)
                .onSuccess { content ->
                    _uiState.value = _uiState.value.copy(
                        response = content,
                        isLoading = false,
                        error = null
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
