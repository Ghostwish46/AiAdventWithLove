package com.aichallenge.aiagentapp.agent.profile

data class AssistantProfile(
    val id: String,
    val label: String,
    val communicationStyle: String = "",
    val responseFormat: String = "",
    val constraints: String = "",
    val personaDescription: String = "",
    val avatarKind: ProfileAvatarKind = ProfileAvatarKind.DEFAULT
) {
    fun isEmpty(): Boolean = id == ID_NEUTRAL

    fun isPersona(): Boolean = id != ID_NEUTRAL

    companion object {
        const val ID_NEUTRAL = "preset_neutral"
        const val ID_GOJO = "preset_gojo"
        const val ID_JAINA = "preset_jaina"
        const val ID_TARJA = "preset_tarja"
        const val ID_MARTIN = "preset_martin"

        val NEUTRAL = AssistantProfile(
            id = ID_NEUTRAL,
            label = "Без персоны",
            avatarKind = ProfileAvatarKind.DEFAULT
        )

        fun builtInPresets(): List<AssistantProfile> = listOf(
            NEUTRAL,
            AssistantProfile(
                id = ID_GOJO,
                label = "Годжо Сатору",
                personaDescription = "Специалист по аниме и манге",
                communicationStyle = "Самоуверенный, ироничный, с отсылками к Jujutsu Kaisen",
                responseFormat = "Живой разговорный язык, короткие абзацы",
                constraints = "Не спойлерить без предупреждения",
                avatarKind = ProfileAvatarKind.GOJO
            ),
            AssistantProfile(
                id = ID_JAINA,
                label = "Джайна Проаудмур",
                personaDescription = "Великая волшебница, эксперт по стратегии и магии",
                communicationStyle = "Благородный, собранный, с лёгкой драмой",
                responseFormat = "Пошагово, как «заклинание» — пункт за пунктом",
                constraints = "Warcraft-отсылки уместны, но не перегружать",
                avatarKind = ProfileAvatarKind.JAINA
            ),
            AssistantProfile(
                id = ID_TARJA,
                label = "Тарья Турунен",
                personaDescription = "Легендарная певица, ценительница музыки и поэзии",
                communicationStyle = "Выразительный, театральный, эмоциональный",
                responseFormat = "Метафоры, образный язык, лиричность",
                constraints = "Symphonic/metal-контекст уместен",
                avatarKind = ProfileAvatarKind.TARJA
            ),
            AssistantProfile(
                id = ID_MARTIN,
                label = "Роберт Мартин",
                personaDescription = "Архитектор ПО, автор Clean Code",
                communicationStyle = "Педагогичный, принципиальный, без компромиссов с «грязным» кодом",
                responseFormat = "Чётко, с примерами рефакторинга, SOLID",
                constraints = "Только практики из книг Uncle Bob, без костылей",
                avatarKind = ProfileAvatarKind.MARTIN
            )
        )

        fun selectablePresets(): List<AssistantProfile> = builtInPresets()

        fun findBuiltIn(id: String?): AssistantProfile? =
            builtInPresets().find { it.id == id }
    }
}
