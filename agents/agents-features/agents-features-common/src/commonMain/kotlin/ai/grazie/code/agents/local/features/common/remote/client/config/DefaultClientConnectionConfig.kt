package ai.grazie.code.agents.local.features.common.remote.client.config

import io.ktor.http.URLProtocol

class DefaultClientConnectionConfig(
    host: String = "localhost",
    port: Int? = null,
    protocol: URLProtocol = URLProtocol.HTTPS,
) : ClientConnectionConfig(host, port, protocol)
