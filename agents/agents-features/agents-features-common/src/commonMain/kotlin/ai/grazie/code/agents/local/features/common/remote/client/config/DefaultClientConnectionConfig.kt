package ai.grazie.code.agents.local.features.common.remote.client.config

class DefaultClientConnectionConfig(
    host: String = "localhost",
    port: Int = 8080,
    protocol: String = "https",
) : ClientConnectionConfig(host, port, protocol)
