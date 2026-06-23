package com.aichallenge.aiagentapp.data

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

interface LlmClient {
    suspend fun sendMessages(messages: List<ChatMessage>, modelId: String): Result<AgentTurnResult>
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
}

interface ConversationStore {
    fun getAll(): List<Conversation>
    fun getById(id: String): Conversation?
    fun save(conversation: Conversation)
    fun delete(id: String)
}
