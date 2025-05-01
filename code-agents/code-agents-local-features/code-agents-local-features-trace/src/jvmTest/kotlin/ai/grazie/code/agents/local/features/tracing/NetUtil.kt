package ai.grazie.code.agents.local.features.tracing

import io.ktor.utils.io.core.use
import java.net.ServerSocket

object NetUtil {

    fun findAvailablePort(): Int {
        ServerSocket(0).use { socket ->
            return socket.localPort
        }
    }

}