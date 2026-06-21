package com.aichallenge.aiagentapp

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.aichallenge.aiagentapp.agent.ContextStrategy
import com.aichallenge.aiagentapp.agent.SimpleAgent
import com.aichallenge.aiagentapp.data.Usage
import com.aichallenge.aiagentapp.platform.platformCopyToClipboard
import com.aichallenge.aiagentapp.ui.platformSafeAreaModifier

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    navController: NavController?,
    onCopied: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .then(platformSafeAreaModifier())
    ) {
        val screenWidth = maxWidth

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (navController != null && navController.previousBackStackEntry != null) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Agent",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Стратегия: ${state.contextStrategy.displayName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (state.contextStrategy == ContextStrategy.BRANCHING) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (state.canCreateCheckpoint) {
                                OutlinedButton(
                                    onClick = { viewModel.createBranchCheckpoint() },
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text("Разветвить", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (state.isBranched) {
                                state.branches.forEach { branch ->
                                    FilterChip(
                                        selected = state.activeBranchId == branch.id,
                                        onClick = { viewModel.switchBranch(branch.id) },
                                        label = { Text(branch.label, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                    }
                }
                if (navController != null) {
                    IconButton(onClick = { navController.navigate("home") }) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Темы"
                        )
                    }
                }
                if (state.messages.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearChat() }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear chat"
                        )
                    }
                }
            }

            val lastRequestContextTokens =
                state.messages.lastOrNull { it.usage != null }?.usage?.promptTokens ?: 0
            val totalTokensInDialog = state.messages.sumOf { it.usage?.totalTokens ?: 0 }
            val totalCostInDialog = state.messages.sumOf { it.estimatedCostRub ?: 0.0 }
            val contextLength = viewModel.contextLength

            if (state.messages.isNotEmpty()) {
                DialogSummary(
                    lastRequestContextTokens = lastRequestContextTokens,
                    totalTokensInDialog = totalTokensInDialog,
                    totalCostInDialog = totalCostInDialog,
                    contextLength = contextLength,
                    contextStrategy = state.contextStrategy,
                    keepLastMessages = SimpleAgent.KEEP_LAST_MESSAGES,
                    estimatedFullHistoryTokens = viewModel.estimateFullHistoryPromptTokens(),
                    stickyFactsSummary = state.stickyFactsSummary,
                    isBranched = state.isBranched
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    count = state.messages.size,
                    key = { index ->
                        val isLastStreaming = state.isLoading && index == state.messages.lastIndex
                        if (isLastStreaming) "streaming_$index" else "msg_$index"
                    }
                ) { index ->
                    val message = state.messages[index]
                    val displayContent = if (
                        state.isLoading &&
                        index == state.messages.lastIndex &&
                        message.role == "assistant"
                    ) {
                        message.content + state.streamingContent
                    } else {
                        message.content
                    }
                    MessageBubble(
                        message = message.copy(content = displayContent),
                        maxWidth = screenWidth * 0.78f,
                        onCopied = onCopied
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
}

@Composable
private fun MessageBubble(
    message: UiMessage,
    maxWidth: Dp,
    onCopied: () -> Unit
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
            if (message.isLoading && message.content.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                if (message.isLoading && message.content.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                    }
                } else {
                    if (!isUser && (message.usage != null || message.elapsedMs > 0)) {
                        MessageMetrics(
                            usage = message.usage,
                            elapsedMs = message.elapsedMs,
                            estimatedCostRub = message.estimatedCostRub,
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
                if (message.content.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = {
                                platformCopyToClipboard(message.content)
                                onCopied()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Копировать",
                                tint = textColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageMetrics(
    usage: Usage?,
    elapsedMs: Long,
    estimatedCostRub: Double?,
    textColor: Color
) {
    val timeSec = elapsedMs / 1000.0
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "⏱ ${"%.1f".format(timeSec)} сек",
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
        if (estimatedCostRub != null) {
            Text(
                text = "💰 ~${"%.4f".format(estimatedCostRub)} ₽",
                style = MaterialTheme.typography.labelSmall,
                color = textColor
            )
        }
    }
}

@Composable
private fun DialogSummary(
    lastRequestContextTokens: Int,
    totalTokensInDialog: Int,
    totalCostInDialog: Double,
    contextLength: Int?,
    contextStrategy: ContextStrategy,
    keepLastMessages: Int,
    estimatedFullHistoryTokens: Int,
    stickyFactsSummary: String,
    isBranched: Boolean
) {
    val nearLimit = contextLength != null && contextLength > 0 &&
        lastRequestContextTokens >= contextLength * 0.9
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Сводка по диалогу",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = when (contextStrategy) {
                ContextStrategy.SLIDING_WINDOW -> "Стратегия: окно последних $keepLastMessages сообщений"
                ContextStrategy.FACTS_KV ->
                    "Стратегия: зафиксированные факты + окно последних $keepLastMessages сообщений"
                ContextStrategy.BRANCHING ->
                    if (isBranched) {
                        "Стратегия: trunk до checkpoint + хвост активной ветки (до $keepLastMessages)"
                    } else {
                        "Стратегия: branching — создайте checkpoint кнопкой «Разветвить»"
                    }
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        if (contextStrategy == ContextStrategy.FACTS_KV) {
            val preview = if (stickyFactsSummary.isBlank()) {
                "Факты пока пусты (появятся после ответа модели на ваши реплики)."
            } else {
                stickyFactsSummary.take(500) + if (stickyFactsSummary.length > 500) "…" else ""
            }
            Text(
                text = "Факты: $preview",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "Контекст последнего запроса: $lastRequestContextTokens токенов",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (
            (contextStrategy == ContextStrategy.SLIDING_WINDOW || contextStrategy == ContextStrategy.FACTS_KV) &&
            estimatedFullHistoryTokens > 0
        ) {
            Text(
                text = "≈ если бы вся лента была в промпте: $estimatedFullHistoryTokens ток.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            if (lastRequestContextTokens > 0 && estimatedFullHistoryTokens > lastRequestContextTokens) {
                val saved = estimatedFullHistoryTokens - lastRequestContextTokens
                Text(
                    text = "Оценка экономии: ~$saved ток.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (contextStrategy == ContextStrategy.FACTS_KV) {
            Text(
                text = "В API: системный промпт с блоком фактов + последние реплики; лента на экране полная.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (contextStrategy == ContextStrategy.BRANCHING && isBranched) {
            Text(
                text = "В API: общий trunk + последние реплики активной ветки; переключение веток не смешивает контекст.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "Всего в диалоге: $totalTokensInDialog токенов",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Стоимость диалога: ~${"%.4f".format(totalCostInDialog)} ₽",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (contextLength != null) {
            Text(
                text = "Контекст: $lastRequestContextTokens / $contextLength токенов",
                style = MaterialTheme.typography.bodySmall,
                color = if (nearLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
        if (nearLimit) {
            Text(
                text = "Близко к лимиту модели. Следующий запрос может вернуть ошибку.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
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
