package ai.koog.agents.features.common.remote.client.config

import ai.koog.agents.features.common.remote.ConnectionConfig
import io.ktor.http.URLProtocol
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration details for managing client connections.
 *
 * @property host The hostname or IP address of the server to connect to.
 * @property port The port number used for establishing the connection.
 * @property protocol The protocol used for the connection, such as "http" or "https".
 * @property headers A map of custom headers to include with each request.
 * @property reconnectionDelay An optional delay duration before attempting to reconnect after a connection loss.
 * @property requestTimeout The maximum duration to wait for an individual HTTP request to complete.
 *                          Defaults to 5 seconds.
 * @property connectTimeout The maximum duration to wait while establishing a connection to the server.
 *                          Defaults to 15 seconds.
 *
 * @property url A computed property that constructs the base URL for this connection
 *               using the protocol, host, and port.
 * @property sseUrl A computed property that constructs the URL endpoint for Server-Sent Events (SSE).
 * @property healthCheckUrl A computed property that constructs the URL endpoint for health check requests.
 * @property messageUrl A computed property that constructs the URL endpoint for sending or receiving messages.
 */
public abstract class ClientConnectionConfig(
    public val host: String,
    port: Int? = null,
    public val protocol: URLProtocol = URLProtocol.HTTPS,
    public val headers: Map<String, String> = emptyMap(),
    public val reconnectionDelay: Duration? = null,
    public val requestTimeout: Duration? = 5.seconds,
    public val connectTimeout: Duration? = 15.seconds,
) : ConnectionConfig() {

    /**
     * The port number used for establishing a client connection.
     *
     * This property determines the port to be used when constructing the connection URL.
     * If no explicit port is specified during the configuration, the default port for the
     * specified protocol (e.g., 80 for HTTP, 443 for HTTPS) will be used.
     */
    public val port: Int = port ?: protocol.defaultPort

    /**
     * Provides the base URL for the current connection configuration.
     * Constructs the URL using the protocol, host, and port specified in the connection configuration.
     */
    public val url: String
        get() = "${protocol.name}://$host:$port"

    /**
     * Constructs the URL endpoint for Server-Sent Events (SSE) communication.
     *
     * This property is a computed value derived from the base `url` property of the connection
     * configuration. It appends the path `/sse` to the base URL, forming the full URL used for
     * establishing Server-Sent Events connections. The returned URL conforms to the protocol,
     * host, and port settings specified in the configuration.
     *
     * Typical use cases of this property include subscribing to a server's event stream to receive
     * real-time updates or notifications using SSE.
     */
    public val sseUrl: String
        get() = "$url/sse"

    /**
     * A computed property that constructs the URL endpoint for health check requests.
     *
     * This property generates the URL by appending the "health" path segment
     * to the base URL defined by the `url` property within the `ClientConnectionConfig` class.
     *
     * The generated URL is intended to be used for verifying the availability
     * and responsiveness of the remote server or service.
     */
    public val healthCheckUrl: String
        get() = "$url/health"

    /**
     * Constructs the URL endpoint for sending or receiving messages.
     *
     * This property is a computed value that combines the base `url` of the client connection
     * configuration with the `/message` path segment. It is often used as the target URL
     * for communication with the message handling endpoint of a remote server.
     *
     * Common use cases include sending feature-related messages or retrieving messages from the server
     * as part of a feature messaging system.
     */
    public val messageUrl: String
        get() = "$url/message"
}
