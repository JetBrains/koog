package ai.koog.prompt.executor.clients.openrouter.models

import ai.koog.prompt.executor.clients.openai.base.models.OpenAIBaseLLMRequest
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIBaseLLMResponse
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIBaseLLMStreamResponse
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIChoice
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIResponseFormat
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIStaticContent
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIStreamChoice
import ai.koog.prompt.executor.clients.openai.base.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolChoice
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIUsage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenRouter Chat Completions API Request
 * https://openrouter.ai/docs/api-reference
 *
 */
@Serializable
internal class OpenRouterChatCompletionRequest(
    val messages: List<OpenAIMessage> = emptyList(),
    val prompt: String? = null,
    override val model: String? = null,
    override val stream: Boolean? = null,
    override val temperature: Double? = null,
    val tools: List<OpenAITool>? = null,
    val toolChoice: OpenAIToolChoice? = null,
    override val topP: Double? = null,
    override val topLogprobs: Int? = null,
    val maxTokens: Int? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val responseFormat: OpenAIResponseFormat? = null,
    val stop: List<String>? = null,
    val logprobs: Boolean? = null,
    val seed: Int? = null,
    val topK: Int? = null,
    val repetitionPenalty: Double? = null,
    val logitBias: Map<Int, Double>? = null,
    val minP: Double? = null,
    val topA: Double? = null,
    val prediction: OpenAIStaticContent? = null,
    val transforms: List<String>? = null,
    val models: List<String>? = null,
    val route: String? = null,
    val provider: ProviderPreferences? = null,
    val user: String? = null
) : OpenAIBaseLLMRequest

/**
 * @property order List of provider slugs to try in order (e.g. ["anthropic", "openai"])
 * @property allowFallbacks Whether to allow backup providers when the primary is unavailable
 * @property requireParameters Only use providers that support all parameters in your request
 * @property dataCollection `allow` Control whether to use providers that may store data
 * @property only List of provider slugs to allow for this request
 * @property ignore List of provider slugs to skip for this request
 * @property quantizations List of quantization levels to filter by (e.g. ["int4", "int8"])
 * @property sort Sort providers by price or throughput. (e.g. "price" or "throughput")
 * @property maxPrice The maximum pricing you want to pay for this request
 *
 */
@Serializable
public class ProviderPreferences(
    public val order: List<String>? = null,
    public val allowFallbacks: Boolean? = null,
    public val requireParameters: Boolean? = null,
    public val dataCollection: String? = null,
    public val only: List<String>? = null,
    public val ignore: List<String>? = null,
    public val quantizations: List<String>? = null,
    public val sort: String? = null,
    public val maxPrice: Map<String, String>? = null
)

/**
 * OpenRouter Chat Completion Response
 * https://openrouter.ai/docs/responses
 */
@Serializable
public class OpenRouterChatCompletionResponse(
    public val choices: List<OpenAIChoice>,
    override val created: Long,
    override val id: String,
    override val model: String,
    public val systemFingerprint: String? = null,
    @SerialName("object")
    public val objectType: String = "chat.completion",
    public val usage: OpenAIUsage? = null,
) : OpenAIBaseLLMResponse

/**
 * OpenRouter Chat Completion Streaming Response
 */
@Serializable
public class OpenRouterChatCompletionStreamResponse(
    public val choices: List<OpenAIStreamChoice>,
    override val created: Long,
    override val id: String,
    override val model: String,
    public val systemFingerprint: String? = null,
    @SerialName("object")
    public val objectType: String = "chat.completion.chunk",
    public val usage: OpenAIUsage? = null,
) : OpenAIBaseLLMStreamResponse
