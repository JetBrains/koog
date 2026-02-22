package ai.koog.prompt.executor.clients.bedrock.converse

import ai.koog.prompt.params.LLMParams
import aws.sdk.kotlin.services.bedrockruntime.model.PerformanceConfiguration
import aws.sdk.kotlin.services.bedrockruntime.model.PromptVariableValues
import kotlinx.serialization.json.JsonElement

internal fun LLMParams.toBedrockConverseParams(): BedrockConverseParams {
    if (this is BedrockConverseParams) return this
    return BedrockConverseParams(
        temperature = temperature,
        maxTokens = maxTokens,
        numberOfChoices = numberOfChoices,
        speculation = speculation,
        schema = schema,
        toolChoice = toolChoice,
        user = user,
        additionalProperties = additionalProperties,
    )
}

/**
 * Bedrock Converse API parameters layered on top of [LLMParams].
 *
 * [AWS docs](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html)
 *
 * @property temperature Sampling temperature in [0.0, 1.0]. Higher ⇒ more random;
 *   lower ⇒ more deterministic. Use closer to 0.0 for analytical tasks, closer to 1.0
 *   for creative tasks. Adjust this **or** [topP], not both.
 * @property maxTokens Maximum number of tokens the model may generate for this response.
 * @property numberOfChoices Number of completions to generate for the prompt (cost scales with N).
 * @property speculation Provider-specific control for speculative decoding / draft acceleration.
 * @property schema JSON Schema to constrain model output (validated when supported).
 * @property toolChoice Controls if/which tool must be called.
 * @property user not used for Bedrock Converse API
 * @property additionalProperties Additional properties that can be used to store custom parameters.
 * Translates to [aws.sdk.kotlin.services.bedrockruntime.model.ConverseRequest.additionalModelRequestFields].
 * @property topP Nucleus sampling in (0.0, 1.0]; use **instead of** [temperature].
 * Cuts off token sampling at a cumulative probability threshold.
 * @property stopSequences Custom text sequences that cause the model to stop generating.
 * If matched, response will have stop_reason of "stop_sequence".
 * @property performanceConfig Model performance settings for the request, such as latency.
 * @property promptVariables Contains a map of variables in a prompt from Prompt management to objects containing the
 * values to fill in for them when running model invocation. This field is ignored if you don't specify a prompt
 * resource in the `modelId` field.
 * @property requestMetadata Key-value pairs that you can use to filter invocation logs.
 * See Converse API docs for limitations.
 */
@Suppress("LongParameterList")
public class BedrockConverseParams(
    temperature: Double? = null,
    maxTokens: Int? = null,
    numberOfChoices: Int? = null,
    speculation: String? = null,
    schema: Schema? = null,
    toolChoice: ToolChoice? = null,
    user: String? = null,
    additionalProperties: Map<String, JsonElement>? = null,
    public val topP: Double? = null,
    public val stopSequences: List<String>? = null,
    public val performanceConfig: PerformanceConfiguration? = null,
    public val promptVariables: Map<String, PromptVariableValues>? = null,
    public val requestMetadata: Map<String, String>? = null,
) : LLMParams(
    temperature,
    maxTokens,
    numberOfChoices,
    speculation,
    schema,
    toolChoice,
    user,
    additionalProperties,
) {
    init {
        require(temperature == null || temperature in 0.0..1.0) {
            "temperature must be in [0.0, 1.0], but was $temperature"
        }
        require(maxTokens == null || maxTokens >= 1) {
            "maxTokens must be >= 1, but was $maxTokens"
        }
        require(topP == null || topP in 0.0..1.0) {
            "topP must be in [0.0, 1.0], but was $topP"
        }

        // --- Stop sequences ---
        if (stopSequences != null) {
            require(stopSequences.isNotEmpty()) { "stopSequences must not be empty when provided." }
            require(stopSequences.all { it.isNotBlank() }) { "stopSequences must not be blank." }
        }
    }

    /**
     * Creates a copy of this instance with the ability to modify any of its properties.
     */
    public fun copy(
        temperature: Double? = this.temperature,
        maxTokens: Int? = this.maxTokens,
        numberOfChoices: Int? = this.numberOfChoices,
        speculation: String? = this.speculation,
        schema: Schema? = this.schema,
        toolChoice: ToolChoice? = this.toolChoice,
        user: String? = this.user,
        additionalProperties: Map<String, JsonElement>? = this.additionalProperties,
        topP: Double? = this.topP,
        stopSequences: List<String>? = this.stopSequences,
    ): BedrockConverseParams = BedrockConverseParams(
        temperature = temperature,
        maxTokens = maxTokens,
        numberOfChoices = numberOfChoices,
        speculation = speculation,
        schema = schema,
        toolChoice = toolChoice,
        user = user,
        additionalProperties = additionalProperties,
        topP = topP,
        stopSequences = stopSequences,
    )

    override fun equals(other: Any?): Boolean = when {
        this === other -> true

        other !is BedrockConverseParams -> false

        else ->
            @Suppress("DuplicatedCode")
            temperature == other.temperature &&
                maxTokens == other.maxTokens &&
                numberOfChoices == other.numberOfChoices &&
                speculation == other.speculation &&
                schema == other.schema &&
                toolChoice == other.toolChoice &&
                user == other.user &&
                additionalProperties == other.additionalProperties &&
                topP == other.topP &&
                stopSequences == other.stopSequences &&
                performanceConfig == other.performanceConfig &&
                promptVariables == other.promptVariables &&
                requestMetadata == other.requestMetadata
    }

    override fun hashCode(): Int = listOf(
        temperature, maxTokens, numberOfChoices, speculation, schema, toolChoice, user, additionalProperties, topP,
        stopSequences, performanceConfig, promptVariables, requestMetadata
    ).fold(0) { acc, element ->
        31 * acc + (element?.hashCode() ?: 0)
    }

    @Suppress("DuplicatedCode")
    override fun toString(): String = buildString {
        append("BedrockConverseParams(")
        append("temperature=$temperature")
        append(", maxTokens=$maxTokens")
        append(", numberOfChoices=$numberOfChoices")
        append(", speculation=$speculation")
        append(", schema=$schema")
        append(", toolChoice=$toolChoice")
        append(", user=$user")
        append(", additionalProperties=$additionalProperties")
        append(", topP=$topP")
        append(", stopSequences=$stopSequences")
        append(", performanceConfig=$performanceConfig")
        append(", promptVariables=$promptVariables")
        append(", requestMetadata=$requestMetadata")
        append(")")
    }
}
