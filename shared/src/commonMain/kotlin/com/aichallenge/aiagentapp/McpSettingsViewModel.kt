package com.aichallenge.aiagentapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichallenge.aiagentapp.data.McpConnectionMode
import com.aichallenge.aiagentapp.data.McpSettings
import com.aichallenge.aiagentapp.data.McpSettingsStore
import com.aichallenge.aiagentapp.mcp.McpConnectionTester
import com.aichallenge.aiagentapp.mcp.McpToolInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class McpSettingsUiState(
    val connectionMode: McpConnectionMode = McpConnectionMode.HTTP,
    val httpUrl: String = "",
    val stdioCommand: String = "",
    val isTesting: Boolean = false,
    val connected: Boolean = false,
    val tools: List<McpToolInfo> = emptyList(),
    val errorMessage: String? = null,
)

class McpSettingsViewModel(
    private val settingsStore: McpSettingsStore,
    private val connectionTester: McpConnectionTester,
) : ViewModel() {

    private val _uiState = MutableStateFlow(McpSettingsUiState())
    val uiState: StateFlow<McpSettingsUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        val settings = settingsStore.load()
        _uiState.update {
            it.copy(
                connectionMode = settings.connectionMode,
                httpUrl = settings.httpUrl,
                stdioCommand = settings.stdioCommand,
                connected = false,
                tools = emptyList(),
                errorMessage = null,
            )
        }
    }

    fun setConnectionMode(mode: McpConnectionMode) {
        _uiState.update {
            it.copy(
                connectionMode = mode,
                connected = false,
                tools = emptyList(),
                errorMessage = null,
            )
        }
    }

    fun setHttpUrl(value: String) {
        _uiState.update { it.copy(httpUrl = value, errorMessage = null) }
    }

    fun setStdioCommand(value: String) {
        _uiState.update { it.copy(stdioCommand = value, errorMessage = null) }
    }

    fun testConnection() {
        val state = _uiState.value
        settingsStore.save(
            McpSettings(
                connectionMode = state.connectionMode,
                httpUrl = state.httpUrl.trim(),
                stdioCommand = state.stdioCommand.trim(),
            )
        )

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isTesting = true,
                    connected = false,
                    tools = emptyList(),
                    errorMessage = null,
                )
            }

            val result = when (state.connectionMode) {
                McpConnectionMode.HTTP -> connectionTester.testHttp(state.httpUrl)
                McpConnectionMode.STDIO -> connectionTester.testStdio(state.stdioCommand)
            }

            result.fold(
                onSuccess = { tools ->
                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            connected = true,
                            tools = tools,
                            errorMessage = null,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            connected = false,
                            tools = emptyList(),
                            errorMessage = error.message ?: "Не удалось подключиться к MCP-серверу",
                        )
                    }
                },
            )
        }
    }
}
