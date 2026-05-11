package ai.koog.test.utils

import ai.koog.http.client.KoogHttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.reflect.KClass

/**
 * Test double for [KoogHttpClient] that captures the last POST request details.
 *
 * This client is intended for tests where you want to:
 * - verify the requested path,
 * - inspect the request payload,
 * - and return a controlled response based on the requested response type.
 *
 * GET requests are not expected and will fail fast.
 * SSE calls are intentionally inert and return an empty flow.
 *
 * @property clientName The logical name of the client instance.
 * @property responder A function that produces a response object for the requested response type.
 */
public class CapturingKoogHttpClient(
    override val clientName: String,
    private val responder: (KClass<*>) -> Any,
) : KoogHttpClient {
    /** Captured path from the most recent POST request. */
    public var lastPath: String? = null

    /** Captured request body from the most recent POST request. */
    public var lastRequest: Any? = null

    override suspend fun <R : Any> get(
        path: String,
        responseType: KClass<R>,
        parameters: Map<String, String>,
        headers: Map<String, String>,
    ): R {
        error("GET is not expected in this test")
    }

    override suspend fun <T : Any, R : Any> post(
        path: String,
        request: T,
        requestBodyType: KClass<T>,
        responseType: KClass<R>,
        parameters: Map<String, String>,
        headers: Map<String, String>,
    ): R {
        lastPath = path
        lastRequest = request
        @Suppress("UNCHECKED_CAST")
        return responder(responseType) as R
    }

    override fun <T : Any, R : Any, O : Any> sse(
        path: String,
        request: T,
        requestBodyType: KClass<T>,
        dataFilter: (String?) -> Boolean,
        decodeStreamingResponse: (String) -> R,
        processStreamingChunk: (R) -> O?,
        parameters: Map<String, String>,
        headers: Map<String, String>,
    ): Flow<O> = emptyFlow()

    override fun <T : Any> lines(
        path: String,
        request: T,
        requestBodyType: KClass<T>,
        parameters: Map<String, String>,
        headers: Map<String, String>,
    ): Flow<String> = emptyFlow()

    override fun close(): Unit = Unit
}
