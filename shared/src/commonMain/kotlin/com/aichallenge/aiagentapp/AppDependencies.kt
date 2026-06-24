package com.aichallenge.aiagentapp

import com.aichallenge.aiagentapp.data.ConversationStore
import com.aichallenge.aiagentapp.data.LongTermMemoryStore
import com.aichallenge.aiagentapp.data.LlmClient
import com.aichallenge.aiagentapp.data.ModelInfo
import com.aichallenge.aiagentapp.data.ProfileCatalogStore

data class AppDependencies(
    val conversationRepository: ConversationStore,
    val longTermMemoryRepository: LongTermMemoryStore,
    val profileCatalogRepository: ProfileCatalogStore,
    val deepSeekRepository: LlmClient,
    val modelInfo: ModelInfo,
    val systemPrompt: String
)

const val DEFAULT_SYSTEM_PROMPT =
    "Ты полезный AI-ассистент. Отвечай чётко и по делу на русском языке."

fun defaultModelInfo(): ModelInfo = ModelInfo(
    id = "openai/gpt-oss-120b",
    label = "GPT-OSS 120B",
    tier = "Сильная",
    inputPricePerM = 5.0,
    outputPricePerM = 26.0,
    contextLength = 131_000
)
