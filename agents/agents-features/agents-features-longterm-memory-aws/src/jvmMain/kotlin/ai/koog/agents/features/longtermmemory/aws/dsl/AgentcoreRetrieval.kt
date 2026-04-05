package ai.koog.agents.features.longtermmemory.aws.dsl

import ai.koog.agents.core.annotation.ExperimentalAgentsApi
import ai.koog.agents.features.longtermmemory.aws.AgentcoreCompositeSearchStrategy
import ai.koog.agents.features.longtermmemory.aws.AgentcoreCompositeSearchStrategy.AgentcoreSearchSubrequest
import ai.koog.agents.features.longtermmemory.aws.AgentcoreNamespaceResolver
import ai.koog.agents.features.longtermmemory.aws.AgentcoreNamespaceScope
import ai.koog.agents.features.longtermmemory.aws.AgentcoreSearchStorage
import ai.koog.agents.longtermmemory.feature.LongTermMemory
import ai.koog.agents.longtermmemory.retrieval.augmentation.PromptAugmenter
import ai.koog.agents.longtermmemory.retrieval.augmentation.SystemPromptAugmenter
import aws.sdk.kotlin.services.bedrockagentcore.BedrockAgentCoreClient

/**
 * Java-friendly entry point for configuring AgentCore long-term memory retrieval.
 *
 * Mirrors the Kotlin DSL in [agentcore]: every builder produces an
 * [AgentcoreCompositeSearchStrategy] over one or more [AgentcoreSearchSubrequest]s, paired with an
 * [AgentcoreSearchStorage] and a [PromptAugmenter].
 *
 * Typical Java usage:
 * ```java
 * AgentcoreRetrievalConfig cfg = AgentcoreRetrieval.builder(client, "mem-123")
 *     .semantic("sem-1", "alice", 5)
 *     .userPreferences("up-1", "alice", 50)
 *     .build();
 *
 * LongTermMemory.RetrievalSettings settings = new LongTermMemory.RetrievalSettingsBuilder()
 *     .apply(cfg::applyTo)
 *     .build();
 * ```
 *
 * Or, even more directly, feed the config into an existing builder:
 * ```java
 * var retrievalSettings = new LongTermMemory.RetrievalSettingsBuilder();
 * cfg.applyTo(retrievalSettings);
 * ```
 */
@ExperimentalAgentsApi
public object AgentcoreRetrieval {
    /**
     * Start a fluent Java builder for an AgentCore composite retrieval configuration.
     */
    @JvmStatic
    public fun builder(
        client: BedrockAgentCoreClient,
        memoryId: String,
    ): AgentcoreRetrievalJavaBuilder {
        require(memoryId.isNotBlank()) { "memoryId must not be blank" }
        return AgentcoreRetrievalJavaBuilder(client, memoryId)
    }
}

/**
 * Immutable result of [AgentcoreRetrievalJavaBuilder.build]. Wraps the three AgentCore
 * retrieval components that [LongTermMemory.RetrievalSettingsBuilder] needs.
 */
@ExperimentalAgentsApi
public class AgentcoreRetrievalConfig internal constructor(
    public val storage: AgentcoreSearchStorage,
    public val searchStrategy: AgentcoreCompositeSearchStrategy,
    public val promptAugmenter: PromptAugmenter,
) {
    /**
     * Apply this configuration to a [LongTermMemory.RetrievalSettingsBuilder]. Sets
     * [LongTermMemory.RetrievalSettingsBuilder.storage],
     * [LongTermMemory.RetrievalSettingsBuilder.searchStrategy],
     * [LongTermMemory.RetrievalSettingsBuilder.promptAugmenter], and clears
     * [LongTermMemory.RetrievalSettingsBuilder.namespace] (each subrequest carries its own).
     */
    public fun applyTo(builder: LongTermMemory.RetrievalSettingsBuilder): LongTermMemory.RetrievalSettingsBuilder {
        builder.storage = storage
        builder.searchStrategy = searchStrategy
        builder.promptAugmenter = promptAugmenter
        builder.namespace = null
        return builder
    }
}

/**
 * Fluent builder that collects AgentCore retrieval subrequests and an augmenter, then builds an
 * [AgentcoreRetrievalConfig]. Designed for Java callers (all methods return `this`, all
 * optional parameters are exposed as explicit overloads rather than Kotlin default args).
 *
 * Every `add*` method appends a [AgentcoreSearchSubrequest]; the resulting strategy is always a
 * composite, regardless of how many subrequests are added. Multiple subrequests may share the same
 * `memoryStrategyId`/`actorId` and differ only in namespace scope — this is how an
 * EPISODIC strategy issues session‑scoped episodes and actor‑scoped reflections with a
 * single strategy id.
 */
