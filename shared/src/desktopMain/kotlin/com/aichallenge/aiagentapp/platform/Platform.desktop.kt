package com.aichallenge.aiagentapp.platform

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

actual fun platformAppDataDir(): File {
    val dir = File(System.getProperty("user.home"), ".aiagentapp")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

actual fun platformReadApiKeys(): ApiKeys {
    val fromEnvRouter = System.getenv("ROUTERAI_API_KEY")?.takeIf { it.isNotBlank() }
    val fromEnvDeepSeek = System.getenv("DEEPSEEK_API_KEY")?.takeIf { it.isNotBlank() }
    val fromFile = readKeysFromLocalProperties()
    return ApiKeys(
        routerAiApiKey = fromEnvRouter ?: fromFile.first ?: "test-key-replace-me",
        deepSeekApiKey = fromEnvDeepSeek ?: fromFile.second ?: "test-key-replace-me"
    )
}

actual fun platformCopyToClipboard(text: String) {
    val selection = StringSelection(text)
    Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
}

private fun readKeysFromLocalProperties(): Pair<String?, String?> {
    val candidates = listOf(
        File("local.properties"),
        File("../local.properties"),
        File("../../local.properties")
    )
    for (file in candidates) {
        if (!file.exists()) continue
        var router: String? = null
        var deepSeek: String? = null
        file.readLines(Charsets.UTF_8).forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("ROUTERAI_API_KEY=") ->
                    router = trimmed.substringAfter('=').trim().ifBlank { null }
                trimmed.startsWith("DEEPSEEK_API_KEY=") ->
                    deepSeek = trimmed.substringAfter('=').trim().ifBlank { null }
            }
        }
        if (router != null || deepSeek != null) return router to deepSeek
    }
    return null to null
}
