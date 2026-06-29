package com.aichallenge.aiagentapp.agent.task

import com.aichallenge.aiagentapp.data.ChatMessage
import com.aichallenge.aiagentapp.data.TaskStateClassificationResult

/**
 * Когда не нужен полный FSM planning → execution → validation.
 * Простые Q&A, примеры кода и мета-вопросы («почему?») не должны включать planning.
 *
 * Режим FSM для диалога ([TaskFsmScope]) фиксируется по первому сообщению;
 * на последующих ходах FSM применяется только при [shouldApplyFsmOnTurn].
 */
object TaskStateActivationPolicy {

    private val metaClarificationPrefixes = listOf(
        "почему", "зачем", "как так", "что значит", "объясни", "explain", "why", "how come"
    )

    private val simpleRequestMarkers = listOf(
        "пример", "покажи", "приведи", "напиши код", "дай код", "snippet",
        "что такое", "как получить", "как сделать", "можно ли", "сколько",
        "в чём разница", "чем отличается", "расскажи", "опиши"
    )

    private val projectIntentMarkers = listOf(
        "реализуй", "разработай", "создай приложение", "сделай приложение",
        "спроектируй", "mvp", "с нуля", "полностью", "поэтапно", "план работ",
        "доклад", "презентац", "архитектур", "implement", "build app"
    )

    private val taskContinuationMarkers = listOf(
        "начинай", "продолж", "утверж", "согласен", "приступ", "выполняй",
        "go ahead", "proceed", "approve"
    )

    fun isMetaClarification(message: String): Boolean {
        val trimmed = message.trim()
        if (trimmed.length > 120) return false
        val lower = trimmed.lowercase().trimEnd('?', '.', '!', ' ')
        if (lower.split(Regex("\\s+")).size > 8) return false
        return metaClarificationPrefixes.any { lower == it || lower.startsWith("$it ") || lower.endsWith(" $it") }
    }

    fun isSimpleSingleTurnRequest(message: String): Boolean {
        val lower = message.lowercase()
        if (lower.length > 250) return false
        if (projectIntentMarkers.any { lower.contains(it) }) return false
        return simpleRequestMarkers.any { lower.contains(it) }
    }

    fun hasProjectIntent(message: String): Boolean {
        val lower = message.lowercase()
        return projectIntentMarkers.any { lower.contains(it) }
    }

    fun hasTaskContinuationIntent(message: String): Boolean {
        val lower = message.trim().lowercase()
        if (taskContinuationMarkers.any { lower.contains(it) }) return true
        if (lower.startsWith("да") && lower.length <= 40) return true
        if (lower.startsWith("ok") && lower.length <= 40) return true
        return false
    }

    fun isFirstUserTurn(conversationSoFar: List<ChatMessage>): Boolean =
        conversationSoFar.none { it.role == "user" }

    /**
     * FSM уже включён для диалога — применять ли его на этом ходе (промпт, классификация, валидация).
     */
    fun shouldApplyFsmOnTurn(message: String, current: TaskState): Boolean {
        if (!current.isActive || current.phase == TaskPhase.DONE) return false
        if (isPrematureUserClosure(message)) return true
        if (hasProjectIntent(message) || hasTaskContinuationIntent(message)) return true
        if (isMetaClarification(message) || isSimpleSingleTurnRequest(message)) return false
        if (!hasProjectIntent(message) && message.trim().length < 80) return false
        return when (current.phase) {
            TaskPhase.PLANNING -> message.trim().length >= 12
            TaskPhase.EXECUTION, TaskPhase.VALIDATION -> message.trim().length >= 20
            TaskPhase.DONE -> false
        }
    }

    fun isLightweightTask(state: TaskState): Boolean {
        if (!state.isActive) return true
        return state.phase == TaskPhase.PLANNING &&
            state.completedSteps.isEmpty() &&
            state.planningFacts.isEmpty() &&
            !state.planApproved
    }

    fun shouldSkipClassification(message: String, current: TaskState): Boolean {
        if (isMetaClarification(message)) return true
        if (!current.isActive && isSimpleSingleTurnRequest(message)) return true
        if (!current.isActive && !hasProjectIntent(message) && message.trim().length < 80) return true
        return false
    }

    fun shouldDeactivate(current: TaskState, message: String): Boolean {
        if (!current.isActive) return false
        if (!isLightweightTask(current)) return false
        return isMetaClarification(message) || isSimpleSingleTurnRequest(message)
    }

    fun isPrematureUserClosure(message: String): Boolean {
        val lower = message.lowercase()
        return listOf(
            "спасибо", "благодар", "выполнил задачу", "задача выполнена",
            "всё понял", "все понял", "всё ясно", "хватит", "достаточно"
        ).any { lower.contains(it) }
    }

    fun sanitize(
        result: TaskStateClassificationResult,
        message: String,
        current: TaskState
    ): TaskStateClassificationResult {
        if (shouldSkipClassification(message, current)) {
            return TaskStateClassificationResult(activate = false)
        }
        if (isMetaClarification(message) || (!hasProjectIntent(message) && isSimpleSingleTurnRequest(message))) {
            return TaskStateClassificationResult(activate = false)
        }
        if (result.activate && !hasProjectIntent(message) && isSimpleSingleTurnRequest(message)) {
            return result.copy(
                activate = false,
                requestedPhase = null,
                advancePhase = false,
                openQuestions = emptyList()
            )
        }
        if (result.requestedPhase?.equals("PLANNING", ignoreCase = true) == true &&
            current.phase != TaskPhase.PLANNING &&
            !hasProjectIntent(message)
        ) {
            return result.copy(
                activate = false,
                requestedPhase = null,
                advancePhase = false
            )
        }
        if (current.isActive && current.phase != TaskPhase.DONE && isPrematureUserClosure(message)) {
            val blockedDone = result.requestedPhase?.equals("DONE", ignoreCase = true) == true ||
                result.advancePhase && current.phase != TaskPhase.VALIDATION
            return result.copy(
                activate = true,
                requestedPhase = if (blockedDone) null else result.requestedPhase,
                advancePhase = if (blockedDone) false else result.advancePhase,
                completedStep = "",
                expectedAction = when (current.phase) {
                    TaskPhase.PLANNING -> if (current.openQuestions.isNotEmpty()) {
                        "вежливо ответить, напомнить что задача на planning и перечислить неотвеченные вопросы"
                    } else {
                        "вежливо ответить, напомнить что задача на planning и результат ещё не готов — нужен переход к execution"
                    }
                    TaskPhase.EXECUTION -> "вежливо ответить, напомнить что результат ещё не готов — этап execution"
                    TaskPhase.VALIDATION -> "вежливо ответить, напомнить что нужен отчёт о проверке перед завершением"
                    TaskPhase.DONE -> result.expectedAction
                }
            )
        }
        return result
    }
}
