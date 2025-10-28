package ai.koog.prompt.executor.clients.dashscope.models

import ai.koog.prompt.executor.clients.openai.base.models.OpenAIBaseLLMRequest
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIBaseLLMResponse
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIBaseLLMStreamResponse
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIChoice
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIResponseFormat
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
 * DashScope Chat Completions API Request using OpenAI-compatible format
 *
 * @property messages A list of messages comprising the conversation so far.
 * @property model ID of the model to use (e.g., qwen-flash, qwen-plus, qwen-max, qwen-long).
 * @property stream If set, partial message deltas will be sent.
 * @property temperature What sampling temperature to use, between 0 and 2.
 * @property tools A list of tools the model may call.
 * @property toolChoice Controls which (if any) tool is called by the model.
 * @property topP An alternative to sampling with temperature, called nucleus sampling.
 * @property maxTokens The maximum number of tokens that can be generated.
 * @property frequencyPenalty Number between -2.0 and 2.0. Penalizes new tokens based on frequency.
 * @property presencePenalty Number between -2.0 and 2.0. Penalizes new tokens based on presence.
 * @property responseFormat An object specifying the format that the model must output.
 * @property stop Up to 16 sequences where the API will stop generating further tokens.
 * @property streamOptions Options for streaming response. Only set when stream is true.
 */
@Serializable
internal class DashscopeChatCompletionRequest(
    val messages: List<OpenAIMessage>,
    override val model: String,
    val enableThinking: Boolean? = null,
    override val stream: Boolean? = null,
    override val temperature: Double? = null,
    val tools: List<OpenAITool>? = null,
    val toolChoice: OpenAIToolChoice? = null,
    override val topP: Double? = null,
    override val topLogprobs: Int? = null,
    val logprobs: Boolean? = null,
    val maxTokens: Int? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val responseFormat: OpenAIResponseFormat? = null,
    val stop: List<String>? = null,
    val streamOptions: OpenAIStreamOptions? = null,
    val parallelToolCalls: Boolean? = null,
    val enableSearch: Boolean? = null,
    val additionalProperties: Map<String, JsonElement>? = null,
) : OpenAIBaseLLMRequest

/**
 * DashScope Chat Completion Response using OpenAI-compatible format
 */
@Serializable
public class DashscopeChatCompletionResponse(
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
 * DashScope Chat Completion Streaming Response using OpenAI-compatible format
 */
@Serializable
public class DashscopeChatCompletionStreamResponse(
    public val choices: List<OpenAIStreamChoice>,
    override val created: Long,
    override val id: String,
    override val model: String,
    public val systemFingerprint: String? = null,
    @SerialName("object")
    public val objectType: String = "chat.completion.chunk",
    public val usage: OpenAIUsage? = null,
) : OpenAIBaseLLMStreamResponse

internal object DashscopeChatCompletionRequestSerializer :
    AdditionalPropertiesFlatteningSerializer<DashscopeChatCompletionRequest>(DashscopeChatCompletionRequest.serializer())
