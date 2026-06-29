package com.aichallenge.aiagentapp.agent.prompt

data class PromptContextDecision(
    val includeProfile: Boolean = true,
    val relevantBlockIds: List<String> = emptyList(),
    val includeTaskState: Boolean = false,
    val relevanceReason: String = ""
)
