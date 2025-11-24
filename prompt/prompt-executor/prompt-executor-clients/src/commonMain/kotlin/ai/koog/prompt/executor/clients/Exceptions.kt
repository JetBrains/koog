package ai.koog.prompt.executor.clients

/**
 * Exception for Koog LLM clients
 */
public class LLMClientException(
    clientName: String,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(
    buildString {
        appendLine("Error from client: $clientName")
        message?.let { appendLine(it) }
    },
    cause
)
