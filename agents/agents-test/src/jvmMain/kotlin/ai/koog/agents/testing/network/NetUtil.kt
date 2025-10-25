package ai.koog.agents.testing.network

import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketException

/**
 * Utility object providing network-related utility functions.
 */
public object NetUtil {

    private val logger = KotlinLogging.logger { }

    /**
     * Finds and returns an available port on the local machine.
     *
     * This method opens a temporary server socket to identify an unused port and ensures
     * that the port is released immediately after identification.
     *
     * @return an integer representing an available port number on the local machine
     */
    public fun findAvailablePort(): Int {
        ServerSocket(0).use { socket ->
            return socket.localPort
        }
    }

    /**
     * Check if a specified port is available.
     *
     * @return [Throwable] instance received from ServerSocket when try to bind the port, or NULL otherwise if port is free.
     */
    public fun isPortAvailable(port: Int): Boolean {
        try {
            ServerSocket().use { socket ->
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(port), 0)
            }
            return true
        } catch (t: SocketException) {
            logger.debug(t) { "Unable to bind to port <$port>." }
            return false
        }
    }
}
