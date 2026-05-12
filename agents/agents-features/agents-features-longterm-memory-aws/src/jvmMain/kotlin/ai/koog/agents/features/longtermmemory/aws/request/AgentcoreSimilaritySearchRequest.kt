package ai.koog.agents.features.longtermmemory.aws.request

import ai.koog.agents.features.longtermmemory.aws.augmentation.AgentcoreMemoryStrategy
import ai.koog.rag.base.storage.search.HasFilterExpression
import ai.koog.rag.base.storage.search.HasScoreThreshold
import ai.koog.rag.base.storage.search.HasTextQuery

/**
 * An AWS AgentCore search request that performs similarity (vector) search.
 *
 * The [queryText] is converted into an embedding vector by the AgentCore backend
 * and compared against stored memory vectors within the given [memoryStrategyId].
 *
 * @property strategyType the memory strategy kind used to route augmentation of retrieved records
 * @property memoryStrategyId the identifier of the AgentCore memory strategy to query
 * @property queryText the text query to find semantically similar memories for
 * @property limit maximum number of results to return (default: 10)
 * @property offset number of results to skip for pagination (default: 0)
 * @property minScore optional minimum similarity score threshold; results below this value are excluded
 * @property filterExpression optional metadata filter expression to narrow down results
 */
public data class AgentcoreSimilaritySearchRequest(
    val strategyType: AgentcoreMemoryStrategy,
    override val memoryStrategyId: String,
    override val queryText: String,
    override val limit: Int = 10,
    override val offset: Int = 0,
    override val minScore: Double? = null,
    override val filterExpression: String? = null,
) : AgentcoreSearchRequest, HasTextQuery, HasScoreThreshold, HasFilterExpression
