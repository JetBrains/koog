package ai.koog.agents.testing.network

import io.ktor.utils.io.core.use
import java.net.ServerSocket

/**
 * Utility object providing network-related utility functions.
 */
public object NetUtil {

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

}