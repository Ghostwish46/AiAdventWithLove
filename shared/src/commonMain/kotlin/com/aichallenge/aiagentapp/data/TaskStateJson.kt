package com.aichallenge.aiagentapp.data

import com.aichallenge.aiagentapp.agent.task.TaskFsmScope
import com.aichallenge.aiagentapp.agent.task.TaskPhase
import com.aichallenge.aiagentapp.agent.task.TaskState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class TaskStatePayload(
    val state: TaskState,
    val fsmScope: TaskFsmScope = TaskFsmScope.UNDECIDED
)

@Serializable
private data class TaskStateDto(
    val phase: String = TaskPhase.PLANNING.name,
    val currentStep: String = "",
    val expectedAction: String = "",
    val taskGoal: String? = null,
    val completedSteps: List<String> = emptyList(),
    val isActive: Boolean = false,
    val openQuestions: List<String> = emptyList(),
    val planningFacts: Map<String, String> = emptyMap(),
    val planApproved: Boolean = false,
    val executionResultReady: Boolean = false,
    val validationReported: Boolean = false,
    /** ENABLED | DISABLED | UNDECIDED — режим FSM, зафиксированный по первому сообщению. */
    val fsmScope: String? = null
)

private val taskStateJson = Json { ignoreUnknownKeys = true }

fun parseTaskStateJson(json: String?): TaskState = parseTaskStatePayload(json).state

fun parseTaskStatePayload(json: String?): TaskStatePayload {
    if (json.isNullOrBlank()) return TaskStatePayload(TaskState.inactive())
    return try {
        val dto = taskStateJson.decodeFromString(TaskStateDto.serializer(), json)
        TaskStatePayload(
            state = TaskState(
                phase = TaskPhase.fromSavedName(dto.phase),
                currentStep = dto.currentStep,
                expectedAction = dto.expectedAction,
                taskGoal = dto.taskGoal,
                completedSteps = dto.completedSteps,
                isActive = dto.isActive,
                openQuestions = dto.openQuestions,
                planningFacts = dto.planningFacts,
                planApproved = dto.planApproved,
                executionResultReady = dto.executionResultReady,
                validationReported = dto.validationReported
            ),
            fsmScope = TaskFsmScope.fromSavedName(dto.fsmScope)
        )
    } catch (_: Exception) {
        TaskStatePayload(TaskState.inactive())
    }
}

fun encodeTaskStateJson(state: TaskState): String =
    encodeTaskStatePayload(state, TaskFsmScope.UNDECIDED)

fun encodeTaskStatePayload(state: TaskState, fsmScope: TaskFsmScope): String {
    val dto = TaskStateDto(
        phase = state.phase.name,
        currentStep = state.currentStep,
        expectedAction = state.expectedAction,
        taskGoal = state.taskGoal,
        completedSteps = state.completedSteps,
        isActive = state.isActive,
        openQuestions = state.openQuestions,
        planningFacts = state.planningFacts,
        planApproved = state.planApproved,
        executionResultReady = state.executionResultReady,
        validationReported = state.validationReported,
        fsmScope = when (fsmScope) {
            TaskFsmScope.UNDECIDED -> null
            else -> fsmScope.name
        }
    )
    return taskStateJson.encodeToString(TaskStateDto.serializer(), dto)
}
