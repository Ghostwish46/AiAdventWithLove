package com.aichallenge.mcpserver.plantators

import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val session = PlantatorsSession()
    val client = PlantatorsClient(session)
    try {
        println("=== get_phases (public) ===")
        val phases = client.getPhases()
        println("Phases count: ${phases.size}, first: ${phases.firstOrNull()?.name}")

        println("=== get_product_types (public) ===")
        val types = client.getProductTypes()
        println("Product types: ${types.joinToString { it.name.orEmpty() }}")

        println("=== create_guest ===")
        val guest = client.createGuestAndStoreSession()
        println("Guest id=${guest.id}, token=${guest.uniqueId}, authenticated=${session.isAuthenticated}")

        println("=== get_client_info (authenticated) ===")
        runCatching { client.getClientInfo() }
            .onSuccess { println(encodeResult(it)) }
            .onFailure { println("Expected if API auth header differs: ${it.message}") }

        println("Smoke test completed.")
    } finally {
        client.close()
    }
}
