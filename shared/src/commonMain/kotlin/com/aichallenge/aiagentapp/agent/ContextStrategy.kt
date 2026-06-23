package com.aichallenge.aiagentapp.agent

/**
 * Стратегии управления контекстом (День 10).
 * Выбирается один раз при создании диалога и не меняется в процессе общения.
 */
enum class ContextStrategy {
    /** В промпт — только последние [SimpleAgent.KEEP_LAST_MESSAGES] реплик. */
    SLIDING_WINDOW,

    /** Sticky facts + окно последних [SimpleAgent.KEEP_LAST_MESSAGES] реплик. */
    FACTS_KV,

    /** Checkpoint + независимые ветки A/B от точки разветвления. */
    BRANCHING,

    /** Три слоя памяти: краткосрочная / рабочая / долговременная (День 11). */
    MEMORY_LAYERS;

    val displayName: String
        get() = when (this) {
            SLIDING_WINDOW -> "Sliding Window"
            FACTS_KV -> "Sticky Facts"
            BRANCHING -> "Branching"
            MEMORY_LAYERS -> "Memory Layers"
        }

    companion object {
        val selectable: List<ContextStrategy> = entries

        fun fromSavedName(name: String?): ContextStrategy =
            when (name) {
                "FACTS_KV" -> FACTS_KV
                "BRANCHING" -> BRANCHING
                "MEMORY_LAYERS" -> MEMORY_LAYERS
                "FULL", "SLIDING_WINDOW", null -> SLIDING_WINDOW
                else -> entries.find { it.name == name } ?: SLIDING_WINDOW
            }
    }
}
