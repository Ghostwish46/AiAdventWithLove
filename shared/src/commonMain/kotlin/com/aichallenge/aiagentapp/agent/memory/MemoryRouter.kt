package com.aichallenge.aiagentapp.agent.memory

/**
 * Явные правила роутинга: что и в какой слой памяти попадает.
 */
object MemoryRouter {

    private val longTermTriggers = listOf(
        "запомни",
        "запиши в профиль",
        "на будущее",
        "запомни надолго",
        "сохрани в профиль",
        "мой профиль"
    )

    /** Правило 1: каждая реплика всегда идёт в краткосрочную память. */
    fun routeMessageToShortTerm(): MemoryLayer = MemoryLayer.SHORT_TERM

    /** Правило 3a: пользователь явно просит запомнить надолго. */
    fun hasLongTermTrigger(message: String): Boolean {
        val lower = message.lowercase()
        return longTermTriggers.any { lower.contains(it) }
    }

    /** Правило 3b: ручной pin — всегда в долговременную. */
    fun routeManualPin(key: String, value: String): Pair<MemoryLayer, String> =
        MemoryLayer.LONG_TERM to "«$key» → longTerm: ручной pin пользователя"

    /** Применить результат LLM-классификатора с явным логом. */
    fun applyClassification(
        working: WorkingMemory,
        longTerm: LongTermMemory,
        workingUpdates: Map<String, String>,
        longTermUpdates: Map<String, String>,
        routingLogFromLlm: List<String>
    ): MemoryRoutingResult {
        val log = mutableListOf<String>()
        log.addAll(routingLogFromLlm)
        workingUpdates.forEach { (k, v) ->
            if (v.isNotBlank() && k !in routingLogFromLlm.joinToString()) {
                log.add("«$k» → working: $v")
            }
        }
        longTermUpdates.forEach { (k, v) ->
            if (v.isNotBlank() && k !in routingLogFromLlm.joinToString()) {
                log.add("«$k» → longTerm: $v")
            }
        }
        return MemoryRoutingResult(
            working = working.merge(workingUpdates),
            longTerm = longTerm.merge(longTermUpdates),
            routingLog = log
        )
    }

    fun logShortTermAppend(role: String): String =
        "реплика ($role) → shortTerm: текущий диалог"
}

data class MemoryRoutingResult(
    val working: WorkingMemory,
    val longTerm: LongTermMemory,
    val routingLog: List<String>
)
