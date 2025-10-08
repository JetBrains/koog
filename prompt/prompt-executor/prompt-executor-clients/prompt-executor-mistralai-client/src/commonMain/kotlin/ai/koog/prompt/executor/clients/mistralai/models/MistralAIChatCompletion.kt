package ai.koog.prompt.executor.clients.mistralai.models

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
import ai.koog.prompt.executor.clients.serialization.AdditionalPropertiesFlatteningSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mistral AI Chat Completion Request
 *
 * @property model ID of the model to use.
 * @property temperature What sampling temperature to use, we recommend between 0.0 and 0.7.
 * Higher values like 0.7 will make the output more random,
 * while lower values like 0.2 will make it more focused and deterministic.
 * We generally recommend altering this or [topP] but not both.
 * The default value varies depending on the model you are targeting.
 * @property topP Nucleus sampling, where the model considers the results of the tokens with `topP` probability mass.
 * So 0.1 means only the tokens comprising the top 10% probability mass are considered.
 * We generally recommend altering this or [temperature] but not both.
 * @property maxTokens The maximum number of tokens to generate in the completion.
 * The token count of your prompt plus `maxTokens` cannot exceed the model's context length.
 * @property stream Whether to stream back partial progress.
 * If set, tokens will be sent as data-only server-side events as they become available,
 * with the stream terminated by a data: [DONE] message.
 * Otherwise, the server will hold the request open until the timeout or until completion,
 * with the response containing the full result as JSON.
 * @property stop Stop generation if this token is detected.
 * Or if one of these tokens is detected when providing an array
 * @property randomSeed The seed to use for random sampling.
 * If set, different calls will generate deterministic results.
 * @property messages The prompt(s) to generate completions for, encoded as a list of dict with role and content.
 * @property responseFormat
 * @property tools
 * @property toolChoice
 * @property presencePenalty determines how much the model penalizes the repetition of words or phrases.
 * A higher presence penalty encourages the model to use a wider variety of words and phrases,
 * making the output more diverse and creative.
 * @property frequencyPenalty penalizes the repetition of words based on their frequency in the generated text.
 * A higher frequency penalty discourages the model from repeating words that have already appeared frequently in the output,
 * promoting diversity and reducing repetition.
 * @property numberOfChoices Number of completions to return for each request, input tokens are only billed once.
 * @property prediction Enable users to specify expected results,
 * optimizing response times by leveraging known or predictable content.
 * This approach is especially effective for updating text documents or code files with minimal changes,
 * reducing latency while maintaining high-quality results.
 * @property parallelToolCalls
 * @property promptMode Allows toggling between the reasoning mode and no system prompt.
 * When set to `reasoning` the system prompt for reasoning models will be used.
 * @property safePrompt Whether to inject a safety prompt before all conversations.
 */
@Serializable
internal class MistralAIChatCompletionRequest(
    override val model: String,
    override val temperature: Double? = null,
    override val topP: Double? = null,
    val maxTokens: Int? = null,
    override val stream: Boolean? = null,
    val stop: List<String>? = null,
    val randomSeed: Int? = null,
    val messages: List<OpenAIMessage>,
    val responseFormat: OpenAIResponseFormat? = null,
    val tools: List<OpenAITool>? = null,
    val toolChoice: OpenAIToolChoice? = null,
    val presencePenalty: Double? = null,
    val frequencyPenalty: Double? = null,
    @SerialName("n")
    val numberOfChoices: Int? = null,
    val prediction: OpenAIStaticContent? = null,
    val parallelToolCalls: Boolean? = null,
    val promptMode: String? = null,
    val safePrompt: Boolean? = null,
    override val topLogprobs: Int? = null,
    val additionalProperties: Map<String, kotlinx.serialization.json.JsonElement>? = null,
) : OpenAIBaseLLMRequest

/**
 * Mistral AI Chat Completion Response
 *
 */
@Serializable
public class MistralAIChatCompletionResponse(
    override val id: String,
    @SerialName("object")
    public val objectType: String,
    override val model: String,
    public val usage: MistralAIUsage,
    override val created: Long,
    public val choices: List<OpenAIChoice>
) : OpenAIBaseLLMResponse

/**
 * @property completionTokens Number of tokens in the generated completion.
 * @property promptTokens Number of tokens in the prompt.
 * @property totalTokens Total number of tokens used in the request (prompt + completion).
 * @property promptAudioSeconds
 */
@Serializable
public class MistralAIUsage(
    @SerialName("prompt_tokens")
    public val promptTokens: Int,
    @SerialName("completion_tokens")
    public val completionTokens: Int,
    @SerialName("total_tokens")
    public val totalTokens: Int,
    @SerialName("prompt_audio_seconds")
    public val promptAudioSeconds: Int? = null,
)

/**
 * Mistral AI Chat Completion Streaming Response
 */
@Serializable
public class MistralAIChatCompletionStreamResponse(
    public val choices: List<OpenAIStreamChoice>,
    override val created: Long,
    override val id: String,
    override val model: String,
    public val systemFingerprint: String,
    @SerialName("object")
    public val objectType: String,
    public val usage: MistralAIUsage? = null,
) : OpenAIBaseLLMStreamResponse

internal object MistralAIChatCompletionRequestSerializer :
    AdditionalPropertiesFlatteningSerializer<MistralAIChatCompletionRequest>(MistralAIChatCompletionRequest.serializer())
