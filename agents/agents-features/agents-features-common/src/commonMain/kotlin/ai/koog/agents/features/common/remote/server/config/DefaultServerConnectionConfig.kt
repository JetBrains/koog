package ai.koog.agents.features.common.remote.server.config

import io.ktor.http.DEFAULT_PORT

/**
 * Default implementation of the server connection configuration.
 *
 * This class provides configuration settings for setting up a server connection,
 * extending the `ServerConnectionConfig` base class. It initializes the server
 * port configuration to a default value unless explicitly specified.
 *
 * @param port The port number on which the server will listen to. Defaults to 8080.
 */
public class DefaultServerConnectionConfig(port: Int = DEFAULT_PORT) : ServerConnectionConfig(port = port)
