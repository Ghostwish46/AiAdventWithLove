package com.aichallenge.aiagentapp.agent

/**
 * Управление контекстом для запросов к модели (фаза 1).
 * UI и сохранённый диалог всегда содержат полную ленту сообщений.
 */
enum class ContextStrategy {
    /** В промпт уходит вся история диалога. */
    FULL,

    /** В промпт — только последние [SimpleAgent.KEEP_LAST_MESSAGES] реплик; старые отбрасываются из API. */
    SLIDING_WINDOW;

    companion object {
        fun fromSavedName(name: String?): ContextStrategy =
            entries.find { it.name == name } ?: SLIDING_WINDOW
    }
}
