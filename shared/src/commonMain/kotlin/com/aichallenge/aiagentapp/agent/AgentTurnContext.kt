package com.aichallenge.aiagentapp.agent

import com.aichallenge.aiagentapp.agent.invariant.InvariantBlock
import com.aichallenge.aiagentapp.agent.prompt.PromptContextDecision
import com.aichallenge.aiagentapp.data.InvariantConflictResult

data class AgentTurnContext(
    val promptContext: PromptContextDecision = PromptContextDecision(),
    val relevantBlocks: List<InvariantBlock> = emptyList(),
    val invariantConflict: InvariantConflictResult? = null,
    val reworkViolations: List<String> = emptyList(),
    /** FSM задачи применяется на этом ходе (промпт, классификация, валидация). */
    val applyTaskStateThisTurn: Boolean = false
)
