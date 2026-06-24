package com.aichallenge.aiagentapp.data

import com.aichallenge.aiagentapp.agent.profile.AssistantProfile

interface ProfileCatalogStore {
    fun loadCustomProfiles(): List<AssistantProfile>
    fun saveCustomProfiles(profiles: List<AssistantProfile>)
}
