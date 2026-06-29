package com.aichallenge.aiagentapp.agent.task

/**
 * Режим FSM для диалога: решается один раз по первому сообщению пользователя.
 */
enum class TaskFsmScope {
    /** Новый чат, первое сообщение ещё не обработано. */
    UNDECIDED,
    /** Первое сообщение — не многошаговая задача; FSM не используется в этом диалоге. */
    DISABLED,
    /** Первое сообщение — многошаговая задача; FSM доступен на релевантных ходах. */
    ENABLED;

    companion object {
        fun fromSavedName(name: String?): TaskFsmScope =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: UNDECIDED
    }
}
