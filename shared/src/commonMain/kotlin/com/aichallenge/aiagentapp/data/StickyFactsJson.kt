package com.aichallenge.aiagentapp.data

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val factsJson = Json { ignoreUnknownKeys = true }
private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

fun parseStickyFactsJson(json: String?): Map<String, String> {
    if (json.isNullOrBlank()) return emptyMap()
    return try {
        factsJson.decodeFromString(mapSerializer, json)
    } catch (_: Exception) {
        emptyMap()
    }
}

fun encodeStickyFactsJson(map: Map<String, String>): String =
    factsJson.encodeToString(mapSerializer, map)
