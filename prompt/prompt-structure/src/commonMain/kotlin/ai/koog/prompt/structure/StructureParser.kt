package ai.koog.prompt.structure

import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerializationException


/**
 * A parser for processing structured data that utilizes language models (LLMs) and attempts to
 * correct any errors in the provided content to produce valid structured outputs.
 *
 * @property executor A `PromptExecutor` instance responsible for executing language model prompts.
 * @property fixingModel The language model to use for processing and attempting to fix format. Defaults to the `GPT4o` model in the `OpenAIModels`.
 */
public class StructureParser(
    private val executor: PromptExecutor,
    private val fixingModel: LLModel = OpenAIModels.Chat.GPT4o,
) {
    private companion object {
        private val logger = KotlinLogging.logger {  }
    }

    /**
     * Parses the given content string into an instance of the specified type using the provided structured data schema.
     * If the initial parsing fails, attempts to fix the content based on the error and retries parsing.
     *
     * @param structure The structured data schema and serializer to use for parsing the content.
     * @param content The string content to parse into the structured data type.
     * @return An instance of the parsed data type.
     * @throws SerializationException If parsing fails both initially and after attempting to fix the content.
     */
    public suspend fun <T> parse(structure: StructuredData<T>, content: String): T {
        return try {
            structure.parse(content)
        } catch (e: SerializationException) {
            logger.warn(e) { "Unable to parse structure: $content" }
            tryFixStructure(content, e, structure)
        }
    }

    private suspend fun <T> tryFixStructure(
        content: String,
        exception: SerializationException,
        structure: StructuredData<T>
    ): T {
        val prompt = prompt(
            "code-engine-structure-fixing",
            LLMParams(
                schema = structure.schema
            )
        ) {
            system {
                markdown {
                    +"You are agent responsible for converting incorrectly generated LLM Structured Output into valid JSON that adheres to the given JSON schema."
                    +"Your sole responsibility is to fix the generated JSON to conform to the given schema."
                    newline()

                    h2("PROCESS")
                    bulleted {
                        item("Evaluate what parts are incorrect and fix them.")
                        item("Drop unknown fields and come-up with values for missing fields based on semantics.")
                        item("Carefully check the types of the fields and fix if any are incorrect.")
                        item("Utilize the provided exception to determine the possible error, but do not forget about other possible mistakes.")
                    }

                    h2("KEY PRINCIPLES")
                    bulleted {
                        item("You MUST stick to the original data, make as few changes as possible to convert it into valid JSON.")
                        item("Do not drop, alter or change any semantic data unless it is necessary to fit into JSON schema.")
                    }

                    h2("DEFINITION")
                    structure.definition(this)
                }
            }
            user {
                markdown {
                    h2("EXCEPTION")
                    codeblock(exception.message ?: "Unknown exception")
                    h2("CONTENT")
                    codeblock(content)
                }
            }
        }

        return try {
            val fixed = executor.executeStructuredOneShot(prompt, fixingModel, structure).structure
            logger.info { "Fixed the structure into: $fixed" }
            fixed
        } catch (e: SerializationException) {
            logger.error(e) { "Unable to parse structure after fixing: $content" }
            throw e
        }
    }
}
