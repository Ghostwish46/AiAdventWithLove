package com.aichallenge.aiagentapp.agent.memory

/**
 * Долговременная память: профиль, предпочтения, устойчивые знания (между сессиями).
 */
data class LongTermMemory(
    val profile: Map<String, String> = emptyMap(),
    val preferences: Map<String, String> = emptyMap(),
    val knowledge: Map<String, String> = emptyMap()
) {
    fun merge(updates: Map<String, String>): LongTermMemory {
        if (updates.isEmpty()) return this
        val newProfile = LinkedHashMap(profile)
        val newPreferences = LinkedHashMap(preferences)
        val newKnowledge = LinkedHashMap(knowledge)
        updates.forEach { (key, value) ->
            if (value.isBlank()) {
                newProfile.remove(key)
                newPreferences.remove(key)
                newKnowledge.remove(key)
                return@forEach
            }
            when (classifyLongTermKey(key)) {
                LongTermSection.PROFILE -> newProfile[key] = value
                LongTermSection.PREFERENCES -> newPreferences[key] = value
                LongTermSection.KNOWLEDGE -> newKnowledge[key] = value
            }
        }
        return copy(profile = newProfile, preferences = newPreferences, knowledge = newKnowledge)
    }

    fun pinEntry(key: String, value: String): LongTermMemory {
        if (value.isBlank()) return this
        val section = classifyLongTermKey(key)
        return when (section) {
            LongTermSection.PROFILE -> copy(profile = profile + (key to value))
            LongTermSection.PREFERENCES -> copy(preferences = preferences + (key to value))
            LongTermSection.KNOWLEDGE -> copy(knowledge = knowledge + (key to value))
        }
    }

    fun displayText(): String {
        if (profile.isEmpty() && preferences.isEmpty() && knowledge.isEmpty()) return "(пусто)"
        return buildString {
            if (profile.isNotEmpty()) {
                appendLine("Профиль:")
                profile.forEach { (k, v) -> appendLine("  $k: $v") }
            }
            if (preferences.isNotEmpty()) {
                appendLine("Предпочтения:")
                preferences.forEach { (k, v) -> appendLine("  $k: $v") }
            }
            if (knowledge.isNotEmpty()) {
                appendLine("Знания:")
                knowledge.forEach { (k, v) -> appendLine("  $k: $v") }
            }
        }.trim()
    }

    fun isEmpty(): Boolean = profile.isEmpty() && preferences.isEmpty() && knowledge.isEmpty()

    private enum class LongTermSection { PROFILE, PREFERENCES, KNOWLEDGE }

    private fun classifyLongTermKey(key: String): LongTermSection {
        val lower = key.lowercase()
        return when {
            lower.contains("предпочт") || lower.contains("стиль") || lower.contains("формат") ||
                lower.contains("язык") || lower.contains("тон") -> LongTermSection.PREFERENCES
            lower.contains("знан") || lower.contains("факт") || lower.contains("решени") ||
                lower.contains("договор") -> LongTermSection.KNOWLEDGE
            else -> LongTermSection.PROFILE
        }
    }
}
