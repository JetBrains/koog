package ai.koog.prompt.structure

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Message
import ai.koog.prompt.structure.annotations.InternalStructuredOutputApi
import ai.koog.prompt.structure.json.JsonStructure
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.JsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.reflect.KType

/**
 * Represents a container for structured data parsed from a response message.
 *
 * This class is designed to encapsulate both the parsed structured output and the original raw
 * text as returned from a processing step, such as a language model execution.
 *
 * @param T The type of the structured data contained within this response.
 * @property data The parsed structured data corresponding to the specific schema.
 * @property structure The structure used for the response.
 * @property message The original assistant message from which the structure was parsed.
 */
public data class StructuredResponse<T>(
    val data: T,
    val structure: Structure<T, *>,
    val message: Message.Assistant
)

/**
 * Configures structured output behavior.
 * Defines which structures in which modes should be used for each provider when requesting a structured output.
 *
 * @property default Fallback [StructuredRequest] to be used when there's no suitable structure found in [byProvider]
 * for a requested [LLMProvider]. Defaults to `null`, meaning structured output would fail with error in such a case.
 *
 * @property byProvider A map matching [LLMProvider] to compatible [StructuredRequest] definitions. Each provider may
 * require different schema formats. E.g. for [JsonStructure] this means you have to use the appropriate
 * [JsonSchemaGenerator] implementation for each provider for [StructuredRequest.Native], or fallback to [StructuredRequest.Manual]
 *
 * @property fixingParser Optional parser that handles malformed responses by using an auxiliary LLM to
 * intelligently fix parsing errors. When specified, parsing errors trigger additional
 * LLM calls with error context to attempt correction of the structure format.
 */
