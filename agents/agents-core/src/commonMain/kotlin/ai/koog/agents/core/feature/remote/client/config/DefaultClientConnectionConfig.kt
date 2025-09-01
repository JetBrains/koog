package ai.koog.agents.core.feature.remote.client.config

import io.ktor.http.URLProtocol

/**
 * Default implementation for configuring a client connection.
 *
 * This class extends `ClientConnectionConfig` and sets default values for
 * the host, port, and protocol properties, providing a simple way to define
 * a client connection with minimal customization.
 *
 * @param host The hostname or IP address of the server to connect to. Defaults to "localhost".
 * @param port The port number used for establishing the connection. If not specified, the default
 *             port for the specified protocol will be used.
 * @param protocol The protocol used for the connection, such as HTTP or HTTPS. Defaults to HTTPS.
 */
public class DefaultClientConnectionConfig(
    host: String = DEFAULT_HOST,
    port: Int? = DEFAULT_PORT,
    protocol: URLProtocol = defaultProtocol,
) : ClientConnectionConfig(host, port, protocol) {

    private companion object {

        const val DEFAULT_PORT: Int = 50881

        const val DEFAULT_HOST: String = "127.0.0.1"

        val defaultProtocol: URLProtocol = URLProtocol.HTTPS
    }
}
