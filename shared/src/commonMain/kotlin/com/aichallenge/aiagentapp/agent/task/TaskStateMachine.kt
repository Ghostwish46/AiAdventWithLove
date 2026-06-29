package com.aichallenge.aiagentapp.agent.task

import com.aichallenge.aiagentapp.data.TaskStateClassificationResult

class TaskStateMachine(initial: TaskState = TaskState.inactive()) {

    private var state: TaskState = initial

    fun snapshot(): TaskState = state

    fun clearBlockedTransition(): TaskState {
        state = state.copy(blockedTransition = null)
        return state
    }

    fun advancePhase(): TaskState {
        if (!state.isActive || state.phase == TaskPhase.DONE) return state
        val next = state.phase.next() ?: return state
        return applyTransition(next)
    }

    fun applyClassification(result: TaskStateClassificationResult): TaskState {
        if (!result.activate) return state

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
        if (result.planApproved) {
            updated = updated.copy(planApproved = true)
        }
        if (result.executionResultReady) {
            updated = updated.copy(executionResultReady = true)
        }
        if (result.validationReported) {
            updated = updated.copy(validationReported = true)
        }

        val requestedPhase = result.requestedPhase?.let { name ->
            runCatching { TaskPhase.valueOf(name.uppercase()) }.getOrNull()
        }
        if (requestedPhase != null) {
            updated = applyTransitionToState(updated, requestedPhase)
        } else if (result.advancePhase && updated.phase != TaskPhase.DONE) {
            val next = updated.phase.next()
            if (next != null) {
                updated = applyTransitionToState(updated, next)
            }
        } else {
            state = updated.copy(blockedTransition = null)
        }
        return state
    }

    private fun applyTransition(target: TaskPhase): TaskState {
        state = applyTransitionToState(state, target)
        return state
    }

    private fun applyTransitionToState(current: TaskState, target: TaskPhase): TaskState =
        when (val result = TaskPhaseTransitions.transition(current, target)) {
            is TransitionResult.Allowed -> {
                state = result.newState
                result.newState
            }
            is TransitionResult.Blocked -> {
                state = result.state
                result.state
            }
            is TransitionResult.NoChange -> {
                state = result.state
                result.state
            }
        }

    fun reset() {
        state = TaskState.inactive()
    }
}
