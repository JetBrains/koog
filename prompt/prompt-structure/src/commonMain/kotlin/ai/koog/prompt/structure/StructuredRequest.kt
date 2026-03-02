package ai.koog.prompt.structure

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.structure.json.JsonStructure

/**
 * Defines how structured outputs should be generated.
 *
 * Can be [StructuredRequest.Manual] or [StructuredRequest.Native]
 *
 * @param T The type of structured data.
 */
public sealed interface StructuredRequest<T> {
    /**
     * The definition of a structure.
     */
    public val structure: Structure<T, *>

    /**
     * Instructs the model to produce structured output through explicit prompting.
     *
     * Uses an additional user message containing [Structure.definition] to guide
     * the model in generating correctly formatted responses.
     *
     * @property structure The structure definition to be used in output generation.
     */
    public data class Manual<T>(override val structure: Structure<T, *>) : StructuredRequest<T>

    /**
     * Leverages a model's built-in structured output capabilities.
     *
     * Uses [Structure.schema] to define the expected response format through the model's
     * native structured output functionality.
     *
     * Note: [Structure.examples] are not used with this mode, only the schema is sent via parameters.
     *
     * @property structure The structure definition to be used in output generation.
     */
    public data class Native<T>(override val structure: Structure<T, *>) : StructuredRequest<T>
}

/**
 * Configures structured output behavior.
 * Defines which structures in which modes should be used for each provider when requesting a structured output.
 *
 * @property default Fallback [StructuredRequest] to be used when there's no suitable structure found in [byProvider]
 * for a requested [LLMProvider]. Defaults to `null`, meaning structured output would fail with error in such a case.
 *
 * @property byProvider A map matching [LLMProvider] to compatible [StructuredRequest] definitions. Each provider may
 * require different schema formats. E.g. for [JsonStructure] this means you have to use the appropriate
 * [ai.koog.prompt.structure.json.generator.JsonSchemaGenerator] implementation for each provider for [StructuredRequest.Native], or fallback to [StructuredRequest.Manual]
 *
 */
public data class StructuredRequestConfig<T>(
    public val default: StructuredRequest<T>? = null,
    public val byProvider: Map<LLMProvider, StructuredRequest<T>> = emptyMap(),
) {
    /**
     * Updates a given prompt to configure structured output using the specified large language model (LLM).
     * Depending on the model's support for structured outputs, the prompt is updated either manually or natively.
     *
     * @param model The large language model (LLModel) used to determine the structured output configuration.
     * @param prompt The original prompt to be updated with the structured output configuration.
     * @return A new prompt reflecting the updated structured output configuration.
     */
    public fun updatePrompt(model: LLModel, prompt: Prompt): Prompt {
        return when (val mode = structuredRequest(model)) {
            // Don't set schema parameter in prompt and coerce the model manually with user message to provide a structured response.
            is StructuredRequest.Manual -> {
                prompt(prompt) {
                    user(
                        markdown {
                            StructuredOutputPrompts.outputInstructionPrompt(this, mode.structure)
                        }
                    )
                }
            }

            // Rely on built-in model capabilities to provide structured response.
            is StructuredRequest.Native -> {
                prompt(prompt) {
                    // If examples are supplied, append them
                    if (mode.structure.examples.isNotEmpty()) {
                        user {
                            mode.structure.examples(this)
                        }
                    }
                }.withUpdatedParams { schema = mode.structure.schema }
            }
        }
    }

    /**
     * Retrieves the structured data configuration for a specific large language model (LLM).
     *
     * The method determines the appropriate structured data setup based on the given LLM
     * instance, ensuring compatibility with the model's provider and capabilities.
     *
     * @param model The large language model (LLM) instance used to identify the structured data configuration.
     * @return The structured data configuration represented as a `StructuredData` instance.
     */
    public fun structure(model: LLModel): Structure<T, *> {
        return structuredRequest(model).structure
    }

    /**
     * Retrieves the structured output configuration for a specific large language model (LLM).
     *
     * The method determines the appropriate structured output instance based on the model's provider.
     * If no specific configuration is found for the provider, it falls back to the default configuration.
     * Throws an exception if no default configuration is available.
     *
     * @param model The large language model (LLM) used to identify the structured output configuration.
     * @return An instance of `StructuredOutput` that represents the structured output configuration for the model.
     * @throws IllegalArgumentException if no configuration is found for the provider and no default configuration is set.
     */
    private fun structuredRequest(model: LLModel): StructuredRequest<T> {
        return byProvider[model.provider]
            ?: default
            ?: throw IllegalArgumentException("No structure found for provider ${model.provider}")
    }
}
