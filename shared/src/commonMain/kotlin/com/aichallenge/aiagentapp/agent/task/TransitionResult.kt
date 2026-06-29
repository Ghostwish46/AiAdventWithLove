package com.aichallenge.aiagentapp.agent.task

sealed class TransitionResult {
    data class Allowed(val newState: TaskState) : TransitionResult()
    data class Blocked(
        val reason: String,
        val requested: TaskPhase,
        val state: TaskState
    ) : TransitionResult()

    data class NoChange(val state: TaskState) : TransitionResult()
}
