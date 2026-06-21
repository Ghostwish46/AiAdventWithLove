package com.aichallenge.aiagentapp

import androidx.lifecycle.ViewModel
import com.aichallenge.aiagentapp.data.Conversation
import com.aichallenge.aiagentapp.data.ConversationStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel(
    private val conversationRepository: ConversationStore
) : ViewModel() {

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    fun loadConversations() {
        _conversations.value = conversationRepository.getAll()
    }
}
