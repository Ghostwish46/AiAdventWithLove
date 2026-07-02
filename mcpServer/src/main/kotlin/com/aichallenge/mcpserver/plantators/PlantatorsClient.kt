package com.aichallenge.mcpserver.plantators

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

/**
 * HTTP client aligned with plantators_assistant Retrofit interfaces:
 * - Auth via header `token` (value = ClientResponse.UniqueId)
 * - Lowercase resource paths where used in the mobile app (plants/, products/, phases/)
 */
class PlantatorsClient(
    private val session: PlantatorsSession,
    private val httpClient: HttpClient = createHttpClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    },
) {
    private val baseUrl = "http://plantators-a.1gb.ru/api"

    // --- Clients ---

    suspend fun createGuest(): ClientResponse {
        val response = postJson("$baseUrl/createNewGuest", body = emptyMap<String, String>(), auth = false)
        return decodeSuccess(response)
    }

    suspend fun loginClient(login: String, password: String): ClientResponse {
        val response = postJson(
            "$baseUrl/loginClient",
            body = AuthModel(login = login, password = password),
            auth = false,
        )
        return decodeSuccess(response)
    }

    suspend fun getClientInfo(): ClientResponse {
        requireAuth()
        val response = getWithAuth("$baseUrl/getClientInfo")
        return decodeSuccess(response)
    }

    suspend fun editClient(input: ClientInput): ClientResponse {
        requireAuth()
        val response = putJson("$baseUrl/editClient", body = input)
        return decodeSuccess(response)
    }

    // --- Plants ---

    suspend fun getPlants(): List<PlantResponse> {
        requireAuth()
        val response = getWithAuth("$baseUrl/plants/?")
        return decodeSuccess(response)
    }

    suspend fun createPlant(plant: ClientPlant): PlantResponse {
        requireAuth()
        val body = plant.copy(clientId = plant.clientId.takeIf { it > 0 } ?: session.clientId ?: 0)
        val response = postJson("$baseUrl/plants/?", body = body)
        return decodeSuccess(response)
    }

    suspend fun updatePlant(id: Int, plant: ClientPlant): PlantResponse {
        requireAuth()
        val response = putJson("$baseUrl/plants/$id", body = plant)
        return decodeSuccess(response)
    }

    suspend fun deletePlant(id: Int): PlantResponse {
        requireAuth()
        val response = deleteWithAuth("$baseUrl/plants/$id")
        return decodeSuccess(response)
    }

    // --- Products ---

    suspend fun getProducts(): List<ProductResponse> {
        val response = getOptionalAuth("$baseUrl/products/?")
        return decodeSuccess(response)
    }

    suspend fun getProductsByPlant(plantId: Int): List<ProductOfPlantResponse> {
        requireAuth()
        val response = getWithAuth("$baseUrl/products/?plantId=$plantId")
        return decodeSuccess(response)
    }

    suspend fun getProductsByPlantAndType(plantId: Int, typeId: Int): List<ProductResponse> {
        val response = getOptionalAuth("$baseUrl/products/?plantId=$plantId&typeId=$typeId")
        return decodeSuccess(response)
    }

    suspend fun getProduct(id: Int): ProductResponse {
        val response = getOptionalAuth("$baseUrl/products/$id")
        return decodeSuccess(response)
    }

    suspend fun addProductsToPlant(plantId: Int, products: List<Product>): List<ProductOfPlantResponse> {
        requireAuth()
        val response = postJson("$baseUrl/products/?plantId=$plantId", body = products)
        return decodeSuccess(response)
    }

    // --- Phases ---

    suspend fun getPhases(): List<PhaseResponse> {
        val response = getOptionalAuth("$baseUrl/phases/?")
        return decodeSuccess(response)
    }

    suspend fun getPlantPhases(plantId: Int): List<PlantPhaseResponse> {
        requireAuth()
        val response = getWithAuth("$baseUrl/phases/?plantId=$plantId")
        return decodeSuccess(response)
    }

    // --- ProductDosings ---

    suspend fun getProductDayDosing(plantId: Int): List<ProductDosingResponse> {
        requireAuth()
        val response = getWithAuth("$baseUrl/ProductDayDosing/?plantId=$plantId")
        return decodeSuccess(response)
    }

    suspend fun getProductDosings(productId: Int, phaseId: Int): List<ProductDosingResponse> {
        val response = getOptionalAuth("$baseUrl/ProductDosings?productId=$productId&phaseId=$phaseId")
        return decodeSuccess(response)
    }

    suspend fun getProductDosing(id: Int): ProductDosingResponse {
        val response = getOptionalAuth("$baseUrl/ProductDosings/$id")
        return decodeSuccess(response)
    }

    suspend fun generateDosingForPlant(
        plantId: Int,
        phaseId: Int,
        currentDay: Int,
        products: List<Product>,
    ): List<PlantPhaseResponse> {
        requireAuth()
        val response = postJson(
            "$baseUrl/GenerateDosingForPlant/?plantId=$plantId&phaseId=$phaseId&currentDay=$currentDay",
            body = products,
        )
        return decodeSuccess(response)
    }

    suspend fun addDaysForPhase(plantPhaseId: Int, additionalDays: Int): List<PlantPhaseResponse> {
        requireAuth()
        val response = postJson(
            "$baseUrl/addDaysForPhase/?plantPhaseId=$plantPhaseId&additionalDays=$additionalDays",
            body = emptyMap<String, String>(),
        )
        return decodeSuccess(response)
    }

    suspend fun finishPhaseForPlant(plantPhaseId: Int): List<PlantPhaseResponse> {
        requireAuth()
        val response = postJson(
            "$baseUrl/finishPhaseForPlant/?plantPhaseId=$plantPhaseId",
            body = emptyMap<String, String>(),
        )
        return decodeSuccess(response)
    }

    // --- Advices & ProductTypes ---

    suspend fun getAdvices(): List<AdviceResponse> {
        val response = getOptionalAuth("$baseUrl/Advices")
        return decodeSuccess(response)
    }

    suspend fun getRandomAdvice(): AdviceResponse {
        val response = getOptionalAuth("$baseUrl/getRandomAdvice/?")
        return decodeSuccess(response)
    }

    suspend fun getProductTypes(): List<ProductTypeResponse> {
        val response = getOptionalAuth("$baseUrl/ProductTypes")
        return decodeSuccess(response)
    }

    suspend fun createGuestAndStoreSession(): ClientResponse {
        val client = createGuest()
        session.updateFromClient(client)
        return client
    }

    suspend fun loginAndStoreSession(login: String, password: String): ClientResponse {
        val client = loginClient(login, password)
        session.updateFromClient(client)
        return client
    }

    fun close() {
        httpClient.close()
    }

    private fun requireAuth() {
        if (!session.isAuthenticated) {
            throw PlantatorsAuthException("Not authenticated. Call create_guest or login_client first.")
        }
    }

    private suspend fun getWithAuth(url: String): HttpResponse =
        httpClient.get(url) { applyAuthHeaders() }

    private suspend fun getOptionalAuth(url: String): HttpResponse =
        httpClient.get(url) { applyAuthHeadersIfPresent() }

    private suspend fun deleteWithAuth(url: String): HttpResponse =
        httpClient.delete(url) { applyAuthHeaders() }

    private suspend inline fun <reified T> postJson(url: String, body: T, auth: Boolean = true): HttpResponse =
        httpClient.post(url) {
            if (auth) applyAuthHeadersIfPresent() else if (session.isAuthenticated) applyAuthHeaders()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(body))
        }

    private suspend inline fun <reified T> putJson(url: String, body: T): HttpResponse =
        httpClient.put(url) {
            applyAuthHeaders()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(body))
        }

    private fun io.ktor.client.request.HttpRequestBuilder.applyAuthHeaders() {
        session.authHeaders().forEach { (name, value) -> header(name, value) }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyAuthHeadersIfPresent() {
        if (session.isAuthenticated) applyAuthHeaders()
    }

    private suspend inline fun <reified T> decodeSuccess(response: HttpResponse): T {
        val bodyText = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val apiMessage = runCatching {
                json.decodeFromString<ApiErrorResponse>(bodyText).message
            }.getOrNull()
            error(apiMessage ?: "HTTP ${response.status.value}: $bodyText")
        }
        return json.decodeFromString(bodyText)
    }

    companion object {
        fun createHttpClient(): HttpClient = HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 60_000
            }
        }
    }
}
