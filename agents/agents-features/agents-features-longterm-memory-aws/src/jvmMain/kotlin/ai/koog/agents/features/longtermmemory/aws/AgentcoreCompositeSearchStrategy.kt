package ai.koog.agents.features.longtermmemory.aws

import ai.koog.agents.features.longtermmemory.aws.augmentation.AgentcoreMemoryStrategy
import ai.koog.agents.features.longtermmemory.aws.request.AgentcoreCompositeSearchRequest
import ai.koog.agents.features.longtermmemory.aws.request.AgentcoreListingSearchRequest
import ai.koog.agents.features.longtermmemory.aws.request.AgentcoreSearchRequest
import ai.koog.agents.features.longtermmemory.aws.request.AgentcoreSimilaritySearchRequest
import ai.koog.agents.longtermmemory.retrieval.search.SearchStrategy
import ai.koog.rag.base.storage.search.SearchRequest

/**
 * A [SearchStrategy] that produces an [AgentcoreCompositeSearchRequest] from a fixed
 * list of subrequest templates at retrieval time.
 *
 * subrequests can target different AgentCore strategies (different `memoryStrategyId`) and
 * different namespace scopes — for example, a PREFERENCE listing merged with a
 * SEMANTIC similarity search, or EPISODES (session-scoped) merged with
 * REFLECTIONS (actor-scoped).
 *
 * The outer query string produced by
 * [ai.koog.agents.longtermmemory.retrieval.search.SearchQueryProvider] is injected into each
 * similarity subrequest at [create] time. Listing subrequests do not use the query.
 *
 * Example:
 * ```kotlin
 * val strategy = AgentcoreCompositeSearchStrategy(
 *     listOf(
 *         AgentcoreSearchSubrequest.similarity(
 *             memoryStrategyId = "sem-1",
 *             namespace = AgentcoreNamespaceResolver.Default.resolve(AgentcoreNamespaceScope.Actor("sem-1", "alice")),
 *             limit = 5,
 *         ),
 *         AgentcoreSearchSubrequest.listing(
 *             memoryStrategyId = "prefs-1",
 *             namespace = AgentcoreNamespaceResolver.Default.resolve(AgentcoreNamespaceScope.Actor("prefs-1", "alice")),
 *             limit = 20,
 *         ),
 *     )
 * )
 * ```
 */
public class AgentcoreCompositeSearchStrategy(
    public val subrequests: List<AgentcoreSearchSubrequest>,
) : SearchStrategy {

    init {
        require(subrequests.isNotEmpty()) { "AgentcoreCompositeSearchStrategy must contain at least one subrequest" }
    }

    override fun create(query: String): SearchRequest = AgentcoreCompositeSearchRequest(
        entries = subrequests.map { template ->
            AgentcoreCompositeSearchRequest.Entry(
                request = template.buildRequest(query),
                namespace = template.namespace,
            )
        },
    )

    /**
     * Declarative template for a single subrequest of an [AgentcoreCompositeSearchStrategy].
     *
     * Templates are resolved into concrete [AgentcoreSearchRequest]s at
     * [AgentcoreCompositeSearchStrategy.create] time; this lets the containing strategy
     * inject the per-turn query string into similarity subrequests while leaving listing subrequests
     * query-free.
     */
    public sealed interface AgentcoreSearchSubrequest {
        /**
         * Namespace to which the produced subrequest request will be scoped.
         */
        public val namespace: String

        /**
         * Produces the concrete subrequest [AgentcoreSearchRequest] for the given [query].
         */
        public fun buildRequest(query: String): AgentcoreSearchRequest

        public companion object {
            /**
             * Build a similarity-search subrequest against [memoryStrategyId] in [namespace].
             *
             * The [query] passed to [AgentcoreCompositeSearchStrategy.create] is used
             * as `queryText`.
             */
            public fun similarity(
                strategyType: AgentcoreMemoryStrategy,
                memoryStrategyId: String,
                namespace: String,
                limit: Int = 10,
                minScore: Double? = null,
                filterExpression: String? = null,
            ): AgentcoreSearchSubrequest = Similarity(
                strategyType = strategyType,
                memoryStrategyId = memoryStrategyId,
                namespace = namespace,
                limit = limit,
                minScore = minScore,
                filterExpression = filterExpression,
            )

            /**
             * Build a listing subrequest against [memoryStrategyId] in [namespace]. The query
             * is ignored.
             */
            public fun listing(
                strategyType: AgentcoreMemoryStrategy,
                memoryStrategyId: String,
                namespace: String,
                limit: Int = 10,
            ): AgentcoreSearchSubrequest = Listing(
                strategyType = strategyType,
                memoryStrategyId = memoryStrategyId,
                namespace = namespace,
                limit = limit,
            )
        }

        private data class Similarity(
            val strategyType: AgentcoreMemoryStrategy,
            val memoryStrategyId: String,
            override val namespace: String,
            val limit: Int,
            val minScore: Double?,
            val filterExpression: String?,
        ) : AgentcoreSearchSubrequest {
            override fun buildRequest(query: String): AgentcoreSearchRequest =
                AgentcoreSimilaritySearchRequest(
                    strategyType = strategyType,
                    memoryStrategyId = memoryStrategyId,
                    queryText = query,
                    limit = limit,
                    minScore = minScore,
                    filterExpression = filterExpression,
                )
        }

        private data class Listing(
            val strategyType: AgentcoreMemoryStrategy,
            val memoryStrategyId: String,
            override val namespace: String,
            val limit: Int,
        ) : AgentcoreSearchSubrequest {
            override fun buildRequest(query: String): AgentcoreSearchRequest =
                AgentcoreListingSearchRequest(
                    strategyType = strategyType,
                    memoryStrategyId = memoryStrategyId,
                    limit = limit,
                )
        }
    }
}
