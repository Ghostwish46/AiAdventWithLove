package com.aichallenge.mcpserver.plantators

class PlantatorsSession {
    var clientId: Int? = null
        private set
    /** Auth token from ClientResponse.UniqueId (sent as HTTP header `token`). */
    var token: String? = null
        private set

    val isAuthenticated: Boolean
        get() = !token.isNullOrBlank()

    fun updateFromClient(client: ClientResponse) {
        clientId = client.id
        token = client.uniqueId
    }

    fun clear() {
        clientId = null
        token = null
    }

    fun authHeaders(): Map<String, String> {
        val value = token ?: return emptyMap()
        val headerName = System.getenv("PLANTATORS_AUTH_HEADER")?.takeIf { it.isNotBlank() }
            ?: AUTH_HEADER_NAME
        return mapOf(headerName to value)
    }

    companion object {
        const val AUTH_HEADER_NAME = "token"
    }
}

class PlantatorsAuthException(message: String) : Exception(message)
