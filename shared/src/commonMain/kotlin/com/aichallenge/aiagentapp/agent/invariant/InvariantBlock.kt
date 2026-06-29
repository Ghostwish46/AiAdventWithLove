package com.aichallenge.aiagentapp.agent.invariant

data class InvariantBlock(
    val id: String,
    val title: String,
    val domainHint: String = "",
    val rules: List<InvariantRule> = emptyList(),
    val enabled: Boolean = true
) {
    fun prohibitionRules(): List<InvariantRule> = rules.filter { it.isProhibition() }

    fun preferenceRules(): List<InvariantRule> = rules.filter { !it.isProhibition() }

    companion object {
        const val PRESET_ANDROID_STACK_ID = "preset_android_stack"

        fun defaultAndroidStack(): InvariantBlock = InvariantBlock(
            id = PRESET_ANDROID_STACK_ID,
            title = "Стек Android-разработки",
            domainHint = "android, kotlin, разработка, код, приложение, api, ui",
            rules = listOf(
                InvariantRule("r_kotlin", "Kotlin"),
                InvariantRule("r_compose", "Compose"),
                InvariantRule("r_room", "Room"),
                InvariantRule("r_ktor", "Ktor"),
                InvariantRule("r_no_rx", "Не использовать RxJava"),
                InvariantRule("r_no_java", "Не использовать Java")
            ),
            enabled = true
        )
    }
}
