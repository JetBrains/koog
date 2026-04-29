package ai.koog.http.client

import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.minutes

/**
 * Factory for creating configured [KoogHttpClient] instances.
 *
 * Provider clients describe the HTTP defaults they require through this API, while adapter modules decide which
 * transport implementation backs the resulting client.
 */
public interface KoogHttpClientFactory {

    public companion object {
        public val DEFAULT_REQUEST_TIMEOUT_MS: Long = 15.minutes.inWholeMilliseconds
        public val DEFAULT_CONNECT_TIMEOUT_MS: Long = 10.minutes.inWholeMilliseconds
        public val DEFAULT_SOCKET_TIMEOUT_MS: Long = 15.minutes.inWholeMilliseconds
    }

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
}
