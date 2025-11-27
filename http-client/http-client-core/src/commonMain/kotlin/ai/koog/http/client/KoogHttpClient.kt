package ai.koog.http.client

import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus.Experimental
import kotlin.reflect.KClass

/**
 * Abstract interfaces defining a contract for HTTP client implementations.
 * Provides methods for making HTTP POST requests and handling Server-Sent Events (SSE) streams.
 *
 * Implementations are supposed to use a particular library or framework.
 *
 * @property clientName The name of the client, used for logging and traceability.
 */
@Experimental
public interface KoogHttpClient : AutoCloseable {
    /**
     * The name of the client.
     */
    public val clientName: String

    /**
     * Sends an HTTP GET request to the specified `path` with the provided `request` payload.
     * The type of the request body and the expected response must be explicitly specified
     * using `requestBodyType` and `responseType`, respectively.
     *
     * @param path The endpoint path to which the HTTP POST request is sent.
     * @param responseType The Kotlin class reference representing the expected type of the response.
     * @param parameters Optional query parameters to include in the request.
     *
     * @return The response payload, deserialized into the specified type.
     * @throws Exception if the request fails or the response cannot be deserialized.
     */
    public suspend fun <R : Any> get(
        path: String,
        responseType: KClass<R>,
        parameters: Map<String, String> = emptyMap(),
    ): R

    /**
     * Sends an HTTP POST request to the specified `path` with the provided `request` payload.
     * The type of the request body and the expected response must be explicitly specified
     * using `requestBodyType` and `responseType`, respectively.
     *
     * @param path The endpoint path to which the HTTP POST request is sent.
     * @param request The request payload to be sent in the POST request.
     * @param requestBodyType The Kotlin class reference representing the type of the request body.
     * @param responseType The Kotlin class reference representing the expected type of the response.
     * @param parameters Optional query parameters to include in the request.
     * @return The response payload, deserialized into the specified type.
     * @throws Exception if the request fails or the response cannot be deserialized.
     */
    public suspend fun <T : Any, R : Any> post(
        path: String,
        request: T,
        requestBodyType: KClass<T>,
        responseType: KClass<R>,
        parameters: Map<String, String> = emptyMap(),
    ): R

    /**
     * Initiates a Server-Sent Events (SSE) streaming operation over an HTTP POST request.
     *
     * This function sends a request to the specified `path` with the given `request` payload,
     * processes the streamed chunks of data from the server, and emits the processed results as a flow of strings.
     *
     * @param path The endpoint path to which the SSE POST request is sent.
     * @param request The request payload to be sent in the POST request.
     * @param requestBodyType The Kotlin class reference representing the type of the request body.
     * @param dataFilter A lambda function that determines whether a received streaming data chunk should be processed.
     * It takes the raw data as a string and returns `true` if the data should be included, or `false` otherwise.
     * Defaults to accepting all non-null chunks.
     * @param decodeStreamingResponse A lambda function used to decode the raw streaming response data
     * into the target type. It takes a raw string and converts it into an object of type `R`.
     * @param processStreamingChunk A lambda function that processes the decoded streaming chunk and returns
     * @param parameters Optional query parameters to include in the request.
     * a string result. If the returned value is `null`, the chunk will not be emitted to the resulting flow.
     * @return A [Flow] emitting processed strings derived from the streamed chunks of data.
     */
    public fun <T : Any, R : Any, O : Any> sse(
        path: String,
        request: T,
        requestBodyType: KClass<T>,
        dataFilter: (String?) -> Boolean = { true },
        decodeStreamingResponse: (String) -> R,
        processStreamingChunk: (R) -> O?,
        parameters: Map<String, String> = emptyMap(),
    ): Flow<O>

    /**
     * Easter egg companion object. Guess why it's here.
     *
     * Hint: it was created in order to not bring Ktor and other dependencies to the interface declaration
     * */
    public companion object
}

/**
 * Sends an HTTP POST request to the specified `path` with the provided `request` payload.
 *
 * @param path The endpoint path to which the HTTP POST request is sent.
 * @param request The request payload to be sent in the POST request.
 * @param parameters Optional query parameters to include in the request.
 * @return The response payload, deserialized into the specified type.
 * @throws Exception if the request fails or the response cannot be deserialized.
 */
public suspend inline fun <reified T : Any, reified R : Any> KoogHttpClient.post(
    path: String,
    request: T,
    parameters: Map<String, String> = emptyMap(),
): R = post(path, request, T::class, R::class, parameters)

/**
 * Sends an HTTP GET request to the specified `path` with the provided parameters.
 *
 * @param path The endpoint path to which the HTTP GET request is sent.
 * @param parameters Optional query parameters to include in the request.
 * @return The response payload, deserialized into the specified type.
 * @throws Exception if the request fails or the response cannot be deserialized.
 */
public suspend inline fun <reified R : Any> KoogHttpClient.get(
    path: String,
    parameters: Map<String, String> = emptyMap(),
): R = get(path, R::class, parameters)

/**
 * Initiates a Server-Sent Events (SSE) streaming operation over an HTTP POST request.
 *
 * This function sends a request to the specified `path` with the given `request` payload,
 * processes the streamed chunks of data from the server, and emits the processed results as a flow of strings.
 *
 * @param path The endpoint path to which the SSE POST request is sent.
 * @param request The request payload to be sent in the POST request.
 * @param dataFilter A lambda function that determines whether a received streaming data chunk should be processed.
 * It takes the raw data as a string and returns `true` if the data should be included, or `false` otherwise.
 * Defaults to accepting all non-null chunks.
 * @param decodeStreamingResponse A lambda function used to decode the raw streaming response data
 * into the target type. It takes a raw string and converts it into an object of type `R`.
 * @param processStreamingChunk A lambda function that processes the decoded streaming chunk and returns
 * @param parameters Optional query parameters to include in the request.
 * a string result. If the returned value is `null`, the chunk will not be emitted to the resulting flow.
 * @return A [Flow] emitting processed strings derived from the streamed chunks of data.
 */
public inline fun <reified T : Any, reified R : Any, O : Any> KoogHttpClient.sse(
    path: String,
    request: T,
    noinline dataFilter: (String?) -> Boolean = { true },
    noinline decodeStreamingResponse: (String) -> R,
    noinline processStreamingChunk: (R) -> O?,
    parameters: Map<String, String> = emptyMap(),
): Flow<O> = sse(path, request, T::class, dataFilter, decodeStreamingResponse, processStreamingChunk, parameters)
