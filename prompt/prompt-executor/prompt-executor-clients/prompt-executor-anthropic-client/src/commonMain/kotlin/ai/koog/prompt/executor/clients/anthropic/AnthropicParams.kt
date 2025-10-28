package ai.koog.prompt.executor.clients.anthropic

import ai.koog.prompt.executor.clients.anthropic.models.AnthropicMCPServerURLDefinition
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicServiceTier
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicThinking
import ai.koog.prompt.params.LLMParams
import kotlinx.serialization.json.JsonElement

internal fun LLMParams.toAnthropicParams(): AnthropicParams {
    if (this is AnthropicParams) return this
    return AnthropicParams(
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
 * Anthropic Messages API parameters layered on top of [LLMParams].
 *
 * @property temperature Sampling temperature in [0.0, 1.0]. Higher ⇒ more random;
 *   lower ⇒ more deterministic. Use closer to 0.0 for analytical tasks, closer to 1.0
 *   for creative tasks. Adjust this **or** [topP], not both.
 * @property maxTokens Maximum number of tokens the model may generate for this response.
 * @property numberOfChoices Number of completions to generate for the prompt (cost scales with N).
 * @property speculation Provider-specific control for speculative decoding / draft acceleration.
 * @property schema JSON Schema to constrain model output (validated when supported).
 * @property toolChoice Controls if/which tool must be called (`none`/`auto`/`required`/specific).
 * @property user not used for Anthropic
 * @property additionalProperties Additional properties that can be used to store custom parameters.
 * @property topP Nucleus sampling in (0.0, 1.0]; use **instead of** [temperature].
 * Cuts off token sampling at a cumulative probability threshold.
 * @property topK Sample only from top K options for each subsequent token (≥ 0). Recommended for advanced use cases.
 * @property stopSequences Custom text sequences that cause the model to stop generating.
 * If matched, response will have stop_reason of "stop_sequence".
 * @property container Container identifier for reuse across requests.
 * @property mcpServers MCP servers to be used in this request
 * @property serviceTier Determines whether to use priority capacity (if available) or standard capacity for this request.
 * @property thinking Configuration for enabling Claude's extended thinking.
 */
@Suppress("LongParameterList")
public class AnthropicParams(
    temperature: Double? = null,
    maxTokens: Int? = null,
    numberOfChoices: Int? = null,
    speculation: String? = null,
    schema: Schema? = null,
    toolChoice: ToolChoice? = null,
    user: String? = null,
    additionalProperties: Map<String, JsonElement>? = null,
    public val topP: Double? = null,
    public val topK: Int? = null,
    public val stopSequences: List<String>? = null,
    public val container: String? = null,
    public val mcpServers: List<AnthropicMCPServerURLDefinition>? = null,
    public val serviceTier: AnthropicServiceTier? = null,
    public val thinking: AnthropicThinking? = null,
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
        require(topK == null || topK >= 0) {
            "topK must be >= 0, but was $topK"
        }
        require(container == null || container.isNotBlank()) {
            "container must be non-blank when provided"
        }

        // --- Stop sequences ---
        if (stopSequences != null) {
            require(stopSequences.isNotEmpty()) { "stopSequences must not be empty when provided." }
            require(stopSequences.all { it.isNotBlank() }) { "stopSequences must not be blank." }
        }

        // --- MCP servers ---
        if (mcpServers != null) {
            require(mcpServers.size <= 20) {
                "mcpServers supports at most 20 servers, but was ${mcpServers.size}"
            }
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
        topK: Int? = this.topK,
        stopSequences: List<String>? = this.stopSequences,
        container: String? = this.container,
        mcpServers: List<AnthropicMCPServerURLDefinition>? = this.mcpServers,
        serviceTier: AnthropicServiceTier? = this.serviceTier,
        thinking: AnthropicThinking? = this.thinking,
    ): AnthropicParams = AnthropicParams(
        temperature = temperature,
        maxTokens = maxTokens,
        numberOfChoices = numberOfChoices,
        speculation = speculation,
        schema = schema,
        toolChoice = toolChoice,
        user = user,
        additionalProperties = additionalProperties,
        topP = topP,
        topK = topK,
        stopSequences = stopSequences,
        container = container,
        mcpServers = mcpServers,
        serviceTier = serviceTier,
        thinking = thinking,
    )

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is AnthropicParams -> false
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
                topK == other.topK &&
                stopSequences == other.stopSequences &&
                container == other.container &&
                mcpServers == other.mcpServers &&
                serviceTier == other.serviceTier &&
                thinking == other.thinking
    }

    override fun hashCode(): Int = listOf(
        temperature, maxTokens, numberOfChoices,
        speculation, schema, toolChoice, user,
        additionalProperties, topP, topK,
        stopSequences, container, mcpServers,
        serviceTier, thinking
    ).fold(0) { acc, element ->
        31 * acc + (element?.hashCode() ?: 0)
    }

    override fun toString(): String = buildString {
        append("AnthropicParams(")
        append("temperature=$temperature")
        append(", maxTokens=$maxTokens")
        append(", numberOfChoices=$numberOfChoices")
        append(", speculation=$speculation")
        append(", schema=$schema")
        append(", toolChoice=$toolChoice")
        append(", user=$user")
        append(", additionalProperties=$additionalProperties")
        append(", topP=$topP")
        append(", topK=$topK")
        append(", stopSequences=$stopSequences")
        append(", container=$container")
        append(", mcpServers=$mcpServers")
        append(", serviceTier=$serviceTier")
        append(", thinking=$thinking")
        append(")")
    }
}
