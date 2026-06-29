package com.aichallenge.aiagentapp

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.aichallenge.aiagentapp.agent.ContextStrategy
import com.aichallenge.aiagentapp.agent.SimpleAgent
import com.aichallenge.aiagentapp.agent.memory.MemorySnapshot
import com.aichallenge.aiagentapp.agent.profile.AssistantProfile
import com.aichallenge.aiagentapp.agent.invariant.InvariantBlock
import com.aichallenge.aiagentapp.agent.task.TaskPhase
import com.aichallenge.aiagentapp.agent.task.TaskPhaseTransitions
import com.aichallenge.aiagentapp.agent.task.TaskState
import com.aichallenge.aiagentapp.data.Usage
import com.aichallenge.aiagentapp.platform.platformCopyToClipboard
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.aichallenge.aiagentapp.ui.MarkdownText
import com.aichallenge.aiagentapp.ui.ProfileAvatar
import com.aichallenge.aiagentapp.ui.platformSafeAreaModifier

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    navController: NavController?,
    onCopied: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.isLoading, state.messages.lastOrNull()?.content) {
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
        val useSidePanel = screenWidth >= 600.dp
        val summaryPanelWidth = if (useSidePanel) 340.dp else 280.dp
        val chatColumnWidth = if (state.messages.isNotEmpty()) {
            screenWidth - summaryPanelWidth - 1.dp
        } else {
            screenWidth
        }

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
                    if (state.assistantProfile.isPersona()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProfileAvatar(profile = state.assistantProfile, size = 24.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = state.assistantProfile.label,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    } else {
                        Text(
                            text = "AI Agent",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
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
            val showSummary = state.messages.isNotEmpty()

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
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
                                assistantProfile = state.assistantProfile,
                                maxWidth = chatColumnWidth * 0.85f,
                                onCopied = onCopied,
                                showPinToLongTerm = state.contextStrategy == ContextStrategy.MEMORY_LAYERS &&
                                    message.role == "user" &&
                                    !message.isLoading &&
                                    !message.isError &&
                                    message.content.isNotBlank(),
                                onPinToLongTerm = { viewModel.pinToLongTerm(message.content) },
                                onRetry = if (message.isError) viewModel::retryLastFailedSend else null
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

                if (showSummary) {
                    Spacer(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )
                    Column(
                        modifier = Modifier
                            .width(summaryPanelWidth)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TaskStatePanel(
                            taskState = state.taskState,
                            blockedMessage = state.blockedTransitionMessage
                        )
                        InvariantsPanel(
                            blocks = state.activeInvariantBlocks,
                            relevanceReason = state.relevanceReason,
                            reworkStatus = state.reworkStatus
                        )
                        DialogSummary(
                            lastRequestContextTokens = lastRequestContextTokens,
                            totalTokensInDialog = totalTokensInDialog,
                            totalCostInDialog = totalCostInDialog,
                            contextLength = contextLength,
                            contextStrategy = state.contextStrategy,
                            keepLastMessages = SimpleAgent.KEEP_LAST_MESSAGES,
                            estimatedFullHistoryTokens = viewModel.estimateFullHistoryPromptTokens(),
                            stickyFactsSummary = state.stickyFactsSummary,
                            isBranched = state.isBranched,
                            memorySnapshot = state.memorySnapshot,
                            lastRoutingLog = state.lastRoutingLog
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: UiMessage,
    assistantProfile: AssistantProfile,
    maxWidth: Dp,
    onCopied: () -> Unit,
    showPinToLongTerm: Boolean = false,
    onPinToLongTerm: () -> Unit = {},
    onRetry: (() -> Unit)? = null
) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            ProfileAvatar(
                profile = assistantProfile,
                size = 36.dp,
                modifier = Modifier.padding(end = 8.dp, top = 4.dp)
            )
        }
        Column(
            modifier = Modifier.widthIn(max = maxWidth),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            if (!isUser && assistantProfile.isPersona()) {
                Text(
                    text = assistantProfile.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                )
            }
            MessageBubbleContent(
                message = message,
                isUser = isUser,
                onCopied = onCopied,
                showPinToLongTerm = showPinToLongTerm,
                onPinToLongTerm = onPinToLongTerm,
                onRetry = onRetry
            )
        }
    }
}

@Composable
private fun MessageBubbleContent(
    message: UiMessage,
    isUser: Boolean,
    onCopied: () -> Unit,
    showPinToLongTerm: Boolean,
    onPinToLongTerm: () -> Unit,
    onRetry: (() -> Unit)? = null
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
            if (message.isError) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
                if (onRetry != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onRetry) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Повторить")
                    }
                }
            } else if (message.isLoading && message.content.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MarkdownText(
                        text = message.content,
                        modifier = Modifier.weight(1f),
                        color = textColor
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
                if (!isUser) {
                    MarkdownText(
                        text = message.content,
                        modifier = Modifier.fillMaxWidth(),
                        color = textColor
                    )
                } else {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor
                    )
                }
            }
            if (message.content.isNotEmpty() && !message.isError) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (showPinToLongTerm) {
                        IconButton(
                            onClick = onPinToLongTerm,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = "В долговременную",
                                tint = textColor
                            )
                        }
                    }
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
    isBranched: Boolean,
    memorySnapshot: MemorySnapshot? = null,
    lastRoutingLog: List<String> = emptyList()
) {
    val nearLimit = contextLength != null && contextLength > 0 &&
        lastRequestContextTokens >= contextLength * 0.9
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(10.dp),
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
                ContextStrategy.MEMORY_LAYERS ->
                    "Стратегия: 3 слоя памяти (краткосрочная / рабочая / долговременная)"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        if (contextStrategy == ContextStrategy.MEMORY_LAYERS && memorySnapshot != null) {
            MemoryLayersPanel(memorySnapshot, lastRoutingLog, keepLastMessages)
        }
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
            (contextStrategy == ContextStrategy.SLIDING_WINDOW ||
                contextStrategy == ContextStrategy.FACTS_KV ||
                contextStrategy == ContextStrategy.MEMORY_LAYERS) &&
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
        if (contextStrategy == ContextStrategy.MEMORY_LAYERS) {
            Text(
                text = "В API: system с рабочей и долговременной памятью + последние $keepLastMessages реплик.",
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
private fun TaskStatePanel(taskState: TaskState, blockedMessage: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Состояние задачи",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (!taskState.isActive) {
            Text(
                text = "Задача не активна — появится после сообщения с явной целью.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        Text(
            text = taskState.phase.displayDescription,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val allowed = TaskPhaseTransitions.allowedTargets(taskState.phase)
        if (allowed.isNotEmpty()) {
            Text(
                text = "Переходы: ${allowed.joinToString { it.name }}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (taskState.planApproved) {
            Text(text = "✓ План утверждён", style = MaterialTheme.typography.labelSmall)
        }
        if (taskState.executionResultReady) {
            Text(text = "✓ Результат готов", style = MaterialTheme.typography.labelSmall)
        }
        if (taskState.validationReported) {
            Text(text = "✓ Проверка проведена", style = MaterialTheme.typography.labelSmall)
        }
        if (!blockedMessage.isNullOrBlank()) {
            Text(
                text = "⚠ $blockedMessage",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TaskPhase.entries.chunked(2).forEach { rowPhases ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rowPhases.forEach { phase ->
                        TaskPhaseChip(
                            phase = phase,
                            selected = taskState.phase == phase,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowPhases.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        if (!taskState.taskGoal.isNullOrBlank()) {
            Text(
                text = "Цель: ${taskState.taskGoal}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (taskState.currentStep.isNotBlank()) {
            Text(
                text = "Шаг: ${taskState.currentStep}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (taskState.expectedAction.isNotBlank()) {
            Text(
                text = "Ожидание: ${taskState.expectedAction}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (taskState.phase == TaskPhase.PLANNING && taskState.planningFacts.isNotEmpty()) {
            val facts = taskState.planningFacts.entries.joinToString("\n") { "• ${it.key}: ${it.value}" }
            Text(
                text = "Собрано:\n$facts",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (taskState.phase == TaskPhase.PLANNING && taskState.openQuestions.isNotEmpty()) {
            val questions = taskState.openQuestions.mapIndexed { i, q -> "${i + 1}. $q" }.joinToString("\n")
            Text(
                text = "Ждём ответы:\n$questions",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (taskState.completedSteps.isNotEmpty()) {
            val preview = taskState.completedSteps.joinToString("\n• ", prefix = "• ")
            Text(
                text = "Выполнено:\n$preview",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InvariantsPanel(
    blocks: List<InvariantBlock>,
    relevanceReason: String,
    reworkStatus: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Инварианты",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        if (blocks.isEmpty()) {
            Text(
                text = "Для этого запроса блоки не применяются.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            blocks.forEach { block ->
                Text(
                    text = block.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
                block.rules.take(5).forEach { rule ->
                    Text(
                        text = "• ${rule.text}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (block.rules.size > 5) {
                    Text(
                        text = "…ещё ${block.rules.size - 5}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        if (relevanceReason.isNotBlank()) {
            Text(
                text = relevanceReason,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (reworkStatus.isNotBlank()) {
            Text(
                text = reworkStatus,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun TaskPhaseChip(
    phase: TaskPhase,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val label = when (phase) {
        TaskPhase.PLANNING -> "Planning"
        TaskPhase.EXECUTION -> "Execution"
        TaskPhase.VALIDATION -> "Validation"
        TaskPhase.DONE -> "Done"
    }
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
            maxLines = 2
        )
    }
}

@Composable
private fun MemoryLayersPanel(
    snapshot: MemorySnapshot,
    lastRoutingLog: List<String>,
    keepLastMessages: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Слои памяти",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        MemoryLayerSection(
            title = "Краткосрочная (последние $keepLastMessages реплик)",
            content = if (snapshot.shortTermLines.isEmpty()) {
                "(пусто)"
            } else {
                snapshot.shortTermLines.joinToString("\n")
            }
        )
        MemoryLayerSection(
            title = "Рабочая (текущая задача)",
            content = snapshot.workingText
        )
        MemoryLayerSection(
            title = "Долговременная (между сессиями)",
            content = snapshot.longTermText
        )
        if (lastRoutingLog.isNotEmpty()) {
            MemoryLayerSection(
                title = "Последний роутинг",
                content = lastRoutingLog.joinToString("\n")
            )
        }
    }
}

@Composable
private fun MemoryLayerSection(title: String, content: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = content,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InputBar(
    input: String,
    isLoading: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(input)) }

    LaunchedEffect(input) {
        if (input != textFieldValue.text) {
            textFieldValue = TextFieldValue(input)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { updated ->
                textFieldValue = updated
                onInputChange(updated.text)
            },
            modifier = Modifier
                .weight(1f)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter
                    if (!isEnter) return@onPreviewKeyEvent false
                    if (event.isShiftPressed) {
                        val start = textFieldValue.selection.min
                        val end = textFieldValue.selection.max
                        val newText = buildString {
                            append(textFieldValue.text.substring(0, start))
                            append('\n')
                            append(textFieldValue.text.substring(end))
                        }
                        val cursor = start + 1
                        textFieldValue = TextFieldValue(newText, TextRange(cursor, cursor))
                        onInputChange(newText)
                        true
                    } else {
                        if (input.isNotBlank() && !isLoading) {
                            onSend()
                        }
                        true
                    }
                },
            placeholder = { Text("Напишите сообщение... (Shift+Enter — новая строка)") },
            enabled = !isLoading,
            minLines = 1,
            maxLines = 8,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default
            ),
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
