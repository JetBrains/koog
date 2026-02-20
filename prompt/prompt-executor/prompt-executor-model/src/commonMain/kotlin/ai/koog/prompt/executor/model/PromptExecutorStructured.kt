package ai.koog.prompt.executor.model

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.structure.StructuredRequest
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.prompt.structure.StructuredResponse
import ai.koog.prompt.structure.annotations.InternalStructuredOutputApi
import ai.koog.prompt.structure.json.JsonStructure
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

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
    fixingParser: StructureFixingParser? = null
): Result<StructuredResponse<T>> {
    val updatedPrompt = config.updatePrompt(model, prompt)
    val response = this.execute(prompt = updatedPrompt, model = model).filterNot { it is Message.Reasoning }.single()

    return runCatching {
        require(response is Message.Assistant) { "Response for structured output must be an assistant message, got ${response::class.simpleName} instead" }

        parseResponseToStructuredResponse(response, config, model, fixingParser)
    }
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
 * @param prompt The prompt to be executed.
 * @param model LLM to execute requests.
 * @param serializer Serializer for the requested structure type.
 * @param examples Optional list of examples in case manual mode will be used. These examples might help the model to
 * understand the format better.
 * @param fixingParser Optional parser that handles malformed responses by using an auxiliary LLM to
 * intelligently fix parsing errors. When specified, parsing errors trigger additional
 * LLM calls with error context to attempt correction of the structure format.
 *
 * @return [Result] with parsed [ai.koog.prompt.structure.StructuredResponse] or error.
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

    val structuredRequest = getStructuredRequest(
        model,
        id,
        getStandardJsonSchemaGenerator(model),
        getBasicJsonSchemaGenerator(model),
        serializer,
        examples
    )

    return executeStructured(
        prompt = prompt,
        model = model,
        config = StructuredRequestConfig(
            default = structuredRequest,
        ),
        fixingParser = fixingParser
    )
}

private fun <T> getStructuredRequest(
    model: LLModel,
    id: String,
    standardJsonSchemaGenerator: StandardJsonSchemaGenerator,
    basicJsonSchemaGenerator: BasicJsonSchemaGenerator,
    serializer: KSerializer<T>,
    examples: List<T>
): StructuredRequest<T> {
    val structuredRequest = when {
        model.supports(LLMCapability.Schema.JSON.Standard) -> StructuredRequest.Native(
            JsonStructure.create(
                id = id,
                serializer = serializer,
                schemaGenerator = standardJsonSchemaGenerator
            )
        )

        model.supports(LLMCapability.Schema.JSON.Basic) -> StructuredRequest.Native(
            JsonStructure.create(
                id = id,
                serializer = serializer,
                schemaGenerator = basicJsonSchemaGenerator
            )
        )

        else -> StructuredRequest.Manual(
            JsonStructure.create(
                id = id,
                serializer = serializer,
                schemaGenerator = StandardJsonSchemaGenerator.Default,
                examples = examples,
            )
        )
    }
    return structuredRequest
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
    model: LLModel,
    fixingParser: StructureFixingParser? = null
): StructuredResponse<T> {
    // Use fixingParser if provided, otherwise parse directly
    val structure = config.structure(model)
    val structureResponse = fixingParser
        ?.parse(this, structure, response.content)
        ?: structure.parse(response.content)

    return StructuredResponse(
        data = structureResponse,
        structure = structure,
        message = response
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
