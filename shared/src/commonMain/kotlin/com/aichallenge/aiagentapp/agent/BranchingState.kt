package com.aichallenge.aiagentapp.agent

import com.aichallenge.aiagentapp.data.ChatMessage

data class DialogBranch(
    val id: String,
    val label: String,
    val messages: List<ChatMessage> = emptyList()
)

/** Состояние ветвления: общий trunk до checkpoint + независимые ветки. */
data class BranchingState(
    val trunk: List<ChatMessage>,
    val branches: List<DialogBranch>,
    val activeBranchId: String
) {
    val isActive: Boolean get() = branches.isNotEmpty()

    fun activeBranch(): DialogBranch? = branches.find { it.id == activeBranchId }

    /** Сообщения для отображения: trunk + активная ветка. */
    fun displayMessages(): List<ChatMessage> =
        trunk + (activeBranch()?.messages ?: emptyList())

    companion object {
        fun createCheckpoint(trunk: List<ChatMessage>): BranchingState = BranchingState(
            trunk = trunk.toList(),
            branches = listOf(
                DialogBranch(id = "a", label = "Ветка A"),
                DialogBranch(id = "b", label = "Ветка B")
            ),
            activeBranchId = "a"
        )
    }
}
