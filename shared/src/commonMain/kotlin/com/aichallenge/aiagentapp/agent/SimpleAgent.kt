package com.aichallenge.aiagentapp.agent

import com.aichallenge.aiagentapp.agent.invariant.InvariantPromptBuilder
import com.aichallenge.aiagentapp.agent.invariant.InvariantsCatalog
import com.aichallenge.aiagentapp.agent.memory.AgentMemory
import com.aichallenge.aiagentapp.agent.memory.MemoryPromptBuilder
import com.aichallenge.aiagentapp.agent.memory.MemoryRouter
import com.aichallenge.aiagentapp.agent.memory.MemorySnapshot
import com.aichallenge.aiagentapp.agent.memory.WorkingMemory
import com.aichallenge.aiagentapp.agent.profile.AssistantProfile
import com.aichallenge.aiagentapp.agent.profile.ProfilePromptBuilder
import com.aichallenge.aiagentapp.agent.prompt.PromptContextDecision
import com.aichallenge.aiagentapp.agent.prompt.PromptRelevanceRouter
import com.aichallenge.aiagentapp.agent.task.TaskFsmScope
import com.aichallenge.aiagentapp.agent.task.TaskState
import com.aichallenge.aiagentapp.agent.task.TaskStateActivationPolicy
import com.aichallenge.aiagentapp.agent.task.TaskStateMachine
import com.aichallenge.aiagentapp.agent.task.TaskStatePromptBuilder
import com.aichallenge.aiagentapp.agent.validation.ValidationResult
import com.aichallenge.aiagentapp.DEFAULT_SYSTEM_PROMPT
import com.aichallenge.aiagentapp.data.AgentTurnResult
import com.aichallenge.aiagentapp.data.ChatMessage
import com.aichallenge.aiagentapp.data.InvariantConflictResult
import com.aichallenge.aiagentapp.data.LlmClient
import com.aichallenge.aiagentapp.data.LongTermMemoryStore
import com.aichallenge.aiagentapp.data.ModelInfo
import com.aichallenge.aiagentapp.data.StreamEvent
import kotlinx.coroutines.flow.Flow

