package ai.grazie.code.agents.core.tools

sealed class ToolException(override val message: String): Exception() {
    class ValidationFailure(message: String): ToolException(message)
}

fun validate(expectation: Boolean, message: () -> String) {
    if (!expectation) throw ToolException.ValidationFailure(message())
}

fun <T: Any> validateNotNull(value: T?, message: () -> String): T {
    if (value == null) throw ToolException.ValidationFailure(message())
    return value
}

fun fail(message: String): Nothing = throw ToolException.ValidationFailure(message)