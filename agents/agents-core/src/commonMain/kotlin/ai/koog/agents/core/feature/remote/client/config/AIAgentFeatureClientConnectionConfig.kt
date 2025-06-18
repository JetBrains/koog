package ai.koog.agents.core.feature.remote.client.config

import ai.koog.agents.core.feature.agentFeatureMessageSerializersModule
import ai.koog.agents.features.common.remote.client.config.ClientConnectionConfig
import io.ktor.http.URLProtocol

/**
 * A configuration class for setting up a client connection to an AI agent's feature service.
 *
 * Inherits from `ClientConnectionConfig` and introduces additional logic to integrate
 * specific serializers required for handling feature-related message types.
 *
 * @constructor Creates a new instance of the configuration with a specified host, optional port,
 * and protocol. The default protocol is HTTPS.
 *
 * @param host The hostname or IP address of the server to connect to.
 * @param port The port number used for establishing the connection, or null to use the default port for the protocol.
 * @param protocol The protocol used for the connection (e.g., HTTP, HTTPS), defaulting to HTTPS.
 *
 * This class automatically appends a predefined serializers module, `agentFeatureMessageSerializersModule`,
 * to the JSON configuration during initialization. This enables proper serialization and deserialization of
 * feature-related message payloads.
 */
public class AIAgentFeatureClientConnectionConfig(
    host: String,
    port: Int? = null,
    protocol: URLProtocol = URLProtocol.HTTPS,
) : ClientConnectionConfig(host, port, protocol) {

    init {
        appendSerializersModule(agentFeatureMessageSerializersModule)
    }
}
