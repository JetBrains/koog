package ai.grazie.code.agents.core.feature.remote.server.config

import ai.grazie.code.agents.core.feature.agentFeatureMessageSerializersModule
import ai.grazie.code.agents.local.features.common.remote.server.config.ServerConnectionConfig

public class AIAgentFeatureServerConnectionConfig(port: Int) : ServerConnectionConfig(port) {

    init {
        appendSerializersModule(agentFeatureMessageSerializersModule)
    }
}
