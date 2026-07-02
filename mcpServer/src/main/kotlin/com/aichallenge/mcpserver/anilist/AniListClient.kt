package com.aichallenge.mcpserver.anilist

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AniListClient(
    private val httpClient: HttpClient = HttpClient(CIO),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val endpoint = "https://graphql.anilist.co"

    private val searchQuery = """
        query (${'$'}search: String!, ${'$'}perPage: Int) {
          Page(page: 1, perPage: ${'$'}perPage) {
            media(search: ${'$'}search, type: ANIME, sort: POPULARITY_DESC) {
              id
              title { romaji english native }
              format
              episodes
              averageScore
              genres
            }
          }
        }
    """.trimIndent()

    suspend fun searchAnime(search: String, perPage: Int): List<AnimeMedia> {
        val payload = json.encodeToString(
            GraphQlRequest(
                query = searchQuery,
                variables = SearchVariables(search = search, perPage = perPage),
            )
        )
        val response = httpClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }.body<String>()

        val parsed = json.decodeFromString<GraphQlResponse>(response)
        parsed.errors?.firstOrNull()?.let { error ->
            error("AniList API error: ${error.message}")
        }

        return parsed.data?.page?.media.orEmpty()
    }

    fun close() {
        httpClient.close()
    }
}

@Serializable
private data class GraphQlRequest(
    val query: String,
    val variables: SearchVariables,
)

@Serializable
private data class SearchVariables(
    val search: String,
    val perPage: Int,
)

@Serializable
private data class GraphQlResponse(
    val data: PageData? = null,
    val errors: List<GraphQlError>? = null,
)

@Serializable
private data class GraphQlError(
    val message: String? = null,
)

@Serializable
private data class PageData(
    val page: Page? = null,
)

@Serializable
private data class Page(
    val media: List<AnimeMedia>? = null,
)

@Serializable
data class AnimeMedia(
    val id: Int,
    val title: AnimeTitle? = null,
    val format: String? = null,
    val episodes: Int? = null,
    @SerialName("averageScore") val averageScore: Int? = null,
    val genres: List<String>? = null,
)

@Serializable
data class AnimeTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
)

fun AnimeMedia.displayTitle(): String {
    val t = title
    return t?.english?.takeIf { it.isNotBlank() }
        ?: t?.romaji?.takeIf { it.isNotBlank() }
        ?: t?.native?.takeIf { it.isNotBlank() }
        ?: "Unknown"
}

fun List<AnimeMedia>.toToolText(): String {
    if (isEmpty()) return "No anime found."
    return joinToString(separator = "\n\n") { media ->
        buildString {
            append("ID: ${media.id}")
            append("\nTitle: ${media.displayTitle()}")
            media.format?.let { append("\nFormat: $it") }
            media.episodes?.let { append("\nEpisodes: $it") }
            media.averageScore?.let { append("\nScore: $it/100") }
            media.genres?.takeIf { it.isNotEmpty() }?.let { append("\nGenres: ${it.joinToString(", ")}") }
        }
    }
}
