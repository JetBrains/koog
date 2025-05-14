package ai.grazie.code.agents.core.feature.remote.client.config

import ai.grazie.code.agents.core.feature.agentFeatureMessageSerializersModule
import ai.grazie.code.agents.features.common.remote.client.config.ClientConnectionConfig
import io.ktor.http.URLProtocol

class AIAgentFeatureClientConnectionConfig(
    host: String,
    port: Int? = null,
    protocol: URLProtocol = URLProtocol.HTTPS,
) : ClientConnectionConfig(host, port, protocol) {

    init {
        appendSerializersModule(agentFeatureMessageSerializersModule)
    }
}
