package ai.koog.spring.ai.vectorstore

/**
 * Exception thrown when a Koog vector-store operation fails.
 *
 * Wraps backend provider errors and filter-expression parsing failures
 * with operation context so that callers receive a consistent, Koog-specific
 * exception type instead of raw backend exceptions.
 *
 * @param operation a short label describing the failed operation (e.g. `"add"`, `"search"`, `"delete"`)
 * @param message human-readable description of the failure
 * @param cause the original exception, if any
 */
public class KoogVectorStoreException(
    operation: String,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(
    buildString {
        append("Koog VectorStore operation '$operation' failed")
        if (message != null) {
            append(": ")
            append(message)
        }
    },
    cause
)
