package ai.koog.prompt.executor.clients.requesty.models

import ai.koog.prompt.executor.clients.openai.base.models.OpenAIBaseLLMRequest
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIBaseLLMResponse
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIBaseLLMStreamResponse
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIChoice
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIResponseFormat
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIStaticContent
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIStreamChoice
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIStreamOptions
import ai.koog.prompt.executor.clients.openai.base.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolChoice
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIUsage
import ai.koog.prompt.executor.clients.serialization.AdditionalPropertiesFlatteningSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Requesty Chat Completions API Request
 * https://docs.requesty.ai
 *
 * The request follows the OpenAI Chat Completions format.
 *
 * @property messages A list of messages comprising the conversation so far.
 * @property model ID of the model to use, for example `openai/gpt-4o-mini`.
 * @property stream If set, partial message deltas will be sent as server-sent events.
 * @property temperature What sampling temperature to use, between 0 and 2.
 * @property tools A list of tools the model may call.
 * @property toolChoice Controls which (if any) tool is called by the model.
 * @property topP Nucleus sampling parameter.
 * @property topLogprobs Number of most likely tokens to return at each token position. Requires [logprobs].
 * @property maxTokens The maximum number of tokens that can be generated in the chat completion.
 * @property frequencyPenalty Number between -2.0 and 2.0 penalizing frequent tokens.
 * @property presencePenalty Number between -2.0 and 2.0 penalizing tokens already present.
 * @property responseFormat An object specifying the format that the model must output.
 * @property stop Up to 4 sequences where the API will stop generating further tokens.
 * @property logprobs Whether to return log probabilities of the output tokens or not.
 * @property prediction Static predicted output content used for speculative decoding.
 * @property user A unique identifier representing the end-user.
 * @property streamOptions Options for streaming response. Only set this when you set stream: true.
 */
@Serializable
internal class RequestyChatCompletionRequest(
    val messages: List<OpenAIMessage>,
    override val model: String,
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
    val prediction: OpenAIStaticContent? = null,
    val user: String? = null,
    val streamOptions: OpenAIStreamOptions? = null,
    val additionalProperties: Map<String, JsonElement>? = null,
) : OpenAIBaseLLMRequest

/**
 * Requesty Chat Completion Response
 * https://docs.requesty.ai
 */
@Serializable
public class RequestyChatCompletionResponse(
    public val choices: List<OpenAIChoice>,
    override val created: Long = 0L,
    override val id: String = "",
    override val model: String = "",
    public val systemFingerprint: String? = null,
    @SerialName("object")
    public val objectType: String = "chat.completion",
    public val usage: OpenAIUsage? = null,
) : OpenAIBaseLLMResponse

/**
 * Requesty Chat Completion Streaming Response
 */
@Serializable
public class RequestyChatCompletionStreamResponse(
    public val choices: List<OpenAIStreamChoice> = emptyList(),
    override val created: Long = 0L,
    override val id: String = "",
    override val model: String = "",
    public val systemFingerprint: String? = null,
    @SerialName("object")
    public val objectType: String = "chat.completion.chunk",
    public val usage: OpenAIUsage? = null,
) : OpenAIBaseLLMStreamResponse

internal object RequestyChatCompletionRequestSerializer :
    AdditionalPropertiesFlatteningSerializer<RequestyChatCompletionRequest>(RequestyChatCompletionRequest.serializer())
