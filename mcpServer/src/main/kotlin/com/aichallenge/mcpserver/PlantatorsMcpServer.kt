package com.aichallenge.mcpserver

import com.aichallenge.mcpserver.plantators.ClientInput
import com.aichallenge.mcpserver.plantators.encodeResult
import com.aichallenge.mcpserver.plantators.ClientPlant
import com.aichallenge.mcpserver.plantators.PlantatorsClient
import com.aichallenge.mcpserver.plantators.intProp
import com.aichallenge.mcpserver.plantators.parseProductsJson
import com.aichallenge.mcpserver.plantators.requireInt
import com.aichallenge.mcpserver.plantators.requireString
import com.aichallenge.mcpserver.plantators.runPlantatorsTool
import com.aichallenge.mcpserver.plantators.schema
import com.aichallenge.mcpserver.plantators.stringArg
import com.aichallenge.mcpserver.plantators.stringProp
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

fun createPlantatorsMcpServer(client: PlantatorsClient): Server {
    return Server(
        serverInfo = Implementation(
            name = "plantators-mcp-server",
            version = "1.0.0",
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false),
            ),
        ),
    ) {
        val slowApiNote = " Note: Plantators API may respond slowly (up to ~60s)."

        // --- Clients ---

        addTool(
            name = "create_guest",
            description = "Create a guest client session via POST api/createNewGuest. Stores token (UniqueId) for subsequent authenticated calls.$slowApiNote",
            inputSchema = schema(),
        ) { _ ->
            runPlantatorsTool { encodeResult(client.createGuestAndStoreSession()) }
        }

        addTool(
            name = "login_client",
            description = "Login with credentials via POST api/loginClient. Stores session for authenticated calls.$slowApiNote",
            inputSchema = schema(
                "login" to stringProp("Client login"),
                "password" to stringProp("Client password"),
                requiredNames = listOf("login", "password"),
            ),
        ) { request ->
            val args: JsonObject = request.params.arguments ?: buildJsonObject {}
            runPlantatorsTool {
                encodeResult(
                    client.loginAndStoreSession(
                        login = args.requireString("login"),
                        password = args.requireString("password"),
                    ),
                )
            }
        }

        addTool(
            name = "get_client_info",
            description = "Get current client profile via GET api/getClientInfo. Requires create_guest or login_client.$slowApiNote",
            inputSchema = schema(),
        ) { _ ->
            runPlantatorsTool { encodeResult(client.getClientInfo()) }
        }

        addTool(
            name = "edit_client",
            description = "Update client profile via PUT api/editClient. Requires authentication.$slowApiNote",
            inputSchema = schema(
                "first_name" to stringProp("First name"),
                "last_name" to stringProp("Last name"),
                "patronymic" to stringProp("Patronymic"),
                "email" to stringProp("Email"),
                "login" to stringProp("Login"),
                "password" to stringProp("Password"),
            ),
        ) { request ->
            val args: JsonObject = request.params.arguments ?: buildJsonObject {}
            runPlantatorsTool {
                encodeResult(
                    client.editClient(
                        ClientInput(
                            firstName = args.stringArg("first_name"),
                            lastName = args.stringArg("last_name"),
                            patronymic = args.stringArg("patronymic"),
                            email = args.stringArg("email"),
                            login = args.stringArg("login"),
                            password = args.stringArg("password"),
                        ),
                    ),
                )
            }
        }

        // --- Plants ---

        addTool(
            name = "get_plants",
            description = "List all plants for the current client via GET api/Plants. Requires authentication.$slowApiNote",
            inputSchema = schema(),
        ) { _ ->
            runPlantatorsTool { encodeResult(client.getPlants()) }
        }

        addTool(
            name = "create_plant",
            description = "Create a new plant via POST api/Plants. Requires authentication.$slowApiNote",
            inputSchema = schema(
                "name" to stringProp("Plant name"),
                "description" to stringProp("Plant description"),
                "main_logo" to stringProp("Main logo URL or path"),
                requiredNames = listOf("name"),
            ),
        ) { request ->
            val args: JsonObject = request.params.arguments ?: buildJsonObject {}
            runPlantatorsTool {
                encodeResult(
                    client.createPlant(
                        ClientPlant(
                            name = args.requireString("name"),
                            description = args.stringArg("description"),
                            mainLogo = args.stringArg("main_logo"),
                        ),
                    ),
                )
            }
        }

        addTool(
            name = "update_plant",
            description = "Update an existing plant via PUT api/Plants/{id}. Requires authentication.$slowApiNote",
            inputSchema = schema(
                "id" to intProp("Plant ID"),
                "name" to stringProp("Plant name"),
                "description" to stringProp("Plant description"),
                "main_logo" to stringProp("Main logo URL or path"),
                requiredNames = listOf("id", "name"),
            ),
        ) { request ->
            val args: JsonObject = request.params.arguments ?: buildJsonObject {}
            val id = args.requireInt("id")
            runPlantatorsTool {
                encodeResult(
                    client.updatePlant(
                        id = id,
                        plant = ClientPlant(
                            id = id,
                            name = args.requireString("name"),
                            description = args.stringArg("description"),
                            mainLogo = args.stringArg("main_logo"),
                        ),
                    ),
                )
            }
        }

        addTool(
            name = "delete_plant",
            description = "Delete a plant via DELETE api/Plants/{id}. Requires authentication.$slowApiNote",
            inputSchema = schema(
                "id" to intProp("Plant ID"),
                requiredNames = listOf("id"),
            ),
        ) { request ->
            val args: JsonObject = request.params.arguments ?: buildJsonObject {}
            runPlantatorsTool { encodeResult(client.deletePlant(args.requireInt("id"))) }
        }

        // --- Products ---

        addTool(
            name = "get_products_by_plant",
            description = "Get products linked to a plant via GET api/Products?plantId=. Requires authentication.$slowApiNote",
            inputSchema = schema(
                "plant_id" to intProp("Plant ID"),
                requiredNames = listOf("plant_id"),
            ),
        ) { request ->
            val args: JsonObject = request.params.arguments ?: buildJsonObject {}
            runPlantatorsTool { encodeResult(client.getProductsByPlant(args.requireInt("plant_id"))) }
        }

        addTool(
            name = "get_products_by_plant_and_type",
            description = "Get products filtered by plant and type via GET api/Products?plantId=&typeId=.$slowApiNote",
            inputSchema = schema(
                "plant_id" to intProp("Plant ID"),
                "type_id" to intProp("Product type ID"),
                requiredNames = listOf("plant_id", "type_id"),
            ),
        ) { request ->
            val args: JsonObject = request.params.arguments ?: buildJsonObject {}
            runPlantatorsTool {
                encodeResult(
                    client.getProductsByPlantAndType(
                        plantId = args.requireInt("plant_id"),
                        typeId = args.requireInt("type_id"),
                    ),
                )
            }
        }

        addTool(
            name = "get_product",
            description = "Get a single product by ID via GET api/Products/{id}.$slowApiNote",
            inputSchema = schema(
                "id" to intProp("Product ID"),
                requiredNames = listOf("id"),
            ),
        ) { request ->
            val args: JsonObject = request.params.arguments ?: buildJsonObject {}
            runPlantatorsTool { encodeResult(client.getProduct(args.requireInt("id"))) }
        }

        addTool(
            name = "add_products_to_plant",
            description = "Add products to a plant via POST api/Products?plantId=. Requires authentication. Pass products as JSON array.$slowApiNote",
            inputSchema = schema(
                "plant_id" to intProp("Plant ID"),
                "products_json" to stringProp("JSON array of Product objects, e.g. [{\"Id\":1,\"ProductTypeId\":1}]"),
                requiredNames = listOf("plant_id", "products_json"),
            ),
        ) { request ->
            val args: JsonObject = request.params.arguments ?: buildJsonObject {}
            runPlantatorsTool {
                encodeResult(
                    client.addProductsToPlant(
                        plantId = args.requireInt("plant_id"),
                        products = client.parseProductsJson(args.requireString("products_json")),
                    ),
                )
            }
        }

        // --- Phases ---

        addTool(
            name = "get_phases",
            description = "List all growth phases via GET api/Phases.$slowApiNote",
            inputSchema = schema(),
        ) { _ ->
            runPlantatorsTool { encodeResult(client.getPhases()) }
        }

        addTool(
            name = "get_plant_phases",
            description = "Get phases for a specific plant via GET api/Phases?plantId=. Requires authentication.$slowApiNote",
            inputSchema = schema(
                "plant_id" to intProp("Plant ID"),
                requiredNames = listOf("plant_id"),
            ),
        ) { request ->
            val args: JsonObject = request.params.arguments ?: buildJsonObject {}
            runPlantatorsTool { encodeResult(client.getPlantPhases(args.requireInt("plant_id"))) }
        }

        // --- ProductDosings ---

        addTool(
            name = "get_product_day_dosing",
            description = "Get today's product dosing for a plant via GET api/ProductDayDosing?plantId=. Requires authentication.$slowApiNote",
            inputSchema = schema(
                "plant_id" to intProp("Plant ID"),
                requiredNames = listOf("plant_id"),
            ),
        ) { request ->
            val args: JsonObject = request.params.arguments ?: buildJsonObject {}
            runPlantatorsTool { encodeResult(client.getProductDayDosing(args.requireInt("plant_id"))) }
        }

        addTool(
            name = "get_product_dosings",
            description = "Get product dosings by product and phase via GET api/ProductDosings?productId=&phaseId=.$slowApiNote",
            inputSchema = schema(
                "product_id" to intProp("Product ID"),
                "phase_id" to intProp("Phase ID"),
                requiredNames = listOf("product_id", "phase_id"),
            ),
        ) { request ->
            val args: JsonObject = request.params.arguments ?: buildJsonObject {}
            runPlantatorsTool {
                encodeResult(
                    client.getProductDosings(
                        productId = args.requireInt("product_id"),
                        phaseId = args.requireInt("phase_id"),
                    ),
                )
            }
        }

        addTool(
            name = "get_product_dosing",
            description = "Get a single product dosing by ID via GET api/ProductDosings/{id}.$slowApiNote",
            inputSchema = schema(
                "id" to intProp("Product dosing ID"),
                requiredNames = listOf("id"),
            ),
        ) { request ->
            val args: JsonObject = request.params.arguments ?: buildJsonObject {}
            runPlantatorsTool { encodeResult(client.getProductDosing(args.requireInt("id"))) }
        }

        addTool(
            name = "generate_dosing_for_plant",
            description = "Generate dosing schedule for a plant via POST api/GenerateDosingForPlant. Requires authentication.$slowApiNote",
            inputSchema = schema(
                "plant_id" to intProp("Plant ID"),
                "phase_id" to intProp("Phase ID"),
                "current_day" to intProp("Current day number"),
                "products_json" to stringProp("JSON array of Product objects"),
                requiredNames = listOf("plant_id", "phase_id", "current_day", "products_json"),
            ),
        ) { request ->
            val args: JsonObject = request.params.arguments ?: buildJsonObject {}
            runPlantatorsTool {
                encodeResult(
                    client.generateDosingForPlant(
                        plantId = args.requireInt("plant_id"),
                        phaseId = args.requireInt("phase_id"),
                        currentDay = args.requireInt("current_day"),
                        products = client.parseProductsJson(args.requireString("products_json")),
                    ),
                )
            }
        }

        addTool(
            name = "add_days_for_phase",
            description = "Add extra days to a plant phase via POST api/addDaysForPhase. Requires authentication.$slowApiNote",
            inputSchema = schema(
                "plant_phase_id" to intProp("Plant phase ID"),
                "additional_days" to intProp("Number of additional days"),
                requiredNames = listOf("plant_phase_id", "additional_days"),
            ),
        ) { request ->
            val args: JsonObject = request.params.arguments ?: buildJsonObject {}
            runPlantatorsTool {
                encodeResult(
                    client.addDaysForPhase(
                        plantPhaseId = args.requireInt("plant_phase_id"),
                        additionalDays = args.requireInt("additional_days"),
                    ),
                )
            }
        }

        addTool(
            name = "finish_phase_for_plant",
            description = "Finish current phase for a plant via POST api/finishPhaseForPlant. Requires authentication.$slowApiNote",
            inputSchema = schema(
                "plant_phase_id" to intProp("Plant phase ID"),
                requiredNames = listOf("plant_phase_id"),
            ),
        ) { request ->
            val args: JsonObject = request.params.arguments ?: buildJsonObject {}
            runPlantatorsTool {
                encodeResult(client.finishPhaseForPlant(args.requireInt("plant_phase_id")))
            }
        }

        // --- Advices & ProductTypes ---

        addTool(
            name = "get_advices",
            description = "List all growing advices via GET api/Advices.$slowApiNote",
            inputSchema = schema(),
        ) { _ ->
            runPlantatorsTool { encodeResult(client.getAdvices()) }
        }

        addTool(
            name = "get_random_advice",
            description = "Get a random growing advice via GET api/GetRandomAdvice.$slowApiNote",
            inputSchema = schema(),
        ) { _ ->
            runPlantatorsTool { encodeResult(client.getRandomAdvice()) }
        }

        addTool(
            name = "get_product_types",
            description = "List product types (fertilizer, stimulator, etc.) via GET api/ProductTypes.$slowApiNote",
            inputSchema = schema(),
        ) { _ ->
            runPlantatorsTool { encodeResult(client.getProductTypes()) }
        }
    }
}
