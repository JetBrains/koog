package ai.koog.agents.core.feature.remote.client.config

import ai.koog.agents.core.feature.remote.ConnectionConfig
import io.ktor.http.URLProtocol
import kotlin.time.Duration

/**
 * Base configuration class for managing client-side connections in a remote communication setup.
 *
 * This abstract class is designed to provide configuration settings required to establish a client connection,
 * such as protocol, host, port, custom headers, and timeout options. It includes utility properties for
 * constructing URL endpoints corresponding to specific functionalities like Server-Sent Events (SSE),
 * health checks, and message handling.
 *
 * Subclasses can extend this base configuration to implement more specific connection requirements or initialize
 * predefined settings suitable for particular use cases.
 *
 * @property host The hostname or IP address of the target server to connect to.
 * @property port The port number for the connection, using the default for the specified protocol if not provided.
 * @property protocol The URL protocol (e.g., HTTP, HTTPS) used for the connection.
 * @property headers Optional HTTP headers to include in requests, defaulting to an empty map.
 * @property reconnectionDelay The delay between attempts to reconnect, if applicable, defaulting to null.
 * @property requestTimeout The timeout duration for requests, defaulting to null.
 * @property connectTimeout The timeout duration for establishing a connection, defaulting to null.
 */
public abstract class ClientConnectionConfig(
    public val host: String,
    port: Int? = null,
    protocol: URLProtocol? = null,
    headers: Map<String, List<String>>? = null,
    public val reconnectionDelay: Duration? = null,
    public val requestTimeout: Duration? = null,
    public val connectTimeout: Duration? = null,
) : ConnectionConfig() {

    /**
     * Companion object for the `ClientConnectionConfig` class.
     *
     * Provides default configuration settings such as the default protocol
     * used across instances of `ClientConnectionConfig` or its subclasses.
     */
    public companion object {

        /**
         * The default protocol used for establishing a client connection.
         *
         * This value is set to `URLProtocol.HTTPS`, ensuring secure communication by default.
         * It is used in classes such as `ClientConnectionConfig` and its implementations
         * for initializing the protocol property when no specific protocol is provided.
         */
        public val defaultProtocol: URLProtocol = URLProtocol.HTTPS
    }

    /**
     * The protocol used for the client connection.
     *
     * This property determines the URL protocol (e.g., HTTP or HTTPS) used to establish the connection.
     * If a specific protocol is not provided during the configuration, the default protocol is used.
     */
    public val protocol: URLProtocol = protocol ?: defaultProtocol

    /**
     * The port number used for establishing a client connection.
     *
     * This property determines the port to be used when constructing the connection URL.
     * If no explicit port is specified during the configuration, the default port for the
     * specified protocol (e.g., 80 for HTTP, 443 for HTTPS) will be used.
     */
    public val port: Int = port ?: this.protocol.defaultPort

    /**
     * A collection of headers to be applied to HTTP requests made by the client.
     *
     * This map with headers can be used to provide additional metadata, authentication information,
     * or other HTTP-specific data required by the server.
     *
     * If no headers are explicitly defined, this map defaults to an empty state, ensuring that
     * requests do not include unnecessary headers.
     */
    public val headers: Map<String, List<String>> = headers ?: emptyMap()

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
