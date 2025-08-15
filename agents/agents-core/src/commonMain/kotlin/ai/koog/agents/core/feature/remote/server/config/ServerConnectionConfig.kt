package ai.koog.agents.core.feature.remote.server.config

import ai.koog.agents.core.feature.remote.ConnectionConfig

/**
 * Configuration class for setting up a server connection.
 *
 * @property host The host on which the server will listen to;
 * @property port The port number on which the server will listen to;
 * @property waitConnection Indicates whether the server waits for a first connection before continuing.
 */
public abstract class ServerConnectionConfig(
    public val host: String,
    public val port: Int,
    public val waitConnection: Boolean,
) : ConnectionConfig()
