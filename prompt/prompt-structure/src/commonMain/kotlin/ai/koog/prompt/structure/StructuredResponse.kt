package ai.koog.prompt.structure

import ai.koog.prompt.message.Message

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
