package com.aichallenge.aiagentapp.agent

/**
 * Управление контекстом для запросов к модели (фаза 1).
 * UI и сохранённый диалог всегда содержат полную ленту сообщений.
 */
enum class ContextStrategy {
    /** В промпт уходит вся история диалога. */
    FULL,

    /** В промпт — только последние [SimpleAgent.KEEP_LAST_MESSAGES] реплик; старые отбрасываются из API. */
    SLIDING_WINDOW,

    /**
     * Sticky facts (ключ–значение) + окно последних [SimpleAgent.KEEP_LAST_MESSAGES] реплик.
     * Факты обновляются вызовом модели после каждого сообщения пользователя; полная лента остаётся в UI.
     */
    FACTS_KV;

    companion object {
        fun fromSavedName(name: String?): ContextStrategy =
            entries.find { it.name == name } ?: SLIDING_WINDOW
    }
}
