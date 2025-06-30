package ai.koog.prompt.structure

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutorExt.execute
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.structure.json.JsonStructureLanguage
import ai.koog.prompt.text.TextContentBuilderBase
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException

private val logger = KotlinLogging.logger {  }

/**
 * Adds a structured representation of the given value to the text content using the specified language.
 *
 * The method uses the language's formatting capabilities to generate a textual representation
 * of the input value and appends it to the content being built by the `TextContentBuilder`.
 *
 * @param T The type of the value to be structured.
 * @param language The `JsonStructureLanguage` instance used to format the value into a structured textual representation.
 * @param value The value of type `T` to be formatted and added to the text content.
 */
public inline fun <reified T> TextContentBuilderBase<*>.structure(language: JsonStructureLanguage, value: T) {
    +language.pretty(value)
}

/**
 * Adds a structured JSON representation of the given value to the [TextContentBuilderBase].
 *
 * @param language The [JsonStructureLanguage] instance used for defining the serialization and formatting rules.
 * @param value The value to be serialized and added to the builder.
 * @param serializer The [KSerializer] instance used to serialize the value into the structured JSON format.
 */
public fun <T> TextContentBuilderBase<*>.structure(language: JsonStructureLanguage, value: T, serializer: KSerializer<T>) {
    +language.pretty(value, serializer)
}

/**
 * Represents a container for structured data parsed from raw text.
 *
 * This class is designed to encapsulate both the parsed structured output and the original raw
 * text as returned from a processing step, such as a language model execution.
 *
 * @param T The type of the structured data contained within this response.
 * @property structure The parsed structured data corresponding to the specific schema.
 * @property raw The raw string from which the structured data was parsed.
 */
public data class StructuredResponse<T>(val structure: T, val raw: String)

/**
 * Executes a given prompt and parses the resulting text, expecting structured data in the response message.
 *
 * NOTE: you have to manually handle LLM coercion into structured output, e.g. using prompt schema parameter.
 *
 * @param prompt The prompt to be executed.
 * @param structure The structure definition that includes the parser and schema information
 * for interpreting the raw response from the execution.
 * @return A [StructuredResponse] containing both parsed structure and raw text
 */
public suspend fun <T> PromptExecutor.executeStructuredOneShot(
    prompt: Prompt,
    model: LLModel,
    structure: StructuredData<T>
): StructuredResponse<T> {
    val response = this.execute(prompt = prompt, model = model)
    val responseContent = response.content
    return StructuredResponse(
        structure = structure.parse(responseContent),
        raw = responseContent
    )
}

/**
 * Executes a prompt and ensures the response is properly structured by applying automatic output coercion.
 *
 * This method enhances structured output parsing reliability by:
 * 1. Injecting structured output instructions into the original prompt
 * 2. Executing the enriched prompt to receive a raw response
 * 3. Using a separate LLM call to parse/coerce the response if direct parsing fails
 *
 * Unlike [execute(prompt, structure)] which simply attempts to parse the raw response and fails
 * if the format doesn't match exactly, this method actively works to transform unstructured or
 * malformed outputs into the expected structure through additional LLM processing.
 *
 * @param structure The structured data definition with schema and parsing logic
 * @param prompt The prompt to execute
 * @param mainModel The main model to execute prompt
 * @param retries Number of parsing attempts before giving up
 * @param fixingModel LLM used for output coercion (transforming malformed outputs)
 * @return A [StructuredResponse] containing both parsed structure and raw text
 * @throws IllegalStateException if parsing fails after all retries
 */
public suspend fun <T> PromptExecutor.executeStructured(
    prompt: Prompt,
    mainModel: LLModel,
    structure: StructuredData<T>,
    retries: Int = 1,
    fixingModel: LLModel = OpenAIModels.Chat.GPT4o
): Result<StructuredResponse<T>> {
    val prompt = prompt(prompt) {
        user {
            markdown {
                StructuredOutputPrompts.output(this, structure)
            }
        }
    }

    val structureParser = StructureParser(this, fixingModel)

    repeat(retries) { attempt ->
        logger.debug { "Execute the prompt: <$prompt>" }
        val response = execute(prompt = prompt, model = mainModel)

        try {
            logger.debug { "$attempt/$retries: Try to parse LLM response content: <${response.content}>" }
            val structure = structureParser.parse(structure, response.content)
            return Result.success(
                StructuredResponse(
                    structure = structure,
                    raw = response.content,
                )
            )
        } catch (t: SerializationException) {
            logger.warn(t) { "Failed to Unable to parse structure from content: <${response.content}>" }
        }
    }

    return Result.failure(
        exception = LLMStructuredParsingError("Unable to parse structure after <$retries> retries")
    )
}
