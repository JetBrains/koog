package ai.koog.http.client

/**
 * Base exception class for HTTP clients in koog
 */
public class KoogHttpClientException(
    public val clientName: String? = null,
    public val statusCode: Int? = null,
    public val errorBody: String? = null,
    message: String? = null,
    cause: Throwable? = null
) : Exception(
    buildString {
        appendLine("Error from client: ${clientName ?: "unknown client"}")
        message?.let { appendLine("Message: $it") }
        statusCode?.let { appendLine("Status code: $it") }
        errorBody?.let {
            appendLine("Error body:")
            appendLine(it)
        }
    },
    cause
)
