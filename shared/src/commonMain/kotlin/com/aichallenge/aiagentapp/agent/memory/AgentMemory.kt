package com.aichallenge.aiagentapp.agent.memory

import com.aichallenge.aiagentapp.data.ChatMessage
import com.aichallenge.aiagentapp.data.MemoryClassificationResult

/**
 * Фасад трёхслойной памяти ассистента.
 */
class AgentMemory(
    maxShortTermMessages: Int,
    initialWorking: WorkingMemory = WorkingMemory(),
    initialLongTerm: LongTermMemory = LongTermMemory(),
    initialHistory: List<ChatMessage> = emptyList()
) {
    private val shortTerm = ShortTermMemory(maxShortTermMessages)
    private var working = initialWorking
    private var longTerm = initialLongTerm
    private var lastRoutingLog: List<String> = emptyList()

    init {
        if (initialHistory.isNotEmpty()) {
            shortTerm.replaceAll(initialHistory)
        }
    }

    fun getWorkingMemory(): WorkingMemory = working

    fun getLongTermMemory(): LongTermMemory = longTerm

    fun getLastRoutingLog(): List<String> = lastRoutingLog

    fun appendTurn(message: ChatMessage): List<String> {
        shortTerm.append(message)
        return listOf(MemoryRouter.logShortTermAppend(message.role))
    }

    fun applyClassification(result: MemoryClassificationResult): List<String> {
        val routing = MemoryRouter.applyClassification(
            working = working,
            longTerm = longTerm,
            workingUpdates = result.working,
            longTermUpdates = result.longTerm,
            routingLogFromLlm = result.routingLog
        )
        working = routing.working
        longTerm = routing.longTerm
        lastRoutingLog = routing.routingLog
        return routing.routingLog
    }

    fun pinToLongTerm(key: String, value: String): List<String> {
        longTerm = longTerm.pinEntry(key, value)
        val (_, logLine) = MemoryRouter.routeManualPin(key, value)
        lastRoutingLog = listOf(logLine)
        return lastRoutingLog
    }

    fun replaceLongTerm(from: LongTermMemory) {
        longTerm = from
    }

    fun shortTermMessages(): List<ChatMessage> = shortTerm.snapshot()

    fun removeLastShortTermTurn() {
        shortTerm.removeLast()
    }

    fun snapshot(): MemorySnapshot = MemorySnapshot(
        shortTermLines = shortTerm.displayLines(),
        workingText = working.displayText(),
        longTermText = longTerm.displayText(),
        routingLog = lastRoutingLog
    )

    fun clear() {
        shortTerm.clear()
        working = WorkingMemory()
        lastRoutingLog = emptyList()
    }
}

data class MemorySnapshot(
    val shortTermLines: List<String>,
    val workingText: String,
    val longTermText: String,
    val routingLog: List<String>
)
