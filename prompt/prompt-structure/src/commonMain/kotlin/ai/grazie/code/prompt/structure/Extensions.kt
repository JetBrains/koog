package ai.grazie.code.prompt.structure

import ai.grazie.code.prompt.markdown.markdown
import ai.grazie.code.prompt.structure.json.JsonStructureLanguage
import ai.grazie.utils.mpp.LoggerFactory
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import ai.jetbrains.code.prompt.executor.model.PromptExecutor
import ai.jetbrains.code.prompt.llm.LLModel
import ai.jetbrains.code.prompt.text.TextContentBuilder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException

private val logger = LoggerFactory.create("ai.grazie.code.prompt.structure.Extensions")

public inline fun <reified T> TextContentBuilder.structure(language: JsonStructureLanguage, value: T) {
    +language.pretty(value)
}

public fun <T> TextContentBuilder.structure(language: JsonStructureLanguage, value: T, serializer: KSerializer<T>) {
    +language.pretty(value, serializer)
}

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
   model: LLModel, structure: StructuredData<T>
): StructuredResponse<T> {
    val text = execute(prompt, model)
    return StructuredResponse(structure = structure.parse(text), raw = text)
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

    repeat(retries) {
        val text = execute(prompt, mainModel)
        try {
            return Result.success(
                StructuredResponse(
                    structure = structureParser.parse(structure, text),
                    raw = text,
                )
            )
        } catch (e: SerializationException) {
            logger.warning(e) { "Unable to parse structure, retrying: $text" }
        }
    }

    return Result.failure(LLMStructuredParsingError("Unable to parse structure after $retries retries"))
}

class LLMStructuredParsingError(message: String) : Exception(message)
