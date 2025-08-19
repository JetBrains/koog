package ai.koog.prompt.executor.clients.openai

import ai.koog.agents.utils.SuitableForIO
import ai.koog.prompt.dsl.ModerationCategory
import ai.koog.prompt.dsl.ModerationCategoryResult
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLMEmbeddingProvider
import ai.koog.prompt.executor.clients.openai.models.Content
import ai.koog.prompt.executor.clients.openai.models.ContentPart
import ai.koog.prompt.executor.clients.openai.models.OpenAIEmbeddingRequest
import ai.koog.prompt.executor.clients.openai.models.OpenAIEmbeddingResponse
import ai.koog.prompt.executor.clients.openai.structure.OpenAIBasicJsonSchemaGenerator
import ai.koog.prompt.executor.clients.openai.structure.OpenAIStandardJsonSchemaGenerator
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Attachment
import ai.koog.prompt.message.Message
import ai.koog.prompt.structure.RegisteredBasicJsonSchemaGenerators
import ai.koog.prompt.structure.RegisteredStandardJsonSchemaGenerators
import ai.koog.prompt.structure.annotations.InternalStructuredOutputApi
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * Represents the settings for configuring an OpenAI client.
 *
 * @property baseUrl The base URL of the OpenAI API. Defaults to "https://api.openai.com".
 * @property timeoutConfig Configuration for connection timeouts, including request, connect, and socket timeouts.
 * @property chatCompletionsPath The path of the OpenAI Chat Completions API. Defaults to "v1/chat/completions".
 * @property embeddingsPath The path of the OpenAI Embeddings API. Defaults to "v1/embeddings".
 * @property moderationsPath The path of the OpenAI Moderations API. Defaults to "v1/moderations".
 */
public class OpenAIClientSettings(
    baseUrl: String = "https://api.openai.com",
    timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
    chatCompletionsPath: String = "v1/chat/completions",
    public val embeddingsPath: String = "v1/embeddings",
    public val moderationsPath: String = "v1/moderations",
) : OpenAIBasedSettings(baseUrl, chatCompletionsPath, timeoutConfig)

/**
 * Implementation of [LLMClient] for OpenAI API.
 * Uses Ktor HttpClient to communicate with the OpenAI API.
 *
 * @param apiKey The API key for the OpenAI API
 * @param settings The base URL and timeouts for the OpenAI API, defaults to "https://api.openai.com" and 900 s
 * @param clock Clock instance used for tracking response metadata timestamps.
 */
