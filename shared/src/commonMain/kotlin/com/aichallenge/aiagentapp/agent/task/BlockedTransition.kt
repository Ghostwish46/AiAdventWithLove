package com.aichallenge.aiagentapp.agent.task

data class BlockedTransition(
    val from: TaskPhase,
    val requestedTo: TaskPhase,
    val reason: String
)
