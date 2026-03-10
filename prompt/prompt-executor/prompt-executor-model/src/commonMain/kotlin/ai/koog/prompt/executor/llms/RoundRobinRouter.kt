package ai.koog.prompt.executor.llms

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement

/**
 * Distributes requests across clients in sequential rotation, ensuring even load distribution.
 *
 * When multiple clients serve the same provider, cycles through them using an atomic counter.
 * Thread-safe for concurrent access.
 *
 * @param clientsPerProvider Map of providers to their available clients
 */
@OptIn(ExperimentalAtomicApi::class, ExperimentalRoutingApi::class)
public class RoundRobinRouter(clientsPerProvider: Map<LLMProvider, List<LLMClient>>) : LLMClientRouter {

    init {
        require(clientsPerProvider.isNotEmpty()) { "RoundRobinRouter requires at least one LLMClient." }
    }

    /**
     * Creates router from a flat list of clients, grouping by provider automatically.
     */
    public constructor(clients: List<LLMClient>) : this(clients.groupBy { it.llmProvider() })

    /**
     * Creates router from vararg clients, grouping by provider automatically.
     */
    public constructor(vararg clients: LLMClient) : this(clients.toList())

    /**
     * Creates router from provider-client pairs, grouping by provider automatically.
     */
    public constructor(vararg llmClients: Pair<LLMProvider, LLMClient>) :
        this(llmClients.groupBy { it.first }.mapValues { it.value.map { it.second } })

    override val clients: List<LLMClient> = clientsPerProvider.values.flatten()

    private val clientPoolsPerProvider: Map<LLMProvider, ClientPool>

    init {
        clientPoolsPerProvider = clientsPerProvider.mapValues { (_, clients) ->
            when (clients.size) {
                1 -> ClientPool.Single(clients.first())
                else -> ClientPool.Multiple(clients)
            }
        }
    }

    override fun clientFor(model: LLModel): LLMClient? {
        return clientPoolsPerProvider[model.provider]?.next()
    }

    private sealed class ClientPool {
        abstract fun next(): LLMClient

        class Single(val client: LLMClient) : ClientPool() {
            override fun next(): LLMClient = client
        }

        class Multiple(val clients: List<LLMClient>) : ClientPool() {
            private val counter = AtomicInt(0)

            override fun next(): LLMClient {
                return clients[counter.fetchAndIncrement().mod(clients.size)]
            }
        }
    }
}
