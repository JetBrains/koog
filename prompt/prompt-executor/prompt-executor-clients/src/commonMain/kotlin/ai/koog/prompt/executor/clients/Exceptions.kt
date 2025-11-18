package ai.koog.prompt.executor.clients

import ai.koog.http.client.HttpClientException

/**
 * Exception for Koog LLM clients
 */
public class LLMClientException(
    clientName: String,
    message: String? = null,
    statusCode: Int? = null,
    errorBody: String? = null,
    cause: Throwable? = null,
) : HttpClientException(clientName, statusCode, message, errorBody, cause)
