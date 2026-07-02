package com.aichallenge.aiagentapp

import com.aichallenge.aiagentapp.data.ConversationRepository
import com.aichallenge.aiagentapp.data.DeepSeekRepository
import com.aichallenge.aiagentapp.data.LongTermMemoryRepository
import com.aichallenge.aiagentapp.data.InvariantsRepository
import com.aichallenge.aiagentapp.data.McpCatalogRepository
import com.aichallenge.aiagentapp.data.ProfileCatalogRepository
import com.aichallenge.aiagentapp.data.ModelInfo
import com.aichallenge.aiagentapp.data.createRouterAiApi
import com.aichallenge.aiagentapp.mcp.McpRegistryImpl
import com.aichallenge.aiagentapp.mcp.McpToolExecutorImpl

fun createAppDependencies(
    modelInfo: ModelInfo = defaultModelInfo(),
    systemPrompt: String = DEFAULT_SYSTEM_PROMPT
): AppDependencies {
    val api = createRouterAiApi()
    val mcpCatalogStore = McpCatalogRepository()
    val mcpRegistry = McpRegistryImpl(mcpCatalogStore)
    return AppDependencies(
        conversationRepository = ConversationRepository(),
        longTermMemoryRepository = LongTermMemoryRepository(),
        profileCatalogRepository = ProfileCatalogRepository(),
        invariantsRepository = InvariantsRepository(),
        mcpRegistry = mcpRegistry,
        mcpToolExecutor = McpToolExecutorImpl(mcpRegistry),
        deepSeekRepository = DeepSeekRepository(api),
        modelInfo = modelInfo,
        systemPrompt = systemPrompt
    )
}
