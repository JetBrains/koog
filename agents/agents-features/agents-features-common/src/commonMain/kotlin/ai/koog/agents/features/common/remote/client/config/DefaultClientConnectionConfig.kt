package ai.koog.agents.features.common.remote.client.config

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
    host: String = "localhost",
    port: Int? = null,
    protocol: URLProtocol = URLProtocol.HTTPS,
) : ClientConnectionConfig(host, port, protocol)
