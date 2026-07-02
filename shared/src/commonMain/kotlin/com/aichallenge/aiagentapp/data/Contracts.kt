package com.aichallenge.aiagentapp.data

import com.aichallenge.aiagentapp.agent.invariant.InvariantBlock
import com.aichallenge.aiagentapp.agent.profile.AssistantProfile
import com.aichallenge.aiagentapp.agent.prompt.PromptContextDecision
import com.aichallenge.aiagentapp.agent.task.TaskState
import com.aichallenge.aiagentapp.agent.validation.ValidationResult

sealed class StreamEvent {
    data class Chunk(val text: String) : StreamEvent()
    data class Done(val usage: Usage?) : StreamEvent()
    data class Error(val message: String) : StreamEvent()
}

data class AgentTurnResult(
    val content: String,
    val usage: Usage?,
    val elapsedMs: Long,
    val estimatedCostRub: Double? = null
)

data class MemoryClassificationResult(
    val working: Map<String, String> = emptyMap(),
    val longTerm: Map<String, String> = emptyMap(),
    val routingLog: List<String> = emptyList()
)

data class TaskStateClassificationResult(
    val activate: Boolean = false,
    val taskGoal: String? = null,
    val currentStep: String = "",
    val expectedAction: String = "",
    val advancePhase: Boolean = false,
    val requestedPhase: String? = null,
    val completedStep: String = "",
    val openQuestions: List<String>? = null,
    val planningFacts: Map<String, String> = emptyMap(),
    val planApproved: Boolean = false,
    val executionResultReady: Boolean = false,
    val validationReported: Boolean = false
)

data class InvariantConflictResult(
    val hasConflict: Boolean = false,
    val violatedRuleTexts: List<String> = emptyList(),
    val conflictSummary: String = "",
    val suggestedAlternative: String = ""
)

interface LlmClient {
    suspend fun sendMessages(messages: List<ChatMessage>, modelId: String): Result<AgentTurnResult>
    suspend fun sendMessagesWithTools(
        messages: List<ToolChatMessage>,
        tools: List<LlmToolDefinition>,
        modelId: String,
        toolChoice: String = "auto",
    ): Result<LlmToolResponse>
    fun sendMessagesStreaming(messages: List<ChatMessage>, modelId: String): kotlinx.coroutines.flow.Flow<StreamEvent>
    suspend fun mergeStickyFacts(
        existingFacts: Map<String, String>,
        newUserMessage: String,
        recentContext: List<ChatMessage>,
        modelId: String
    ): Result<Map<String, String>>
    suspend fun classifyMemoryUpdate(
        existingWorking: Map<String, String>,
        existingLongTerm: Map<String, String>,
        newUserMessage: String,
        recentContext: List<ChatMessage>,
        forceLongTerm: Boolean,
        modelId: String
    ): Result<MemoryClassificationResult>
    suspend fun classifyTaskStateUpdate(
        currentState: TaskState,
        newUserMessage: String,
        recentContext: List<ChatMessage>,
        modelId: String
    ): Result<TaskStateClassificationResult>
    suspend fun classifyPromptContext(
        userMessage: String,
        assistantProfile: AssistantProfile,
        invariantBlocks: List<InvariantBlock>,
        taskState: TaskState,
        recentContext: List<ChatMessage>,
        modelId: String
    ): Result<PromptContextDecision>
    suspend fun classifyInvariantConflict(
        userMessage: String,
        relevantBlocks: List<InvariantBlock>,
        modelId: String
    ): Result<InvariantConflictResult>
    suspend fun validateAssistantResponse(
        response: String,
        userMessage: String,
        relevantBlocks: List<InvariantBlock>,
        taskState: TaskState,
        includeTaskState: Boolean,
        modelId: String
    ): Result<ValidationResult>
}

interface ConversationStore {
    fun getAll(): List<Conversation>
    fun getById(id: String): Conversation?
    fun save(conversation: Conversation)
    fun delete(id: String)
}
