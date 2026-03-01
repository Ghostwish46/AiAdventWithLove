package com.aichallenge.aiagentapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.aichallenge.aiagentapp.data.AVAILABLE_MODELS
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val state by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Your question") },
                placeholder = { Text("Ask something...") },
                enabled = !state.isLoading,
                minLines = 2,
                maxLines = 4
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AVAILABLE_MODELS.forEach { model ->
                    FilterChip(
                        selected = state.selectedModel == model,
                        onClick = { viewModel.selectModel(model) },
                        label = { Text(model.label) },
                        enabled = !state.isLoading
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.send() },
                    modifier = Modifier.weight(1f),
                    enabled = state.query.isNotBlank() && !state.isLoading
                ) {
                    Text("Send")
                }

                OutlinedButton(
                    onClick = { viewModel.clearResults() },
                    modifier = Modifier.weight(1f),
                    enabled = state.results.isNotEmpty() && !state.isLoading
                ) {
                    Text("Clear")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.results.forEach { result ->
                    ModelResultCard(
                        result = result,
                        onCopy = { text ->
                            clipboardManager.setText(AnnotatedString(text))
                            scope.launch { snackbarHostState.showSnackbar("Copied!") }
                        }
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun ModelResultCard(
    result: ModelResult,
    onCopy: (String) -> Unit
) {
    when {
        result.isLoading -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${result.model.label} (${result.model.tier})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CircularProgressIndicator()
                }
            }
        }

        result.error != null -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "${result.model.label} (${result.model.tier})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = result.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        else -> {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCopy(result.response) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "${result.model.label} (${result.model.tier})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    MetricsRow(result)

                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Tap to copy",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = result.response,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricsRow(result: ModelResult) {
    val timeSec = result.elapsedMs / 1000.0
    val usage = result.usage
    val cost = result.estimateCostRub()

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = String.format(Locale.US, "⏱ %.1f сек", timeSec),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (usage != null) {
            Text(
                text = "📊 ${usage.promptTokens} prompt + ${usage.completionTokens} completion = ${usage.totalTokens} tokens",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (cost != null) {
            Text(
                text = String.format(Locale.US, "💰 ~%.4f ₽", cost),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
