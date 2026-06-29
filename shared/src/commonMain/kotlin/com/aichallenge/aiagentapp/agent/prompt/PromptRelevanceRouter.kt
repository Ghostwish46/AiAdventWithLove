package com.aichallenge.aiagentapp.agent.prompt

import com.aichallenge.aiagentapp.agent.invariant.InvariantBlock
import com.aichallenge.aiagentapp.agent.profile.AssistantProfile

object PromptRelevanceRouter {

    /** Запасной вариант, если LLM-классификатор недоступен: подключаем все блоки — лучше лишний, чем пропустить. */
    fun fallbackDecision(
        userMessage: String,
        assistantProfile: AssistantProfile,
        invariantBlocks: List<InvariantBlock>,
        applyTaskStateThisTurn: Boolean = false
    ): PromptContextDecision = PromptContextDecision(
        includeProfile = assistantProfile.isPersona(),
        relevantBlockIds = invariantBlocks.filter { it.enabled }.map { it.id },
        includeTaskState = applyTaskStateThisTurn,
        relevanceReason = "fallback: все активные блоки (LLM недоступен)"
    )
}
