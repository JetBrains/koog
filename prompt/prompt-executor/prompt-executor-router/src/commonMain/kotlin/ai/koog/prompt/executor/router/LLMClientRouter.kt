package ai.koog.prompt.executor.router

import ai.koog.prompt.executor.router.RoutingStrategy.ROUND_ROBIN
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLModel

/**
 * Routes requests to available LLM clients.
 *
 * Responsible for selecting which client should handle a request for a given model,
 * based on factors like load distribution, availability, or health.
 */
public interface LLMClientRouter {

    /**
     * All clients available for routing.
     */
    public val availableClients: List<LLMClient>

    /**
     * Selects a client to handle the given model.
     *
     * @param model The model to route
     * @return A client capable of serving the model, or null if none available
     */
    public fun chooseRouteFor(model: LLModel): LLMClient?

    public companion object {

        public operator fun invoke(
            strategy: RoutingStrategy,
            clients: List<LLMClient>
        ): LLMClientRouter {
            return when (strategy) {
                ROUND_ROBIN -> RoundRobinRouter(clients)
            }
        }

        public operator fun invoke(
            strategy: RoutingStrategy,
            vararg clients: LLMClient
        ): LLMClientRouter =
            invoke(strategy, clients.toList())
    }
}
