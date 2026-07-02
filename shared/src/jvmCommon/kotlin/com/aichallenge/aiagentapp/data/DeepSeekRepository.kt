package com.aichallenge.aiagentapp.data

import com.aichallenge.aiagentapp.agent.task.TaskState
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.FieldNamingPolicy
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.IOException

data class ErrorBody(
    @SerializedName("error") val error: ErrorDetail? = null
)

data class ErrorDetail(
    @SerializedName("message") val message: String? = null,
    @SerializedName("type") val type: String? = null
)

class DeepSeekRepository(private val routerAiApi: DeepSeekApi) : LlmClient {

    override suspend fun sendMessages(
        messages: List<ChatMessage>,
        modelId: String
    ): Result<AgentTurnResult> = withContext(Dispatchers.IO) {
        if (messages.isEmpty()) return@withContext Result.failure(IllegalArgumentException("No messages"))

        val request = DeepSeekRequest(
            model = modelId,
            messages = messages.toApiChatMessages(),
            stream = false
        )

        val startMs = System.currentTimeMillis()
        try {
            val response = routerAiApi.createChatCompletion(request)
            val elapsedMs = System.currentTimeMillis() - startMs

            if (response.isSuccessful) {
                val body = response.body()
                val content = body?.choices?.firstOrNull()?.message?.content
                if (content != null) {
                    val usage = body?.usage?.toUsage()?.takeIf { it.isMeaningful() }
                        ?: estimateUsage(messages, content)
                    Result.success(AgentTurnResult(content, usage, elapsedMs))
                } else {
                    Result.failure(Exception("Empty response from API"))
                }
            } else {
                val errorMsg = response.errorBody()?.string()?.let { raw ->
                    try {
                        Gson().fromJson(raw, ErrorBody::class.java)?.error?.message ?: raw
                    } catch (_: Exception) {
                        raw
                    }
                } ?: "Error: ${response.code()} ${response.message()}"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: IOException) {
            Result.failure(Exception("Network error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendMessagesWithTools(
        messages: List<ToolChatMessage>,
        tools: List<LlmToolDefinition>,
        modelId: String,
        toolChoice: String,
    ): Result<LlmToolResponse> = withContext(Dispatchers.IO) {
        if (messages.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No messages"))
        }
        if (tools.isEmpty() && toolChoice != "none") {
            return@withContext Result.failure(IllegalArgumentException("No tools"))
        }

        val request = DeepSeekRequest(
            model = modelId,
            messages = messages.toApiToolChatMessages(),
            stream = false,
            tools = tools.takeIf { it.isNotEmpty() }?.toApiToolDefinitions(),
            toolChoice = toolChoice,
        )

        val startMs = System.currentTimeMillis()
        try {
            val response = routerAiApi.createChatCompletion(request)
            val elapsedMs = System.currentTimeMillis() - startMs
            if (!response.isSuccessful) {
                val errorMsg = response.errorBody()?.string()?.let { raw ->
                    try {
                        Gson().fromJson(raw, ErrorBody::class.java)?.error?.message ?: raw
                    } catch (_: Exception) {
                        raw
                    }
                } ?: "Error: ${response.code()} ${response.message()}"
                return@withContext Result.failure(Exception(errorMsg))
            }

            val body = response.body()
            val choice = body?.choices?.firstOrNull()
            val message = choice?.message
                ?: return@withContext Result.failure(Exception("Empty response from API"))

            val (content, toolCalls) = message.toLlmToolResponseMessage()
            val usage = body.usage?.toUsage()?.takeIf { it.isMeaningful() }
            Result.success(
                LlmToolResponse(
                    content = content,
                    toolCalls = toolCalls,
                    usage = usage,
                    elapsedMs = elapsedMs,
                    finishReason = choice.finishReason,
                )
            )
        } catch (e: IOException) {
            Result.failure(Exception("Network error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun mergeStickyFacts(
        existingFacts: Map<String, String>,
        newUserMessage: String,
        recentContext: List<ChatMessage>,
        modelId: String
    ): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        val gsonLocal = Gson()
        val mapType = object : TypeToken<Map<String, String>>() {}.type
        val contextLines = recentContext.takeLast(6).joinToString("\n") { m ->
            val label = if (m.role == "user") "П" else "А"
            "$label: ${m.content.take(600)}"
        }
        val system = (
            "Извлекай и обновляй факты диалога: цели, ограничения, предпочтения, решения, договорённости, важные имена и даты, кодовые слова. " +
                "На вход — текущие факты JSON, последние реплики и новое сообщение пользователя. " +
                "Верни ТОЛЬКО один валидный JSON-объект с ключами и строковыми значениями, без markdown, без ```, без пояснений. " +
                "Обнови и дополни; устаревшие ключи удали."
            )
        val userPayload = buildString {
            appendLine("Текущие факты (JSON):")
            appendLine(gsonLocal.toJson(existingFacts))
            appendLine("Последние реплики:")
            appendLine(contextLines.ifBlank { "(нет)" })
            appendLine("Новое сообщение пользователя:")
            appendLine(newUserMessage)
        }
        val request = DeepSeekRequest(
            model = modelId,
            messages = listOf(
                ChatMessage(role = "system", content = system),
                ChatMessage(role = "user", content = userPayload)
            ).toApiChatMessages(),
            stream = false,
            maxTokens = 512,
            temperature = 0.2
        )
        try {
            val response = routerAiApi.createChatCompletion(request)
            if (response.isSuccessful) {
                val raw = response.body()?.choices?.firstOrNull()?.message?.content?.trim()
                    ?: return@withContext Result.failure(Exception("Empty facts response"))
                val parsed = parseFactsJsonResponse(raw, gsonLocal, mapType)
                if (parsed != null) Result.success(parsed)
                else Result.failure(Exception("Failed to parse facts JSON"))
            } else {
                val err = response.errorBody()?.string() ?: response.message()
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun classifyMemoryUpdate(
        existingWorking: Map<String, String>,
        existingLongTerm: Map<String, String>,
        newUserMessage: String,
        recentContext: List<ChatMessage>,
        forceLongTerm: Boolean,
        modelId: String
    ): Result<MemoryClassificationResult> = withContext(Dispatchers.IO) {
        val gsonLocal = Gson()
        val contextLines = recentContext.takeLast(6).joinToString("\n") { m ->
            val label = if (m.role == "user") "П" else "А"
            "$label: ${m.content.take(600)}"
        }
        val system = buildString {
            append(
                "Ты классификатор памяти ассистента. Раздели информацию из сообщения пользователя по слоям:\n" +
                    "- working: цели, ограничения, решения и статус ТЕКУЩЕЙ задачи (только этот чат)\n" +
                    "- longTerm: профиль пользователя, устойчивые предпочтения, знания на будущее (между сессиями)\n" +
                    "- routingLog: массив строк с явным объяснением, куда и почему попала каждая запись\n\n" +
                    "Верни ТОЛЬКО один валидный JSON без markdown:\n" +
                    "{\"working\": {\"ключ\": \"значение\"}, \"longTerm\": {\"ключ\": \"значение\"}, \"routingLog\": [\"ключ → working: причина\"]}\n" +
                    "Если обновлений нет — верни пустые объекты и пустой routingLog."
            )
            if (forceLongTerm) {
                append("\n\nПользователь явно просит запомнить надолго — извлеки в longTerm.")
            }
        }
        val userPayload = buildString {
            appendLine("Текущая рабочая память (JSON):")
            appendLine(gsonLocal.toJson(existingWorking))
            appendLine("Текущая долговременная память (JSON):")
            appendLine(gsonLocal.toJson(existingLongTerm))
            appendLine("Последние реплики:")
            appendLine(contextLines.ifBlank { "(нет)" })
            appendLine("Новое сообщение пользователя:")
            appendLine(newUserMessage)
        }
        val request = DeepSeekRequest(
            model = modelId,
            messages = listOf(
                ChatMessage(role = "system", content = system),
                ChatMessage(role = "user", content = userPayload)
            ).toApiChatMessages(),
            stream = false,
            maxTokens = 768,
            temperature = 0.2
        )
        try {
            val response = routerAiApi.createChatCompletion(request)
            if (response.isSuccessful) {
                val raw = response.body()?.choices?.firstOrNull()?.message?.content?.trim()
                    ?: return@withContext Result.failure(Exception("Empty memory classification response"))
                val parsed = parseMemoryClassificationResponse(raw, gsonLocal)
                if (parsed != null) Result.success(parsed)
                else Result.failure(Exception("Failed to parse memory classification JSON"))
            } else {
                val err = response.errorBody()?.string() ?: response.message()
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun classifyTaskStateUpdate(
        currentState: TaskState,
        newUserMessage: String,
        recentContext: List<ChatMessage>,
        modelId: String
    ): Result<TaskStateClassificationResult> = withContext(Dispatchers.IO) {
        val gsonLocal = Gson()
        val contextLines = recentContext.takeLast(6).joinToString("\n") { m ->
            val label = if (m.role == "user") "П" else "А"
            "$label: ${m.content.take(600)}"
        }
        val system = buildString {
            append(
                "Ты классификатор состояния задачи ассистента. Этапы: planning → execution → validation → done.\n" +
                    "По сообщению пользователя и контексту диалога обнови формализованное состояние задачи.\n\n" +
                    "Верни ТОЛЬКО один валидный JSON без markdown:\n" +
                    "{\n" +
                    "  \"activate\": true/false,\n" +
                    "  \"taskGoal\": \"...\",\n" +
                    "  \"currentStep\": \"...\",\n" +
                    "  \"expectedAction\": \"...\",\n" +
                    "  \"advancePhase\": true/false,\n" +
                    "  \"requestedPhase\": \"PLANNING|EXECUTION|VALIDATION|DONE|null\",\n" +
                    "  \"completedStep\": \"...\",\n" +
                    "  \"openQuestions\": [\"вопрос 1\", \"вопрос 2\"],\n" +
                    "  \"planningFacts\": {\"ключ\": \"значение\"},\n" +
                    "  \"planApproved\": true/false,\n" +
                    "  \"executionResultReady\": true/false,\n" +
                    "  \"validationReported\": true/false\n" +
                    "}\n\n" +
                    "Общие правила:\n" +
                    "- activate=true, если пользователь ставит новую задачу или продолжает текущую\n" +
                    "- requestedPhase — только СЛЕДУЮЩИЙ допустимый этап или откат (execution→planning, validation→execution). Skip запрещён.\n" +
                    "- advancePhase=true эквивалентен requestedPhase=следующий этап\n" +
                    "- currentStep — что делаем сейчас; expectedAction — что ассистент должен сделать в СЛЕДУЮЩЕМ ответе\n" +
                    "- completedStep — один завершённый шаг (если есть), иначе пустая строка\n" +
                    "- advancePhase=true только если этап явно завершён (например «план готов, начинай» → execution)\n\n" +
                    "Когда НЕ активировать FSM (activate=false, requestedPhase=null):\n" +
                    "- простые одношаговые запросы: «приведи пример», «покажи код», «объясни», «что такое»\n" +
                    "- мета-вопросы и уточнения к уже данному ответу: «почему?», «зачем?», «как так?», «что значит?»\n" +
                    "- короткие болтовни и уточнения без явного проекта\n" +
                    "- FSM нужен только для многошаговых задач: «реализуй приложение», «напиши доклад», «сделай MVP с нуля»\n\n" +
                    "PLANNING — особые правила:\n" +
                    "- openQuestions: полный список вопросов БЕЗ ответа. Обновляй каждый ход.\n" +
                    "- При первом planning-ответе ассистент должен задать СРАЗУ НЕСКОЛЬКО вопросов (3–7) одним списком — все они в openQuestions.\n" +
                    "- ЗАПРЕЩЕНО expectedAction «задать следующий/один вопрос» — только «задать все уточняющие вопросы списком» или «напомнить о неотвеченных и принять ответы».\n" +
                    "- Когда пользователь ответил на вопрос — убери его из openQuestions, добавь факт в planningFacts.\n" +
                    "- Пользователь может ответить сразу на несколько вопросов в одном сообщении — убери все закрытые из openQuestions.\n" +
                    "- Если пользователь ответил только на часть — остальные ОСТАЮТСЯ в openQuestions; expectedAction: «напомнить оставшиеся вопросы списком».\n" +
                    "- advancePhase=true на planning ТОЛЬКО если openQuestions пуст ИЛИ пользователь явно просит начать работу/решай сам.\n" +
                    "- Если пользователь пишет «на своё усмотрение»/«пропусти» по теме — зафиксируй в planningFacts и убери связанный вопрос.\n" +
                    "- Можно добавлять новые openQuestions, если ответ пользователя выявил новую неясность.\n" +
                    "- planningFacts: собранные ответы (аудитория, длительность, формат, ограничения и т.д.)\n\n" +
                    "EXECUTION — особые правила:\n" +
                    "- currentStep: что именно создаётся сейчас (черновик, структура, материалы).\n" +
                    "- expectedAction: «выдать/доработать результат по плану», не «уточнить цель».\n" +
                    "- advancePhase=true на execution, когда основной результат готов и пора проверять (→ validation).\n" +
                    "- Не advancePhase, если результат ещё черновой или пользователь просит доработать.\n\n" +
                    "VALIDATION — особые правила:\n" +
                    "- Этап мульти-перспективной проверки готового результата: эксперт, аудитория/слушатель, исполнитель, критик.\n" +
                    "- Ассистент ОБЯЗАН явно отчитаться о проверке: в ответе должен быть блок «Отчёт о проверке» с итогами по перспективам.\n" +
                    "- expectedAction: «отчитаться о проверке как эксперт», «добавить в отчёт оценку аудитории», " +
                    "«завершить отчёт и улучшить результат» — в зависимости от того, что ещё не сделано.\n" +
                    "- currentStep: какая перспектива/аспект проверяется сейчас или «итоговое улучшение».\n" +
                    "- completedStep: завершённая перспектива проверки (например «проверка экспертом», «оценка аудитории»).\n" +
                    "- advancePhase=true на validation ТОЛЬКО после явного отчёта о проверке, проверки с разных ролей И предложенных улучшений, " +
                    "или если пользователь явно доволен («всё ок», «принимаю»).\n" +
                    "- Не advancePhase, если проверка поверхностная или пользователь просит доработать/перепроверить.\n" +
                    "- openQuestions на validation обычно пуст; не возвращайся к planning-уточнениям без запроса.\n\n" +
                    "Преждевременное «завершение» (благодарность пользователя):\n" +
                    "- «Спасибо», «задача выполнена», «всё понял» НЕ означают реальное завершение, если phase ≠ done.\n" +
                    "- На planning/execution/validation: НЕ ставь requestedPhase=DONE, advancePhase=true, completedStep «Завершено».\n" +
                    "- Если пользователь благодарит на planning без ответов на openQuestions — openQuestions остаются, phase=planning.\n" +
                    "- expectedAction при благодарности на planning: «вежливо ответить, но напомнить этап и неотвеченные вопросы / что результат ещё не готов».\n" +
                    "- Реальный done только после validation с отчётом о проверке.\n\n" +
                    "Для болтовни без задачи: activate=false и пустые поля."
            )
        }
        val userPayload = buildString {
            appendLine("Текущее состояние задачи:")
            appendLine("isActive: ${currentState.isActive}")
            appendLine("phase: ${currentState.phase.name}")
            appendLine("taskGoal: ${currentState.taskGoal ?: "(нет)"}")
            appendLine("currentStep: ${currentState.currentStep.ifBlank { "(нет)" }}")
            appendLine("expectedAction: ${currentState.expectedAction.ifBlank { "(нет)" }}")
            appendLine("completedSteps: ${currentState.completedSteps.joinToString("; ").ifBlank { "(нет)" }}")
            appendLine("openQuestions: ${currentState.openQuestions.joinToString("; ").ifBlank { "(нет)" }}")
            appendLine("planningFacts: ${gsonLocal.toJson(currentState.planningFacts)}")
            appendLine("planApproved: ${currentState.planApproved}")
            appendLine("executionResultReady: ${currentState.executionResultReady}")
            appendLine("validationReported: ${currentState.validationReported}")
            appendLine("Допустимые переходы из ${currentState.phase.name}: ${
                com.aichallenge.aiagentapp.agent.task.TaskPhaseTransitions
                    .allowedTargets(currentState.phase).joinToString { it.name }
            }")
            appendLine("Последние реплики:")
            appendLine(contextLines.ifBlank { "(нет)" })
            appendLine("Новое сообщение пользователя:")
            appendLine(newUserMessage)
        }
        val request = DeepSeekRequest(
            model = modelId,
            messages = listOf(
                ChatMessage(role = "system", content = system),
                ChatMessage(role = "user", content = userPayload)
            ).toApiChatMessages(),
            stream = false,
            maxTokens = 768,
            temperature = 0.2
        )
        try {
            val response = routerAiApi.createChatCompletion(request)
            if (response.isSuccessful) {
                val raw = response.body()?.choices?.firstOrNull()?.message?.content?.trim()
                    ?: return@withContext Result.failure(Exception("Empty task state classification response"))
                val parsed = parseTaskStateClassificationResponse(raw, gsonLocal)
                if (parsed != null) Result.success(parsed)
                else Result.failure(Exception("Failed to parse task state classification JSON"))
            } else {
                val err = response.errorBody()?.string() ?: response.message()
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseTaskStateClassificationResponse(raw: String, gson: Gson): TaskStateClassificationResult? {
        var t = raw.trim()
        if (t.startsWith("```")) {
            t = t.removePrefix("```json").removePrefix("```JSON").removePrefix("```").trim()
            val endFence = t.lastIndexOf("```")
            if (endFence >= 0) t = t.substring(0, endFence).trim()
        }
        return try {
            val dto = gson.fromJson(t, TaskStateClassificationDto::class.java) ?: return null
            TaskStateClassificationResult(
                activate = dto.activate ?: false,
                taskGoal = dto.taskGoal,
                currentStep = dto.currentStep ?: "",
                expectedAction = dto.expectedAction ?: "",
                advancePhase = dto.advancePhase ?: false,
                requestedPhase = dto.requestedPhase,
                completedStep = dto.completedStep ?: "",
                openQuestions = dto.openQuestions,
                planningFacts = dto.planningFacts ?: emptyMap(),
                planApproved = dto.planApproved ?: false,
                executionResultReady = dto.executionResultReady ?: false,
                validationReported = dto.validationReported ?: false
            )
        } catch (_: Exception) {
            null
        }
    }

    private data class TaskStateClassificationDto(
        val activate: Boolean? = null,
        val taskGoal: String? = null,
        val currentStep: String? = null,
        val expectedAction: String? = null,
        val advancePhase: Boolean? = null,
        val requestedPhase: String? = null,
        val completedStep: String? = null,
        val openQuestions: List<String>? = null,
        val planningFacts: Map<String, String>? = null,
        val planApproved: Boolean? = null,
        val executionResultReady: Boolean? = null,
        val validationReported: Boolean? = null
    )

    private fun parseMemoryClassificationResponse(raw: String, gson: Gson): MemoryClassificationResult? {
        var t = raw.trim()
        if (t.startsWith("```")) {
            t = t.removePrefix("```json").removePrefix("```JSON").removePrefix("```").trim()
            val endFence = t.lastIndexOf("```")
            if (endFence >= 0) t = t.substring(0, endFence).trim()
        }
        return try {
            val dto = gson.fromJson(t, MemoryClassificationDto::class.java) ?: return null
            MemoryClassificationResult(
                working = dto.working ?: emptyMap(),
                longTerm = dto.longTerm ?: emptyMap(),
                routingLog = dto.routingLog ?: emptyList()
            )
        } catch (_: Exception) {
            null
        }
    }

    private data class MemoryClassificationDto(
        val working: Map<String, String>? = null,
        val longTerm: Map<String, String>? = null,
        val routingLog: List<String>? = null
    )

    private fun parseFactsJsonResponse(
        raw: String,
        gson: Gson,
        mapType: java.lang.reflect.Type
    ): Map<String, String>? {
        var t = raw.trim()
        if (t.startsWith("```")) {
            t = t.removePrefix("```json").removePrefix("```JSON").removePrefix("```").trim()
            val endFence = t.lastIndexOf("```")
            if (endFence >= 0) t = t.substring(0, endFence).trim()
        }
        return try {
            gson.fromJson<Map<String, String>>(t, mapType)
        } catch (_: Exception) {
            null
        }
    }

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    override fun sendMessagesStreaming(
        messages: List<ChatMessage>,
        modelId: String
    ): Flow<StreamEvent> = flow {
        if (messages.isEmpty()) return@flow
        val request = DeepSeekRequest(
            model = modelId,
            messages = messages.toApiChatMessages(),
            stream = true,
            streamOptions = StreamOptions(includeUsage = true)
        )
        try {
            val response = routerAiApi.createChatCompletionStream(request)
            if (!response.isSuccessful) {
                val errorMsg = response.errorBody()?.string()?.let { raw ->
                    try {
                        Gson().fromJson(raw, ErrorBody::class.java)?.error?.message ?: raw
                    } catch (_: Exception) {
                        raw
                    }
                } ?: "Error: ${response.code()} ${response.message()}"
                emit(StreamEvent.Error(errorMsg))
                return@flow
            }
            val body = response.body() ?: run {
                emit(StreamEvent.Error("Empty response body"))
                return@flow
            }
            var usage: Usage? = null
            val accumulated = StringBuilder()
            body.byteStream().bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    val trimmed = line.trim()
                    if (!trimmed.startsWith("data: ")) continue
                    val data = trimmed.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val chunk = gson.fromJson(data, StreamChunk::class.java)
                        chunk.usage?.toUsage()?.let { parsed ->
                            if (parsed.isMeaningful()) usage = parsed
                        }
                        val delta = chunk.choices?.firstOrNull()?.delta ?: continue
                        val content = delta.content
                        if (!content.isNullOrEmpty()) {
                            accumulated.append(content)
                            emit(StreamEvent.Chunk(content))
                        }
                    } catch (_: Exception) {
                        // skip unparseable chunk
                    }
                }
            }
            val finalUsage = usage?.takeIf { it.isMeaningful() }
                ?: estimateUsage(messages, accumulated.toString())
            emit(StreamEvent.Done(finalUsage))
        } catch (e: Exception) {
            emit(StreamEvent.Error(e.message ?: "Stream error"))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun classifyPromptContext(
        userMessage: String,
        assistantProfile: com.aichallenge.aiagentapp.agent.profile.AssistantProfile,
        invariantBlocks: List<com.aichallenge.aiagentapp.agent.invariant.InvariantBlock>,
        taskState: TaskState,
        recentContext: List<ChatMessage>,
        modelId: String
    ): Result<com.aichallenge.aiagentapp.agent.prompt.PromptContextDecision> =
        withContext(Dispatchers.IO) {
            val blocksDescription = com.aichallenge.aiagentapp.agent.invariant.InvariantBlocksFormatter
                .formatForClassifier(invariantBlocks)
            val contextLines = recentContext.takeLast(8).joinToString("\n") { m ->
                val label = if (m.role == "user") "П" else "А"
                "$label: ${m.content.take(500)}"
            }
            val system = """
                Ты классификатор релевантности инвариантов для промпта ассистента.
                Тебе дан список блоков инвариантов (название, domainHint, правила) и сообщение пользователя.
                Самостоятельно реши, какие блоки относятся к этому вопросу.

                Верни ТОЛЬКО JSON:
                {
                  "includeProfile": true/false,
                  "relevantBlockIds": ["id1", "id2"],
                  "includeTaskState": true/false,
                  "relevanceReason": "кратко: почему выбраны эти блоки"
                }

                Как выбирать relevantBlockIds (семантически):
                1. Прочитай domainHint и каждое правило блока — правила СТРОГИЕ, не рекомендации.
                2. Блок релевантен, если запрос относится к его теме ИЛИ может затронуть/нарушить его правила.
                3. Если пользователь просит альтернативу из другой категории (другой язык, жанр, фреймворк) —
                   блок с обязательным предпочтением ОБЯЗАТЕЛЬНО включить.
                4. Учитывай контекст диалога.
                5. Не включай блок, если запрос полностью в другом домене.

                ${com.aichallenge.aiagentapp.agent.invariant.StrictInvariantPolicy.classifierRules}

                includeProfile: true если нужен стиль/персона для этого вопроса.
                includeTaskState: true только если для этого хода нужны правила этапа задачи
                (активная многошаговая задача И сообщение относится к её выполнению, а не простой Q&A).
                includeTaskState=false для простых вопросов, примеров кода, «почему?», «спасибо» вне контекста задачи.
            """.trimIndent()
            val userPayload = buildString {
                appendLine("Персона: ${assistantProfile.label}")
                if (assistantProfile.personaDescription.isNotBlank()) {
                    appendLine("Описание персоны: ${assistantProfile.personaDescription}")
                }
                appendLine("Задача активна: ${taskState.isActive}, phase: ${taskState.phase.name}")
                appendLine()
                appendLine("=== Блоки инвариантов ===")
                appendLine(blocksDescription)
                appendLine()
                appendLine("=== Последние реплики диалога ===")
                appendLine(contextLines.ifBlank { "(нет)" })
                appendLine()
                appendLine("=== Новое сообщение пользователя ===")
                appendLine(userMessage)
            }
            classifyJsonCall(system, userPayload, modelId) { raw, gson ->
                val dto = gson.fromJson(stripMarkdownJson(raw), PromptContextDto::class.java) ?: return@classifyJsonCall null
                val validIds = invariantBlocks.map { it.id }.toSet()
                val filteredIds = (dto.relevantBlockIds ?: emptyList()).filter { it in validIds }
                com.aichallenge.aiagentapp.agent.prompt.PromptContextDecision(
                    includeProfile = dto.includeProfile ?: assistantProfile.isPersona(),
                    relevantBlockIds = filteredIds,
                    includeTaskState = dto.includeTaskState ?: false,
                    relevanceReason = dto.relevanceReason ?: ""
                )
            }
        }

    override suspend fun classifyInvariantConflict(
        userMessage: String,
        relevantBlocks: List<com.aichallenge.aiagentapp.agent.invariant.InvariantBlock>,
        modelId: String
    ): Result<InvariantConflictResult> = withContext(Dispatchers.IO) {
        if (relevantBlocks.isEmpty()) {
            return@withContext Result.success(InvariantConflictResult())
        }
        val blocksText = com.aichallenge.aiagentapp.agent.invariant.InvariantBlocksFormatter
            .formatForClassifier(relevantBlocks)
        val strictSummary = com.aichallenge.aiagentapp.agent.invariant.StrictRulesExtractor
            .formatStrictSummary(relevantBlocks)
        val system = """
            Ты проверяешь, требует ли запрос пользователя нарушить СТРОГИЕ инварианты.
            Прочитай каждый блок: название, domainHint, все правила.
            Верни ТОЛЬКО JSON:
            {
              "hasConflict": true/false,
              "violatedRuleTexts": ["..."],
              "conflictSummary": "...",
              "suggestedAlternative": "..."
            }

            ${com.aichallenge.aiagentapp.agent.invariant.StrictInvariantPolicy.conflictRules}
        """.trimIndent()
        val userPayload = buildString {
            appendLine("=== Блоки инвариантов ===")
            appendLine(blocksText)
            if (strictSummary.isNotBlank()) {
                appendLine()
                appendLine("=== Строгие ограничения (сводка) ===")
                appendLine(strictSummary)
            }
            appendLine()
            appendLine("=== Запрос пользователя ===")
            appendLine(userMessage)
        }
        classifyJsonCall(system, userPayload, modelId) { raw, gson ->
            val dto = gson.fromJson(stripMarkdownJson(raw), InvariantConflictDto::class.java) ?: return@classifyJsonCall null
            InvariantConflictResult(
                hasConflict = dto.hasConflict ?: false,
                violatedRuleTexts = dto.violatedRuleTexts ?: emptyList(),
                conflictSummary = dto.conflictSummary ?: "",
                suggestedAlternative = dto.suggestedAlternative ?: ""
            )
        }
    }

    override suspend fun validateAssistantResponse(
        response: String,
        userMessage: String,
        relevantBlocks: List<com.aichallenge.aiagentapp.agent.invariant.InvariantBlock>,
        taskState: TaskState,
        includeTaskState: Boolean,
        modelId: String
    ): Result<com.aichallenge.aiagentapp.agent.validation.ValidationResult> =
        withContext(Dispatchers.IO) {
            val local = com.aichallenge.aiagentapp.agent.validation.ResponseValidator.validateLocally(
                response = response,
                relevantBlocks = relevantBlocks,
                taskState = taskState,
                includeTaskState = includeTaskState
            )
            if (local is com.aichallenge.aiagentapp.agent.validation.ValidationResult.Fail) {
                return@withContext Result.success(local)
            }
            if (relevantBlocks.isEmpty() && !includeTaskState) {
                return@withContext Result.success(com.aichallenge.aiagentapp.agent.validation.ValidationResult.Pass(response))
            }
            val rulesText = com.aichallenge.aiagentapp.agent.invariant.InvariantBlocksFormatter
                .formatForClassifier(relevantBlocks)
            val onlyConstraints = com.aichallenge.aiagentapp.agent.invariant.StrictRulesExtractor
                .formatStrictSummary(relevantBlocks)
            val system = """
                Проверь ответ ассистента на нарушение СТРОГИХ инвариантов и правил этапа задачи.
                Верни ТОЛЬКО JSON:
                {"pass": true/false, "violations": ["..."]}

                ${com.aichallenge.aiagentapp.agent.invariant.StrictInvariantPolicy.validationRules}

                Дополнительно для FSM задачи:
                - pass=false на planning, если ответ «рад что помог» / завершение без результата
                - pass=false если ответ завершает задачу без прохождения execution → validation → done
            """.trimIndent()
            val userPayload = buildString {
                appendLine("=== Блоки инвариантов ===")
                appendLine(rulesText.ifBlank { "(нет)" })
                if (onlyConstraints.isNotBlank()) {
                    appendLine()
                    appendLine("=== Строгие ограничения (сводка) ===")
                    appendLine(onlyConstraints)
                }
                appendLine()
                appendLine("Этап задачи: ${if (includeTaskState) taskState.phase.name else "не применяется"}")
                appendLine("Запрос пользователя:")
                appendLine(userMessage)
                appendLine("Ответ ассистента:")
                appendLine(response.take(4000))
            }
            classifyJsonCall(system, userPayload, modelId) { raw, gson ->
                val dto = gson.fromJson(stripMarkdownJson(raw), ValidateResponseDto::class.java) ?: return@classifyJsonCall null
                if (dto.pass == true) {
                    com.aichallenge.aiagentapp.agent.validation.ValidationResult.Pass(response)
                } else {
                    com.aichallenge.aiagentapp.agent.validation.ValidationResult.Fail(dto.violations ?: listOf("Нарушение инвариантов"))
                }
            }
        }

    private suspend fun <T> classifyJsonCall(
        system: String,
        userPayload: String,
        modelId: String,
        parse: (String, Gson) -> T?
    ): Result<T> {
        val request = DeepSeekRequest(
            model = modelId,
            messages = listOf(
                ChatMessage(role = "system", content = system),
                ChatMessage(role = "user", content = userPayload)
            ).toApiChatMessages(),
            stream = false,
            maxTokens = 512,
            temperature = 0.1
        )
        return try {
            val response = routerAiApi.createChatCompletion(request)
            if (response.isSuccessful) {
                val raw = response.body()?.choices?.firstOrNull()?.message?.content?.trim()
                    ?: return Result.failure(Exception("Empty classification response"))
                val parsed = parse(raw, Gson())
                if (parsed != null) Result.success(parsed)
                else Result.failure(Exception("Failed to parse classification JSON"))
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: response.message()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun stripMarkdownJson(raw: String): String {
        var t = raw.trim()
        if (t.startsWith("```")) {
            t = t.removePrefix("```json").removePrefix("```JSON").removePrefix("```").trim()
            val endFence = t.lastIndexOf("```")
            if (endFence >= 0) t = t.substring(0, endFence).trim()
        }
        return t
    }

    private data class PromptContextDto(
        val includeProfile: Boolean? = null,
        val relevantBlockIds: List<String>? = null,
        val includeTaskState: Boolean? = null,
        val relevanceReason: String? = null
    )

    private data class InvariantConflictDto(
        val hasConflict: Boolean? = null,
        val violatedRuleTexts: List<String>? = null,
        val conflictSummary: String? = null,
        val suggestedAlternative: String? = null
    )

    private data class ValidateResponseDto(
        val pass: Boolean? = null,
        val violations: List<String>? = null
    )
}
