package ai.koog.prompt.executor.clients.mistralai

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.utils.SuitableForIO
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.mistralai.mapper.MistralAIMessageMapper.mapToMistralAIMessage
import ai.koog.prompt.executor.clients.mistralai.mapper.MistralAIToolMapper.createMistralAITools
import ai.koog.prompt.executor.clients.mistralai.model.MistralAIChatCompletionsRequest
import ai.koog.prompt.executor.clients.mistralai.model.MistralChatCompletionsResponse
import ai.koog.prompt.executor.clients.mistralai.model.MistralAIChoice
import ai.koog.prompt.executor.clients.mistralai.model.asString
import ai.koog.prompt.executor.model.LLMChoice
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

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): LLMChoice {
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

    override fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> = throw NotImplementedError()

    @OptIn(ExperimentalUuidApi::class)
    private fun createMistralAIRequest(
        prompt: Prompt,
        tools: List<ToolDescriptor>,
        model: LLModel
    ): MistralAIChatCompletionsRequest {
        val mistralAIMessages = prompt.messages.map { message -> mapToMistralAIMessage(message) }
        val mistralAITools = createMistralAITools(tools)

        return MistralAIChatCompletionsRequest(
            model = settings.modelVersionsMap[model]
                ?: throw IllegalArgumentException("Unsupported model: $model"),
            messages = mistralAIMessages,
            maxTokens = prompt.params.maxTokens,
            temperature = prompt.params.temperature,
            tools = mistralAITools,
            stream = false
        )
    }

    private fun processMistralAiResponse(response: MistralChatCompletionsResponse): LLMChoice {
        if (response.choices.isEmpty()) {
            logger.error { "Empty choices in MistralAI response" }
            error("Empty choices in MistralAI response")
        }

        val inputTokensCount = response.usage.promptTokens
        val outputTokensCount = response.usage.completionTokens
        val totalTokensCount = response.usage.totalTokens

        val metaInfo = ResponseMetaInfo.create(
            clock,
            totalTokensCount = totalTokensCount,
            inputTokensCount = inputTokensCount,
            outputTokensCount = outputTokensCount
        )

        return response.choices.map { processMistralAIChoice(it, metaInfo) }.first()
    }

    private fun processMistralAIChoice(choice: MistralAIChoice, metaInfo: ResponseMetaInfo): LLMChoice {
        val message = choice.message

        return when {
            message.toolCalls != null && message.toolCalls.isNotEmpty() ->
                message.toolCalls.map { toolCall ->
                    Message.Tool.Call(
                        id = toolCall.id,
                        tool = toolCall.function.name,
                        content = toolCall.function.arguments.asString(),
                        metaInfo = metaInfo
                    )
                }

            message.content != null -> {
                listOf(
                    Message.Assistant(
                        content = message.content,
                        finishReason = choice.finishReason.name.lowercase(),
                        metaInfo = metaInfo
                    )
                )
            }

            else -> {
                logger.error { "Unexpected response from MistralAI: no tool calls and no content" }
                error("Unexpected response from MistralAI: no tool calls and no content")
            }
        }
    }

    public override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult = throw NotImplementedError()
}

