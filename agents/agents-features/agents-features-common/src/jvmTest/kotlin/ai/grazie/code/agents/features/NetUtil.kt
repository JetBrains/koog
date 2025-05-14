package ai.grazie.code.agents.features

import io.ktor.utils.io.core.*
import java.net.ServerSocket

object NetUtil {

    fun findAvailablePort(): Int {
        ServerSocket(0).use { socket ->
            return socket.localPort
        }
    }

}
