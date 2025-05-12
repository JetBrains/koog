package ai.grazie.code.agents.local.features.common.remote.client.config

import ai.grazie.code.agents.local.features.common.remote.ConnectionConfig
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
abstract class ClientConnectionConfig(
    val host: String,
    val port: Int,
    val protocol: String = "https",
    val headers: Map<String, String> = emptyMap(),
    val reconnectionDelay: Duration? = null,
    val requestTimeout: Duration? = 5.seconds,
    val connectTimeout: Duration? = 15.seconds,
) : ConnectionConfig() {

    val url: String
        get() = "$protocol://$host:$port"

    val sseUrl: String
        get() = "$url/sse"

    val healthCheckUrl: String
        get() = "$url/health"

    val messageUrl: String
        get() = "$url/message"
}