package com.aichallenge.aiagentapp.platform

import java.io.File

data class ApiKeys(
    val routerAiApiKey: String,
    val deepSeekApiKey: String
)

expect fun platformAppDataDir(): File

expect fun platformReadApiKeys(): ApiKeys

expect fun platformCopyToClipboard(text: String)
