package ai.koog.agents.core.feature.debugger

import ai.koog.agents.core.feature.config.FeatureConfig
import kotlin.time.Duration

/**
 * Configuration class for managing debugger-specific settings.
 *
 * This class extends the base `FeatureConfig` to enable the configuration of
 * debugger-related parameters. It allows setting and retrieving the port
 * number used by the debugger.
 */
public class DebuggerConfig : FeatureConfig() {

    private var _port: Int? = null

    private var _awaitInitialConnectionTimeout: Duration? = null

    /**
     * The port number used by the debugger.
     */
    public val port: Int?
        get() = _port

    /**
     * The optional duration to wait for establishing a connection with the debugger.
     * Use an infinite awaiting if value is not defined.
     */
    public val awaitInitialConnectionTimeout: Duration?
        get() = _awaitInitialConnectionTimeout

    /**
     * Sets the port number to be used by the debugger.
     *
     * @param port The port number to set.
     */
    public fun setPort(port: Int) {
        _port = port
    }

    /**
     * Sets the duration to wait for establishing an initial connection with the debugger.
     *
     * @param timeout The duration to be set for awaiting the initial connection.
     */
    public fun setAwaitInitialConnectionTimeout(timeout: Duration) {
        _awaitInitialConnectionTimeout = timeout
    }
}
