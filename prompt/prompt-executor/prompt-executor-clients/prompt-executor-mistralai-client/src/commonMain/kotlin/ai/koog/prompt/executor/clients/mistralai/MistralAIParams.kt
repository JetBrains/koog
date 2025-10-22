package ai.koog.prompt.executor.clients.mistralai

import ai.koog.prompt.params.LLMParams
import kotlinx.serialization.json.JsonElement

internal fun LLMParams.toMistralAIParams(): MistralAIParams {
    if (this is MistralAIParams) return this
    return MistralAIParams(
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
 * MistralAI chat-completions parameters layered on top of [LLMParams].
 *
 * @property temperature What sampling temperature to use, we recommend between 0.0 and 0.7.
 *   Higher values like 0.7 will make the output more random,
 *   while lower values like 0.2 will make it more focused and deterministic.
 *   We generally recommend altering this **or** [topP] but not both.
 * @property maxTokens Maximum number of tokens the model may generate for this response.
 * @property numberOfChoices Number of completions to generate for the prompt (cost scales with N).
 * @property speculation Provider-specific control for speculative decoding / draft acceleration.
 * @property schema JSON Schema to constrain model output (validated when supported).
 * @property toolChoice Controls if/which tool must be called (`none`/`auto`/`required`/specific).
 * @property user stable end-user identifier
 * @property additionalProperties Additional properties that can be used to store custom parameters.
 * @property topP Nucleus sampling, where the model considers the results of the tokens with topP probability mass.
 *   So 0.1 means only the tokens comprising the top 10% probability mass are considered.
 *   We generally recommend altering this or [temperature] but not both.
 * @property stop Stop sequences (0–4 items); generation halts before any of these.
 * @property randomSeed The seed to use for random sampling. If set, different calls will generate deterministic results.
 * @property presencePenalty Number in [-2.0, 2.0]—determines how much the model penalizes the repetition of words or phrases.
 * @property frequencyPenalty Number in [-2.0, 2.0]—penalizes the repetition of words based on their frequency in the generated text.
 * @property parallelToolCalls Allow multiple tool calls in parallel.
 * @property promptMode Allows toggling between the reasoning mode and no system prompt.
 *   When set to `reasoning` the system prompt for reasoning models will be used.
 * @property safePrompt Whether to inject a safety prompt before all conversations.
 */
public class MistralAIParams(
    temperature: Double? = null,
    maxTokens: Int? = null,
    numberOfChoices: Int? = null,
    speculation: String? = null,
    schema: Schema? = null,
    toolChoice: ToolChoice? = null,
    user: String? = null,
    additionalProperties: Map<String, JsonElement>? = null,
    public val topP: Double? = null,
    public val stop: List<String>? = null,
    public val randomSeed: Int? = null,
    public val presencePenalty: Double? = null,
    public val frequencyPenalty: Double? = null,
    public val parallelToolCalls: Boolean? = null,
    public val promptMode: String? = null,
    public val safePrompt: Boolean? = null,
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
        topP: Double? = this.topP,
        stop: List<String>? = this.stop,
        randomSeed: Int? = this.randomSeed,
        presencePenalty: Double? = this.presencePenalty,
        frequencyPenalty: Double? = this.frequencyPenalty,
        parallelToolCalls: Boolean? = this.parallelToolCalls,
        promptMode: String? = this.promptMode,
        safePrompt: Boolean? = this.safePrompt,
    ): MistralAIParams = MistralAIParams(
        temperature = temperature,
        maxTokens = maxTokens,
        numberOfChoices = numberOfChoices,
        speculation = speculation,
        schema = schema,
        toolChoice = toolChoice,
        user = user,
        additionalProperties = additionalProperties,
        topP = topP,
        stop = stop,
        randomSeed = randomSeed,
        presencePenalty = presencePenalty,
        frequencyPenalty = frequencyPenalty,
        parallelToolCalls = parallelToolCalls,
        promptMode = promptMode,
        safePrompt = safePrompt,
    )

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is MistralAIParams -> false
        else ->
            temperature == other.temperature &&
                maxTokens == other.maxTokens &&
                numberOfChoices == other.numberOfChoices &&
                speculation == other.speculation &&
                schema == other.schema &&
                toolChoice == other.toolChoice &&
                user == other.user &&
                additionalProperties == other.additionalProperties &&
                topP == other.topP &&
                stop == other.stop &&
                randomSeed == other.randomSeed &&
                presencePenalty == other.presencePenalty &&
                frequencyPenalty == other.frequencyPenalty &&
                parallelToolCalls == other.parallelToolCalls &&
                promptMode == other.promptMode &&
                safePrompt == other.safePrompt
    }

    override fun hashCode(): Int = listOf(
        temperature, maxTokens, numberOfChoices,
        speculation, schema, toolChoice, user,
        additionalProperties, topP, stop, randomSeed,
        presencePenalty, frequencyPenalty, parallelToolCalls,
        promptMode, safePrompt,
    ).fold(0) { acc, element ->
        31 * acc + (element?.hashCode() ?: 0)
    }

    override fun toString(): String = buildString {
        append("MistralAIParams(")
        append("temperature=$temperature")
        append(", maxTokens=$maxTokens")
        append(", numberOfChoices=$numberOfChoices")
        append(", speculation=$speculation")
        append(", schema=$schema")
        append(", toolChoice=$toolChoice")
        append(", user=$user")
        append(", additionalProperties=$additionalProperties")
        append(", topP=$topP")
        append(", stop=$stop")
        append(", randomSeed=$randomSeed")
        append(", presencePenalty=$presencePenalty")
        append(", frequencyPenalty=$frequencyPenalty")
        append(", parallelToolCalls=$parallelToolCalls")
        append(", promptMode=$promptMode")
        append(", safePrompt=$safePrompt")
        append(")")
    }
}
