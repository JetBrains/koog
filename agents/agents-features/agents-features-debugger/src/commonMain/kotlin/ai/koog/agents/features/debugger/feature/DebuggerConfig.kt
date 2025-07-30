package ai.koog.agents.features.debugger.feature

import ai.koog.agents.core.feature.config.FeatureConfig

/**
 * Configuration class for managing debugger-specific settings.
 *
 * This class extends the base `FeatureConfig` to enable the configuration of
 * debugger-related parameters. It allows setting and retrieving the port
 * number used by the debugger.
 */
public class DebuggerConfig : FeatureConfig() {

    private var _port: Int? = null

    /**
     * The port number used by the debugger.
     */
    public val port: Int?
        get() = _port

    /**
     * Sets the port number to be used by the debugger.
     *
     * @param port The port number to set.
     */
    public fun setPort(port: Int) {
        _port = port
    }
}
