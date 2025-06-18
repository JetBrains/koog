package ai.koog.prompt.structure

/**
 * Exception indicating an error during the parsing of structured output from a language model.
 *
 * This exception is thrown when the structured parsing of a language model's response fails,
 * often due to malformed or unexpected data not conforming to the expected schema.
 *
 * @constructor Creates a new instance of [LLMStructuredParsingError].
 * @param message A detailed message describing the cause of the parsing error.
 */
public class LLMStructuredParsingError(message: String) : Exception(message)
