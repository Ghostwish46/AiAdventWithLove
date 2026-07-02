package com.aichallenge.aiagentapp.mcp

import kotlinx.serialization.Serializable

@Serializable
data class McpToolUsage(
    val serverName: String,
    val toolName: String,
    val argumentsJson: String = "",
    val resultText: String = "",
) {
    fun displayLabel(): String = "$serverName · $toolName"

    fun previewArguments(maxLen: Int = 600): String =
        argumentsJson.trim().ifBlank { "{}" }.let { truncate(it, maxLen) }

    fun previewResult(maxLen: Int = 2000): String =
        resultText.trim().ifBlank { "(пустой ответ)" }.let { truncate(it, maxLen) }

    private fun truncate(text: String, maxLen: Int): String =
        if (text.length <= maxLen) text else text.take(maxLen) + "… (+${text.length - maxLen} симв.)"
}

fun List<McpToolUsage>.displaySummary(): String =
    joinToString(", ") { it.displayLabel() }

fun List<McpToolUsage>.hasCallDetails(): Boolean =
    any { it.argumentsJson.isNotBlank() || it.resultText.isNotBlank() }
