package ai.grazie.code.agents.features.common.remote.server.config

import ai.grazie.code.agents.features.common.remote.ConnectionConfig

/**
 * Configuration class for setting up a server connection.
 *
 * @property port The port number on which the server will listen to. Defaults to 8080;
 * @property jsonConfig The effective JSON configuration to be used, falling back to a default configuration
 *                      if a custom configuration is not provided;
 */
abstract class ServerConnectionConfig(val port: Int = DEFAULT_PORT) : ConnectionConfig() {

    companion object {
        private const val DEFAULT_PORT = 8080
    }
}