package com.aichallenge.aiagentapp.data

import com.aichallenge.aiagentapp.agent.task.TaskPhase
import com.aichallenge.aiagentapp.agent.task.TaskState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class TaskStateDto(
    val phase: String = TaskPhase.PLANNING.name,
    val currentStep: String = "",
    val expectedAction: String = "",
    val taskGoal: String? = null,
    val completedSteps: List<String> = emptyList(),
    val isActive: Boolean = false,
    val openQuestions: List<String> = emptyList(),
    val planningFacts: Map<String, String> = emptyMap()
)

private val taskStateJson = Json { ignoreUnknownKeys = true }

fun parseTaskStateJson(json: String?): TaskState {
    if (json.isNullOrBlank()) return TaskState.inactive()
    return try {
        val dto = taskStateJson.decodeFromString(TaskStateDto.serializer(), json)
        TaskState(
            phase = TaskPhase.fromSavedName(dto.phase),
            currentStep = dto.currentStep,
            expectedAction = dto.expectedAction,
            taskGoal = dto.taskGoal,
            completedSteps = dto.completedSteps,
            isActive = dto.isActive,
            openQuestions = dto.openQuestions,
            planningFacts = dto.planningFacts
        )
    } catch (_: Exception) {
        TaskState.inactive()
    }
}

fun encodeTaskStateJson(state: TaskState): String {
    val dto = TaskStateDto(
        phase = state.phase.name,
        currentStep = state.currentStep,
        expectedAction = state.expectedAction,
        taskGoal = state.taskGoal,
        completedSteps = state.completedSteps,
        isActive = state.isActive,
        openQuestions = state.openQuestions,
        planningFacts = state.planningFacts
    )
    return taskStateJson.encodeToString(TaskStateDto.serializer(), dto)
}
