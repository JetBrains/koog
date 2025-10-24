package ai.koog.prompt.executor.clients.bedrock

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationCategory
import ai.koog.prompt.dsl.ModerationCategoryResult
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLMEmbeddingProvider
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.BedrockAnthropicInvokeModel
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.ai21.BedrockAI21JambaSerialization
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.ai21.JambaRequest
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.amazon.BedrockAmazonNovaSerialization
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.amazon.BedrockAmazonTitanEmbeddingSerialization
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.amazon.NovaRequest
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.anthropic.BedrockAnthropicClaudeSerialization
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.cohere.BedrockCohereSerialization
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.meta.BedrockMetaLlamaSerialization
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.meta.LlamaRequest
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.utils.io.SuitableForIO
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.bedrockruntime.applyGuardrail
import aws.sdk.kotlin.services.bedrockruntime.model.ApplyGuardrailResponse
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailAction
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailContentBlock
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailContentFilterType
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailContentSource
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailImageBlock
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailImageFormat
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailImageSource.Bytes
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailTextBlock
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelRequest
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelWithResponseStreamResponse
import aws.sdk.kotlin.services.bedrockruntime.model.ResponseStream
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.http.auth.BearerTokenProvider
import aws.smithy.kotlin.runtime.identity.IdentityProvider
import aws.smithy.kotlin.runtime.net.url.Url
import aws.smithy.kotlin.runtime.retries.StandardRetryStrategy
import aws.smithy.kotlin.runtime.util.type
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

/**
 * Configuration settings for connecting to the AWS Bedrock API.
 *
 * @property region The AWS region where Bedrock service is hosted.
 * @property timeoutConfig Configuration for connection timeouts.
 * @property endpointUrl Optional custom endpoint URL for testing or private deployments.
 * @property maxRetries Maximum number of retries for failed requests.
 * @property enableLogging Whether to enable detailed AWS SDK logging.
 * @property moderationGuardrailsSettings Optional settings of the AWS bedrock Guardrails (see [AWS documentation](https://docs.aws.amazon.com/bedrock/latest/userguide/guardrails-use-independent-api.html) ) that would be used for the [LLMClient.moderate] request
 */
public class BedrockClientSettings(
    internal val region: String = BedrockRegions.US_WEST_2.regionCode,
    internal val timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
    internal val endpointUrl: String? = null,
    internal val maxRetries: Int = 3,
    internal val enableLogging: Boolean = false,
    internal val moderationGuardrailsSettings: BedrockGuardrailsSettings? = null
)

/**
 * Represents the settings configuration for Bedrock guardrails.
 *
 * See [AWS documentation](https://docs.aws.amazon.com/bedrock/latest/userguide/guardrails-use-independent-api.html) for more information
 *
 * @property guardrailIdentifier A unique identifier for the guardrail.
 * @property guardrailVersion The version of the guardrail configuration.
 */
public class BedrockGuardrailsSettings(
    internal val guardrailIdentifier: String,
    internal val guardrailVersion: String,
)

/**
 * Creates a new Bedrock LLM client configured with the specified AWS credentials and settings.
 *
 * @param bedrockClient The runtime client for interacting with Bedrock, highly configurable
 * @param clock A clock used for time-based operations
 * @param moderationGuardrailsSettings Optional settings of the AWS bedrock Guardrails (see [AWS documentation](https://docs.aws.amazon.com/bedrock/latest/userguide/guardrails-use-independent-api.html) ) that would be used for the [LLMClient.moderate] request
 * @return A configured [LLMClient] instance for Bedrock
 */