public data class StructuredRequestConfig<T>(
    public val default: StructuredRequest<T>? = null,
    public val byProvider: Map<LLMProvider, StructuredRequest<T>> = emptyMap(),
    public val fixingParser: StructureFixingParser? = null
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
 * Executes a prompt with structured output, enhancing it with schema instructions or native structured output
 * parameter, and parses the response into the defined structure.
 *
 * **Note**: While many language models advertise support for structured output via JSON schema,
 * the actual level of support varies between models and even between versions
 * of the same model. Some models may produce malformed outputs or deviate from
 * the schema in subtle ways, especially with complex structures like polymorphic types.
 *
 * @param prompt The prompt to be executed.
 * @param model LLM to execute requests.
 * @param config A configuration defining structures and behavior.
 *
 * @return [kotlin.Result] with parsed [StructuredResponse] or error.
 */
public suspend fun <T> PromptExecutor.executeStructured(
    prompt: Prompt,
    model: LLModel,
    config: StructuredRequestConfig<T>,
): Result<StructuredResponse<T>> {
    val updatedPrompt = config.updatePrompt(model, prompt)
    val response = this.execute(prompt = updatedPrompt, model = model).filterNot { it is Message.Reasoning }.single()

    return runCatching {
        require(response is Message.Assistant) { "Response for structured output must be an assistant message, got ${response::class.simpleName} instead" }

        parseResponseToStructuredResponse(response, config, model)
    }
}

/**
 * Registered mapping of providers to their respective known simple JSON schema format generators.
 * The registration is supposed to be done by the LLM clients when they are loaded, to communicate their custom formats.
 *
 * Used to attempt to get a proper generator implicitly in the simple version of [executeStructured] (that does not accept [StructuredRequest] explicitly)
 * to attempt to generate an appropriate schema for the passed [KType].
 */
@InternalStructuredOutputApi
public val RegisteredBasicJsonSchemaGenerators: MutableMap<LLMProvider, BasicJsonSchemaGenerator> = mutableMapOf()

/**
 * Registered mapping of providers to their respective known full JSON schema format generators.
 * The registration is supposed to be done by the LLM clients on their initialization, to communicate their custom formats.
 *
 * Used to attempt to get a proper generator implicitly in the simple version of [executeStructured] (that does not accept [StructuredRequest] explicitly)
 * to attempt to generate an appropriate schema for the passed [KType].
 */
@InternalStructuredOutputApi
public val RegisteredStandardJsonSchemaGenerators: MutableMap<LLMProvider, StandardJsonSchemaGenerator> = mutableMapOf()

/**
 * Executes a prompt with structured output, enhancing it with schema instructions or native structured output
 * parameter, and parses the response into the defined structure.
 *
 * This is a simple version of the full `executeStructured`. Unlike the full version, it does not require specifying
 * struct definitions and structured output modes manually. It attempts to find the best approach to provide a structured
 * output based on the defined [model] capabilities.
 *
 * For example, it chooses which JSON schema to use (simple or full) and with which mode (native or manual).
 *
 * @param prompt The prompt to be executed.
 * @param model LLM to execute requests.
 * @param serializer Serializer for the requested structure type.
 * @param examples Optional list of examples in case manual mode will be used. These examples might help the model to
 * understand the format better.
 * @param fixingParser Optional parser that handles malformed responses by using an auxiliary LLM to
 * intelligently fix parsing errors. When specified, parsing errors trigger additional
 * LLM calls with error context to attempt correction of the structure format.
 *
 * @return [kotlin.Result] with parsed [StructuredResponse] or error.
 */
@OptIn(InternalStructuredOutputApi::class)
public suspend fun <T> PromptExecutor.executeStructured(
    prompt: Prompt,
    model: LLModel,
    serializer: KSerializer<T>,
    examples: List<T> = emptyList(),
    fixingParser: StructureFixingParser? = null,
): Result<StructuredResponse<T>> {
    @Suppress("UNCHECKED_CAST")
    val id = serializer.descriptor.serialName.substringAfterLast(".")

    val structuredRequest = when {
        LLMCapability.Schema.JSON.Standard in model.capabilities -> StructuredRequest.Native(
            JsonStructure.create(
                id = id,
                serializer = serializer,
                schemaGenerator = RegisteredStandardJsonSchemaGenerators[model.provider] ?: StandardJsonSchemaGenerator
            )
        )

        LLMCapability.Schema.JSON.Basic in model.capabilities -> StructuredRequest.Native(
            JsonStructure.create(
                id = id,
                serializer = serializer,
                schemaGenerator = RegisteredBasicJsonSchemaGenerators[model.provider] ?: BasicJsonSchemaGenerator
            )
        )

        else -> StructuredRequest.Manual(
            JsonStructure.create(
                id = id,
                serializer = serializer,
                schemaGenerator = StandardJsonSchemaGenerator,
                examples = examples,
            )
        )
    }

    return executeStructured(
        prompt = prompt,
        model = model,
        config = StructuredRequestConfig(
            default = structuredRequest,
            fixingParser = fixingParser,
        )
    )
}

/**
 * Executes a prompt with structured output, enhancing it with schema instructions or native structured output
 * parameter, and parses the response into the defined structure.
 *
 * This is a simple version of the full `executeStructured`. Unlike the full version, it does not require specifying
 * struct definitions and structured output modes manually. It attempts to find the best approach to provide a structured
 * output based on the defined [model] capabilities.
 *
 * For example, it chooses which JSON schema to use (simple or full) and with which mode (native or manual).
 *
 * @param T The structure to request.
 * @param prompt The prompt to be executed.
 * @param model LLM to execute requests.
 * @param examples Optional list of examples in case manual mode will be used. These examples might help the model to
 * understand the format better.
 * @param fixingParser Optional parser that handles malformed responses by using an auxiliary LLM to
 * intelligently fix parsing errors. When specified, parsing errors trigger additional
 * LLM calls with error context to attempt correction of the structure format.
 *
 * @return [kotlin.Result] with parsed [StructuredResponse] or error.
 */
public suspend inline fun <reified T> PromptExecutor.executeStructured(
    prompt: Prompt,
    model: LLModel,
    examples: List<T> = emptyList(),
    fixingParser: StructureFixingParser? = null,
): Result<StructuredResponse<T>> {
    return executeStructured(
        prompt = prompt,
        model = model,
        serializer = serializer<T>(),
        examples = examples,
        fixingParser = fixingParser,
    )
}

/**
 * Parses a structured response from the assistant message using the provided structured output configuration
 * and language model. If a fixing parser is specified in the configuration, it will be used; otherwise,
 * the structure will be parsed directly.
 *
 * @param T The type of the structured output.
 * @param response The assistant's response message to be parsed.
 * @param config The structured output configuration defining how the response should be parsed.
 * @param model The language model to be used for parsing the structured output.
 * @return A `StructuredResponse<T>` containing the parsed structure and the original assistant message.
 */
public suspend fun <T> PromptExecutor.parseResponseToStructuredResponse(
    response: Message.Assistant,
    config: StructuredRequestConfig<T>,
    model: LLModel
): StructuredResponse<T> {
    // Use fixingParser if provided, otherwise parse directly
    val structure = config.structure(model)
    val structureResponse = config.fixingParser
        ?.parse(this, structure, response.content)
        ?: structure.parse(response.content)

    return StructuredResponse(
        data = structureResponse,
        structure = structure,
        message = response
    )
}
