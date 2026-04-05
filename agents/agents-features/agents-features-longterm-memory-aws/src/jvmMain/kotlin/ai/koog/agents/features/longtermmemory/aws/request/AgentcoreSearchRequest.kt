package ai.koog.agents.features.longtermmemory.aws.request

import ai.koog.rag.base.storage.search.SearchRequest

/**
 * Base sealed interface for all AWS AgentCore search requests.
 *
 * Extends [SearchRequest] with an additional [memoryStrategyId] property that
 * identifies which AgentCore memory strategy to query. Concrete implementations
 * are [AgentcoreSimilaritySearchRequest] (vector/similarity search),
 * [AgentcoreListingSearchRequest] (listing without a query), and
 * [AgentcoreCompositeSearchRequest] (multiple sub-requests in one logical step).
 */
public sealed interface AgentcoreSearchRequest : SearchRequest {
    /**
     * The identifier of the AgentCore memory strategy to query.
     *
     * This value is used to route the request to the correct memory strategy
     * configured in AWS AgentCore (e.g., a specific knowledge base or episode store).
     */
    public val memoryStrategyId: String
}
