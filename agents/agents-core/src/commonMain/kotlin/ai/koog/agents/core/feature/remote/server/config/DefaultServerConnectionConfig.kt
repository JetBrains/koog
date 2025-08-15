package ai.koog.agents.core.feature.remote.server.config

/**
 * Default implementation of the server connection configuration.
 *
 * This class provides configuration settings for setting up a server connection,
 * extending the `ServerConnectionConfig` base class. It initializes the server
 * port configuration to a default value unless explicitly specified.
 *
 * @param host The hostname or IP address of the server to connect to. Defaults to '127.0.0.1';
 * @param port The port number on which the server will listen to. Defaults to 50881;
 * @param waitConnection Indicates whether the server waits for a first connection before continuing.
 *        Set to 'false' by default.
 */
public class DefaultServerConnectionConfig(
    host: String = DEFAULT_HOST,
    port: Int = DEFAULT_PORT,
    waitConnection: Boolean = DEFAULT_WAIT_CONNECTION,
) : ServerConnectionConfig(host = host, port = port, waitConnection = waitConnection) {

    /**
     * Contains default configurations for server connection parameters.
     *
     * This companion object provides constant values used as default
     * configurations for setting up a server connection. These defaults
     * include the server port, host address, and a suspend flag.
     *
     * These constants are used in the `DefaultServerConnectionConfig` class
     * to provide an initial configuration unless explicitly overridden.
     *
     * @property DEFAULT_PORT The default port number the server will listen to.
     * @property DEFAULT_HOST The default host address for the connection.
     * @property DEFAULT_WAIT_CONNECTION Indicates whether the server waits for a first connection before continuing.
     */
    public companion object {

        /**
         * The default port number the server will listen to. Defaults to 50881.
         */
        public const val DEFAULT_PORT: Int = 50881

        /**
         * The default hostname or IP address for the server connection.
         */
        public const val DEFAULT_HOST: String = "127.0.0.1"

        internal const val DEFAULT_WAIT_CONNECTION: Boolean = false
    }
}
