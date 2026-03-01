package com.aichallenge.aiagentapp

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.aichallenge.aiagentapp.data.ModelInfo
import java.util.Locale

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AI Agent",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            if (state.messages.isNotEmpty()) {
                IconButton(onClick = { viewModel.clearChat() }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear chat"
                    )
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.messages) { message ->
                MessageBubble(
                    message = message,
                    modelInfo = viewModel.modelInfo,
                    maxWidth = screenWidth * 0.78f
                )
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }
        }

        InputBar(
            input = state.input,
            isLoading = state.isLoading,
            onInputChange = viewModel::updateInput,
            onSend = viewModel::send
        )
    }
}

@Composable
private fun MessageBubble(
    message: UiMessage,
    modelInfo: ModelInfo,
    maxWidth: androidx.compose.ui.unit.Dp
) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        val bgColor = when {
            message.isError -> MaterialTheme.colorScheme.errorContainer
            isUser -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.secondaryContainer
        }
        val textColor = when {
            message.isError -> MaterialTheme.colorScheme.onErrorContainer
            isUser -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSecondaryContainer
        }
        val shape = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = if (isUser) 16.dp else 4.dp,
            bottomEnd = if (isUser) 4.dp else 16.dp
        )

        Column(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .clip(shape)
                .background(bgColor)
                .padding(12.dp)
        ) {
            if (message.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                if (!isUser && (message.usage != null || message.elapsedMs > 0)) {
                    MessageMetrics(
                        usage = message.usage,
                        elapsedMs = message.elapsedMs,
                        modelInfo = modelInfo,
                        textColor = textColor
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun MessageMetrics(
    usage: com.aichallenge.aiagentapp.data.Usage?,
    elapsedMs: Long,
    modelInfo: ModelInfo,
    textColor: androidx.compose.ui.graphics.Color
) {
    val timeSec = elapsedMs / 1000.0
    val cost = usage?.let { u ->
        u.promptTokens.toDouble() / 1_000_000 * modelInfo.inputPricePerM +
            u.completionTokens.toDouble() / 1_000_000 * modelInfo.outputPricePerM
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = String.format(Locale.US, "⏱ %.1f сек", timeSec),
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
        if (usage != null) {
            Text(
                text = "📊 ${usage.promptTokens} + ${usage.completionTokens} = ${usage.totalTokens} токенов",
                style = MaterialTheme.typography.labelSmall,
                color = textColor
            )
        }
        if (cost != null) {
            Text(
                text = String.format(Locale.US, "💰 ~%.4f ₽", cost),
                style = MaterialTheme.typography.labelSmall,
                color = textColor
            )
        }
    }
}

@Composable
private fun InputBar(
    input: String,
    isLoading: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Напишите сообщение...") },
            enabled = !isLoading,
            maxLines = 4,
            shape = RoundedCornerShape(24.dp)
        )

        IconButton(
            onClick = onSend,
            enabled = input.isNotBlank() && !isLoading
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = if (input.isNotBlank() && !isLoading)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline
            )
        }
    }
}
