package ai.koog.agents.core.feature.remote.server.config

import ai.koog.agents.core.feature.remote.ConnectionConfig
import kotlin.time.Duration

/**
 * Configuration class for setting up a server connection.
 *
 * @property host The host on which the server will listen to;
 * @property port The port number on which the server will listen to;
 * @property awaitInitialConnection Indicates whether the server waits for an initial connection before continuing.
 * @property awaitInitialConnectionTimeout The timeout duration for awaiting the initial client connection.
 */
public abstract class ServerConnectionConfig(
    public val host: String,
    public val port: Int,
    public val awaitInitialConnection: Boolean,
    public val awaitInitialConnectionTimeout: Duration,
) : ConnectionConfig()
