@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.a2a.client

import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.transport.ClientTransport
import ai.koog.a2a.utils.RWLock
import kotlin.concurrent.Volatile

/**
 * A2A client responsible for sending requests to A2A server.
 */
public open class A2AClient(
    private val transport: ClientTransport,
    private val agentCardResolver: AgentCardResolver,
) {
    @Volatile
    public lateinit var agentCard: AgentCard
        private set

    private val cardLock = RWLock()

    public suspend fun connect(): AgentCard = cardLock.withWriteLock {
        agentCard = agentCardResolver.resolve()
        agentCard
    }
}

public fun A2AClient(
    transport: ClientTransport,
    baseUrl: String,
    cardPath: String = UrlAgentCardResolver.wellKnownPath,
): A2AClient {
    return A2AClient(
        transport = transport,
        agentCardResolver = UrlAgentCardResolver(baseUrl, cardPath),
    )
}
