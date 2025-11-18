package ai.koog.http.client

/**
 * Base exception class for HTTP clients in koog
 */
public abstract class HttpClientException(
    public val clientName: String? = null,
    public val statusCode: Int? = null,
    public val errorBody: String? = null,
    message: String? = null,
    cause: Throwable? = null
) : Exception(
    buildString {
        clientName?.let { appendLine("Error from client: $it") }
        message?.let { appendLine("Message: $it") }
        statusCode?.let { appendLine("Status code: $it") }
        errorBody?.let {
            appendLine("Error body:")
            appendLine(it)
        }
    },
    cause
)

/**
 * Exception for Koog HTTP clients
 */
public class KoogHttpClientException(
    clientName: String,
    statusCode: Int? = null,
    errorBody: String? = null,
    message: String? = null,
    cause: Throwable? = null
) : HttpClientException(clientName, statusCode, errorBody, message, cause)
