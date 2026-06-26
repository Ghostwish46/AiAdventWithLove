package com.aichallenge.aiagentapp.agent.task

import com.aichallenge.aiagentapp.data.TaskStateClassificationResult

class TaskStateMachine(initial: TaskState = TaskState.inactive()) {

    private var state: TaskState = initial

    fun snapshot(): TaskState = state

    fun advancePhase(): TaskState {
        if (!state.isActive || state.phase == TaskPhase.DONE) return state
        val next = state.phase.next() ?: return state
        state = state.copy(phase = next)
        return state
    }

    fun applyClassification(result: TaskStateClassificationResult): TaskState {
        if (!result.activate && !state.isActive) return state

        var updated = state
        if (result.activate && !state.isActive) {
            updated = updated.copy(
                isActive = true,
                phase = TaskPhase.PLANNING,
                taskGoal = result.taskGoal?.takeIf { it.isNotBlank() } ?: updated.taskGoal
            )
        }
        if (result.taskGoal?.isNotBlank() == true) {
            updated = updated.copy(taskGoal = result.taskGoal)
        }
        if (result.currentStep.isNotBlank()) {
            updated = updated.copy(currentStep = result.currentStep)
        }
        if (result.expectedAction.isNotBlank()) {
            updated = updated.copy(expectedAction = result.expectedAction)
        }
        if (result.completedStep.isNotBlank()) {
            val step = result.completedStep.trim()
            if (step !in updated.completedSteps) {
                updated = updated.copy(completedSteps = updated.completedSteps + step)
            }
        }
        if (result.openQuestions != null) {
            updated = updated.copy(openQuestions = result.openQuestions)
        }
        if (result.planningFacts.isNotEmpty()) {
            updated = updated.copy(planningFacts = updated.planningFacts + result.planningFacts)
        }
        if (result.advancePhase && updated.phase != TaskPhase.DONE) {
            val canAdvanceFromPlanning =
                updated.phase != TaskPhase.PLANNING || updated.openQuestions.isEmpty()
            if (canAdvanceFromPlanning) {
                updated = updated.copy(phase = updated.phase.next() ?: TaskPhase.DONE)
            }
        }
        state = updated
        return state
    }

    fun reset() {
        state = TaskState.inactive()
    }
}
