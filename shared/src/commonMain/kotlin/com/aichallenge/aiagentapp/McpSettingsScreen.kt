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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.aichallenge.aiagentapp.data.McpConnectionMode
import com.aichallenge.aiagentapp.mcp.McpToolInfo
import com.aichallenge.aiagentapp.ui.platformSafeAreaModifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpSettingsScreen(
    viewModel: McpSettingsViewModel,
    navController: NavController,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .then(platformSafeAreaModifier()),
        topBar = {
            TopAppBar(
                title = { Text("MCP") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "Подключитесь к внешнему MCP-серверу и получите список доступных инструментов (tools).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.connectionMode == McpConnectionMode.HTTP,
                        onClick = { viewModel.setConnectionMode(McpConnectionMode.HTTP) },
                        label = { Text("HTTP URL") },
                    )
                    FilterChip(
                        selected = uiState.connectionMode == McpConnectionMode.STDIO,
                        onClick = { viewModel.setConnectionMode(McpConnectionMode.STDIO) },
                        label = { Text("Stdio") },
                    )
                }
            }

            item {
                when (uiState.connectionMode) {
                    McpConnectionMode.HTTP -> {
                        OutlinedTextField(
                            value = uiState.httpUrl,
                            onValueChange = viewModel::setHttpUrl,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("URL MCP-сервера") },
                            placeholder = { Text("http://127.0.0.1:3000/mcp") },
                            singleLine = true,
                            supportingText = {
                                Text("Сервер должен быть уже запущен и доступен по этому адресу.")
                            },
                        )
                    }
                    McpConnectionMode.STDIO -> {
                        OutlinedTextField(
                            value = uiState.stdioCommand,
                            onValueChange = viewModel::setStdioCommand,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Команда запуска") },
                            placeholder = { Text("npx -y @modelcontextprotocol/server-everything") },
                            singleLine = true,
                            supportingText = {
                                Text("Приложение само запустит процесс и подключится через stdin/stdout.")
                            },
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = viewModel::testConnection,
                    enabled = !uiState.isTesting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (uiState.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 12.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(if (uiState.isTesting) "Подключение…" else "Проверить подключение")
                }
            }

            uiState.errorMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (uiState.connected) {
                item {
                    Text(
                        text = "MCP подключён. Инструментов: ${uiState.tools.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            items(uiState.tools, key = { it.name }) { tool ->
                McpToolCard(tool = tool)
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun McpToolCard(tool: McpToolInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = tool.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = tool.description?.takeIf { it.isNotBlank() } ?: "Без описания",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