class SimpleAgent(
    private val repository: LlmClient,
    private val modelInfo: ModelInfo,
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    initialHistory: List<ChatMessage> = emptyList(),
    initialContextStrategy: ContextStrategy = ContextStrategy.SLIDING_WINDOW,
    initialStickyFacts: Map<String, String> = emptyMap(),
    initialBranching: BranchingState? = null,
    initialWorkingMemory: WorkingMemory = WorkingMemory(),
    private val longTermMemoryStore: LongTermMemoryStore? = null,
    private val assistantProfile: AssistantProfile = AssistantProfile.NEUTRAL,
    initialTaskState: TaskState = TaskState.inactive(),
    initialTaskFsmScope: TaskFsmScope = TaskFsmScope.UNDECIDED,
    private val invariantsCatalog: InvariantsCatalog = InvariantsCatalog()
) {
    companion object {
        const val KEEP_LAST_MESSAGES = 8
        const val MAX_REWORK_ATTEMPTS = 2
    }

    private val contextStrategyInternal = initialContextStrategy
    private val linearMessages = mutableListOf<ChatMessage>()
    private val windowForApi = mutableListOf<ChatMessage>()
    private val stickyFacts = LinkedHashMap<String, String>().apply { putAll(initialStickyFacts) }
    private var branchingState: BranchingState? = initialBranching
    private val taskStateMachine = TaskStateMachine(initialTaskState)
    private var taskFsmScope: TaskFsmScope = resolveInitialFsmScope(
        initialTaskFsmScope,
        initialTaskState,
        initialHistory
    )
    private var turnContext: AgentTurnContext = AgentTurnContext()
    private var lastUserMessage: String = ""

    private val agentMemory: AgentMemory? =
        if (contextStrategyInternal == ContextStrategy.MEMORY_LAYERS) {
            AgentMemory(
                maxShortTermMessages = KEEP_LAST_MESSAGES,
                initialWorking = initialWorkingMemory,
                initialLongTerm = longTermMemoryStore?.load()
                    ?: com.aichallenge.aiagentapp.agent.memory.LongTermMemory(),
                initialHistory = initialHistory
            )
        } else {
            null
        }

    init {
        when (contextStrategyInternal) {
            ContextStrategy.BRANCHING -> {
                if (branchingState == null) {
                    linearMessages.addAll(initialHistory)
                    syncWindowFromLinear()
                }
            }
            ContextStrategy.SLIDING_WINDOW, ContextStrategy.FACTS_KV -> {
                linearMessages.addAll(initialHistory)
                syncWindowFromLinear()
            }
            ContextStrategy.MEMORY_LAYERS -> {
                linearMessages.addAll(initialHistory)
            }
        }
    }

    fun getContextStrategy(): ContextStrategy = contextStrategyInternal
    fun getAssistantProfile(): AssistantProfile = assistantProfile
    fun getInvariantsCatalog(): InvariantsCatalog = invariantsCatalog
    fun getTurnContext(): AgentTurnContext = turnContext
    fun getRollingSummary(): String = ""
    fun getStickyFactsMap(): Map<String, String> = stickyFacts.toMap()
    fun getStickyFactsDisplay(): String =
        if (stickyFacts.isEmpty()) "" else stickyFacts.entries.joinToString("\n") { "${it.key}: ${it.value}" }
    fun getWorkingMemory(): WorkingMemory = agentMemory?.getWorkingMemory() ?: WorkingMemory()
    fun getMemorySnapshot(): MemorySnapshot? = agentMemory?.snapshot()
    fun getBranchingState(): BranchingState? = branchingState
    fun isBranched(): Boolean = branchingState?.isActive == true
    fun getTaskState(): TaskState = taskStateMachine.snapshot()
    fun getTaskFsmScope(): TaskFsmScope = taskFsmScope
    fun advanceTaskPhase(): TaskState = taskStateMachine.advancePhase()

    private fun resolveInitialFsmScope(
        savedScope: TaskFsmScope,
        savedState: TaskState,
        history: List<ChatMessage>
    ): TaskFsmScope {
        if (savedScope != TaskFsmScope.UNDECIDED) return savedScope
        if (savedState.isActive) return TaskFsmScope.ENABLED
        if (history.any { it.role == "user" }) return TaskFsmScope.DISABLED
        return TaskFsmScope.UNDECIDED
    }

    private fun computeApplyTaskStateThisTurn(userMessage: String): Boolean {
        if (taskFsmScope != TaskFsmScope.ENABLED) return false
        return TaskStateActivationPolicy.shouldApplyFsmOnTurn(
            userMessage,
            taskStateMachine.snapshot()
        )
    }

    suspend fun prepareTurn(userMessage: String, conversationSoFar: List<ChatMessage>) {
        lastUserMessage = userMessage
        turnContext = AgentTurnContext()

        updateTaskStateForUserMessage(userMessage, conversationSoFar)
        val applyTaskState = computeApplyTaskStateThisTurn(userMessage)

        val enabledBlocks = invariantsCatalog.enabledBlocks()
        val decision = repository.classifyPromptContext(
            userMessage = userMessage,
            assistantProfile = assistantProfile,
            invariantBlocks = enabledBlocks,
            taskState = taskStateMachine.snapshot(),
            recentContext = conversationSoFar,
            modelId = modelInfo.id
        ).getOrElse {
            PromptRelevanceRouter.fallbackDecision(
                userMessage,
                assistantProfile,
                enabledBlocks,
                applyTaskState
            )
        }

        val relevantBlocks = invariantsCatalog.blocksByIds(decision.relevantBlockIds)
        turnContext = turnContext.copy(
            promptContext = decision.copy(includeTaskState = applyTaskState),
            relevantBlocks = relevantBlocks,
            applyTaskStateThisTurn = applyTaskState
        )

        if (relevantBlocks.isNotEmpty()) {
            repository.classifyInvariantConflict(
                userMessage = userMessage,
                relevantBlocks = relevantBlocks,
                modelId = modelInfo.id
            ).onSuccess { conflict ->
                if (conflict.hasConflict) {
                    turnContext = turnContext.copy(invariantConflict = conflict)
                }
            }
        }
    }

    fun setReworkViolations(violations: List<String>) {
        turnContext = turnContext.copy(reworkViolations = violations)
    }

    fun clearTurnContext() {
        taskStateMachine.clearBlockedTransition()
        turnContext = AgentTurnContext()
    }

    suspend fun updateTaskStateForUserMessage(
        newUserMessage: String,
        conversationSoFar: List<ChatMessage>
    ) {
        when (taskFsmScope) {
            TaskFsmScope.DISABLED -> return
            TaskFsmScope.UNDECIDED -> {
                if (TaskStateActivationPolicy.isFirstUserTurn(conversationSoFar)) {
                    resolveFsmScopeOnFirstUserMessage(newUserMessage, conversationSoFar)
                }
                return
            }
            TaskFsmScope.ENABLED -> Unit
        }

        val current = taskStateMachine.snapshot()
        if (!TaskStateActivationPolicy.shouldApplyFsmOnTurn(newUserMessage, current)) {
            return
        }
        if (TaskStateActivationPolicy.shouldSkipClassification(newUserMessage, current)) {
            return
        }

        repository.classifyTaskStateUpdate(
            currentState = current,
            newUserMessage = newUserMessage,
            recentContext = conversationSoFar,
            modelId = modelInfo.id
        ).onSuccess { result ->
            val sanitized = TaskStateActivationPolicy.sanitize(result, newUserMessage, current)
            if (sanitized.activate) {
                taskStateMachine.applyClassification(sanitized)
            }
        }
    }

    private suspend fun resolveFsmScopeOnFirstUserMessage(
        message: String,
        conversationSoFar: List<ChatMessage>
    ) {
        val inactive = TaskState.inactive()
        if (TaskStateActivationPolicy.shouldSkipClassification(message, inactive)) {
            taskFsmScope = TaskFsmScope.DISABLED
            taskStateMachine.reset()
            return
        }

        repository.classifyTaskStateUpdate(
            currentState = inactive,
            newUserMessage = message,
            recentContext = conversationSoFar,
            modelId = modelInfo.id
        ).onSuccess { result ->
            val sanitized = TaskStateActivationPolicy.sanitize(result, message, inactive)
            taskFsmScope = if (sanitized.activate) TaskFsmScope.ENABLED else TaskFsmScope.DISABLED
            if (sanitized.activate) {
                taskStateMachine.applyClassification(sanitized)
            } else {
                taskStateMachine.reset()
            }
        }.onFailure {
            val enabled = TaskStateActivationPolicy.hasProjectIntent(message)
            taskFsmScope = if (enabled) TaskFsmScope.ENABLED else TaskFsmScope.DISABLED
            if (enabled) {
                taskStateMachine.applyClassification(
                    com.aichallenge.aiagentapp.data.TaskStateClassificationResult(activate = true)
                )
            } else {
                taskStateMachine.reset()
            }
        }
    }

    suspend fun validateResponse(response: String): ValidationResult {
        val taskState = taskStateMachine.snapshot()
        val includeTaskState = turnContext.applyTaskStateThisTurn
        val local = com.aichallenge.aiagentapp.agent.validation.ResponseValidator.validateLocally(
            response = response,
            relevantBlocks = turnContext.relevantBlocks,
            taskState = taskState,
            includeTaskState = includeTaskState
        )
        if (local is ValidationResult.Fail) return local

        return repository.validateAssistantResponse(
            response = response,
            userMessage = lastUserMessage,
            relevantBlocks = turnContext.relevantBlocks,
            taskState = taskState,
            includeTaskState = includeTaskState,
            modelId = modelInfo.id
        ).getOrElse { ValidationResult.Pass(response) }
    }

    suspend fun generateNonStreaming(): Result<AgentTurnResult> =
        repository.sendMessages(buildMessagesForApi(), modelInfo.id)

    fun createBranchCheckpoint(): Boolean {
        if (contextStrategyInternal != ContextStrategy.BRANCHING || branchingState != null) return false
        val trunk = linearMessages.toList()
        if (trunk.isEmpty()) return false
        branchingState = BranchingState.createCheckpoint(trunk)
        linearMessages.clear()
        windowForApi.clear()
        return true
    }

    fun switchBranch(branchId: String): Boolean {
        val state = branchingState ?: return false
        if (state.branches.none { it.id == branchId }) return false
        branchingState = state.copy(activeBranchId = branchId)
        return true
    }

    suspend fun mergeStickyFactsForUserMessage(
        newUserMessage: String,
        conversationSoFar: List<ChatMessage>
    ) {
        repository.mergeStickyFacts(
            stickyFacts.toMap(),
            newUserMessage,
            conversationSoFar,
            modelInfo.id
        ).onSuccess { merged ->
            stickyFacts.clear()
            stickyFacts.putAll(merged)
        }
    }

    suspend fun updateMemoryForUserMessage(
        newUserMessage: String,
        conversationSoFar: List<ChatMessage>
    ): List<String> {
        val memory = agentMemory ?: return emptyList()
        val forceLongTerm = MemoryRouter.hasLongTermTrigger(newUserMessage)
        val existingLongTerm = memory.getLongTermMemory()
        val allLongTerm = existingLongTerm.profile + existingLongTerm.preferences + existingLongTerm.knowledge
        return repository.classifyMemoryUpdate(
            existingWorking = memory.getWorkingMemory().facts,
            existingLongTerm = allLongTerm,
            newUserMessage = newUserMessage,
            recentContext = conversationSoFar,
            forceLongTerm = forceLongTerm,
            modelId = modelInfo.id
        ).map { result ->
            memory.applyClassification(result)
        }.getOrElse { emptyList() }.also {
            persistLongTermMemory()
        }
    }

    fun pinMessageToLongTerm(messageContent: String): List<String> {
        val memory = agentMemory ?: return emptyList()
        val key = "запись_${linearMessages.count { it.role == "user" }}"
        val log = memory.pinToLongTerm(key, messageContent.trim())
        persistLongTermMemory()
        return log
    }

    private fun persistLongTermMemory() {
        val memory = agentMemory ?: return
        val store = longTermMemoryStore ?: return
        store.save(memory.getLongTermMemory())
    }

    fun getHistory(): List<ChatMessage> = displayMessages()

    fun displayMessages(): List<ChatMessage> =
        when {
            contextStrategyInternal == ContextStrategy.BRANCHING && branchingState != null ->
                branchingState!!.displayMessages()
            contextStrategyInternal == ContextStrategy.BRANCHING ->
                linearMessages.toList()
            else -> linearMessages.toList()
        }

    fun getModelInfo(): ModelInfo = modelInfo

    fun addAssistantMessage(content: String) {
        addAssistantMessageInternal(content)
    }

    fun appendUserMessage(trimmed: String) {
        appendUser(trimmed)
    }

    private fun appendUser(trimmed: String) {
        val msg = ChatMessage(role = "user", content = trimmed)
        when {
            contextStrategyInternal == ContextStrategy.BRANCHING && branchingState != null ->
                appendToActiveBranch(msg)
            contextStrategyInternal == ContextStrategy.BRANCHING -> linearMessages.add(msg)
            contextStrategyInternal == ContextStrategy.MEMORY_LAYERS -> {
                linearMessages.add(msg)
                agentMemory?.appendTurn(msg)
            }
            else -> {
                linearMessages.add(msg)
                windowForApi.add(msg)
            }
        }
    }

    private fun addAssistantMessageInternal(content: String) {
        val msg = ChatMessage(role = "assistant", content = content)
        when {
            contextStrategyInternal == ContextStrategy.BRANCHING && branchingState != null ->
                appendToActiveBranch(msg)
            contextStrategyInternal == ContextStrategy.BRANCHING -> linearMessages.add(msg)
            contextStrategyInternal == ContextStrategy.MEMORY_LAYERS -> {
                linearMessages.add(msg)
                agentMemory?.appendTurn(msg)
            }
            else -> {
                linearMessages.add(msg)
                windowForApi.add(msg)
                trimWindow()
            }
        }
    }

    private fun appendToActiveBranch(msg: ChatMessage) {
        val state = branchingState ?: return
        val branches = state.branches.map { branch ->
            if (branch.id == state.activeBranchId) {
                branch.copy(messages = branch.messages + msg)
            } else {
                branch
            }
        }
        branchingState = state.copy(branches = branches)
    }

    fun rollbackLastUserMessage() {
        when {
            contextStrategyInternal == ContextStrategy.BRANCHING && branchingState != null -> {
                val state = branchingState ?: return
                val branches = state.branches.map { branch ->
                    if (branch.id == state.activeBranchId &&
                        branch.messages.isNotEmpty() &&
                        branch.messages.last().role == "user"
                    ) {
                        branch.copy(messages = branch.messages.dropLast(1))
                    } else {
                        branch
                    }
                }
                branchingState = state.copy(branches = branches)
            }
            contextStrategyInternal == ContextStrategy.BRANCHING -> {
                if (linearMessages.isNotEmpty() && linearMessages.last().role == "user") {
                    linearMessages.removeAt(linearMessages.lastIndex)
                }
            }
            contextStrategyInternal == ContextStrategy.MEMORY_LAYERS -> {
                if (linearMessages.isNotEmpty() && linearMessages.last().role == "user") {
                    linearMessages.removeAt(linearMessages.lastIndex)
                }
                agentMemory?.removeLastShortTermTurn()
            }
            else -> {
                if (linearMessages.isNotEmpty() && linearMessages.last().role == "user") {
                    linearMessages.removeAt(linearMessages.lastIndex)
                }
                if (windowForApi.isNotEmpty() && windowForApi.last().role == "user") {
                    windowForApi.removeAt(windowForApi.lastIndex)
                }
            }
        }
    }

    fun processQueryStreaming(): Flow<StreamEvent> =
        repository.sendMessagesStreaming(buildMessagesForApi(), modelInfo.id)

    fun buildMessagesForApi(): List<ChatMessage> = buildList {
        val ctx = turnContext.promptContext
        var systemContent = systemPrompt

        if (ctx.includeProfile) {
            systemContent = ProfilePromptBuilder.buildSystemPrompt(systemContent, assistantProfile)
        }

        systemContent = when (contextStrategyInternal) {
            ContextStrategy.FACTS_KV -> {
                if (stickyFacts.isNotEmpty()) {
                    val block = stickyFacts.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                    "$systemContent\n\nЗафиксированные факты из диалога (ключ — значение):\n$block"
                } else {
                    systemContent
                }
            }
            ContextStrategy.MEMORY_LAYERS -> {
                val memory = agentMemory
                if (memory != null) {
                    MemoryPromptBuilder.buildSystemPrompt(
                        basePrompt = systemContent,
                        working = memory.getWorkingMemory(),
                        longTerm = memory.getLongTermMemory()
                    )
                } else {
                    systemContent
                }
            }
            else -> systemContent
        }

        if (turnContext.relevantBlocks.isNotEmpty()) {
            systemContent += "\n\n" + InvariantPromptBuilder.buildInvariantsBlock(turnContext.relevantBlocks)
            if (turnContext.applyTaskStateThisTurn) {
                systemContent += InvariantPromptBuilder.appendStateLine(taskStateMachine.snapshot())
            }
        }

        if (turnContext.applyTaskStateThisTurn) {
            systemContent = TaskStatePromptBuilder.buildSystemPrompt(
                systemContent,
                taskStateMachine.snapshot()
            )
        }

        turnContext.invariantConflict?.let { conflict ->
            systemContent += InvariantPromptBuilder.buildConflictBlock(
                conflictSummary = conflict.conflictSummary.ifBlank { "Запрос нарушает инварианты" },
                violatedRules = conflict.violatedRuleTexts,
                suggestedAlternative = conflict.suggestedAlternative
            )
        }

        if (turnContext.reworkViolations.isNotEmpty()) {
            systemContent += InvariantPromptBuilder.buildReworkBlock(turnContext.reworkViolations)
        }

        add(ChatMessage(role = "system", content = systemContent))
        addAll(apiPayloadMessages())
    }

    private fun apiPayloadMessages(): List<ChatMessage> =
        when (contextStrategyInternal) {
            ContextStrategy.SLIDING_WINDOW, ContextStrategy.FACTS_KV -> windowForApi.toList()
            ContextStrategy.BRANCHING -> branchingApiMessages()
            ContextStrategy.MEMORY_LAYERS -> agentMemory?.shortTermMessages() ?: emptyList()
        }

    private fun branchingApiMessages(): List<ChatMessage> {
        val state = branchingState
        if (state == null) {
            return linearMessages.takeLast(KEEP_LAST_MESSAGES)
        }
        val activeTail = state.activeBranch()?.messages?.takeLast(KEEP_LAST_MESSAGES) ?: emptyList()
        return state.trunk + activeTail
    }

    private fun syncWindowFromLinear() {
        windowForApi.clear()
        if (linearMessages.size > KEEP_LAST_MESSAGES) {
            windowForApi.addAll(linearMessages.takeLast(KEEP_LAST_MESSAGES))
        } else {
            windowForApi.addAll(linearMessages)
        }
    }

    private fun trimWindow() {
        while (windowForApi.size > KEEP_LAST_MESSAGES) {
            windowForApi.removeAt(0)
        }
    }

    fun estimatePromptTokensIfFullHistory(fullUiMessages: List<ChatMessage>): Int {
        val chars = systemPrompt.length + fullUiMessages.sumOf { it.content.length + 8 }
        return kotlin.math.ceil(chars / 3.0).toInt()
    }

    suspend fun flushPendingSummarization() = Unit

    fun clearHistory() {
        linearMessages.clear()
        windowForApi.clear()
        stickyFacts.clear()
        branchingState = null
        agentMemory?.clear()
        taskStateMachine.reset()
        taskFsmScope = TaskFsmScope.UNDECIDED
        turnContext = AgentTurnContext()
    }
}
