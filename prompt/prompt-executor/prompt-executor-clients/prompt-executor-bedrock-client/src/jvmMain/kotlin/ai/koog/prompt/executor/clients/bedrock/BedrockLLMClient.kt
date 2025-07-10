package ai.koog.prompt.executor.clients.bedrock

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.utils.SuitableForIO
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.anthropic.AnthropicMessageRequest
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.anthropic.BedrockAnthropicClaudeSerialization
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.ai21.BedrockAI21JambaSerialization
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.ai21.JambaRequest
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.meta.BedrockMetaLlamaSerialization
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.amazon.BedrockAmazonNovaSerialization
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.amazon.NovaRequest
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.meta.LlamaRequest
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.message.Message
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelRequest
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelWithResponseStreamResponse
import aws.sdk.kotlin.services.bedrockruntime.model.ResponseStream
import aws.smithy.kotlin.runtime.retries.StandardRetryStrategy
import aws.smithy.kotlin.runtime.net.url.Url
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Configuration settings for connecting to the AWS Bedrock API.
 *
 * @property region The AWS region where Bedrock service is hosted.
 * @property timeoutConfig Configuration for connection timeouts.
 * @property endpointUrl Optional custom endpoint URL for testing or private deployments.
 * @property maxRetries Maximum number of retries for failed requests.
 * @property enableLogging Whether to enable detailed AWS SDK logging.
 */
public class BedrockClientSettings(
    internal val region: String = "us-east-1",
    internal val timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
    internal val endpointUrl: String? = null,
    internal val maxRetries: Int = 3,
    internal val enableLogging: Boolean = false
)

/**
 * Creates a new Bedrock LLM client configured with the specified AWS credentials and settings.
 *
 * @param bedrockClient The runtime client for interacting with Bedrock, highly configurable
 * @param clock A clock used for time-based operations
 * @return A configured [LLMClient] instance for Bedrock
 */
