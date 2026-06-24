package com.aichallenge.aiagentapp

import androidx.lifecycle.ViewModel
import com.aichallenge.aiagentapp.agent.profile.AssistantProfile
import com.aichallenge.aiagentapp.agent.profile.ProfileCatalog
import com.aichallenge.aiagentapp.data.Conversation
import com.aichallenge.aiagentapp.data.ConversationStore
import com.aichallenge.aiagentapp.data.ProfileCatalogStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel(
    private val conversationRepository: ConversationStore,
    private val profileCatalogStore: ProfileCatalogStore
) : ViewModel() {

    private val catalog = ProfileCatalog(profileCatalogStore.loadCustomProfiles())

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    fun loadConversations() {
        catalog.updateCustomProfiles(profileCatalogStore.loadCustomProfiles())
        _conversations.value = conversationRepository.getAll()
    }

    fun resolveProfile(profileId: String?): AssistantProfile = catalog.resolve(profileId)
}
