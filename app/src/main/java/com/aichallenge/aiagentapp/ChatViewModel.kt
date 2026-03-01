package com.aichallenge.aiagentapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichallenge.aiagentapp.data.DeepSeekRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TemperatureResult(
    val temperature: Double,
    val response: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

data class ChatUiState(
    val query: String = "",
    val temperatureInput: String = "0.7",
    val results: List<TemperatureResult> = emptyList(),
    val isLoading: Boolean = false
)

class ChatViewModel(private val repository: DeepSeekRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun updateQuery(text: String) {
        _uiState.value = _uiState.value.copy(query = text)
    }

    fun updateTemperature(text: String) {
        val filtered = text.filter { it.isDigit() || it == '.' }
        _uiState.value = _uiState.value.copy(temperatureInput = filtered)
    }

    fun send() {
        val state = _uiState.value
        if (state.query.isBlank() || state.isLoading) return

        val temp = state.temperatureInput.toDoubleOrNull()
        if (temp == null || temp < 0 || temp > 2) return

        val newResult = TemperatureResult(temperature = temp, isLoading = true)
        val updatedResults = state.results + newResult
        _uiState.value = state.copy(results = updatedResults, isLoading = true)

        val resultIndex = updatedResults.lastIndex

        viewModelScope.launch {
            repository.sendWithTemperature(state.query, temp)
                .onSuccess { content ->
                    _uiState.value = _uiState.value.copy(
                        results = _uiState.value.results.toMutableList().apply {
                            this[resultIndex] = this[resultIndex].copy(
                                response = content,
                                isLoading = false
                            )
                        },
                        isLoading = false
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        results = _uiState.value.results.toMutableList().apply {
                            this[resultIndex] = this[resultIndex].copy(
                                isLoading = false,
                                error = e.message ?: "Unknown error"
                            )
                        },
                        isLoading = false
                    )
                }
        }
    }

    fun clearResults() {
        _uiState.value = _uiState.value.copy(results = emptyList())
    }
}
