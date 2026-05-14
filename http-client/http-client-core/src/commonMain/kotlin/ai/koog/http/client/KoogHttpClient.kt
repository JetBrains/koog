package ai.koog.http.client

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus.Experimental
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.minutes

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
     * Sends an HTTP GET request to the specified `path`.
     * The expected response type must be explicitly specified using `responseType`.
     *
     * @param path The endpoint path to which the HTTP GET request is sent.
     * @param responseType The Kotlin class reference representing the expected type of the response.
     * @param parameters Optional query parameters merged with default query parameters configured on the client.
     * @param headers Optional request headers merged with headers configured on the client. Headers with the same
     * name replace configured or inferred header values; other configured headers are preserved.
     *
     * @return The response payload, deserialized into the specified type.
     * @throws Exception if the request fails or the response cannot be deserialized.
     */
    public suspend fun <R : Any> get(
        path: String,
        responseType: KClass<R>,
        parameters: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
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
     * @param parameters Optional query parameters merged with default query parameters configured on the client.
     * @param headers Optional request headers merged with headers configured on the client. Headers with the same
     * name replace configured or inferred header values; other configured headers are preserved.
     * @return The response payload, deserialized into the specified type.
     * @throws Exception if the request fails or the response cannot be deserialized.
     */
    public suspend fun <T : Any, R : Any> post(
        path: String,
        request: T,
        requestBodyType: KClass<T>,
        responseType: KClass<R>,
        parameters: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
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
     * @param parameters Optional query parameters merged with default query parameters configured on the client.
     * @param headers Optional request headers merged with headers configured on the client. Headers with the same
     * name replace configured or inferred header values; other configured headers are preserved.
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
        headers: Map<String, String> = emptyMap(),
    ): Flow<O>

    /**
     * Sends an HTTP POST request and emits each non-blank UTF-8 line of the response body as it arrives.
     *
     * @param path The endpoint path to which the HTTP POST request is sent.
     * @param request The request payload to be sent in the POST request.
     * @param requestBodyType The Kotlin class reference representing the type of the request body.
     * @param parameters Optional query parameters merged with default query parameters configured on the client.
     * @param headers Optional request headers merged with headers configured on the client. Headers with the same
     * name replace configured or inferred header values; other configured headers are preserved.
     * @return A [Flow] emitting each non-blank line of the response body as it arrives.
     * @throws KoogHttpClientException if the server returns a non-success status.
     */
    public fun <T : Any> lines(
        path: String,
        request: T,
        requestBodyType: KClass<T>,
        parameters: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): Flow<String>

    public interface Factory {
        /**
         * Creates a configured [KoogHttpClient].
         *
         * @param clientName The name used for logging and traceability.
         * @param baseUrl Base URL prepended to relative request paths.
         * @param headers Default headers applied to every request.
         * @param queryParameters Default query parameters applied to every request.
         * @param requestTimeoutMillis Maximum time in milliseconds allowed for a request to complete.
         * @param connectTimeoutMillis Maximum time in milliseconds allowed for establishing a connection.
         * @param socketTimeoutMillis Maximum time in milliseconds allowed for waiting on socket reads and writes.
         * @param json JSON instance used for request and response serialization.
         */
        public fun create(
            clientName: String,
            baseUrl: String = "",
            headers: Map<String, String> = emptyMap(),
            queryParameters: Map<String, String> = emptyMap(),
            requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MS,
            connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MS,
            socketTimeoutMillis: Long = DEFAULT_SOCKET_TIMEOUT_MS,
            json: Json = Json
        ): KoogHttpClient

        public companion object {
            public val DEFAULT_REQUEST_TIMEOUT_MS: Long = 15.minutes.inWholeMilliseconds
            public val DEFAULT_CONNECT_TIMEOUT_MS: Long = 10.minutes.inWholeMilliseconds
            public val DEFAULT_SOCKET_TIMEOUT_MS: Long = 15.minutes.inWholeMilliseconds
        }
    }

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
 * @param parameters Optional query parameters merged with default query parameters configured on the client.
 * @param headers Optional request headers merged with headers configured on the client. Headers with the same
 * name replace configured or inferred header values; other configured headers are preserved.
 * @return The response payload, deserialized into the specified type.
 * @throws Exception if the request fails or the response cannot be deserialized.
 */
public suspend inline fun <reified T : Any, reified R : Any> KoogHttpClient.post(
    path: String,
    request: T,
    parameters: Map<String, String> = emptyMap(),
    headers: Map<String, String> = emptyMap(),
): R = post(path, request, T::class, R::class, parameters, headers)

/**
 * Sends an HTTP GET request to the specified `path` with the provided parameters.
 *
 * @param path The endpoint path to which the HTTP GET request is sent.
 * @param parameters Optional query parameters merged with default query parameters configured on the client.
 * @param headers Optional request headers merged with headers configured on the client. Headers with the same
 * name replace configured or inferred header values; other configured headers are preserved.
 * @return The response payload, deserialized into the specified type.
 * @throws Exception if the request fails or the response cannot be deserialized.
 */
public suspend inline fun <reified R : Any> KoogHttpClient.get(
    path: String,
    parameters: Map<String, String> = emptyMap(),
    headers: Map<String, String> = emptyMap(),
): R = get(path, R::class, parameters, headers)

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
 * @param parameters Optional query parameters merged with default query parameters configured on the client.
 * @param headers Optional request headers merged with headers configured on the client. Headers with the same
 * name replace configured or inferred header values; other configured headers are preserved.
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
    headers: Map<String, String> = emptyMap(),
): Flow<O> = sse(
    path = path,
    request = request,
    requestBodyType = T::class,
    dataFilter = dataFilter,
    decodeStreamingResponse = decodeStreamingResponse,
    processStreamingChunk = processStreamingChunk,
    parameters = parameters,
    headers = headers,
)

/**
 * Sends an HTTP POST request and emits each non-blank UTF-8 line of the response body as it arrives.
 *
 * @param path The endpoint path to which the HTTP POST request is sent.
 * @param request The request payload to be sent in the POST request.
 * @param parameters Optional query parameters merged with default query parameters configured on the client.
 * @param headers Optional request headers merged with headers configured on the client. Headers with the same
 * name replace configured or inferred header values; other configured headers are preserved.
 * @return A [Flow] emitting each non-blank line of the response body as it arrives.
 * @throws KoogHttpClientException if the server returns a non-success status.
 */
public inline fun <reified T : Any> KoogHttpClient.lines(
    path: String,
    request: T,
    parameters: Map<String, String> = emptyMap(),
    headers: Map<String, String> = emptyMap(),
): Flow<String> = lines(path, request, T::class, parameters, headers)
