package ai.koog.agents.features.longtermmemory.aws.dsl

import ai.koog.agents.features.longtermmemory.aws.AgentcoreCompositeSearchStrategy
import ai.koog.agents.features.longtermmemory.aws.AgentcoreCompositeSearchStrategy.AgentcoreSearchSubrequest
import ai.koog.agents.features.longtermmemory.aws.AgentcoreNamespaceResolver
import ai.koog.agents.features.longtermmemory.aws.AgentcoreNamespaceScope
import ai.koog.agents.features.longtermmemory.aws.AgentcoreSearchStorage
import ai.koog.agents.features.longtermmemory.aws.augmentation.AgentcoreMemoryStrategy
import ai.koog.agents.features.longtermmemory.aws.augmentation.AgentcorePromptAugmenter
import ai.koog.agents.longtermmemory.feature.LongTermMemory
import ai.koog.agents.longtermmemory.retrieval.augmentation.PromptAugmenter
import ai.koog.agents.longtermmemory.retrieval.augmentation.SystemPromptAugmenter
import ai.koog.agents.longtermmemory.retrieval.search.SearchStrategy
import aws.sdk.kotlin.services.bedrockagentcore.BedrockAgentCoreClient

/**
 * DSL marker for the AgentCore retrieval configuration block to avoid accidental
 * receiver shadowing with the outer [LongTermMemory.RetrievalSettingsBuilder].
 */
@DslMarker
public annotation class AgentcoreLtmDsl

/**
 * Configure AWS Bedrock AgentCore memory as the source of [LongTermMemory]'s retrieval.
 *
 * Every `agentcore { }` block is a composite retrieval: each subrequest helper inside [block]
 * appends one [AgentcoreSearchSubrequest] to an [AgentcoreCompositeSearchStrategy]. Mixed strategies
 * are allowed (e.g., a PREFERENCE listing subrequest and a SEMANTIC similarity subrequest in the
 * same block), and multiple subrequests can target the same `memoryStrategyId` / `actorId` in
 * different namespace scopes (this is how EPISODIC memories issue session‑scoped
 * episodes and actor‑scoped reflections with a single strategy id).
 *
 * At least one subrequest is required.
 *
 * Example:
 * ```kotlin
 * install(LongTermMemory) {
 *     retrieval {
 *         agentcore(client, memoryId = "mem-123") {
 *             semantic(strategyId = "sem-1", actorId = "alice", topK = 5)
 *             userPreferences(strategyId = "up-1", actorId = "alice")
 *         }
 *     }
 * }
 * ```
 *
 * @param client the Bedrock AgentCore client used to talk to AWS.
 * @param memoryId the AgentCore memory store identifier.
 * @param block DSL block appending one or more retrieval subrequests.
 */
public fun LongTermMemory.RetrievalSettingsBuilder.agentcore(
    client: BedrockAgentCoreClient,
    memoryId: String,
    block: AgentcoreRetrievalBuilder.() -> Unit,
) {
    require(memoryId.isNotBlank()) { "memoryId must not be blank" }

    val configured = AgentcoreRetrievalBuilder(client, memoryId).apply(block).buildOrError()

    storage = configured.storage
    searchStrategy = configured.searchStrategy
    namespace = configured.namespace
    promptAugmenter = configured.promptAugmenter
}

/**
 * Builder that collects retrieval subrequests for [agentcore].
 *
 * Each subrequest helper appends one [AgentcoreSearchSubrequest] pointing at a specific AgentCore
 * `memoryStrategyId` and namespace scope. subrequests may freely mix similarity and listing
 * requests, different strategy ids, and different namespace scopes; the same
 * `memoryStrategyId` may appear in multiple subrequests with different namespaces (e.g.,
 * EPISODIC episodes in a session‑scoped namespace and reflections in an actor‑scoped
 * namespace, both under the same strategy id).
 *
 * The augmentation point for the merged result can be chosen via [augmenter]; it
 * defaults to [SystemPromptAugmenter].
 */
