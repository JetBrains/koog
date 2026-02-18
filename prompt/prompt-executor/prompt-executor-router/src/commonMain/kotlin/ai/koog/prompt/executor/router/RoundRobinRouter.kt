package ai.koog.prompt.executor.router

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement

/**
 * Internal implementation of [RoutingStrategy.ROUND_ROBIN].
 *
 * Maintains separate atomic counters per provider for thread-safe rotation.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class RoundRobinRouter(override val availableClients: List<LLMClient>) : LLMClientRouter {

    init {
        require(availableClients.isNotEmpty()) { "RoundRobinRouter requires at least one LLMClient." }
    }

    private val clientsByProvider: Map<LLMProvider, List<LLMClient>> =
        availableClients.groupBy { it.llmProvider() }

    private val countersByProvider: Map<LLMProvider, AtomicInt> =
        clientsByProvider.keys.associateWith { AtomicInt(0) }

    override fun chooseRouteFor(model: LLModel): LLMClient? {
        if (model.provider !in clientsByProvider) return null
        val supportingClients = clientsByProvider[model.provider]!!
        val counter = countersByProvider[model.provider]!!
        return supportingClients[counter.fetchAndIncrement().mod(supportingClients.size)]
    }
}
