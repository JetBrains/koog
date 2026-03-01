package ai.koog.prompt.executor.llms

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLModel

/**
 * Routes requests to available LLM clients.
 *
 * Responsible for selecting which client should handle a request for a given model,
 * based on factors like load distribution, availability, or health.
 */
@OptIn(ExperimentalRoutingApi::class)
public interface LLMClientRouter {

    /**
     * All clients that this router can use
     */
    public val clients: List<LLMClient>

    /**
     * Selects a client to handle the given model.
     *
     * @param model The model to route
     * @return A client capable of serving the model, or null if none available
     */
    public fun clientFor(model: LLModel): LLMClient?
}
