package ai.koog.prompt.executor.clients.google.genai

import ai.koog.prompt.executor.clients.LLMClientException
import com.google.genai.errors.ClientException
import com.google.genai.errors.ServerException
import kotlinx.coroutines.CancellationException

/**
 * Executes a Google GenAI API call, wrapping SDK exceptions into [LLMClientException].
 *
 * Shared by [GoogleGenaiLLMClient] and [GoogleGenaiEmbeddingProvider] to avoid duplicating
 * the error-handling logic.
 *
 * @param clientName Name of the calling client, used in exception messages.
 * @param block The suspend block that performs the actual API call.
 */
@Suppress("TooGenericExceptionCaught")
internal suspend fun <T> callGoogleGenaiApi(clientName: String, block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: ClientException) {
    throw LLMClientException(
        clientName = clientName,
        message = "Status code: ${e.code()} (${e.status()}). ${e.message}",
        cause = e
    )
} catch (e: ServerException) {
    throw LLMClientException(
        clientName = clientName,
        message = "Status code: ${e.code()} (${e.status()}). ${e.message}",
        cause = e
    )
} catch (e: Exception) {
    throw LLMClientException(clientName = clientName, message = e.message, cause = e)
}