public open class OpenAILLMClient(
    apiKey: String,
    private val settings: OpenAIClientSettings = OpenAIClientSettings(),
    baseClient: HttpClient = HttpClient(),
    clock: Clock = Clock.System,
) : AbstractOpenAILLMClient(apiKey, settings, baseClient, clock), LLMEmbeddingProvider {

    @OptIn(InternalStructuredOutputApi::class)
    private companion object {
        private val staticLogger = KotlinLogging.logger { }

        init {
            // On class load register custom OpenAI JSON schema generators for structured output.
            RegisteredBasicJsonSchemaGenerators[LLMProvider.OpenAI] = OpenAIBasicJsonSchemaGenerator
            RegisteredStandardJsonSchemaGenerators[LLMProvider.OpenAI] = OpenAIStandardJsonSchemaGenerator
        }
    }

    override val logger: KLogger = staticLogger

    /**
     * Embeds the given text using the OpenAI embeddings API.
     *
     * @param text The text to embed.
     * @param model The model to use for embedding. Must have the Embed capability.
     * @return A list of floating-point values representing the embedding.
     * @throws IllegalArgumentException if the model does not have the Embed capability.
     */
    override suspend fun embed(text: String, model: LLModel): List<Double> {
        model.requireCapability(LLMCapability.Embed)

        logger.debug { "Embedding text with model: ${model.id}" }

        val request = OpenAIEmbeddingRequest(
            model = model.id,
            input = text
        )

        return withContext(Dispatchers.SuitableForIO) {
            val response = httpClient.post(settings.embeddingsPath) {
                setBody(request)
            }

            if (response.status.isSuccess()) {
                val openAIResponse = response.body<OpenAIEmbeddingResponse>()
                if (openAIResponse.data.isNotEmpty()) {
                    openAIResponse.data.first().embedding
                } else {
                    logger.error { "Empty data in OpenAI embedding response" }
                    error("Empty data in OpenAI embedding response")
                }
            } else {
                val errorBody = response.bodyAsText()
                logger.error { "Error from OpenAI API: ${response.status}: $errorBody" }
                error("Error from OpenAI API: ${response.status}: $errorBody")
            }
        }
    }

    /**
     * Moderates text and image content based on the provided model's capabilities.
     *
     * @param prompt The prompt containing text messages and optional attachments to be moderated.
     * @param model The language model to use for moderation. Must have the `Moderation` capability.
     * @return The moderation result, including flagged content, categories, scores, and associated metadata.
     * @throws IllegalArgumentException If the specified model does not support moderation.
     */
    public override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        logger.debug { "Moderating text and image content with model: $model" }

        model.requireCapability(LLMCapability.Moderation)

        require(prompt.messages.isNotEmpty()) { "Can't moderate an empty prompt" }

        val input = prompt.messages
            .map { message ->
                if (message is Message.WithAttachments) {
                    require(message.attachments.all { it is Attachment.Image }) {
                        "Only image attachments are supported for moderation"
                    }
                }

                message.toOpenAIMessageContent(model)
            }
            .let { contents ->
                /*
                 If all messages contain only text, merge it all in a single text input,
                 to support OpenAI-compatible providers that do not support attachments.

                 Otherwise create a single content instance with all the parts
                 */
                if (contents.all { it is Content.Text }) {
                    val text = contents.joinToString(separator = "\n\n") { (it as Content.Text).value }

                    Content.Text(text)
                } else {
                    val parts = contents.flatMap { content ->
                        when (content) {
                            is Content.Parts -> content.value
                            is Content.Text -> listOf(ContentPart.Text(content.value))
                        }
                    }

                    Content.Parts(parts)
                }
            }

        val request = OpenAIModerationRequest(
            input = input,
            model = model.id
        )

        return withContext(Dispatchers.SuitableForIO) {
            val response = httpClient.post(settings.moderationsPath) {
                setBody(request)
            }

            if (response.status.isSuccess()) {
                val openAIResponse = response.body<OpenAIModerationResponse>()
                if (openAIResponse.results.isNotEmpty()) {
                    val result = openAIResponse.results.first()

                    // Convert OpenAI categories to a map
                    val categories = mapOf(
                        ModerationCategory.Harassment to result.categories.harassment,
                        ModerationCategory.HarassmentThreatening to result.categories.harassmentThreatening,
                        ModerationCategory.Hate to result.categories.hate,
                        ModerationCategory.HateThreatening to result.categories.hateThreatening,
                        ModerationCategory.Sexual to result.categories.sexual,
                        ModerationCategory.SexualMinors to result.categories.sexualMinors,
                        ModerationCategory.Violence to result.categories.violence,
                        ModerationCategory.ViolenceGraphic to result.categories.violenceGraphic,
                        ModerationCategory.SelfHarm to result.categories.selfHarm,
                        ModerationCategory.SelfHarmIntent to result.categories.selfHarmIntent,
                        ModerationCategory.SelfHarmInstructions to result.categories.selfHarmInstructions,
                        ModerationCategory.Illicit to (result.categories.illicit ?: false),
                        ModerationCategory.IllicitViolent to (result.categories.illicitViolent ?: false)
                    )

                    // Convert OpenAI category scores to a map
                    val categoryScores = mapOf(
                        ModerationCategory.Harassment to result.categoryScores.harassment,
                        ModerationCategory.HarassmentThreatening to result.categoryScores.harassmentThreatening,
                        ModerationCategory.Hate to result.categoryScores.hate,
                        ModerationCategory.HateThreatening to result.categoryScores.hateThreatening,
                        ModerationCategory.Sexual to result.categoryScores.sexual,
                        ModerationCategory.SexualMinors to result.categoryScores.sexualMinors,
                        ModerationCategory.Violence to result.categoryScores.violence,
                        ModerationCategory.ViolenceGraphic to result.categoryScores.violenceGraphic,
                        ModerationCategory.SelfHarm to result.categoryScores.selfHarm,
                        ModerationCategory.SelfHarmIntent to result.categoryScores.selfHarmIntent,
                        ModerationCategory.SelfHarmInstructions to result.categoryScores.selfHarmInstructions,
                        ModerationCategory.Illicit to (result.categoryScores.illicit ?: 0.0),
                        ModerationCategory.IllicitViolent to (result.categoryScores.illicitViolent ?: 0.0)
                    )

                    // Convert category applied input types if available
                    val categoryAppliedInputTypes = result.categoryAppliedInputTypes?.let { appliedTypes ->
                        buildMap {
                            appliedTypes.harassment?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                                ?.let { put(ModerationCategory.Harassment, it) }
                            appliedTypes.harassmentThreatening?.map {
                                ModerationResult.InputType.valueOf(it.uppercase())
                            }
                                ?.let { put(ModerationCategory.HarassmentThreatening, it) }
                            appliedTypes.hate?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                                ?.let { put(ModerationCategory.Hate, it) }
                            appliedTypes.hateThreatening?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                                ?.let { put(ModerationCategory.HateThreatening, it) }
                            appliedTypes.sexual?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                                ?.let { put(ModerationCategory.Sexual, it) }
                            appliedTypes.sexualMinors?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                                ?.let { put(ModerationCategory.SexualMinors, it) }
                            appliedTypes.violence?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                                ?.let { put(ModerationCategory.Violence, it) }
                            appliedTypes.violenceGraphic?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                                ?.let { put(ModerationCategory.ViolenceGraphic, it) }
                            appliedTypes.selfHarm?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                                ?.let { put(ModerationCategory.SelfHarm, it) }
                            appliedTypes.selfHarmIntent?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                                ?.let { put(ModerationCategory.SelfHarmIntent, it) }
                            appliedTypes.selfHarmInstructions?.map {
                                ModerationResult.InputType.valueOf(it.uppercase())
                            }
                                ?.let { put(ModerationCategory.SelfHarmInstructions, it) }
                            appliedTypes.illicit?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                                ?.let { put(ModerationCategory.Illicit, it) }
                            appliedTypes.illicitViolent?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                                ?.let { put(ModerationCategory.IllicitViolent, it) }
                        }
                    } ?: emptyMap()

                    ModerationResult(
                        isHarmful = result.flagged,
                        categories = categories.mapValues { (category, detected) ->
                            ModerationCategoryResult(
                                detected,
                                categoryScores[category],
                                categoryAppliedInputTypes[category] ?: emptyList()
                            )
                        }
                    )
                } else {
                    logger.error { "Empty results in OpenAI moderation response" }
                    error("Empty results in OpenAI moderation response")
                }
            } else {
                val errorBody = response.bodyAsText()
                logger.error { "Error from OpenAI API: ${response.status}: $errorBody" }
                error("Error from OpenAI API: ${response.status}: $errorBody")
            }
        }
    }
}
