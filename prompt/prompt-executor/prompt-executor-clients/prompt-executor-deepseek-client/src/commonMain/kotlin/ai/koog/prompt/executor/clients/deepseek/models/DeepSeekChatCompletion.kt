package ai.koog.prompt.executor.clients.deepseek.models

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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DeepSeek Chat Completions API Request
 *
 * @property messages A list of messages comprising the conversation so far.
 * @property model ID of the model to use. You can use deepseek-chat.
 * @property stream If set, partial message deltas will be sent.
 * Tokens will be sent as data-only server-sent events (SSE) as they become available,
 * with the stream terminated by a data: [DONE] message.
 * @property temperature What sampling temperature to use, between 0 and 2.
 * Higher values like 0.8 will make the output more random,
 * while lower values like 0.2 will make it more focused and deterministic.
 * We generally recommend altering this or [topP] but not both.
 * @property tools A list of tools the model may call.
 * Currently, only functions are supported as a tool.
 * Use this to provide a list of functions the model may generate JSON inputs for.
 * A max of 128 functions is supported.
 * @property toolChoice Controls which (if any) tool is called by the model.
 * - `none` means the model will not call any tool and instead generates a message.
 * - `auto` means the model can pick between generating a message or calling one or more tools.
 * - `required` means the model must call one or more tools.
 * Specifying a particular tool via `{"type": "function", "function": {"name": "my_function"}}` forces the model to call that tool.
 * `none` is the default when no tools are present. `auto` is the default if tools are present.
 * @property topP An alternative to sampling with temperature, called nucleus sampling,
 * where the model considers the results of the tokens with top_p probability mass.
 * So 0.1 means only the tokens comprising the top 10% probability mass are considered.
 * We generally recommend altering this or [temperature] but not both.
 * @property topLogprobs An integer between 0 and 20 specifying the number of most likely tokens to return at each token position,
 * each with an associated log probability. [logprobs] must be set to true if this parameter is used.
 * @property maxTokens Integer between 1 and 8192.
 * The maximum number of tokens that can be generated in the chat completion.
 * The total length of input tokens and generated tokens is limited by the model's context length.
 * If [maxTokens] is not specified, default value 4096 is used.
 * @property frequencyPenalty Number between -2.0 and 2.0.
 * Positive values penalize new tokens based on their existing frequency in the text so far,
 * decreasing the model's likelihood to repeat the same line verbatim.
 * @property presencePenalty Number between -2.0 and 2.0.
 * Positive values penalize new tokens based on whether they appear in the text so far,
 * increasing the model's likelihood to talk about new topics.
 * @property responseFormat An object specifying the format that the model must output.
 * Setting to `{ "type": "json_object" }` enables JSON Output,
 * which guarantees the message the model generates is valid JSON.
 * @property stop Up to 16 sequences where the API will stop generating further tokens.
 * @property logprobs Whether to return log probabilities of the output tokens or not.
 * If true, returns the log probabilities of each output token returned in the content of a message.
 * @property streamOptions Options for streaming response. Only set this when you set stream: true.
 */
@Serializable
internal class DeepSeekChatCompletionRequest(
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
    val streamOptions: OpenAIStreamOptions? = null
) : OpenAIBaseLLMRequest

/**
 * DeepSeek Chat Completion Response
 * https://api-docs.deepseek.com/
 */
@Serializable
public class DeepSeekChatCompletionResponse(
    public val choices: List<OpenAIChoice>,
    override val created: Long,
    override val id: String,
    override val model: String,
    public val systemFingerprint: String,
    @SerialName("object")
    public val objectType: String = "chat.completion",
    public val usage: OpenAIUsage? = null,
) : OpenAIBaseLLMResponse

/**
 * DeepSeek Chat Completion Streaming Response
 */
@Serializable
public class DeepSeekChatCompletionStreamResponse(
    public val choices: List<OpenAIStreamChoice>,
    override val created: Long,
    override val id: String,
    override val model: String,
    public val systemFingerprint: String,
    @SerialName("object")
    public val objectType: String = "chat.completion.chunk",
    public val usage: OpenAIUsage? = null,
) : OpenAIBaseLLMStreamResponse
