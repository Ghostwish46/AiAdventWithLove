package com.aichallenge.aiagentapp.data

import com.aichallenge.aiagentapp.agent.invariant.InvariantBlock
import com.aichallenge.aiagentapp.agent.invariant.InvariantRule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class InvariantsDto(
    val blocks: List<InvariantBlockDto> = emptyList()
)

@Serializable
private data class InvariantBlockDto(
    val id: String,
    val title: String,
    val domainHint: String = "",
    val rules: List<InvariantRuleDto> = emptyList(),
    val enabled: Boolean = true
)

@Serializable
private data class InvariantRuleDto(
    val id: String,
    val text: String
)

private val invariantsJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
}

fun parseInvariantsJson(json: String?): List<InvariantBlock> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        invariantsJson.decodeFromString<InvariantsDto>(json).blocks.map { it.toDomain() }
    } catch (_: Exception) {
        emptyList()
    }
}

fun encodeInvariantsJson(blocks: List<InvariantBlock>): String {
    val dto = InvariantsDto(blocks = blocks.map { it.toDto() })
    return invariantsJson.encodeToString(InvariantsDto.serializer(), dto)
}

private fun InvariantBlockDto.toDomain(): InvariantBlock = InvariantBlock(
    id = id,
    title = title,
    domainHint = domainHint,
    rules = rules.map { InvariantRule(id = it.id, text = it.text) },
    enabled = enabled
)

private fun InvariantBlock.toDto(): InvariantBlockDto = InvariantBlockDto(
    id = id,
    title = title,
    domainHint = domainHint,
    rules = rules.map { InvariantRuleDto(id = it.id, text = it.text) },
    enabled = enabled
)
