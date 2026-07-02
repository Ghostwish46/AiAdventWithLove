package com.aichallenge.mcpserver

import com.aichallenge.mcpserver.plantators.PlantatorsClient
import com.aichallenge.mcpserver.plantators.PlantatorsSession
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.server.mcp

fun main(args: Array<String>) {
    val port = args.firstOrNull()?.toIntOrNull() ?: 3001
    val session = PlantatorsSession()
    val plantatorsClient = PlantatorsClient(session)
    val mcpServer = createPlantatorsMcpServer(plantatorsClient)

    println("Plantators MCP server starting on http://127.0.0.1:$port/")
    println("Use URL http://127.0.0.1:$port/mcp in app (client auto-fallback to legacy SSE)")
    try {
        embeddedServer(CIO, host = "127.0.0.1", port = port) {
            mcp {
                mcpServer
            }
        }.start(wait = true)
    } catch (e: Exception) {
        val cause = generateSequence(e as Throwable?) { it.cause }.lastOrNull() ?: e
        if (cause is java.net.BindException) {
            System.err.println(
                """
                |
                |Port $port is already in use.
                |
                |Options:
                |  1. Stop the existing process:  lsof -ti:$port | xargs kill
                |  2. Use another port:          ./gradlew :mcpServer:runPlantatorsMcpServer --args="3002"
                |     (then set MCP URL to http://127.0.0.1:3002/mcp in app settings)
                |
                """.trimMargin()
            )
        }
        throw e
    }
}
