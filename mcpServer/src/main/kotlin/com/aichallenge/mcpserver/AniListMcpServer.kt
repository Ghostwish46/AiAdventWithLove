package com.aichallenge.mcpserver

import com.aichallenge.mcpserver.anilist.AniListClient
import com.aichallenge.mcpserver.anilist.toToolText
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

fun createAniListMcpServer(aniListClient: AniListClient): Server {
    return Server(
        serverInfo = Implementation(
            name = "anilist-mcp-server",
            version = "1.0.0",
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false),
            ),
        ),
    ) {
        addTool(
            name = "search_anime",
            description = "Search anime by title using AniList GraphQL API. Returns id, titles, format, episodes, score, genres.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put(
                        "search",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Anime title or keyword to search for")
                        },
                    )
                    put(
                        "perPage",
                        buildJsonObject {
                            put("type", "integer")
                            put("description", "Number of results (1-10, default 5)")
                            put("minimum", 1)
                            put("maximum", 10)
                        },
                    )
                },
                required = listOf("search"),
            ),
        ) { request ->
            val args: JsonObject = request.params.arguments ?: buildJsonObject {}
            val search = args["search"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (search.isEmpty()) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("Error: 'search' parameter is required")),
                    isError = true,
                )
            }
            val perPage = args["perPage"]?.jsonPrimitive?.int?.coerceIn(1, 10) ?: 5
            val results = aniListClient.searchAnime(search = search, perPage = perPage)
            CallToolResult(content = listOf(TextContent(results.toToolText())))
        }
    }
}
