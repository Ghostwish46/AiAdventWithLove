package com.aichallenge.aiagentapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichallenge.aiagentapp.data.McpConnectionMode
import com.aichallenge.aiagentapp.mcp.McpCatalog
import com.aichallenge.aiagentapp.mcp.McpConnectionStatus
import com.aichallenge.aiagentapp.mcp.McpRegistry
import com.aichallenge.aiagentapp.mcp.McpServerProfile
import com.aichallenge.aiagentapp.mcp.McpToolEntry
import com.aichallenge.aiagentapp.mcp.formatConnectionError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class McpServerEditorState(
    val visible: Boolean = false,
    val editingId: String? = null,
    val name: String = "",
    val connectionMode: McpConnectionMode = McpConnectionMode.HTTP,
    val httpUrl: String = "http://127.0.0.1:3000/mcp",
    val stdioCommand: String = "npx -y @modelcontextprotocol/server-everything",
)

data class McpToolTestState(
    val visible: Boolean = false,
    val toolKey: String = "",
    val toolName: String = "",
    val argumentsJson: String = "{}",
    val isRunning: Boolean = false,
    val result: String? = null,
    val error: String? = null,
)

data class McpToolsUiState(
    val catalog: McpCatalog = McpCatalog(),
    val isRefreshing: Boolean = false,
    val expandedToolKeys: Set<String> = emptySet(),
    val editor: McpServerEditorState = McpServerEditorState(),
    val toolTest: McpToolTestState = McpToolTestState(),
    val globalError: String? = null,
) {
    val serverCount: Int get() = catalog.servers.size
    val toolCount: Int get() = catalog.tools.size
    val activeToolCount: Int get() = catalog.tools.count { tool ->
        val server = catalog.servers.find { it.profile.id == tool.serverId }
        server?.profile?.enabled == true && tool.enabled && tool.status == McpConnectionStatus.ONLINE
    }
}

class McpToolsViewModel(
    private val registry: McpRegistry,
) : ViewModel() {

    private val _uiState = MutableStateFlow(McpToolsUiState())
    val uiState: StateFlow<McpToolsUiState> = _uiState.asStateFlow()

    init {
        loadCatalog()
        refreshAll()
    }

    fun loadCatalog() {
        _uiState.update { it.copy(catalog = registry.getCatalog(), globalError = null) }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, globalError = null) }
            runCatching { registry.refreshAll() }
                .onSuccess { catalog ->
                    _uiState.update { it.copy(catalog = catalog, isRefreshing = false) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            catalog = registry.getCatalog(),
                            isRefreshing = false,
                            globalError = formatConnectionError(error),
                        )
                    }
                }
        }
    }

    fun refreshServer(serverId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, globalError = null) }
            runCatching { registry.refreshServer(serverId) }
                .onSuccess { catalog ->
                    _uiState.update { it.copy(catalog = catalog, isRefreshing = false) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            catalog = registry.getCatalog(),
                            isRefreshing = false,
                            globalError = formatConnectionError(error),
                        )
                    }
                }
        }
    }

    fun setServerEnabled(serverId: String, enabled: Boolean) {
        registry.setServerEnabled(serverId, enabled)
        loadCatalog()
    }

    fun setToolEnabled(serverId: String, toolName: String, enabled: Boolean) {
        registry.setToolEnabled(serverId, toolName, enabled)
        loadCatalog()
    }

    fun toggleToolExpanded(toolKey: String) {
        _uiState.update { state ->
            val expanded = state.expandedToolKeys.toMutableSet()
            if (toolKey in expanded) expanded.remove(toolKey) else expanded.add(toolKey)
            state.copy(expandedToolKeys = expanded)
        }
    }

    fun openAddServer() {
        _uiState.update {
            it.copy(
                editor = McpServerEditorState(visible = true, editingId = null),
            )
        }
    }

    fun openEditServer(profile: McpServerProfile) {
        _uiState.update {
            it.copy(
                editor = McpServerEditorState(
                    visible = true,
                    editingId = profile.id,
                    name = profile.name,
                    connectionMode = profile.connectionMode,
                    httpUrl = profile.httpUrl,
                    stdioCommand = profile.stdioCommand,
                ),
            )
        }
    }

    fun dismissEditor() {
        _uiState.update { it.copy(editor = McpServerEditorState()) }
    }

    fun updateEditorName(value: String) {
        _uiState.update { it.copy(editor = it.editor.copy(name = value)) }
    }

    fun updateEditorMode(mode: McpConnectionMode) {
        _uiState.update { it.copy(editor = it.editor.copy(connectionMode = mode)) }
    }

    fun updateEditorHttpUrl(value: String) {
        _uiState.update { it.copy(editor = it.editor.copy(httpUrl = value)) }
    }

    fun updateEditorStdioCommand(value: String) {
        _uiState.update { it.copy(editor = it.editor.copy(stdioCommand = value)) }
    }

    fun saveEditor() {
        val editor = _uiState.value.editor
        val editingId = editor.editingId
        if (editingId == null) {
            val profile = registry.addServer(
                name = editor.name,
                connectionMode = editor.connectionMode,
                httpUrl = editor.httpUrl,
                stdioCommand = editor.stdioCommand,
            )
            dismissEditor()
            loadCatalog()
            refreshServer(profile.id)
        } else {
            registry.updateServer(
                McpServerProfile(
                    id = editingId,
                    name = editor.name.trim().ifBlank { "MCP Server" },
                    connectionMode = editor.connectionMode,
                    httpUrl = editor.httpUrl.trim(),
                    stdioCommand = editor.stdioCommand.trim(),
                    enabled = registry.getCatalog().servers
                        .find { it.profile.id == editingId }?.profile?.enabled ?: true,
                )
            )
            dismissEditor()
            loadCatalog()
            refreshServer(editingId)
        }
    }

    fun deleteServer(serverId: String) {
        registry.removeServer(serverId)
        loadCatalog()
    }

    fun openToolTest(tool: McpToolEntry) {
        val defaultArgs = when (tool.name) {
            "search_anime" -> """{"search":"Naruto","perPage":3}"""
            else -> "{}"
        }
        _uiState.update {
            it.copy(
                toolTest = McpToolTestState(
                    visible = true,
                    toolKey = tool.toolKey,
                    toolName = tool.name,
                    argumentsJson = defaultArgs,
                ),
            )
        }
    }

    fun dismissToolTest() {
        _uiState.update { it.copy(toolTest = McpToolTestState()) }
    }

    fun updateToolTestArgs(value: String) {
        _uiState.update { it.copy(toolTest = it.toolTest.copy(argumentsJson = value, error = null)) }
    }

    fun runToolTest() {
        val test = _uiState.value.toolTest
        viewModelScope.launch {
            _uiState.update {
                it.copy(toolTest = it.toolTest.copy(isRunning = true, result = null, error = null))
            }
            registry.callTool(test.toolKey, test.argumentsJson).fold(
                onSuccess = { result ->
                    _uiState.update {
                        it.copy(
                            toolTest = it.toolTest.copy(
                                isRunning = false,
                                result = result.text,
                                error = if (result.isError) result.text else null,
                            ),
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            toolTest = it.toolTest.copy(
                                isRunning = false,
                                error = error.message ?: "Tool call failed",
                            ),
                        )
                    }
                },
            )
        }
    }

    fun toolsForServer(serverId: String): List<McpToolEntry> =
        _uiState.value.catalog.tools.filter { it.serverId == serverId }

    fun serverName(serverId: String): String =
        _uiState.value.catalog.servers.find { it.profile.id == serverId }?.profile?.name ?: serverId
}
