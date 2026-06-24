package com.aichallenge.aiagentapp.data

import com.aichallenge.aiagentapp.agent.profile.AssistantProfile
import com.aichallenge.aiagentapp.agent.profile.ProfileAvatarKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class ProfileCatalogDto(
    val customProfiles: List<AssistantProfileDto> = emptyList()
)

@Serializable
private data class AssistantProfileDto(
    val id: String,
    val label: String,
    val communicationStyle: String = "",
    val responseFormat: String = "",
    val constraints: String = "",
    val personaDescription: String = ""
)

private val profileJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
}

fun parseProfileCatalogJson(json: String): List<AssistantProfile> {
    if (json.isBlank()) return emptyList()
    return try {
        profileJson.decodeFromString<ProfileCatalogDto>(json).customProfiles.map { it.toDomain() }
    } catch (_: Exception) {
        emptyList()
    }
}

fun encodeProfileCatalogJson(profiles: List<AssistantProfile>): String {
    val dto = ProfileCatalogDto(
        customProfiles = profiles.map { it.toDto() }
    )
    return profileJson.encodeToString(dto)
}

private fun AssistantProfileDto.toDomain(): AssistantProfile = AssistantProfile(
    id = id,
    label = label,
    communicationStyle = communicationStyle,
    responseFormat = responseFormat,
    constraints = constraints,
    personaDescription = personaDescription,
    avatarKind = ProfileAvatarKind.DEFAULT
)

private fun AssistantProfile.toDto(): AssistantProfileDto = AssistantProfileDto(
    id = id,
    label = label,
    communicationStyle = communicationStyle,
    responseFormat = responseFormat,
    constraints = constraints,
    personaDescription = personaDescription
)
