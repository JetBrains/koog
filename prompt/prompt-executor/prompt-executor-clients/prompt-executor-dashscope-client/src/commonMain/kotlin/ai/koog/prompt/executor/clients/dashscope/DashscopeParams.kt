package ai.koog.prompt.executor.clients.dashscope

import ai.koog.prompt.params.LLMParams
import kotlinx.serialization.json.JsonElement

internal fun LLMParams.toDashscopeParams(): DashscopeParams {
    if (this is DashscopeParams) return this
    return DashscopeParams(
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
 * DashScope chat-completions parameters layered on top of [LLMParams].
 *
 * @property temperature Sampling temperature in [0.0, 2.0]. Higher ⇒ more random;
 *   lower ⇒ more deterministic. Adjust this **or** [topP], not both.
 * @property maxTokens Maximum number of tokens the model may generate for this response.
 * @property numberOfChoices Number of completions to generate for the prompt (cost scales with N).
 * @property speculation Provider-specific control for speculative decoding / draft acceleration.
 * @property schema JSON Schema to constrain model output (validated when supported).
 * @property toolChoice Controls if/which tool must be called (`none`/`auto`/`required`/specific).
 * @property user A unique identifier for the end-user, which can help DashScope to monitor and detect abuse.
 * @property additionalProperties Additional properties that can be used to store custom parameters.
 * @property enableSearch Whether to enable web search functionality (DashScope-specific).
 * @property parallelToolCalls Whether multiple tool calls can be made in parallel (DashScope-specific).
 * @property enableThinking Whether to enable the model's thinking mode (DashScope-specific).
 * @property frequencyPenalty Number in [-2.0, 2.0]—penalizes frequent tokens to reduce repetition.
 * @property presencePenalty Number in [-2.0, 2.0]—encourages introduction of new tokens/topics.
 * @property logprobs Whether to include log-probabilities for output tokens.
 * @property stop Stop sequences (0–4 items); generation halts before any of these.
 * @property topLogprobs Number of top alternatives per position (0–20). Requires [logprobs] = true.
 * @property topP Nucleus sampling in (0.0, 1.0]; use **instead of** [temperature].
 */
@Suppress("LongParameterList")
public class DashscopeParams(
    temperature: Double? = null,
    maxTokens: Int? = null,
    numberOfChoices: Int? = null,
    speculation: String? = null,
    schema: Schema? = null,
    toolChoice: ToolChoice? = null,
    user: String? = null,
    additionalProperties: Map<String, JsonElement>? = null,
    public val enableSearch: Boolean? = null,
    public val parallelToolCalls: Boolean? = null,
    public val enableThinking: Boolean? = null,
    public val frequencyPenalty: Double? = null,
    public val presencePenalty: Double? = null,
    public val logprobs: Boolean? = null,
    public val stop: List<String>? = null,
    public val topLogprobs: Int? = null,
    public val topP: Double? = null,
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
        require(topP == null || topP in 0.0..1.0) {
            "topP must be in (0.0, 1.0], but was $topP"
        }
        if (topLogprobs != null) {
            require(logprobs == true) {
                "`topLogprobs` requires `logprobs=true`."
            }
            require(topLogprobs in 0..20) {
                "topLogprobs must be in [0, 20], but was $topLogprobs"
            }
        }
        require(frequencyPenalty == null || frequencyPenalty in -2.0..2.0) {
            "frequencyPenalty must be in [-2.0, 2.0], but was $frequencyPenalty"
        }
        require(presencePenalty == null || presencePenalty in -2.0..2.0) {
            "presencePenalty must be in [-2.0, 2.0], but was $presencePenalty"
        }

        // --- Stop sequences ---
        if (stop != null) {
            require(stop.isNotEmpty()) { "stop must not be empty when provided." }
            require(stop.size <= 4) { "stop supports at most 4 sequences, but was ${stop.size}" }
            require(stop.all { it.isNotBlank() }) { "stop sequences must not be blank." }
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
        enableSearch: Boolean? = this.enableSearch,
        parallelToolCalls: Boolean? = this.parallelToolCalls,
        enableThinking: Boolean? = this.enableThinking,
        frequencyPenalty: Double? = this.frequencyPenalty,
        presencePenalty: Double? = this.presencePenalty,
        logprobs: Boolean? = this.logprobs,
        stop: List<String>? = this.stop,
        topLogprobs: Int? = this.topLogprobs,
        topP: Double? = this.topP,
    ): DashscopeParams = DashscopeParams(
        temperature = temperature,
        maxTokens = maxTokens,
        numberOfChoices = numberOfChoices,
        speculation = speculation,
        schema = schema,
        toolChoice = toolChoice,
        user = user,
        additionalProperties = additionalProperties,
        enableSearch = enableSearch,
        parallelToolCalls = parallelToolCalls,
        enableThinking = enableThinking,
        frequencyPenalty = frequencyPenalty,
        presencePenalty = presencePenalty,
        logprobs = logprobs,
        stop = stop,
        topLogprobs = topLogprobs,
        topP = topP,
    )

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is DashscopeParams -> false
        else ->
            temperature == other.temperature &&
                maxTokens == other.maxTokens &&
                numberOfChoices == other.numberOfChoices &&
                speculation == other.speculation &&
                schema == other.schema &&
                toolChoice == other.toolChoice &&
                user == other.user &&
                additionalProperties == other.additionalProperties &&
                enableSearch == other.enableSearch &&
                parallelToolCalls == other.parallelToolCalls &&
                enableThinking == other.enableThinking &&
                frequencyPenalty == other.frequencyPenalty &&
                presencePenalty == other.presencePenalty &&
                logprobs == other.logprobs &&
                stop == other.stop &&
                topLogprobs == other.topLogprobs &&
                topP == other.topP
    }

    override fun hashCode(): Int = listOf(
        temperature, maxTokens, numberOfChoices,
        speculation, schema, toolChoice, user,
        additionalProperties, enableSearch, parallelToolCalls,
        enableThinking, frequencyPenalty, presencePenalty,
        logprobs, stop, topLogprobs, topP,
    ).fold(0) { acc, element ->
        31 * acc + (element?.hashCode() ?: 0)
    }

    override fun toString(): String = buildString {
        append("DashscopeParams(")
        append("temperature=$temperature")
        append(", maxTokens=$maxTokens")
        append(", numberOfChoices=$numberOfChoices")
        append(", speculation=$speculation")
        append(", schema=$schema")
        append(", toolChoice=$toolChoice")
        append(", user=$user")
        append(", additionalProperties=$additionalProperties")
        append(", enableSearch=$enableSearch")
        append(", parallelToolCalls=$parallelToolCalls")
        append(", enableThinking=$enableThinking")
        append(", frequencyPenalty=$frequencyPenalty")
        append(", presencePenalty=$presencePenalty")
        append(", logprobs=$logprobs")
        append(", stop=$stop")
        append(", topLogprobs=$topLogprobs")
        append(", topP=$topP")
        append(")")
    }
}
