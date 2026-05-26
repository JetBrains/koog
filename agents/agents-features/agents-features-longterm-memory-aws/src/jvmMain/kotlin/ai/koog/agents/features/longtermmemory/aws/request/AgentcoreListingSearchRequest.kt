package ai.koog.agents.features.longtermmemory.aws.request

import ai.koog.agents.features.longtermmemory.aws.augmentation.AgentcoreMemoryStrategy

/**
 * An AWS AgentCore search request that retrieves memories by listing, without a text query.
 *
 * Unlike [AgentcoreSimilaritySearchRequest], this request does not perform vector search;
 * it simply lists stored memories within the given [memoryStrategyId], subject to
 * [limit] and [offset] for pagination.
 *
 * @property strategyType the memory strategy kind used to route augmentation of retrieved records
 * @property memoryStrategyId the identifier of the AgentCore memory strategy to query
 * @property limit maximum number of results to return (default: 10)
 * @property offset number of results to skip for pagination (default: 0)
 */
public data class AgentcoreListingSearchRequest(
    val strategyType: AgentcoreMemoryStrategy,
    override val memoryStrategyId: String,
    override val limit: Int = 10,
    override val offset: Int = 0,
) : AgentcoreSearchRequest
