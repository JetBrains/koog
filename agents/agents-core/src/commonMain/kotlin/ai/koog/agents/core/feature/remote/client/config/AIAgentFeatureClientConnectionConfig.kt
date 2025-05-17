package ai.koog.agents.core.feature.remote.client.config

import ai.koog.agents.core.feature.agentFeatureMessageSerializersModule
import ai.koog.agents.local.features.common.remote.client.config.ClientConnectionConfig
import io.ktor.http.URLProtocol

public class AIAgentFeatureClientConnectionConfig(
    host: String,
    port: Int? = null,
    protocol: URLProtocol = URLProtocol.HTTPS,
) : ClientConnectionConfig(host, port, protocol) {

    init {
        appendSerializersModule(agentFeatureMessageSerializersModule)
    }
}