public class BedrockLLMClient(
    private val bedrockClient: BedrockRuntimeClient,
    private val clock: Clock = Clock.System,
) : LLMClient {

    private val logger = KotlinLogging.logger {}

    /**
     * Creates a new Bedrock LLM client configured with the specified AWS credentials and settings.
     *
     * @param awsAccessKeyId The AWS access key ID for authentication
     * @param awsSecretAccessKey The AWS secret access key for authentication
     * @param settings Configuration settings for the Bedrock client, such as region and endpoint
     * @param clock A clock used for time-based operations
     * @return A configured [LLMClient] instance for Bedrock
     */
    public constructor(
        awsAccessKeyId: String,
        awsSecretAccessKey: String,
        settings: BedrockClientSettings = BedrockClientSettings(),
        clock: Clock = Clock.System,
    ) : this(
        bedrockClient = BedrockRuntimeClient {
            this.region = settings.region
            this.credentialsProvider = StaticCredentialsProvider {
                this.accessKeyId = awsAccessKeyId
                this.secretAccessKey = awsSecretAccessKey
            }

            // Configure custom endpoint if provided
            settings.endpointUrl?.let { url ->
                this.endpointUrl = Url.parse(url)
            }

            // Configure retry policy
            this.retryStrategy = StandardRetryStrategy {
                maxAttempts = settings.maxRetries
            }
        },
        clock = clock
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    internal fun getBedrockModelFamily(model: LLModel): BedrockModelFamilies {
        require(model.provider == LLMProvider.Bedrock) { "Model ${model.id} is not a Bedrock model" }
        return when {
            model.id.startsWith("anthropic.claude") -> BedrockModelFamilies.AnthropicClaude
            model.id.startsWith("amazon.nova") -> BedrockModelFamilies.AmazonNova
            model.id.startsWith("ai21.jamba") -> BedrockModelFamilies.AI21Jamba
            model.id.startsWith("meta.llama") -> BedrockModelFamilies.Meta
            else -> throw IllegalArgumentException("Model ${model.id} is not a supported Bedrock model")
        }
    }

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> {
        logger.debug { "Executing prompt for model: ${model.id}" }

        val modelFamily = getBedrockModelFamily(model)
        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }

        // Check tool support
        if (tools.isNotEmpty() && !model.capabilities.contains(LLMCapability.Tools)) {
            throw IllegalArgumentException("Model ${model.id} does not support tools")
        }

        val requestBody = when (modelFamily) {
            is BedrockModelFamilies.AI21Jamba -> json.encodeToString(
                JambaRequest.serializer(),
                BedrockAI21JambaSerialization.createJambaRequest(prompt, model, tools)
            )
            is BedrockModelFamilies.AmazonNova -> json.encodeToString(
                NovaRequest.serializer(),
                BedrockAmazonNovaSerialization.createNovaRequest(prompt, model)
            )
            is BedrockModelFamilies.AnthropicClaude -> json.encodeToString(
                AnthropicMessageRequest.serializer(),
                BedrockAnthropicClaudeSerialization.createAnthropicRequest(prompt, model, tools)
            )
            is BedrockModelFamilies.Meta -> json.encodeToString(
                LlamaRequest.serializer(),
                BedrockMetaLlamaSerialization.createLlamaRequest(prompt, model)
            )
        }

        val invokeRequest = InvokeModelRequest {
            this.modelId = model.id
            this.contentType = "application/json"
            this.accept = "*/*"
            this.body = requestBody.encodeToByteArray()
        }

        logger.debug { "Bedrock InvokeModel Request: ModelID: ${model.id}, Body: $requestBody" }

        return withContext(Dispatchers.SuitableForIO) {
            val response = bedrockClient.invokeModel(invokeRequest)
            val responseBodyString = response.body.decodeToString()
            logger.debug { "Bedrock InvokeModel Response: $responseBodyString" }

            if (responseBodyString.isBlank()) {
                logger.error { "Received null or empty body from Bedrock model ${model.id}" }
                error("Received null or empty body from Bedrock model ${model.id}")
            }

            return@withContext when (modelFamily) {
                is BedrockModelFamilies.AI21Jamba -> BedrockAI21JambaSerialization.parseJambaResponse(responseBodyString, clock)
                is BedrockModelFamilies.AmazonNova -> BedrockAmazonNovaSerialization.parseNovaResponse(responseBodyString, clock)
                is BedrockModelFamilies.AnthropicClaude -> BedrockAnthropicClaudeSerialization.parseAnthropicResponse(responseBodyString, clock)
                is BedrockModelFamilies.Meta -> BedrockMetaLlamaSerialization.parseLlamaResponse(responseBodyString, clock)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    override fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
        logger.debug { "Executing streaming prompt for model: ${model.id}" }
        val modelFamily = getBedrockModelFamily(model)

        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }

        val requestBody = when (modelFamily) {
            is BedrockModelFamilies.AI21Jamba -> json.encodeToString(
                JambaRequest.serializer(),
                BedrockAI21JambaSerialization.createJambaRequest(prompt, model, emptyList())
            )
            is BedrockModelFamilies.AmazonNova -> json.encodeToString(
                NovaRequest.serializer(),
                BedrockAmazonNovaSerialization.createNovaRequest(prompt, model)
            )
            is BedrockModelFamilies.AnthropicClaude -> json.encodeToString(
                AnthropicMessageRequest.serializer(),
                BedrockAnthropicClaudeSerialization.createAnthropicRequest(prompt, model, emptyList())
            )
            is BedrockModelFamilies.Meta -> json.encodeToString(
                LlamaRequest.serializer(),
                BedrockMetaLlamaSerialization.createLlamaRequest(prompt, model)
            )
        }

        val streamRequest = InvokeModelWithResponseStreamRequest {
            this.modelId = model.id
            this.contentType = "application/json"
            this.accept = "*/*"
            this.body = requestBody.encodeToByteArray()
        }
        logger.debug { "Bedrock InvokeModelWithResponseStream Request: ModelID: ${model.id}, Body: $requestBody" }

        return channelFlow {
            try {
                withContext(Dispatchers.SuitableForIO) {
                    bedrockClient.invokeModelWithResponseStream(streamRequest) { response: InvokeModelWithResponseStreamResponse ->
                        response.body?.collect { event: ResponseStream ->
                            val chunkBytes = event.asChunk().bytes
                            if (chunkBytes != null) {
                                val chunkJsonString = chunkBytes.decodeToString()
                                send(chunkJsonString)
                                logger.trace { "Bedrock Stream Chunk for model ${model.id}: $chunkJsonString" }
                            } else {
                                logger.warn { "Received null chunk bytes in stream for model ${model.id}" }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Error in Bedrock streaming for model ${model.id}" }
                close(e)
            }
        }.map { chunkJsonString ->
            try {
                if (chunkJsonString.isBlank()) return@map ""

                when (modelFamily) {
                    is BedrockModelFamilies.AI21Jamba -> BedrockAI21JambaSerialization.parseJambaStreamChunk(chunkJsonString)
                    is BedrockModelFamilies.AmazonNova -> BedrockAmazonNovaSerialization.parseNovaStreamChunk(chunkJsonString)
                    is BedrockModelFamilies.AnthropicClaude -> BedrockAnthropicClaudeSerialization.parseAnthropicStreamChunk(chunkJsonString)
                    is BedrockModelFamilies.Meta -> BedrockMetaLlamaSerialization.parseLlamaStreamChunk(chunkJsonString)
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to parse Bedrock stream chunk: $chunkJsonString" }
                throw e
            }
        }
    }

}
