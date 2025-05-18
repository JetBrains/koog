package ai.koog.agents.local.features.common.remote.server.config

import ai.koog.agents.local.features.common.remote.ConnectionConfig

/**
 * Configuration class for setting up a server connection.
 *
 * @property port The port number on which the server will listen to. Defaults to 8080;
 * @property jsonConfig The effective JSON configuration to be used, falling back to a default configuration
 *                      if a custom configuration is not provided;
 */
public abstract class ServerConnectionConfig(public val port: Int = DEFAULT_PORT) : ConnectionConfig() {

    private companion object {
        private const val DEFAULT_PORT = 8080
    }
}
