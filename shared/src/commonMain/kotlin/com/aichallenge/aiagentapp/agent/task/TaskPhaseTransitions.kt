package com.aichallenge.aiagentapp.agent.task

object TaskPhaseTransitions {

    val transitions: Map<TaskPhase, List<TaskPhase>> = mapOf(
        TaskPhase.PLANNING to listOf(TaskPhase.EXECUTION),
        TaskPhase.EXECUTION to listOf(TaskPhase.VALIDATION, TaskPhase.PLANNING),
        TaskPhase.VALIDATION to listOf(TaskPhase.DONE, TaskPhase.EXECUTION),
        TaskPhase.DONE to emptyList()
    )

    fun allowedTargets(from: TaskPhase): List<TaskPhase> = transitions[from] ?: emptyList()

    fun transition(state: TaskState, target: TaskPhase?): TransitionResult {
        if (target == null || target == state.phase) {
            return TransitionResult.NoChange(state.copy(blockedTransition = null))
        }
        val allowed = allowedTargets(state.phase)
        if (target !in allowed) {
            return TransitionResult.Blocked(
                reason = "${state.phase.name} -> ${target.name} запрещён",
                requested = target,
                state = state.copy(
                    blockedTransition = BlockedTransition(
                        from = state.phase,
                        requestedTo = target,
                        reason = "${state.phase.name} -> ${target.name} запрещён"
                    )
                )
            )
        }
        val gateResult = checkGateGuards(state, target)
        if (gateResult != null) {
            return TransitionResult.Blocked(
                reason = gateResult,
                requested = target,
                state = state.copy(
                    blockedTransition = BlockedTransition(
                        from = state.phase,
                        requestedTo = target,
                        reason = gateResult
                    )
                )
            )
        }
        return TransitionResult.Allowed(
            state.copy(
                phase = target,
                blockedTransition = null
            )
        )
    }

    private fun checkGateGuards(state: TaskState, target: TaskPhase): String? = when {
        state.phase == TaskPhase.PLANNING && target == TaskPhase.EXECUTION -> {
            when {
                state.openQuestions.isNotEmpty() ->
                    "Нельзя перейти к execution: есть неотвеченные вопросы planning"
                !state.planApproved && state.planningFacts.isEmpty() ->
                    "Нельзя перейти к execution: план не утверждён и факты не собраны"
                else -> null
            }
        }
        state.phase == TaskPhase.EXECUTION && target == TaskPhase.VALIDATION -> {
            if (!state.executionResultReady) {
                "Нельзя перейти к validation: результат execution ещё не готов"
            } else {
                null
            }
        }
        state.phase == TaskPhase.VALIDATION && target == TaskPhase.DONE -> {
            if (!state.validationReported) {
                "Нельзя завершить задачу: нет отчёта о проверке (validation)"
            } else {
                null
            }
        }
        else -> null
    }
}