public class BedrockLLMClient(
    private val bedrockClient: BedrockRuntimeClient,
    private val moderationGuardrailsSettings: BedrockGuardrailsSettings? = null,
    private val clock: Clock = Clock.System,
) : LLMClient, LLMEmbeddingProvider {

    private val logger = KotlinLogging.logger {}

    /**
     * Creates a new Bedrock LLM client configured with the specified identity provider and settings.
     *
     * @param identityProvider Supplies authentication to AWS Bedrock, supporting both [CredentialsProvider] for AWS credentials
     * and [BearerTokenProvider] for API key-based access.
     * @param settings Configuration settings for the Bedrock client, such as region and endpoint
     * @param clock A clock used for time-based operations
     * @return A configured [LLMClient] instance for Bedrock
     */
    public constructor(
        identityProvider: IdentityProvider,
        settings: BedrockClientSettings = BedrockClientSettings(),
        clock: Clock = Clock.System,
    ) : this(
        bedrockClient = BedrockRuntimeClient {
            this.region = settings.region
            when (identityProvider) {
                is CredentialsProvider -> this.credentialsProvider = identityProvider
                is BearerTokenProvider -> this.bearerTokenProvider = identityProvider
                else -> throw IllegalArgumentException("identityProvider must be either CredentialsProvider or BearerTokenProvider")
            }
            // Configure a custom endpoint if provided
            settings.endpointUrl?.let { url ->
                this.endpointUrl = Url.parse(url)
            }
            // Configure retry policy
            this.retryStrategy = StandardRetryStrategy {
                maxAttempts = settings.maxRetries
            }
        },
        moderationGuardrailsSettings = settings.moderationGuardrailsSettings,
        clock = clock
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    internal fun getBedrockModelFamily(model: LLModel): BedrockModelFamilies {
        require(model.provider == LLMProvider.Bedrock) { "Model ${model.id} is not a Bedrock model" }
        return when {
            model.id.contains("anthropic.claude") -> BedrockModelFamilies.AnthropicClaude
            model.id.contains("amazon.nova") -> BedrockModelFamilies.AmazonNova
            model.id.contains("ai21.jamba") -> BedrockModelFamilies.AI21Jamba
            model.id.contains("meta.llama") -> BedrockModelFamilies.Meta
            model.id.contains("amazon.titan") -> BedrockModelFamilies.TitanEmbedding
            model.id.contains("cohere.embed") -> BedrockModelFamilies.Cohere
            else -> throw IllegalArgumentException("Model ${model.id} is not a supported Bedrock model")
        }
    }

    /**
     * Provides the current language learning model provider utilized by this client.
     *
     * @return the [LLMProvider] instance, specifically `LLMProvider.Bedrock` for this client.
     */
    override fun llmProvider(): LLMProvider = LLMProvider.Bedrock

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> {
        logger.debug { "Executing prompt for model: ${model.id}" }
        val modelFamily = getBedrockModelFamily(model)
        model.requireCapability(LLMCapability.Completion, "Model ${model.id} does not support chat completions")
        // Check tool support
        if (tools.isNotEmpty() && !model.capabilities.contains(LLMCapability.Tools)) {
            throw IllegalArgumentException("Model ${model.id} does not support tools")
        }
        val requestBody = createRequestBody(prompt, model, tools)
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
                is BedrockModelFamilies.TitanEmbedding, is BedrockModelFamilies.Cohere -> error("Model family ${modelFamily.display} does not support chat completions; use embed() API instead.")
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> {
        logger.debug { "Executing streaming prompt for model: ${model.id}" }
        val modelFamily = getBedrockModelFamily(model)
        model.requireCapability(LLMCapability.Completion, "Model ${model.id} does not support chat completions")
        val requestBody = createRequestBody(prompt, model, tools)
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
                    bedrockClient.invokeModelWithResponseStream(
                        streamRequest
                    ) { response: InvokeModelWithResponseStreamResponse ->
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
                if (chunkJsonString.isBlank()) return@map emptyList()
                when (modelFamily) {
                    is BedrockModelFamilies.AI21Jamba -> BedrockAI21JambaSerialization.parseJambaStreamChunk(chunkJsonString)
                    is BedrockModelFamilies.AmazonNova -> BedrockAmazonNovaSerialization.parseNovaStreamChunk(chunkJsonString)
                    is BedrockModelFamilies.AnthropicClaude -> BedrockAnthropicClaudeSerialization.parseAnthropicStreamChunk(chunkJsonString)
                    is BedrockModelFamilies.Meta -> BedrockMetaLlamaSerialization.parseLlamaStreamChunk(chunkJsonString)
                    is BedrockModelFamilies.TitanEmbedding, is BedrockModelFamilies.Cohere -> error("Embedding models do not support streaming chat completions. Use embed() instead.")
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to parse Bedrock stream chunk: $chunkJsonString" }
                throw e
            }
        }.transform { frames ->
            frames.forEach {
                emit(it)
            }
        }
    }

    override suspend fun embed(text: String, model: LLModel): List<Double> {
        model.requireCapability(LLMCapability.Embed)
        logger.debug { "Embedding text with model: ${model.id}" }
        val modelFamily = getBedrockModelFamily(model)
        val requestBody = createEmbeddingRequestBody(text, model)
        val invokeRequest = InvokeModelRequest {
            this.modelId = model.id
            this.contentType = "application/json"
            this.accept = "*/*"
            this.body = requestBody.encodeToByteArray()
        }
        return withContext(Dispatchers.SuitableForIO) {
            val response = bedrockClient.invokeModel(invokeRequest)
            val responseBodyString = response.body.decodeToString()
            logger.debug { "Bedrock Embedding Response: $responseBodyString" }
            when (modelFamily) {
                is BedrockModelFamilies.TitanEmbedding -> {
                    when (model.id) {
                        "amazon.titan-embed-text-v1" -> {
                            val titanResponse = BedrockAmazonTitanEmbeddingSerialization.parseG1Response(responseBodyString)
                            titanResponse.embedding
                        }
                        "amazon.titan-embed-text-v2:0" -> {
                            val titanV2Response = BedrockAmazonTitanEmbeddingSerialization.parseV2Response(responseBodyString)
                            BedrockAmazonTitanEmbeddingSerialization.extractV2Embedding(titanV2Response)
                        }
                        else -> error("Unknown Amazon Titan embedding model ID: ${model.id}")
                    }
                }
                is BedrockModelFamilies.Cohere -> {
                    val cohereResponse = BedrockCohereSerialization.parseResponse(responseBodyString)
                    BedrockCohereSerialization.extractEmbeddings(cohereResponse).first()
                }
                else -> error("Model family: ${modelFamily.display} does not support embeddings; use execute() or executeStreaming() for completion models.")
            }
        }
    }

    private fun createRequestBody(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): String {
        model.requireCapability(LLMCapability.Completion, "This function must only be used with completion-capable models.")
        return when (getBedrockModelFamily(model)) {
            is BedrockModelFamilies.AI21Jamba -> json.encodeToString(
                JambaRequest.serializer(),
                BedrockAI21JambaSerialization.createJambaRequest(prompt, model, tools)
            )
            is BedrockModelFamilies.AmazonNova -> json.encodeToString(
                NovaRequest.serializer(),
                BedrockAmazonNovaSerialization.createNovaRequest(prompt, model, tools)
            )
            is BedrockModelFamilies.AnthropicClaude -> {
                json.encodeToString(
                    BedrockAnthropicInvokeModel.serializer(),
                    BedrockAnthropicClaudeSerialization.createAnthropicRequest(prompt, tools)
                )
            }
            is BedrockModelFamilies.Meta -> json.encodeToString(
                LlamaRequest.serializer(),
                BedrockMetaLlamaSerialization.createLlamaRequest(prompt, model)
            )
            is BedrockModelFamilies.TitanEmbedding,
            is BedrockModelFamilies.Cohere -> error(
                "createRequestBody() should not be used with embedding models. Use createEmbeddingRequestBody() instead for Bedrock embedding models."
            )
        }
    }

    private fun createEmbeddingRequestBody(text: String, model: LLModel): String =
        when (val modelFamily = getBedrockModelFamily(model)) {
            is BedrockModelFamilies.TitanEmbedding -> {
                when (model.id) {
                    "amazon.titan-embed-text-v1" ->
                        BedrockAmazonTitanEmbeddingSerialization.createG1Request(text)

                    "amazon.titan-embed-text-v2:0" ->
                        BedrockAmazonTitanEmbeddingSerialization.createV2Request(text)

                    else -> error("Unknown Amazon Titan embedding model ID: ${model.id}")
                }
            }
            is BedrockModelFamilies.Cohere -> {
                BedrockCohereSerialization.createV3TextRequest(listOf(text))
            }
            else -> error(
                "Model family: ${modelFamily.display} does not support embeddings; use execute() or executeStreaming() for completion models."
            )
        }

    private fun LLModel.requireCapability(capability: LLMCapability, message: String? = null) {
        require(capabilities.contains(capability)) {
            "Model $id does not support ${capability.id}" + (message?.let { ": $it" } ?: "")
        }
    }

    /**
     * Moderates the provided prompt using specified moderation guardrails settings.
     * The method evaluates both input and output of the prompt against guardrails
     * and determines if either is harmful, returning a corresponding result.
     *
     * Requires [moderationGuardrailsSettings] to be set for this [BedrockLLMClient]
     *
     * Note: [model] parameter is unused here
     *
     * @param prompt the input text/content to be evaluated.
     * @param model the language learning model to be used for evaluation.
     * @return a [ModerationResult] indicating whether the content is harmful and
     * a map of categorized moderation results.
     * @throws IllegalArgumentException if moderation guardrails settings are not provided.
     */
    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult {
        if (moderationGuardrailsSettings == null) {
            throw IllegalArgumentException(
                "Moderation Guardrails settings are not provided to the Bedrock client. " +
                    "Please provide them to the BedrockClientSettings when creating the Bedrock client. " +
                    "See https://docs.aws.amazon.com/bedrock/latest/userguide/guardrails-use-independent-api.html for more information."
            )
        }

        require(prompt.messages.isNotEmpty()) {
            "Can't moderate an empty prompt"
        }

        val inputGuardrailResponse = requestGuardrails<Message.Request>(
            moderationGuardrailsSettings,
            prompt,
            GuardrailContentSource.Input
        )

        val outputGuardrailResponse = requestGuardrails<Message.Response>(
            moderationGuardrailsSettings,
            prompt,
            GuardrailContentSource.Output
        )

        val inputIsHarmful = inputGuardrailResponse.action is GuardrailAction.GuardrailIntervened
        val outputIsHarmful = inputGuardrailResponse.action is GuardrailAction.GuardrailIntervened

        val categories = buildMap {
            fillCategoriesMap(inputGuardrailResponse)
            fillCategoriesMap(outputGuardrailResponse)
        }

        return ModerationResult(inputIsHarmful || outputIsHarmful, categories)
    }

    private fun MutableMap<ModerationCategory, ModerationCategoryResult>.fillCategoriesMap(
        guardrailResponse: ApplyGuardrailResponse
    ) {
        fun update(category: ModerationCategory, detected: Boolean?) {
            this[category] = ModerationCategoryResult(this[category]?.detected == true || detected == true)
        }

        guardrailResponse.assessments.forEach { assessment ->
            assessment.contentPolicy?.filters?.forEach { filter ->
                when (filter.type) {
                    GuardrailContentFilterType.Hate -> {
                        update(ModerationCategory.Hate, filter.detected)
                    }

                    GuardrailContentFilterType.Insults -> {
                        update(ModerationCategory.HateThreatening, filter.detected)
                    }

                    GuardrailContentFilterType.Misconduct -> {
                        update(ModerationCategory.Misconduct, filter.detected)
                    }

                    GuardrailContentFilterType.PromptAttack -> {
                        update(ModerationCategory.PromptAttack, filter.detected)
                    }

                    GuardrailContentFilterType.Sexual -> {
                        update(ModerationCategory.Sexual, filter.detected)
                    }

                    GuardrailContentFilterType.Violence -> {
                        update(ModerationCategory.Violence, filter.detected)
                    }

                    else -> {}
                }
            }
            assessment.topicPolicy?.topics?.forEach { topic ->
                update(ModerationCategory(topic.name), topic.detected)
            }
        }
    }

    private suspend inline fun <reified MessageType : Message> requestGuardrails(
        moderationGuardrailsSettings: BedrockGuardrailsSettings,
        prompt: Prompt,
        sourceType: GuardrailContentSource
    ): ApplyGuardrailResponse = bedrockClient.applyGuardrail {
        guardrailIdentifier = moderationGuardrailsSettings.guardrailIdentifier
        guardrailVersion = moderationGuardrailsSettings.guardrailVersion

        source = sourceType

        content = buildList {
            prompt.messages.filterIsInstance<MessageType>().forEach { message ->
                message.parts.forEach { part ->
                    when (part) {
                        is ContentPart.Text -> {
                            add(GuardrailContentBlock.Text(GuardrailTextBlock { text = part.text }))
                        }
                        is ContentPart.Image -> {
                            GuardrailContentBlock.Image(
                                GuardrailImageBlock {
                                    format = when (part.format) {
                                        "jpg", "jpeg", "JPG", "JPEG" -> GuardrailImageFormat.Jpeg
                                        "png", "PNG" -> GuardrailImageFormat.Png
                                        else -> GuardrailImageFormat.SdkUnknown(part.format)
                                    }

                                    when (val imageContent = part.content) {
                                        is AttachmentContent.Binary.Base64 -> source = Bytes(imageContent.asBytes())
                                        is AttachmentContent.Binary.Bytes -> source = Bytes(imageContent.data)
                                        is AttachmentContent.PlainText ->
                                            source =
                                                Bytes(imageContent.text.encodeToByteArray())

                                        else -> {}
                                    }
                                }
                            )
                        }
                        else -> throw IllegalArgumentException("Unsupported attachment type: $this")
                    }
                }
            }
        }
    }

    override fun close() {
        bedrockClient.close()
    }
}
