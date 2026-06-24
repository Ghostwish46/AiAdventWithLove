package com.aichallenge.aiagentapp.agent.profile

class ProfileCatalog(
    private var customProfiles: List<AssistantProfile> = emptyList()
) {
    fun allSelectableProfiles(): List<AssistantProfile> =
        AssistantProfile.selectablePresets() + customProfiles

    fun customProfiles(): List<AssistantProfile> = customProfiles

    fun resolve(profileId: String?): AssistantProfile {
        if (profileId.isNullOrBlank()) return AssistantProfile.NEUTRAL
        AssistantProfile.findBuiltIn(profileId)?.let { return it }
        return customProfiles.find { it.id == profileId } ?: AssistantProfile.NEUTRAL
    }

    fun updateCustomProfiles(profiles: List<AssistantProfile>) {
        customProfiles = profiles
    }

    fun addCustom(profile: AssistantProfile) {
        customProfiles = customProfiles + profile
    }

    fun updateCustom(profile: AssistantProfile) {
        customProfiles = customProfiles.map { if (it.id == profile.id) profile else it }
    }

    fun removeCustom(id: String) {
        customProfiles = customProfiles.filter { it.id != id }
    }
}
