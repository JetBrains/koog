package ai.koog.prompt.executor.clients.openrouter

import ai.koog.prompt.executor.clients.openrouter.models.ProviderPreferences
import ai.koog.prompt.params.LLMParams
import kotlinx.serialization.json.JsonElement

internal fun LLMParams.toOpenRouterParams(): OpenRouterParams {
    if (this is OpenRouterParams) return this
    return OpenRouterParams(
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
 * OpenRouter chat-completions parameters layered on top of [LLMParams].
 *
 * @property temperature Sampling temperature in [0.0, 2.0]. Higher ⇒ more random;
 *   lower ⇒ more deterministic. Adjust this **or** [topP], not both.
 * @property maxTokens Maximum number of tokens the model may generate for this response.
 * @property numberOfChoices Number of completions to generate for the prompt (cost scales with N).
 * @property speculation Provider-specific control for speculative decoding / draft acceleration.
 * @property schema JSON Schema to constrain model output (validated when supported).
 * @property toolChoice Controls if/which tool must be called (`none`/`auto`/`required`/specific).
 * @property user stable end-user identifier
 * @property additionalProperties Additional properties that can be used to store custom parameters.
 * @property frequencyPenalty Number in [-2.0, 2.0]—penalizes frequent tokens to reduce repetition.
 * @property presencePenalty Number in [-2.0, 2.0]—encourages introduction of new tokens/topics.
 * @property logprobs Whether to include log-probabilities for output tokens.
 * @property stop Stop sequences (0–4 items); generation halts before any of these.
 * @property topLogprobs Number of top alternatives per position (0–20). Requires [logprobs] = true.
 * @property topP Nucleus sampling in (0.0, 1.0]; use **instead of** [temperature].
 * @property topK Number of top tokens to consider when generating output (min 1).
 * @property repetitionPenalty Number in (0.0, 2.0] — penalizes token repetition.
 * @property minP Minimum cumulative probability for token inclusion in sampling.
 * @property topA Temperature scaling based on marginal probability gain.
 * @property transforms List of context transforms.
 *   Defines how context is transformed when it exceeds the model's token limit.
 *   Default is ["middle-out"] which truncates from the middle of the prompt.
 *   Use empty list [] for no transformations.
 * @property models List of allowed models for this request.
 * @property route Request routing identifier.
 * @property provider Model provider preferences.
 */
public open class OpenRouterParams(
    temperature: Double? = null,
    maxTokens: Int? = null,
    numberOfChoices: Int? = null,
    speculation: String? = null,
    schema: Schema? = null,
    toolChoice: ToolChoice? = null,
    user: String? = null,
    additionalProperties: Map<String, JsonElement>? = null,
    public val frequencyPenalty: Double? = null,
    public val presencePenalty: Double? = null,
    public val logprobs: Boolean? = null,
    public val stop: List<String>? = null,
    public val topLogprobs: Int? = null,
    public val topP: Double? = null,
    public val topK: Int? = null,
    public val repetitionPenalty: Double? = null,
    public val minP: Double? = null,
    public val topA: Double? = null,
    public val transforms: List<String>? = null,
    public val models: List<String>? = null,
    public val route: String? = null,
    public val provider: ProviderPreferences? = null,
) : LLMParams(
    temperature,
    maxTokens,
    numberOfChoices,
    speculation,
    schema,
    toolChoice,
    user,
    additionalProperties
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
        require(repetitionPenalty == null || repetitionPenalty in 0.0..2.0) {
            "repetitionPenalty must be in (0.0, 2.0], but was $repetitionPenalty"
        }
        require(topK == null || topK >= 1) {
            "topK must be in [1, Infinity], but was $topK"
        }
        require(minP == null || minP in 0.0..1.0) {
            "minP must be in [0.0, 1.0], but was $minP"
        }
        require(topA == null || topA in 0.0..1.0) {
            "topA must be in [0.0, 1.0], but was $topA"
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
        frequencyPenalty: Double? = this.frequencyPenalty,
        presencePenalty: Double? = this.presencePenalty,
        logprobs: Boolean? = this.logprobs,
        stop: List<String>? = this.stop,
        topLogprobs: Int? = this.topLogprobs,
        topP: Double? = this.topP,
        topK: Int? = this.topK,
        repetitionPenalty: Double? = this.repetitionPenalty,
        minP: Double? = this.minP,
        topA: Double? = this.topA,
        transforms: List<String>? = this.transforms,
        models: List<String>? = this.models,
        route: String? = this.route,
        provider: ProviderPreferences? = this.provider,
    ): OpenRouterParams = OpenRouterParams(
        temperature = temperature,
        maxTokens = maxTokens,
        numberOfChoices = numberOfChoices,
        speculation = speculation,
        schema = schema,
        toolChoice = toolChoice,
        user = user,
        additionalProperties = additionalProperties,
        frequencyPenalty = frequencyPenalty,
        presencePenalty = presencePenalty,
        logprobs = logprobs,
        stop = stop,
        topLogprobs = topLogprobs,
        topP = topP,
        topK = topK,
        repetitionPenalty = repetitionPenalty,
        minP = minP,
        topA = topA,
        transforms = transforms,
        models = models,
        route = route,
        provider = provider,
    )

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is OpenRouterParams -> false
        else ->
            temperature == other.temperature &&
                maxTokens == other.maxTokens &&
                numberOfChoices == other.numberOfChoices &&
                speculation == other.speculation &&
                schema == other.schema &&
                toolChoice == other.toolChoice &&
                user == other.user &&
                additionalProperties == other.additionalProperties &&
                frequencyPenalty == other.frequencyPenalty &&
                presencePenalty == other.presencePenalty &&
                logprobs == other.logprobs &&
                stop == other.stop &&
                topLogprobs == other.topLogprobs &&
                topP == other.topP &&
                topK == other.topK &&
                repetitionPenalty == other.repetitionPenalty &&
                minP == other.minP &&
                topA == other.topA &&
                transforms == other.transforms &&
                models == other.models &&
                route == other.route &&
                provider == other.provider
    }

    override fun hashCode(): Int = listOf(
        temperature, maxTokens, numberOfChoices,
        speculation, schema, toolChoice, user,
        additionalProperties, frequencyPenalty, presencePenalty,
        logprobs, stop, topLogprobs, topP,
        topK, repetitionPenalty, minP,
        topA, transforms, models, route, provider,
    ).fold(0) { acc, element ->
        31 * acc + (element?.hashCode() ?: 0)
    }

    override fun toString(): String = buildString {
        append("OpenRouterParams(")
        append("temperature=$temperature")
        append(", maxTokens=$maxTokens")
        append(", numberOfChoices=$numberOfChoices")
        append(", speculation=$speculation")
        append(", schema=$schema")
        append(", toolChoice=$toolChoice")
        append(", user=$user")
        append(", additionalProperties=$additionalProperties")
        append(", frequencyPenalty=$frequencyPenalty")
        append(", presencePenalty=$presencePenalty")
        append(", logprobs=$logprobs")
        append(", stop=$stop")
        append(", topLogprobs=$topLogprobs")
        append(", topP=$topP")
        append(", topK=$topK")
        append(", repetitionPenalty=$repetitionPenalty")
        append(", minP=$minP")
        append(", topA=$topA")
        append(", transforms=$transforms")
        append(", models=$models")
        append(", route=$route")
        append(", provider=$provider")
        append(")")
    }
}
