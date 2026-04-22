package ai.koog.http.client

import kotlin.jvm.JvmOverloads

/**
 * Base exception class for HTTP clients in koog.
 *
 * @property clientName Name of the HTTP client that produced the error, for log attribution.
 * @property statusCode HTTP status code returned by the server, or `null` if no response was received.
 * @property errorBody Raw body of the failed response, or `null` if it could not be read.
 * @property headers HTTP response headers captured from the failed response.
 *   Keys are normalized to lowercase so consumers can look headers up without re-casing
 *   (see [lowercaseHeaderKeys]); values preserve the order and formatting from the server.
 *   Defaults to an empty map when no response was available (e.g., connection errors).
 */
public class KoogHttpClientException @JvmOverloads constructor(
    public val clientName: String? = null,
    public val statusCode: Int? = null,
    public val errorBody: String? = null,
    message: String? = null,
    cause: Throwable? = null,
    public val headers: Map<String, List<String>> = emptyMap()
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
