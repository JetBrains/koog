package ai.koog.prompt.executor.ollama.client

import ai.koog.prompt.params.LLMParams
import kotlinx.serialization.json.JsonElement

/**
 * Represents the parameters for configuring an Ollama Chat interaction with an LLM.
 *
 * @property temperature Controls the randomness of responses. A lower value results in more deterministic outputs,
 * while a higher value introduces more randomness.
 * @property maxTokens Specifies the maximum number of tokens the LLM should generate in its response. Limits the
 * length of the output.
 * @property numberOfChoices Determines the number of response options the LLM will generate. Useful for exploring
 * multiple possibilities for a given prompt.
 * @property speculation Provides contextual guidance or speculative input for the LLM to consider during response
 * generation.
 * @property schema Specifies the structured response format using a predefined schema. Enables structured data
 * generation in compliance with a schema such as JSON.
 * @property toolChoice Configures the behavior of tool integrations, dictating whether LLM should call specific tools,
 * avoid tools, or decide adaptively.
 * @property user Identifies the end user interacting with the chat. Useful for audits or differentiating inputs
 * in multi-user environments.
 * @property additionalProperties An optional map for passing extra parameters to the LLM. This can include
 * implementation-specific configurations or custom options not directly covered by the primary class fields.
 * @property think Determines whether the LLM interaction enforces thoughtfulness, potentially impacting behavior or
 * processing strategies.
 */
public class OllamaParams(
    temperature: Double? = null,
    maxTokens: Int? = null,
    numberOfChoices: Int? = null,
    speculation: String? = null,
    schema: Schema? = null,
    toolChoice: ToolChoice? = null,
    user: String? = null,
    additionalProperties: Map<String, JsonElement>? = null,
    public val think: Boolean? = null,
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
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        if (!super.equals(other)) return false

        other as OllamaParams

        return think == other.think
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (think?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "OllamaParams(think=$think, temperature=$temperature, maxTokens=$maxTokens, numberOfChoices=$numberOfChoices, speculation=$speculation, schema=$schema, toolChoice=$toolChoice, user=$user, additionalProperties=$additionalProperties)"
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
        think: Boolean? = this.think,
    ): OllamaParams {
        return OllamaParams(
            temperature = temperature,
            maxTokens = maxTokens,
            numberOfChoices = numberOfChoices,
            speculation = speculation,
            schema = schema,
            toolChoice = toolChoice,
            user = user,
            additionalProperties = additionalProperties,
            think = think
        )
    }
}
