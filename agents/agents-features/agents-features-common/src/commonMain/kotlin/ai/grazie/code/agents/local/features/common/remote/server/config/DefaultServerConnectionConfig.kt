package ai.grazie.code.agents.local.features.common.remote.server.config

import io.ktor.http.DEFAULT_PORT

class DefaultServerConnectionConfig(port: Int = DEFAULT_PORT) : ServerConnectionConfig(port = port)
