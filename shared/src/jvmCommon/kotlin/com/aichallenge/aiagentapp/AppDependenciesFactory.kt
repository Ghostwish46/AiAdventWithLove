package com.aichallenge.aiagentapp

import com.aichallenge.aiagentapp.data.ConversationRepository
import com.aichallenge.aiagentapp.data.DeepSeekRepository
import com.aichallenge.aiagentapp.data.ModelInfo
import com.aichallenge.aiagentapp.data.createRouterAiApi

fun createAppDependencies(
    modelInfo: ModelInfo = defaultModelInfo(),
    systemPrompt: String = DEFAULT_SYSTEM_PROMPT
): AppDependencies {
    val api = createRouterAiApi()
    return AppDependencies(
        conversationRepository = ConversationRepository(),
        deepSeekRepository = DeepSeekRepository(api),
        modelInfo = modelInfo,
        systemPrompt = systemPrompt
    )
}
