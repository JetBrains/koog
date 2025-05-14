package ai.grazie.code.agents.core.feature.remote.server.config

import ai.grazie.code.agents.core.feature.agentFeatureMessageSerializersModule
import ai.grazie.code.agents.features.common.remote.server.config.ServerConnectionConfig

class AIAgentFeatureServerConnectionConfig(port: Int) : ServerConnectionConfig(port) {

    init {
        appendSerializersModule(agentFeatureMessageSerializersModule)
    }
}
