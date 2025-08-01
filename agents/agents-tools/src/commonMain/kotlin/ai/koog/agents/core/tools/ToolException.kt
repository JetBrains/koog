package ai.koog.agents.core.tools

/**
 * A base sealed class representing exceptions specific to tools.
 * This class provides a structure for exceptions with a custom message.
 */
public sealed class ToolException(override val message: String) : Exception() {
    /**
     * Represents a failure that occurs during validation processes.
     *
     * This exception is a specific type of ToolException used to indicate
     * that validation of some input or process has failed. It typically
     * contains a message that provides more details about the validation failure.
     *
     * @constructor Creates a ValidationFailure instance with the given message.
     * @param message The detail message describing the validation error.
     */
    public class ValidationFailure(message: String) : ToolException(message)
}

/**
 * Validates a given condition and throws a [ToolException.ValidationFailure] exception if the condition is not met.
 *
 * @param expectation The condition that is expected to be true.
 * @param message A lambda function to generate the exception message if the condition is not met.
 */
public fun validate(expectation: Boolean, message: () -> String) {
    if (!expectation) throw ToolException.ValidationFailure(message())
}

/**
 * Validates that the provided value is not null. If the value is null,
 * a [ToolException.ValidationFailure] exception is thrown with the provided error message.
 *
 * @param value The value to be validated as not null.
 * @param message A lambda that provides the error message in case the value is null.
 * @return The same non-null value that was provided as input.
 * @throws ToolException.ValidationFailure if the value is null.
 */
public fun <T : Any> validateNotNull(value: T?, message: () -> String): T {
    if (value == null) throw ToolException.ValidationFailure(message())
    return value
}

/**
 * Throws a [ToolException.ValidationFailure] exception with the specified error message.
 *
 * @param message The error message to include in the exception.
 * @return Nothing, as this function always throws an exception.
 */
public fun fail(message: String): Nothing = throw ToolException.ValidationFailure(message)
