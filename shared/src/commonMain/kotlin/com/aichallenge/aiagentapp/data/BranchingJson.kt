package com.aichallenge.aiagentapp.data

import com.aichallenge.aiagentapp.UiMessage
import com.aichallenge.aiagentapp.agent.BranchingState
import com.aichallenge.aiagentapp.agent.DialogBranch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class BranchingSnapshotDto(
    val trunk: List<SavedMessage>,
    val branches: List<BranchDto>,
    val activeBranchId: String
)

@Serializable
private data class BranchDto(
    val id: String,
    val label: String,
    val messages: List<SavedMessage>
)

data class BranchingUiSnapshot(
    val trunk: List<UiMessage>,
    val branchMessages: Map<String, List<UiMessage>>,
    val activeBranchId: String
)

private val json = Json { ignoreUnknownKeys = true }

fun encodeBranchingSnapshot(state: BranchingState): String {
    val dto = BranchingSnapshotDto(
        trunk = state.trunk.map { it.toSavedMessage() },
        branches = state.branches.map { branch ->
            BranchDto(
                id = branch.id,
                label = branch.label,
                messages = branch.messages.map { it.toSavedMessage() }
            )
        },
        activeBranchId = state.activeBranchId
    )
    return json.encodeToString(BranchingSnapshotDto.serializer(), dto)
}

fun parseBranchingSnapshot(raw: String?): BranchingState? {
    if (raw.isNullOrBlank()) return null
    return try {
        val dto = json.decodeFromString(BranchingSnapshotDto.serializer(), raw)
        BranchingState(
            trunk = dto.trunk.map { it.toChatMessage() },
            branches = dto.branches.map { b ->
                DialogBranch(
                    id = b.id,
                    label = b.label,
                    messages = b.messages.map { it.toChatMessage() }
                )
            },
            activeBranchId = dto.activeBranchId
        )
    } catch (_: Exception) {
        null
    }
}

fun encodeBranchingUiSnapshot(
    trunk: List<UiMessage>,
    branchMessages: Map<String, List<UiMessage>>,
    branches: List<Pair<String, String>>,
    activeBranchId: String
): String {
    val dto = BranchingSnapshotDto(
        trunk = trunk.filter { !it.isLoading && !it.isError }.map { it.toSavedMessage() },
        branches = branches.map { (id, label) ->
            BranchDto(
                id = id,
                label = label,
                messages = (branchMessages[id] ?: emptyList())
                    .filter { !it.isLoading && !it.isError }
                    .map { it.toSavedMessage() }
            )
        },
        activeBranchId = activeBranchId
    )
    return json.encodeToString(BranchingSnapshotDto.serializer(), dto)
}

fun parseBranchingUiSnapshot(raw: String?): BranchingUiSnapshot? {
    if (raw.isNullOrBlank()) return null
    return try {
        val dto = json.decodeFromString(BranchingSnapshotDto.serializer(), raw)
        BranchingUiSnapshot(
            trunk = dto.trunk.map { it.toUiMessage() },
            branchMessages = dto.branches.associate { b ->
                b.id to b.messages.map { it.toUiMessage() }
            },
            activeBranchId = dto.activeBranchId
        )
    } catch (_: Exception) {
        null
    }
}

private fun com.aichallenge.aiagentapp.data.ChatMessage.toSavedMessage(): SavedMessage =
    SavedMessage(role = role, content = content)

private fun UiMessage.toSavedMessage(): SavedMessage = SavedMessage(
    role = role,
    content = content,
    promptTokens = usage?.promptTokens,
    completionTokens = usage?.completionTokens,
    totalTokens = usage?.totalTokens,
    elapsedMs = elapsedMs.takeIf { it > 0 },
    estimatedCostRub = estimatedCostRub,
    mcpToolsUsed = mcpToolsUsed,
)

private fun SavedMessage.toUiMessage(): UiMessage = UiMessage(
    role = role,
    content = content,
    usage = toUsage(),
    elapsedMs = elapsedMs ?: 0,
    estimatedCostRub = estimatedCostRub,
    mcpToolsUsed = mcpToolsUsed,
)
