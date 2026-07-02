package com.aichallenge.mcpserver.plantators

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClientResponse(
    @SerialName("Id") val id: Int = 0,
    @SerialName("FirstName") val firstName: String? = null,
    @SerialName("LastName") val lastName: String? = null,
    @SerialName("Patronymic") val patronymic: String? = null,
    @SerialName("RoleId") val roleId: Int = 0,
    @SerialName("RoleName") val roleName: String? = null,
    @SerialName("Login") val login: String? = null,
    @SerialName("UniqueId") val uniqueId: String? = null,
)

@Serializable
data class AuthModel(
    @SerialName("Login") val login: String,
    @SerialName("Password") val password: String,
)

@Serializable
data class ClientInput(
    @SerialName("FirstName") val firstName: String? = null,
    @SerialName("LastName") val lastName: String? = null,
    @SerialName("Patronymic") val patronymic: String? = null,
    @SerialName("Email") val email: String? = null,
    @SerialName("Login") val login: String? = null,
    @SerialName("Password") val password: String? = null,
)

@Serializable
data class PlantResponse(
    @SerialName("Id") val id: Int = 0,
    @SerialName("Name") val name: String? = null,
    @SerialName("Description") val description: String? = null,
    @SerialName("MainLogo") val mainLogo: String? = null,
)

@Serializable
data class ClientPlant(
    @SerialName("Id") val id: Int = 0,
    @SerialName("ClientId") val clientId: Int = 0,
    @SerialName("Name") val name: String,
    @SerialName("Description") val description: String? = null,
    @SerialName("MainLogo") val mainLogo: String? = null,
)

@Serializable
data class ProductResponse(
    @SerialName("Id") val id: Int = 0,
    @SerialName("Name") val name: String? = null,
    @SerialName("ProductTypeId") val productTypeId: Int = 0,
    @SerialName("Description") val description: String? = null,
    @SerialName("IsSelected") val isSelected: Boolean = false,
)

@Serializable
data class ProductOfPlantResponse(
    @SerialName("Id") val id: Int = 0,
    @SerialName("PlantId") val plantId: Int = 0,
    @SerialName("ProductId") val productId: Int = 0,
)

@Serializable
data class Product(
    @SerialName("Id") val id: Int = 0,
    @SerialName("Name") val name: String? = null,
    @SerialName("ProductTypeId") val productTypeId: Int = 0,
    @SerialName("Description") val description: String? = null,
)

@Serializable
data class PhaseResponse(
    @SerialName("Id") val id: Int = 0,
    @SerialName("Name") val name: String? = null,
    @SerialName("MinDayCount") val minDayCount: Int = 0,
    @SerialName("MaxDayCount") val maxDayCount: Int = 0,
)

@Serializable
data class PlantPhaseResponse(
    @SerialName("Id") val id: Int = 0,
    @SerialName("PlantId") val plantId: Int = 0,
    @SerialName("PhaseId") val phaseId: Int = 0,
    @SerialName("StartDate") val startDate: String? = null,
    @SerialName("EndDate") val endDate: String? = null,
    @SerialName("AdditionalDays") val additionalDays: Int = 0,
)

@Serializable
data class ProductDosingResponse(
    @SerialName("Id") val id: Int = 0,
    @SerialName("ProductId") val productId: Int = 0,
    @SerialName("DosingDescription") val dosingDescription: String? = null,
    @SerialName("Day") val day: Int = 0,
    @SerialName("PhaseId") val phaseId: Int = 0,
)

@Serializable
data class AdviceResponse(
    @SerialName("Id") val id: Int = 0,
    @SerialName("Description") val description: String? = null,
)

@Serializable
data class ProductTypeResponse(
    @SerialName("Id") val id: Int = 0,
    @SerialName("Name") val name: String? = null,
)

@Serializable
data class ApiErrorResponse(
    @SerialName("Message") val message: String? = null,
)
