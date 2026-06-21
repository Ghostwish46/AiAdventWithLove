package com.aichallenge.aiagentapp.data

// --- Model catalogue ---

data class ModelInfo(
    val id: String,
    val label: String,
    val tier: String,
    val inputPricePerM: Double,
    val outputPricePerM: Double,
    val contextLength: Int? = null
)

val AVAILABLE_MODELS = listOf(
    ModelInfo("liquid/lfm-2-24b-a2b", "LFM2 2B", "Слабая", 3.0, 12.0),
    ModelInfo("qwen/qwen3.5-flash-02-23", "Qwen Flash", "Быстрая", 10.0, 40.0),
    ModelInfo("qwen/qwen3.5-27b", "Qwen3.5 27B", "Средняя", 30.0, 241.0),
    ModelInfo("openai/gpt-5.2", "GPT-5.2", "Сильная", 175.0, 1406.0)
)

data class ChatMessage(
    val role: String,
    val content: String
)

data class Usage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0
)
