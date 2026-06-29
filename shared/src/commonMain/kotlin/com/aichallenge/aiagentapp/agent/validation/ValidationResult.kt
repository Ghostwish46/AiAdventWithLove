package com.aichallenge.aiagentapp.agent.validation

sealed class ValidationResult {
    data class Pass(val response: String) : ValidationResult()
    data class Fail(val violations: List<String>) : ValidationResult()
}
