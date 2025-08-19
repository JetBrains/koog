package ai.koog.prompt.executor.clients.mistralai

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.utils.SuitableForIO
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.mistralai.mapper.MistralAIMessageMapper.mapToMistralAIMessage
import ai.koog.prompt.executor.clients.mistralai.mapper.MistralAIToolMapper.createMistralAITools
import ai.koog.prompt.executor.clients.mistralai.model.MistralAIChatCompletionRequest
import ai.koog.prompt.executor.clients.mistralai.model.MistralChatCompletionsResponse
import ai.koog.prompt.executor.clients.mistralai.model.MistralChatCompletionsResponseChoice
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlin.uuid.ExperimentalUuidApi

public class MistralAIClientSettings(
    public val modelVersionsMap: Map<LLModel, String> = DEFAULT_MISTRAL_AI_MODEL_VERSIONS_MAP,
    public val baseUrl: String = "https://api.mistral.ai",
    public val apiVersion: String = "1.0.0",
    public val timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()
)

public open class MistralAILLMClient(
    private val apiKey: String,
    private val settings: MistralAIClientSettings = MistralAIClientSettings(),
    baseClient: HttpClient = HttpClient(),
    private val clock: Clock = Clock.System
) : LLMClient {

    private companion object {
        private val logger = KotlinLogging.logger { }

        private const val DEFAULT_MESSAGE_PATH = "v1/chat/completions"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        namingStrategy = JsonNamingStrategy.SnakeCase
        classDiscriminatorMode = ClassDiscriminatorMode.NONE // Mistral AI request/responses are not polymorphic
    }

    private val httpClient = baseClient.config {
        defaultRequest {
            url(settings.baseUrl)
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $apiKey")
        }
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = settings.timeoutConfig.requestTimeoutMillis // Increase timeout to 60 seconds
            connectTimeoutMillis = settings.timeoutConfig.connectTimeoutMillis
            socketTimeoutMillis = settings.timeoutConfig.socketTimeoutMillis
        }
    }

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        return processMistralAiResponse(getMistralAIResponse(prompt, model, tools))
    }

    private suspend fun getMistralAIResponse(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): MistralChatCompletionsResponse {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }

        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }

        require(model.capabilities.contains(LLMCapability.Tools)) {
            "Model ${model.id} does not support tools"
        }

        val request = createMistralAIRequest(prompt, tools, model)

        return withContext(Dispatchers.SuitableForIO) {
            val response = httpClient.post(DEFAULT_MESSAGE_PATH) {
                setBody(request)
            }

            if (response.status.isSuccess()) {
                response.body<MistralChatCompletionsResponse>()
            } else {
                val errorBody = response.bodyAsText()
                logger.error { "Error from Mistral AI API: ${response.status}: $errorBody" }
                error("Error from Mistral AI API: ${response.status}: $errorBody")
            }
        }
    }

    override fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> = flow {
        TODO()
    }

    @OptIn(ExperimentalUuidApi::class) // todo:make private
    public fun createMistralAIRequest(
        prompt: Prompt,
        tools: List<ToolDescriptor>,
        model: LLModel
    ): MistralAIChatCompletionRequest {
        val mistralAIMessages = prompt.messages.map { message -> mapToMistralAIMessage(message) }
        val mistralAITools = createMistralAITools(tools)

        return MistralAIChatCompletionRequest(
            model = settings.modelVersionsMap[model]
                ?: throw IllegalArgumentException("Unsupported model: $model"),
            messages = mistralAIMessages,
            maxTokens = prompt.params.maxTokens,
            temperature = prompt.params.temperature,
            tools = mistralAITools,
            stream = false
        )
    }

    private fun processMistralAiResponse(response: MistralChatCompletionsResponse): List<Message.Response> {
        val inputTokensCount = response.usage.inputTokens
        val outputTokensCount = response.usage.outputTokens
        val totalTokensCount = response.usage.let { it.inputTokens + it.outputTokens }

        val responses: List<Message.Response> =
            response.choices.mapNotNull { choice: MistralChatCompletionsResponseChoice ->
                when {
                    choice.message.role == "assistant" -> {
                        Message.Assistant(
                            content = choice.message.content ?: "",
                            finishReason = choice.finishReason,
                            metaInfo = ResponseMetaInfo.create(
                                clock,
                                totalTokensCount = totalTokensCount,
                                inputTokensCount = inputTokensCount,
                                outputTokensCount = outputTokensCount,
                            )
                        )
                    }

                    else -> null
                }
            }

        return when {
            responses.any { it is Message.Tool.Call } -> responses.filterIsInstance<Message.Tool.Call>()
            responses.isEmpty() -> listOf(
                Message.Assistant(
                    content = "",
                    finishReason = response.choices.takeIf { it.isNotEmpty() }?.get(0)?.finishReason ?: "error",
                    metaInfo = ResponseMetaInfo.create(
                        clock,
                        totalTokensCount = totalTokensCount,
                        inputTokensCount = inputTokensCount,
                        outputTokensCount = outputTokensCount,
                    )
                )
            )

            else -> responses
        }
    }

    public override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult = TODO()
}