@AgentcoreLtmDsl
public class AgentcoreRetrievalBuilder internal constructor(
    private val client: BedrockAgentCoreClient,
    private val memoryId: String,
) {
    private val subrequests: MutableList<AgentcoreSearchSubrequest> = mutableListOf()

    /**
     * How the retrieved context is inserted into the prompt. Defaults to
     * [AgentcorePromptAugmenter], which routes each record to the appropriate augmentation
     * pathway based on its [ai.koog.agents.features.longtermmemory.aws.augmentation.AgentcoreMemoryStrategy]
     * (system message injection for SEMANTIC/PREFERENCE/EPISODES/REFLECTIONS, user message
     * rewrite for SUMMARY). Set to a [ai.koog.agents.longtermmemory.retrieval.augmentation.UserPromptAugmenter]
     * or [SystemPromptAugmenter] to override with a strategy-agnostic augmenter.
     */
    public var augmenter: PromptAugmenter = AgentcorePromptAugmenter()

    /**
     * Resolver used to build AgentCore namespaces for every helper in this block.
     *
     * Defaults to [AgentcoreNamespaceResolver.Default] which produces AWS's documented layout:
     * `/strategies/{strategyId}/actors/{actorId}/[sessions/{sessionId}/]`.
     *
     * Override it when your memory store was created with a different namespace pattern —
     * either pass [AgentcoreNamespaceResolver.template] with your own templates, or supply a
     * fully custom [AgentcoreNamespaceResolver] implementation. The `subrequest(...)` escape
     * hatch bypasses the resolver: raw subrequest templates keep their own namespace verbatim.
     */
    public var namespaceResolver: AgentcoreNamespaceResolver = AgentcoreNamespaceResolver.Default

    private fun actorNamespace(strategyId: String, actorId: String): String =
        namespaceResolver.resolve(AgentcoreNamespaceScope.Actor(strategyId, actorId))

    private fun sessionNamespace(strategyId: String, actorId: String, sessionId: String): String =
        namespaceResolver.resolve(AgentcoreNamespaceScope.Session(strategyId, actorId, sessionId))

    /**
     * Append a SEMANTIC similarity subrequest against [strategyId] in an actor‑scoped namespace.
     */
    public fun semantic(
        strategyId: String,
        actorId: String,
        topK: Int = 5,
        minScore: Double? = null,
        filterExpression: String? = null,
    ) {
        require(strategyId.isNotBlank()) { "strategyId must not be blank" }
        require(topK > 0) { "topK must be positive, was $topK" }
        subrequests += AgentcoreSearchSubrequest.similarity(
            strategyType = AgentcoreMemoryStrategy.SEMANTIC,
            memoryStrategyId = strategyId,
            namespace = actorNamespace(strategyId, actorId),
            limit = topK,
            minScore = minScore,
            filterExpression = filterExpression,
        )
    }

    /**
     * Append a SUMMARY similarity subrequest against [strategyId] in a session‑scoped namespace.
     */
    public fun summary(
        strategyId: String,
        actorId: String,
        sessionId: String,
        topK: Int = 3,
        minScore: Double? = null,
        filterExpression: String? = null,
    ) {
        require(strategyId.isNotBlank()) { "strategyId must not be blank" }
        require(topK > 0) { "topK must be positive, was $topK" }
        subrequests += AgentcoreSearchSubrequest.similarity(
            strategyType = AgentcoreMemoryStrategy.SUMMARY,
            memoryStrategyId = strategyId,
            namespace = sessionNamespace(strategyId, actorId, sessionId),
            limit = topK,
            minScore = minScore,
            filterExpression = filterExpression,
        )
    }

    /**
     * Append a PREFERENCE listing subrequest against [strategyId] in an actor‑scoped namespace.
     *
     * AgentCore returns this as a listing, so results carry no meaningful score.
     */
    public fun userPreferences(
        strategyId: String,
        actorId: String,
        limit: Int = 50,
    ) {
        require(strategyId.isNotBlank()) { "strategyId must not be blank" }
        require(limit > 0) { "limit must be positive, was $limit" }
        subrequests += AgentcoreSearchSubrequest.listing(
            strategyType = AgentcoreMemoryStrategy.PREFERENCE,
            memoryStrategyId = strategyId,
            namespace = actorNamespace(strategyId, actorId),
            limit = limit,
        )
    }

    /**
     * Append an EPISODIC "episodes" similarity subrequest (session‑scoped).
     */
    public fun episodes(
        strategyId: String,
        actorId: String,
        sessionId: String,
        topK: Int = 3,
        minScore: Double? = null,
        filterExpression: String? = null,
    ) {
        require(strategyId.isNotBlank()) { "strategyId must not be blank" }
        require(topK > 0) { "topK must be positive, was $topK" }
        subrequests += AgentcoreSearchSubrequest.similarity(
            strategyType = AgentcoreMemoryStrategy.EPISODES,
            memoryStrategyId = strategyId,
            namespace = sessionNamespace(strategyId, actorId, sessionId),
            limit = topK,
            minScore = minScore,
            filterExpression = filterExpression,
        )
    }

    /**
     * Append an EPISODIC "reflections" similarity subrequest (actor‑scoped).
     */
    public fun reflections(
        strategyId: String,
        actorId: String,
        topK: Int = 2,
        minScore: Double? = null,
        filterExpression: String? = null,
    ) {
        require(strategyId.isNotBlank()) { "strategyId must not be blank" }
        require(topK > 0) { "topK must be positive, was $topK" }
        subrequests += AgentcoreSearchSubrequest.similarity(
            strategyType = AgentcoreMemoryStrategy.REFLECTIONS,
            memoryStrategyId = strategyId,
            namespace = actorNamespace(strategyId, actorId),
            limit = topK,
            minScore = minScore,
            filterExpression = filterExpression,
        )
    }

    /**
     * Append the two subrequests typically associated with EPISODIC memory: extracted episodes
     * (session‑scoped) and reflections (actor‑scoped). Both subrequests may share the same
     * [strategyId] (the default) — AgentCore distinguishes them purely by namespace —
     * or they may point at different strategy ids via [reflectionsStrategyId].
     *
     * This is convenience sugar; calling [episodes] and [reflections] explicitly is
     * equivalent.
     *
     * @param strategyId strategy id for extracted episodes (session‑scoped).
     * @param reflectionsStrategyId strategy id for reflections (actor‑scoped);
     *   defaults to [strategyId] when AgentCore uses a single strategy id for both.
     */
    public fun episodic(
        strategyId: String,
        actorId: String,
        sessionId: String,
        reflectionsStrategyId: String = strategyId,
        episodesTopK: Int = 3,
        reflectionsTopK: Int = 2,
        minScore: Double? = null,
    ) {
        episodes(
            strategyId = strategyId,
            actorId = actorId,
            sessionId = sessionId,
            topK = episodesTopK,
            minScore = minScore,
        )
        reflections(
            strategyId = reflectionsStrategyId,
            actorId = actorId,
            topK = reflectionsTopK,
            minScore = minScore,
        )
    }

    /**
     * Append a pre‑built subrequest template; escape hatch for uncommon combinations.
     */
    public fun subrequest(template: AgentcoreSearchSubrequest) {
        subrequests += template
    }

    internal fun buildOrError(): Configured {
        check(subrequests.isNotEmpty()) {
            "agentcore { } must contain at least one subrequest " +
                "(semantic/summary/userPreferences/episodes/reflections/episodic/subrequest)"
        }
        return Configured(
            storage = AgentcoreSearchStorage(client, memoryId),
            searchStrategy = AgentcoreCompositeSearchStrategy(subrequests.toList()),
            // Composite requests ignore the top-level namespace; each subrequest carries its own.
            namespace = null,
            promptAugmenter = augmenter,
        )
    }

    internal data class Configured(
        val storage: AgentcoreSearchStorage,
        val searchStrategy: SearchStrategy,
        val namespace: String?,
        val promptAugmenter: PromptAugmenter,
    )
}
