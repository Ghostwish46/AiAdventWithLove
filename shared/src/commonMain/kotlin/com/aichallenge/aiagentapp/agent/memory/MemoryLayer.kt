package com.aichallenge.aiagentapp.agent.memory

/**
 * Три слоя памяти ассистента (День 11).
 */
enum class MemoryLayer {
    /** Текущий диалог — последние реплики. */
    SHORT_TERM,

    /** Данные текущей задачи: цель, ограничения, решения, статус. */
    WORKING,

    /** Профиль, предпочтения, устойчивые знания между сессиями. */
    LONG_TERM;

    val displayName: String
        get() = when (this) {
            SHORT_TERM -> "Краткосрочная"
            WORKING -> "Рабочая"
            LONG_TERM -> "Долговременная"
        }
}
