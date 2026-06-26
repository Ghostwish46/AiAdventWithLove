package com.aichallenge.aiagentapp.agent.task

data class TaskState(
    val phase: TaskPhase = TaskPhase.PLANNING,
    val currentStep: String = "",
    val expectedAction: String = "",
    val taskGoal: String? = null,
    val completedSteps: List<String> = emptyList(),
    val isActive: Boolean = false,
    /** Вопросы planning-этапа, на которые ещё нет ответа. */
    val openQuestions: List<String> = emptyList(),
    /** Собранные на planning ответы и факты (ключ → значение). */
    val planningFacts: Map<String, String> = emptyMap()
) {
    companion object {
        fun inactive(): TaskState = TaskState(isActive = false)
    }
}
