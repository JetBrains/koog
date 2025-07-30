package ai.koog.agents.core.feature.remote.server.config

import ai.koog.agents.core.feature.agentFeatureMessageSerializersModule

/**
 * Configuration class for setting up an agent feature server connection.
 */
public class AIAgentFeatureServerConnectionConfig(host: String, port: Int, waitConnection: Boolean = false) :
    ServerConnectionConfig(host = host, port = port, waitConnection = waitConnection) {

    init {
        appendSerializersModule(agentFeatureMessageSerializersModule)
    }
}