@ExperimentalAgentsApi
public class AgentcoreRetrievalJavaBuilder internal constructor(
    private val client: BedrockAgentCoreClient,
    private val memoryId: String,
) {
    private val subrequests: MutableList<AgentcoreSearchSubrequest> = mutableListOf()
    private var augmenter: PromptAugmenter = SystemPromptAugmenter()
    private var namespaceResolver: AgentcoreNamespaceResolver = AgentcoreNamespaceResolver.Default

    private fun actorNs(strategyId: String, actorId: String): String =
        namespaceResolver.resolve(AgentcoreNamespaceScope.Actor(strategyId, actorId))

    private fun sessionNs(strategyId: String, actorId: String, sessionId: String): String =
        namespaceResolver.resolve(AgentcoreNamespaceScope.Session(strategyId, actorId, sessionId))

    /**
     * Override the default [AgentcoreNamespaceResolver] used to build namespaces for every
     * subrequest appended by this builder. Use [AgentcoreNamespaceResolver.template] when your
     * memory store was created with a different namespace pattern, or pass a fully custom
     * resolver implementation. Does not affect raw subrequest templates passed to [subrequest].
     */
    public fun namespaceResolver(resolver: AgentcoreNamespaceResolver): AgentcoreRetrievalJavaBuilder = apply {
        this.namespaceResolver = resolver
    }

    // --- semantic --------------------------------------------------------------

    /** Append a SEMANTIC similarity subrequest (actor‑scoped) with defaults `topK=5`. */
    public fun semantic(strategyId: String, actorId: String): AgentcoreRetrievalJavaBuilder =
        semantic(strategyId, actorId, DEFAULT_SEMANTIC_TOPK, null, null)

    /** Append a SEMANTIC similarity subrequest (actor‑scoped). */
    public fun semantic(strategyId: String, actorId: String, topK: Int): AgentcoreRetrievalJavaBuilder =
        semantic(strategyId, actorId, topK, null, null)

    /** Append a SEMANTIC similarity subrequest (actor‑scoped), optionally filtered. */
    public fun semantic(
        strategyId: String,
        actorId: String,
        topK: Int,
        minScore: Double?,
        filterExpression: String?,
    ): AgentcoreRetrievalJavaBuilder = apply {
        validateStrategyId(strategyId)
        validatePositive("topK", topK)
        subrequests += AgentcoreSearchSubrequest.similarity(
            memoryStrategyId = strategyId,
            namespace = actorNs(strategyId, actorId),
            limit = topK,
            minScore = minScore,
            filterExpression = filterExpression,
        )
    }

    // --- summary ---------------------------------------------------------------

    /** Append a SUMMARY similarity subrequest (session‑scoped) with defaults `topK=3`. */
    public fun summary(
        strategyId: String,
        actorId: String,
        sessionId: String,
    ): AgentcoreRetrievalJavaBuilder = summary(strategyId, actorId, sessionId, DEFAULT_SUMMARY_TOPK, null, null)

    /** Append a SUMMARY similarity subrequest (session‑scoped). */
    public fun summary(
        strategyId: String,
        actorId: String,
        sessionId: String,
        topK: Int,
    ): AgentcoreRetrievalJavaBuilder = summary(strategyId, actorId, sessionId, topK, null, null)

    /** Append a SUMMARY similarity subrequest (session‑scoped), optionally filtered. */
    public fun summary(
        strategyId: String,
        actorId: String,
        sessionId: String,
        topK: Int,
        minScore: Double?,
        filterExpression: String?,
    ): AgentcoreRetrievalJavaBuilder = apply {
        validateStrategyId(strategyId)
        validatePositive("topK", topK)
        subrequests += AgentcoreSearchSubrequest.similarity(
            memoryStrategyId = strategyId,
            namespace = sessionNs(strategyId, actorId, sessionId),
            limit = topK,
            minScore = minScore,
            filterExpression = filterExpression,
        )
    }

    // --- userPreferences -------------------------------------------------------

    /** Append a USER_PREFERENCE listing subrequest (actor‑scoped) with default `limit=50`. */
    public fun userPreferences(strategyId: String, actorId: String): AgentcoreRetrievalJavaBuilder =
        userPreferences(strategyId, actorId, DEFAULT_USER_PREFERENCES_LIMIT)

    /** Append a USER_PREFERENCE listing subrequest (actor‑scoped). */
    public fun userPreferences(
        strategyId: String,
        actorId: String,
        limit: Int,
    ): AgentcoreRetrievalJavaBuilder = apply {
        validateStrategyId(strategyId)
        validatePositive("limit", limit)
        subrequests += AgentcoreSearchSubrequest.listing(
            memoryStrategyId = strategyId,
            namespace = actorNs(strategyId, actorId),
            limit = limit,
        )
    }

    // --- episodic --------------------------------------------------------------

    /** Append an EPISODIC "episodes" similarity subrequest (session‑scoped). */
    public fun episodes(
        strategyId: String,
        actorId: String,
        sessionId: String,
        topK: Int,
    ): AgentcoreRetrievalJavaBuilder = apply {
        validateStrategyId(strategyId)
        validatePositive("topK", topK)
        subrequests += AgentcoreSearchSubrequest.similarity(
            memoryStrategyId = strategyId,
            namespace = sessionNs(strategyId, actorId, sessionId),
            limit = topK,
        )
    }

    /** Append an EPISODIC "reflections" similarity subrequest (actor‑scoped). */
    public fun reflections(
        strategyId: String,
        actorId: String,
        topK: Int,
    ): AgentcoreRetrievalJavaBuilder = apply {
        validateStrategyId(strategyId)
        validatePositive("topK", topK)
        subrequests += AgentcoreSearchSubrequest.similarity(
            memoryStrategyId = strategyId,
            namespace = actorNs(strategyId, actorId),
            limit = topK,
        )
    }

    /**
     * Convenience: append the two subrequests typically associated with EPISODIC memory —
     * session‑scoped episodes and actor‑scoped reflections — sharing [strategyId].
     */
    public fun episodic(
        strategyId: String,
        actorId: String,
        sessionId: String,
    ): AgentcoreRetrievalJavaBuilder = episodic(
        strategyId = strategyId,
        actorId = actorId,
        sessionId = sessionId,
        reflectionsStrategyId = strategyId,
        episodesTopK = DEFAULT_EPISODES_TOPK,
        reflectionsTopK = DEFAULT_REFLECTIONS_TOPK,
    )

    /** Full-control variant of [episodic]. */
    public fun episodic(
        strategyId: String,
        actorId: String,
        sessionId: String,
        reflectionsStrategyId: String,
        episodesTopK: Int,
        reflectionsTopK: Int,
    ): AgentcoreRetrievalJavaBuilder = apply {
        episodes(strategyId, actorId, sessionId, episodesTopK)
        reflections(reflectionsStrategyId, actorId, reflectionsTopK)
    }

    // --- escape hatch ----------------------------------------------------------

    /** Append a pre‑built subrequest template. Escape hatch for uncommon combinations. */
    public fun subrequest(template: AgentcoreSearchSubrequest): AgentcoreRetrievalJavaBuilder = apply { subrequests += template }

    // --- augmenter -------------------------------------------------------------

    /** Override the default [SystemPromptAugmenter]. */
    public fun augmenter(augmenter: PromptAugmenter): AgentcoreRetrievalJavaBuilder = apply {
        this.augmenter = augmenter
    }

    // --- build -----------------------------------------------------------------

    /** Build the immutable [AgentcoreRetrievalConfig]. At least one subrequest must have been added. */
    public fun build(): AgentcoreRetrievalConfig {
        check(subrequests.isNotEmpty()) {
            "AgentcoreRetrieval builder must contain at least one subrequest " +
                "(semantic/summary/userPreferences/episodes/reflections/episodic/subrequest)"
        }
        return AgentcoreRetrievalConfig(
            storage = AgentcoreSearchStorage(client, memoryId),
            searchStrategy = AgentcoreCompositeSearchStrategy(subrequests.toList()),
            promptAugmenter = augmenter,
        )
    }

    private fun validateStrategyId(strategyId: String) {
        require(strategyId.isNotBlank()) { "strategyId must not be blank" }
    }

    private fun validatePositive(name: String, value: Int) {
        require(value > 0) { "$name must be positive, was $value" }
    }

    private companion object {
        private const val DEFAULT_SEMANTIC_TOPK = 5
        private const val DEFAULT_SUMMARY_TOPK = 3
        private const val DEFAULT_USER_PREFERENCES_LIMIT = 50
        private const val DEFAULT_EPISODES_TOPK = 3
        private const val DEFAULT_REFLECTIONS_TOPK = 2
    }
}
