package com.aichallenge.aiagentapp.agent.profile

object ProfilePromptBuilder {

    fun buildSystemPrompt(basePrompt: String, profile: AssistantProfile): String {
        if (profile.isEmpty()) return basePrompt
        return buildString {
            append(basePrompt)
            append("\n\n--- ПЕРСОНА АССИСТЕНТА ---")
            append("\nТы отвечаешь от лица: ${profile.label}")
            if (profile.personaDescription.isNotBlank()) {
                append(" — ${profile.personaDescription}")
            }
            append('.')
            if (profile.communicationStyle.isNotBlank()) {
                append("\nСтиль общения: ${profile.communicationStyle}")
            }
            if (profile.responseFormat.isNotBlank()) {
                append("\nФормат ответов: ${profile.responseFormat}")
            }
            if (profile.constraints.isNotBlank()) {
                append("\nОграничения: ${profile.constraints}")
            }
            append(
                "\n\nОставайся в этой роли во всех ответах, " +
                    "если пользователь явно не просит выйти из неё."
            )
        }
    }

    fun previewBlock(profile: AssistantProfile): String {
        if (profile.isEmpty()) return "(персона не выбрана)"
        return buildSystemPrompt("", profile).trim()
    }
}
