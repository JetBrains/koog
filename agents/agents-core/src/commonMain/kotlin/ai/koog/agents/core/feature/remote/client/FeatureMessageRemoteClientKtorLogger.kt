package ai.koog.agents.core.feature.remote.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.plugins.logging.Logger

/**
 * A logging utility class that implements the `Logger` interface, designed to provide logging
 * capabilities for the `FeatureMessageRemoteClient` implemented using Ktor.
 *
 * This logger leverages a custom logger instance to output debug messages and manage
 * logging behavior based on the configured debug state.
 */
internal class FeatureMessageRemoteClientKtorLogger : Logger {

    /**
     * Companion object for the `FeatureMessageRemoteClientKtorLogger` class.
     *
     * This object contains utility functions and properties that are shared across all instances of
     * `FeatureMessageRemoteClientKtorLogger`. It also aids in centralizing static-like behavior and
     * configurations specific to this logger.
     */
    private companion object {

        /**
         * A private logger instance used for logging debug and informational messages.
         *
         * This logger is specifically created for the `FeatureMessageRemoteClientKtorLogger` class to handle
         * logging functionality, such as outputting debug information or tracing issues within the class operations.
         * It uses the `LoggerFactory` to configure and provide the appropriate logging mechanism.
         */
        private val logger = KotlinLogging.logger { }
    }

    /**
     * Indicates whether debug logging is enabled for the current logger.
     *
     * This property provides a way to check if debug-level logging is active for the associated logger.
     * If `true`, debug messages will be logged; otherwise, they will be ignored.
     *
     * This can be useful for conditionally executing debug-related code or to avoid unnecessary computations
     * when debug logging is not enabled.
     */
    val debugEnabled: Boolean
        get() = logger.isDebugEnabled()

    /**
     * Logs a debug message using the internal logger.
     *
     * This method captures debug-level log messages and sends them to the
     * associated logger implementation for further processing or storage.
     * It is intended for logging debug information that can aid in diagnosing issues
     * or understanding the program's internal state during execution.
     *
     * @param message The debug message to log.
     */
    override fun log(message: String) {
        logger.debug { message }
    }
}
