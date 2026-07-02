package com.aichallenge.aiagentapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.aichallenge.aiagentapp.data.McpConnectionMode
import com.aichallenge.aiagentapp.mcp.McpConnectionStatus
import com.aichallenge.aiagentapp.mcp.McpServerView
import com.aichallenge.aiagentapp.mcp.McpToolEntry
import com.aichallenge.aiagentapp.mcp.displayLabel
import com.aichallenge.aiagentapp.mcp.parseInputSchema
import com.aichallenge.aiagentapp.mcp.statusHint
import com.aichallenge.aiagentapp.ui.platformSafeAreaModifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpToolsScreen(
    viewModel: McpToolsViewModel,
    navController: NavController,
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.editor.visible) {
        McpServerEditorDialog(
            editor = uiState.editor,
            onDismiss = viewModel::dismissEditor,
            onSave = viewModel::saveEditor,
            onNameChange = viewModel::updateEditorName,
            onModeChange = viewModel::updateEditorMode,
            onHttpUrlChange = viewModel::updateEditorHttpUrl,
            onStdioChange = viewModel::updateEditorStdioCommand,
        )
    }

    if (uiState.toolTest.visible) {
        McpToolTestDialog(
            state = uiState.toolTest,
            onDismiss = viewModel::dismissToolTest,
            onArgsChange = viewModel::updateToolTestArgs,
            onRun = viewModel::runToolTest,
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .then(platformSafeAreaModifier()),
        topBar = {
            TopAppBar(
                title = { Text("MCP Tools") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (uiState.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = viewModel::refreshAll) {
                            Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            androidx.compose.material3.FloatingActionButton(onClick = viewModel::openAddServer) {
                Icon(Icons.Default.Add, contentDescription = "Добавить сервер")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "Tools приходят с MCP-серверов через listTools. Добавьте сервер, обновите список и включите нужные инструменты для агента.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Text(
                    text = "Серверов: ${uiState.serverCount} · Tools: ${uiState.toolCount} · Активно для агента: ${uiState.activeToolCount}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            uiState.globalError?.let { error ->
                item {
                    Text(text = error, color = MaterialTheme.colorScheme.error)
                }
            }

            item {
                Text(
                    text = "Подключения",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (uiState.catalog.servers.isEmpty()) {
                item {
                    Text(
                        text = "Нет MCP-серверов. Нажмите + чтобы добавить.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(uiState.catalog.servers, key = { it.profile.id }) { serverView ->
                McpServerCard(
                    serverView = serverView,
                    isRefreshing = uiState.isRefreshing,
                    onEnabledChange = { viewModel.setServerEnabled(serverView.profile.id, it) },
                    onRefresh = { viewModel.refreshServer(serverView.profile.id) },
                    onEdit = { viewModel.openEditServer(serverView.profile) },
                    onDelete = { viewModel.deleteServer(serverView.profile.id) },
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Инструменты",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            uiState.catalog.servers.forEach { serverView ->
                val tools = viewModel.toolsForServer(serverView.profile.id)
                if (tools.isNotEmpty()) {
                    item(key = "header-${serverView.profile.id}") {
                        Text(
                            text = serverView.profile.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    items(tools, key = { it.toolKey }) { tool ->
                        McpToolCard(
                            tool = tool,
                            serverEnabled = serverView.profile.enabled,
                            expanded = tool.toolKey in uiState.expandedToolKeys,
                            onToggleExpand = { viewModel.toggleToolExpanded(tool.toolKey) },
                            onEnabledChange = {
                                viewModel.setToolEnabled(tool.serverId, tool.name, it)
                            },
                            onTest = { viewModel.openToolTest(tool) },
                        )
                    }
                }
            }

            if (uiState.catalog.tools.isEmpty() && uiState.catalog.servers.isNotEmpty()) {
                item {
                    Text(
                        text = "Нажмите Refresh на сервере, чтобы загрузить tools.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun McpServerCard(
    serverView: McpServerView,
    isRefreshing: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val profile = serverView.profile
    val status = if (isRefreshing && serverView.status != McpConnectionStatus.ONLINE) {
        McpConnectionStatus.CHECKING
    } else {
        serverView.status
    }
    val statusColors = when (status) {
        McpConnectionStatus.ONLINE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        McpConnectionStatus.OFFLINE, McpConnectionStatus.ERROR ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        McpConnectionStatus.CHECKING ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        McpConnectionStatus.UNKNOWN ->
            MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = when (profile.connectionMode) {
                            McpConnectionMode.HTTP -> profile.httpUrl
                            McpConnectionMode.STDIO -> profile.stdioCommand
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(status.displayLabel()) },
                    colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                        containerColor = statusColors.first,
                        labelColor = statusColors.second,
                    ),
                )
            }

            Text(
                text = status.statusHint(),
                style = MaterialTheme.typography.bodySmall,
                color = when (status) {
                    McpConnectionStatus.ONLINE -> MaterialTheme.colorScheme.primary
                    McpConnectionStatus.OFFLINE, McpConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            serverView.lastError?.takeIf { status != McpConnectionStatus.ONLINE }?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    ),
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Для агента", fontWeight = FontWeight.Medium)
                    Text(
                        text = if (profile.enabled) {
                            "Tools этого сервера доступны агенту (если Online)"
                        } else {
                            "Сервер отключён — tools не используются"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = profile.enabled, onCheckedChange = onEnabledChange)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f),
                    enabled = !isRefreshing,
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.width(18.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isRefreshing) "Подключение…" else "Refresh")
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

@Composable
private fun McpToolCard(
    tool: McpToolEntry,
    serverEnabled: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onTest: () -> Unit,
) {
    val agentAvailable = serverEnabled && tool.enabled && tool.status == McpConnectionStatus.ONLINE
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (agentAvailable) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tool.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                AssistChip(onClick = {}, label = { Text(tool.status.displayLabel()) })
            }

            Text(
                text = tool.description?.takeIf { it.isNotBlank() } ?: "Без описания",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Для агента", modifier = Modifier.weight(1f))
                Switch(
                    checked = tool.enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = serverEnabled,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onToggleExpand) {
                    Text(if (expanded) "Скрыть параметры" else "Параметры")
                }
                TextButton(onClick = onTest, enabled = tool.status == McpConnectionStatus.ONLINE) {
                    Text("Тест")
                }
            }

            if (expanded) {
                HorizontalDivider()
                val params = parseInputSchema(tool.inputSchemaJson)
                if (params.isEmpty()) {
                    Text(
                        text = tool.inputSchemaJson ?: "Нет схемы параметров",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                } else {
                    params.forEach { param ->
                        Text(
                            text = buildString {
                                append("• ${param.name}")
                                append(" (${param.type})")
                                if (param.required) append(" *required*")
                                param.description?.let { append(": $it") }
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun McpServerEditorDialog(
    editor: McpServerEditorState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onNameChange: (String) -> Unit,
    onModeChange: (McpConnectionMode) -> Unit,
    onHttpUrlChange: (String) -> Unit,
    onStdioChange: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editor.editingId == null) "Добавить MCP-сервер" else "Редактировать сервер") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = onNameChange,
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = editor.connectionMode == McpConnectionMode.HTTP,
                        onClick = { onModeChange(McpConnectionMode.HTTP) },
                        label = { Text("HTTP") },
                    )
                    FilterChip(
                        selected = editor.connectionMode == McpConnectionMode.STDIO,
                        onClick = { onModeChange(McpConnectionMode.STDIO) },
                        label = { Text("Stdio") },
                    )
                }
                when (editor.connectionMode) {
                    McpConnectionMode.HTTP -> {
                        OutlinedTextField(
                            value = editor.httpUrl,
                            onValueChange = onHttpUrlChange,
                            label = { Text("URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    McpConnectionMode.STDIO -> {
                        OutlinedTextField(
                            value = editor.stdioCommand,
                            onValueChange = onStdioChange,
                            label = { Text("Команда") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
private fun McpToolTestDialog(
    state: McpToolTestState,
    onDismiss: () -> Unit,
    onArgsChange: (String) -> Unit,
    onRun: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Тест: ${state.toolName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.argumentsJson,
                    onValueChange = onArgsChange,
                    label = { Text("Arguments (JSON)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                state.result?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        },
        confirmButton = {
            Button(onClick = onRun, enabled = !state.isRunning) {
                if (state.isRunning) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.width(18.dp))
                } else {
                    Text("Вызвать")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
    )
}
