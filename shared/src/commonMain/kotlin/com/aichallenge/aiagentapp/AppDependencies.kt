package com.aichallenge.aiagentapp

import com.aichallenge.aiagentapp.data.ConversationStore
import com.aichallenge.aiagentapp.data.LongTermMemoryStore
import com.aichallenge.aiagentapp.data.LlmClient
import com.aichallenge.aiagentapp.data.ModelInfo
import com.aichallenge.aiagentapp.data.InvariantsStore
import com.aichallenge.aiagentapp.data.McpSettingsStore
import com.aichallenge.aiagentapp.data.ProfileCatalogStore
import com.aichallenge.aiagentapp.mcp.McpConnectionTester

data class AppDependencies(
    val conversationRepository: ConversationStore,
    val longTermMemoryRepository: LongTermMemoryStore,
    val profileCatalogRepository: ProfileCatalogStore,
    val invariantsRepository: InvariantsStore,
    val mcpSettingsStore: McpSettingsStore,
    val mcpConnectionTester: McpConnectionTester,
    val deepSeekRepository: LlmClient,
    val modelInfo: ModelInfo,
    val systemPrompt: String
)

const val DEFAULT_SYSTEM_PROMPT =
    "Ты полезный AI-ассистент. Отвечай чётко и по делу на русском языке.\n\n" +
    "Форматирование ответов: можно использовать заголовки, списки, **жирный**, *курсив*, `код`. " +
    "Не используй markdown-таблицы (строки с | и ---) — клиент их пока не поддерживает. " +
    "Вместо таблиц оформляй данные нумерованными или маркированными списками."

fun defaultModelInfo(): ModelInfo = ModelInfo(
    id = "openai/gpt-oss-120b",
    label = "GPT-OSS 120B",
    tier = "Сильная",
    inputPricePerM = 5.0,
    outputPricePerM = 26.0,
    contextLength = 131_000
)
