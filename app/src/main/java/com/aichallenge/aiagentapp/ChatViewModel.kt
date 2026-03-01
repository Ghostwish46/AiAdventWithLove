package com.aichallenge.aiagentapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichallenge.aiagentapp.data.AVAILABLE_MODELS
import com.aichallenge.aiagentapp.data.DeepSeekRepository
import com.aichallenge.aiagentapp.data.ModelInfo
import com.aichallenge.aiagentapp.data.Usage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ModelResult(
    val model: ModelInfo,
    val response: String = "",
    val usage: Usage? = null,
    val elapsedMs: Long = 0,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    fun estimateCostRub(): Double? {
        val u = usage ?: return null
        val inputCost = u.promptTokens.toDouble() / 1_000_000 * model.inputPricePerM
        val outputCost = u.completionTokens.toDouble() / 1_000_000 * model.outputPricePerM
        return inputCost + outputCost
    }
}

data class ChatUiState(
    val query: String = "",
    val selectedModel: ModelInfo = AVAILABLE_MODELS[1],
    val results: List<ModelResult> = emptyList(),
    val isLoading: Boolean = false
)

class ChatViewModel(private val repository: DeepSeekRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun updateQuery(text: String) {
        _uiState.value = _uiState.value.copy(query = text)
    }

    fun selectModel(model: ModelInfo) {
        _uiState.value = _uiState.value.copy(selectedModel = model)
    }

    fun send() {
        val state = _uiState.value
        if (state.query.isBlank() || state.isLoading) return

        val model = state.selectedModel
        val newResult = ModelResult(model = model, isLoading = true)
        val updatedResults = state.results + newResult
        _uiState.value = state.copy(results = updatedResults, isLoading = true)

        val resultIndex = updatedResults.lastIndex

        viewModelScope.launch {
            repository.sendWithModel(state.query, model.id)
                .onSuccess { modelResponse ->
                    _uiState.value = _uiState.value.copy(
                        results = _uiState.value.results.toMutableList().apply {
                            this[resultIndex] = this[resultIndex].copy(
                                response = modelResponse.content,
                                usage = modelResponse.usage,
                                elapsedMs = modelResponse.elapsedMs,
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
