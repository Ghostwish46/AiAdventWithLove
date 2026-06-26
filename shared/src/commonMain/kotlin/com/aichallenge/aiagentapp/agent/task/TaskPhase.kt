package com.aichallenge.aiagentapp.agent.task

enum class TaskPhase {
    PLANNING,
    EXECUTION,
    VALIDATION,
    DONE;

    val displayName: String
        get() = when (this) {
            PLANNING -> "планирование"
            EXECUTION -> "выполнение"
            VALIDATION -> "проверка и улучшение"
            DONE -> "завершено"
        }

    fun next(): TaskPhase? = when (this) {
        PLANNING -> EXECUTION
        EXECUTION -> VALIDATION
        VALIDATION -> DONE
        DONE -> null
    }

    companion object {
        fun fromSavedName(name: String?): TaskPhase =
            entries.find { it.name == name } ?: PLANNING
    }
}
