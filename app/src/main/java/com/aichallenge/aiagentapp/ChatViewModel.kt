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
    val formatDescription: String = "",
    val maxChars: String = "",
    val stopSequence: String = "END",
    val rawResponse: String = "",
    val controlledResponse: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

class ChatViewModel(private val repository: DeepSeekRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun updateQuery(text: String) {
        _uiState.value = _uiState.value.copy(query = text, error = null)
    }

    fun updateFormatDescription(text: String) {
        _uiState.value = _uiState.value.copy(formatDescription = text, error = null)
    }

    fun updateMaxChars(text: String) {
        _uiState.value = _uiState.value.copy(maxChars = text.filter { it.isDigit() }.take(6), error = null)
    }

    fun updateStopSequence(text: String) {
        _uiState.value = _uiState.value.copy(stopSequence = text, error = null)
    }

    fun sendRaw() {
        val query = _uiState.value.query
        if (query.isBlank() || _uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )
            repository.sendMessageRaw(query)
                .onSuccess { content ->
                    _uiState.value = _uiState.value.copy(
                        rawResponse = content,
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

    fun sendControlled() {
        val state = _uiState.value
        val query = state.query
        if (query.isBlank() || state.isLoading) return
        val stop = state.stopSequence.trim()
        if (stop.isEmpty()) {
            _uiState.value = state.copy(error = "Stop sequence is empty")
            return
        }
        val maxCharsInt = state.maxChars.toIntOrNull()
        viewModelScope.launch {
            _uiState.value = state.copy(
                isLoading = true,
                error = null
            )
            repository.sendMessageControlled(
                userMessage = query,
                params = DeepSeekRepository.ControlParams(
                    formatDescription = state.formatDescription,
                    maxChars = maxCharsInt,
                    stopSequence = stop
                )
            )
                .onSuccess { content ->
                    _uiState.value = _uiState.value.copy(
                        controlledResponse = content,
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
