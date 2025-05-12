package ai.grazie.code.agents.core.feature.remote.client.config

import ai.grazie.code.agents.core.feature.agentFeatureMessageSerializersModule
import ai.grazie.code.agents.local.features.common.remote.client.config.ClientConnectionConfig

class AgentFeatureClientConnectionConfig(
    host: String,
    port: Int,
    protocol: String = "https",
) : ClientConnectionConfig(host, port, protocol) {

    init {
        appendSerializersModule(agentFeatureMessageSerializersModule)
    }
}
