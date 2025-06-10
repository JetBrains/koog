package ai.koog.agents.core.feature.remote.server.config

import ai.koog.agents.core.feature.agentFeatureMessageSerializersModule
import ai.koog.agents.features.common.remote.server.config.ServerConnectionConfig

public class AIAgentFeatureServerConnectionConfig(host: String, port: Int) :
    ServerConnectionConfig(host = host, port = port) {

    init {
        appendSerializersModule(agentFeatureMessageSerializersModule)
    }
}
