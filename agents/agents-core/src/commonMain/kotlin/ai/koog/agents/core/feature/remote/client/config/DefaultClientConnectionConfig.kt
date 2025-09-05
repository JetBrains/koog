package ai.koog.agents.core.feature.remote.client.config

import io.ktor.http.URLProtocol
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Default implementation of the `ClientConnectionConfig` class.
 *
 * This class provides a predefined set of configurations suitable for establishing
 * client-side connections, simplifying the process of initializing connections
 * with default values.
 *
 * It extends the base functionality of `ClientConnectionConfig`
 * by offering default settings such as host, port, protocol, and timeouts.
 *
 * @param host The hostname or IP address of the server to connect to. Defaults to `127.0.0.1`.
 * @param port The port number for the connection. Defaults to `50881`.
 * @param protocol The protocol to use for the connection (e.g., HTTP, HTTPS). Defaults to `null`, using the
 *        base class's default protocol instead.
 * @param headers Optional HTTP headers to include in the connection. Defaults to `null`, which is treated as empty map.
 * @param reconnectionDelay The delay between connection retry attempts, if applicable. Defaults to `null`.
 * @param requestTimeout The timeout duration for requests. Defaults to `5.seconds`.
 * @param connectTimeout The timeout duration for a connection establishment. Defaults to `15.seconds`.
 */
public class DefaultClientConnectionConfig(
    host: String = DEFAULT_HOST,
    port: Int? = DEFAULT_PORT,
    protocol: URLProtocol? = null,
    headers: Map<String, List<String>>? = null,
    reconnectionDelay: Duration? = null,
    requestTimeout: Duration? = defaultRequestTimeout,
    connectTimeout: Duration? = defaultConnectionTimeout,
) : ClientConnectionConfig(host, port, protocol, headers, reconnectionDelay, requestTimeout, connectTimeout) {

    /**
     * Companion object for the `DefaultClientConnectionConfig` class.
     *
     * Provides default connection configuration values to streamline the creation
     * of client connection configurations.
     */
    public companion object {

        /**
         * Represents the default host address used for client connections.
         *
         * This constant defines the default hostname or IP address as "127.0.0.1",
         * typically representing the local machine (localhost).
         */
        public const val DEFAULT_HOST: String = "127.0.0.1"

        /**
         * Represents the default port number used for client connections.
         */
        public const val DEFAULT_PORT: Int = 50881

        /**
         * Represents the default request timeout used for client connections.
         */
        public val defaultRequestTimeout: Duration = 5.seconds

        /**
         * Represents the default connection timeout used for client connections.
         */
        public val defaultConnectionTimeout: Duration = 15.seconds
    }
}
