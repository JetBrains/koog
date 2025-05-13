package ai.grazie.code.agents.core.feature.remote.client.config

import ai.grazie.code.agents.core.feature.agentFeatureMessageSerializersModule
import ai.grazie.code.agents.local.features.common.remote.client.config.ClientConnectionConfig
import io.ktor.http.URLProtocol

class AgentFeatureClientConnectionConfig(
    host: String,
    port: Int? = null,
    protocol: URLProtocol = URLProtocol.HTTPS,
) : ClientConnectionConfig(host, port, protocol) {

    init {
        appendSerializersModule(agentFeatureMessageSerializersModule)
    }
}
