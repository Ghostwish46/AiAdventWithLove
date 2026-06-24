package com.aichallenge.aiagentapp

import androidx.lifecycle.ViewModel
import com.aichallenge.aiagentapp.agent.profile.AssistantProfile
import com.aichallenge.aiagentapp.agent.profile.ProfileCatalog
import com.aichallenge.aiagentapp.data.ProfileCatalogStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlin.random.Random

class SettingsViewModel(
    private val profileCatalogStore: ProfileCatalogStore
) : ViewModel() {

    private val catalog = ProfileCatalog(profileCatalogStore.loadCustomProfiles())

    private val _customProfiles = MutableStateFlow(catalog.customProfiles())
    val customProfiles: StateFlow<List<AssistantProfile>> = _customProfiles.asStateFlow()

    fun reload() {
        catalog.updateCustomProfiles(profileCatalogStore.loadCustomProfiles())
        _customProfiles.value = catalog.customProfiles()
    }

    fun saveProfile(
        id: String?,
        label: String,
        communicationStyle: String,
        responseFormat: String,
        constraints: String,
        personaDescription: String
    ): String {
        val profileId = id ?: newProfileId()
        val profile = AssistantProfile(
            id = profileId,
            label = label.trim(),
            communicationStyle = communicationStyle.trim(),
            responseFormat = responseFormat.trim(),
            constraints = constraints.trim(),
            personaDescription = personaDescription.trim()
        )
        if (id == null) {
            catalog.addCustom(profile)
        } else {
            catalog.updateCustom(profile)
        }
        persist()
        return profileId
    }

    fun deleteProfile(id: String) {
        catalog.removeCustom(id)
        persist()
    }

    fun getProfile(id: String): AssistantProfile? =
        catalog.customProfiles().find { it.id == id }

    private fun persist() {
        profileCatalogStore.saveCustomProfiles(catalog.customProfiles())
        _customProfiles.value = catalog.customProfiles()
    }

    private fun newProfileId(): String =
        "custom_${Clock.System.now().toEpochMilliseconds()}_${
            (1..6).map { "0123456789abcdef"[Random.nextInt(16)] }.joinToString("")
        }"
}

fun createProfileCatalog(store: ProfileCatalogStore): ProfileCatalog =
    ProfileCatalog(store.loadCustomProfiles())
