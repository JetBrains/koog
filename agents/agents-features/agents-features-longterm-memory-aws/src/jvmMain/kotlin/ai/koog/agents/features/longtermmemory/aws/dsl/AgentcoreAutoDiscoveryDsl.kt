package ai.koog.agents.features.longtermmemory.aws.dsl

import ai.koog.agents.features.longtermmemory.aws.AgentcoreCompositeSearchStrategy
import ai.koog.agents.features.longtermmemory.aws.AgentcoreCompositeSearchStrategy.AgentcoreSearchSubrequest
import ai.koog.agents.features.longtermmemory.aws.AgentcoreNamespaceResolver
import ai.koog.agents.features.longtermmemory.aws.AgentcoreNamespaceScope
import ai.koog.agents.features.longtermmemory.aws.AgentcoreSearchStorage
import ai.koog.agents.features.longtermmemory.aws.augmentation.AgentcoreMemoryStrategy
import ai.koog.agents.features.longtermmemory.aws.augmentation.AgentcorePromptAugmenter
import ai.koog.agents.features.longtermmemory.aws.discovery.AgentcoreDiscoveredStrategy
import ai.koog.agents.features.longtermmemory.aws.discovery.AgentcoreDiscoveredStrategyType
import ai.koog.agents.longtermmemory.feature.LongTermMemory
import ai.koog.agents.longtermmemory.retrieval.augmentation.PromptAugmenter
import aws.sdk.kotlin.services.bedrockagentcore.BedrockAgentCoreClient
import org.slf4j.LoggerFactory

/**
 * Per-strategy overrides for `agentcoreDiscovered { }` materialization. The "explicit wins, discovery fills gaps"
 * semantics: an override is only applied when its [strategyId] matches a discovered strategy, and
 * only the non-null knobs flow through — everything else is filled in from the discovered
 * descriptor or the helper defaults.
 *
 * @param strategyId the discovered strategy this override is bound to (must not be blank).
 * @param topK overrides `topK` for SEMANTIC / SUMMARY / USER_PREFERENCE (`limit` for the
 *   PREFERENCE listing). Ignored for EPISODIC — use [episodesTopK]/[reflectionsTopK].
 * @param minScore minimum similarity score for the underlying similarity subrequest;
 *   ignored for USER_PREFERENCE (listing).
 * @param filterExpression optional AgentCore filter expression to attach to a similarity
 *   subrequest; ignored for USER_PREFERENCE.
 * @param episodesTopK overrides `topK` for an EPISODIC "episodes" subrequest.
 * @param reflectionsTopK overrides `topK` for an EPISODIC "reflections" subrequest.
 * @param namespacePattern overrides the AWS namespace template used for the primary
 *   subrequest only (semantic / user-preference / summary primary, or the episodic
 *   *episodes* namespace). Accepts both `{strategyId}` and `{memoryStrategyId}`
 *   placeholder forms. The supplied template **must** be one of the strategy's
 *   discovered namespaces — passing an unknown template fails fast with a message
 *   listing the discovered alternatives, to prevent typos from silently routing
 *   retrieval to a namespace AWS never indexed.
 * @param reflectionsNamespacePattern overrides the AWS namespace template used for the
 *   EPISODIC reflections subrequest. Ignored for non-EPISODIC strategies. Subject to
 *   the same membership check as [namespacePattern]: it must appear in the strategy's
 *   discovered `reflectionsNamespaces`.
 */
public data class AgentcoreStrategyOverride(
    val strategyId: String,
    val topK: Int? = null,
    val minScore: Double? = null,
    val filterExpression: String? = null,
    val episodesTopK: Int? = null,
    val reflectionsTopK: Int? = null,
    val namespacePattern: String? = null,
    val reflectionsNamespacePattern: String? = null,
) {
    init {
        require(strategyId.isNotBlank()) { "strategyId must not be blank" }
        topK?.let { require(it > 0) { "topK must be positive, was $it" } }
        episodesTopK?.let { require(it > 0) { "episodesTopK must be positive, was $it" } }
        reflectionsTopK?.let { require(it > 0) { "reflectionsTopK must be positive, was $it" } }
    }

    public companion object {
        /**
         * Returns a new [AgentcoreAutoDiscoveryBuilder.StrategyOverrideBuilder] for [strategyId].
         *
         * Intended for Java callers who cannot use the Kotlin DSL `configure(strategyId) { }` overload:
         * ```java
         * AgentcoreStrategyOverride override = AgentcoreStrategyOverride.builder("sem-1")
         *     .topK(10)
         *     .minScore(0.7)
         *     .build();
         * ```
         */
        @JvmStatic
        public fun builder(strategyId: String): AgentcoreAutoDiscoveryBuilder.StrategyOverrideBuilder =
            AgentcoreAutoDiscoveryBuilder.StrategyOverrideBuilder(strategyId)
    }
}

/**
 * Configure AgentCore retrieval from a pre-discovered list of strategies.
 *
 * This is the non-suspending counterpart to [ai.koog.agents.features.longtermmemory.aws.discovery.AgentcoreStrategyDiscovery]:
 * call `AgentcoreStrategyDiscovery(controlClient).discover(memoryId)` *before* entering
 * the synchronous `install(LongTermMemory) { retrieval { ... } }` block, then pass the
 * result here.
 *
 * For each discovered strategy this helper materializes one or two subrequests:
 *  - [AgentcoreDiscoveredStrategyType.SEMANTIC] → one `semantic` similarity subrequest.
 *  - [AgentcoreDiscoveredStrategyType.SUMMARY] → one `summary` similarity subrequest
 *    (requires [sessionId]).
 *  - [AgentcoreDiscoveredStrategyType.USER_PREFERENCE] → one listing subrequest.
 *  - [AgentcoreDiscoveredStrategyType.EPISODIC] → one `episodes` subrequest (session-scoped,
 *    requires [sessionId]) and, when the discovered strategy carries reflection namespaces
 *    or an override supplies them, one `reflections` subrequest (actor-scoped).
 *
 * Namespaces from each discovered strategy are honored verbatim: the helper builds a
 * dedicated [AgentcoreNamespaceResolver.fromAwsTemplate] resolver per subrequest, so a
 * session-scoped episodic strategy whose reflection namespace is actor-scoped is wired
 * up correctly automatically.
 *
 * Explicit overrides supplied inside [block] (via `configure(...)`) take precedence over
 * discovered defaults on a per-`strategyId` basis. `exclude(strategyId)` removes a
 * discovered strategy entirely (e.g. legacy strategies the application doesn't use).
 *
 * Example:
 * ```kotlin
 * val discovered = AgentcoreStrategyDiscovery(controlClient).discover(memoryId)
 * install(LongTermMemory) {
 *     retrieval {
 *         agentcoreDiscovered(client, memoryId, discovered, actorId = "alice", sessionId = sessionId) {
 *             configure(AgentcoreStrategyOverride(strategyId = "sem-1", topK = 10))
 *             exclude("legacy-summary")
 *             augmenter = UserPromptAugmenter()
 *         }
 *     }
 * }
 * ```
 *
 * Fails with an [IllegalStateException] when discovery produced no usable strategies, or
 * when every discovered strategy was excluded.
 *
 * @param client Bedrock AgentCore (runtime) client used for retrieval.
 * @param memoryId AgentCore memory store identifier.
 * @param discoveredStrategies list of strategies returned by [ai.koog.agents.features.longtermmemory.aws.discovery.AgentcoreStrategyDiscovery.discover].
 * @param actorId actor (end-user) id used to resolve namespaces; must not be blank.
 * @param sessionId session id used to resolve namespaces for session-scoped strategies
 *   (SUMMARY, EPISODIC). Required when at least one such strategy survives filtering.
 * @param block optional configuration block for per-strategy overrides, exclusions, and
 *   the augmenter.
 */
public fun LongTermMemory.RetrievalSettingsBuilder.agentcoreDiscovered(
    client: BedrockAgentCoreClient,
    memoryId: String,
    discoveredStrategies: List<AgentcoreDiscoveredStrategy>,
    actorId: String,
    sessionId: String? = null,
    block: AgentcoreAutoDiscoveryBuilder.() -> Unit = {},
) {
    require(memoryId.isNotBlank()) { "memoryId must not be blank" }
    require(actorId.isNotBlank()) { "actorId must not be blank" }
    check(discoveredStrategies.isNotEmpty()) {
        "No supported AgentCore memory strategies found for memory '$memoryId'. " +
            "Run AgentcoreStrategyDiscovery(controlClient).discover('$memoryId') and check that the memory " +
            "has at least one SEMANTIC, USER_PREFERENCE, SUMMARIZATION or EPISODIC strategy configured."
    }

    val builder = AgentcoreAutoDiscoveryBuilder().apply(block)

    // Warn on configure(...) / exclude(...) calls that don't match any discovered strategyId.
    // This is a typo trap rather than a correctness bug, so we log a warning instead of failing.
    val discoveredIds = discoveredStrategies.mapTo(mutableSetOf()) { it.strategyId }
    val unknownExclusions = builder.excludedIds() - discoveredIds
    if (unknownExclusions.isNotEmpty()) {
        logger.warn(
            "agentcoreDiscovered(memoryId='{}'): exclude(...) referenced strategyId(s) {} that were not " +
                "discovered on this memory; they have no effect. Discovered: {}",
            memoryId,
            unknownExclusions,
            discoveredIds,
        )
    }
    val unknownOverrides = builder.overrideIds() - discoveredIds
    if (unknownOverrides.isNotEmpty()) {
        logger.warn(
            "agentcoreDiscovered(memoryId='{}'): configure(...) referenced strategyId(s) {} that were not " +
                "discovered on this memory; the overrides will be ignored. Discovered: {}",
            memoryId,
            unknownOverrides,
            discoveredIds,
        )
    }

    val activeStrategies = discoveredStrategies.filterNot { builder.isExcluded(it.strategyId) }
    check(activeStrategies.isNotEmpty()) {
        "All ${discoveredStrategies.size} discovered AgentCore strategies for memory '$memoryId' were excluded; " +
            "remove some exclude(...) calls or drop the agentcoreDiscovered(...) block entirely."
    }

    val needsSessionId = activeStrategies.any { it.type.requiresSession() }
    if (needsSessionId) {
        requireNotNull(sessionId) {
            "sessionId is required because at least one discovered strategy is session-scoped " +
                "(SUMMARY or EPISODIC). Pass sessionId explicitly to agentcoreDiscovered(...)."
        }
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
    }

    val subrequests = mutableListOf<AgentcoreSearchSubrequest>()
    for (strategy in activeStrategies) {
        val override = builder.overrideFor(strategy.strategyId)
        appendSubrequestsFor(strategy, override, actorId, sessionId, subrequests)
    }

    // Defensive: filtering can in principle drop everything (it doesn't today, but keep the invariant clear).
    check(subrequests.isNotEmpty()) {
        "Auto-discovery produced no retrieval subrequests for memory '$memoryId'."
    }

    storage = AgentcoreSearchStorage(client, memoryId)
    searchStrategy = AgentcoreCompositeSearchStrategy(subrequests.toList())
    // Composite requests ignore the top-level namespace; each subrequest carries its own.
    namespace = null
    promptAugmenter = builder.augmenter
}

private fun AgentcoreDiscoveredStrategyType.requiresSession(): Boolean = when (this) {
    AgentcoreDiscoveredStrategyType.SEMANTIC, AgentcoreDiscoveredStrategyType.USER_PREFERENCE -> false
    AgentcoreDiscoveredStrategyType.SUMMARY, AgentcoreDiscoveredStrategyType.EPISODIC -> true
}

private fun appendSubrequestsFor(
    strategy: AgentcoreDiscoveredStrategy,
    override: AgentcoreStrategyOverride?,
    actorId: String,
    sessionId: String?,
    out: MutableList<AgentcoreSearchSubrequest>,
) {
    val primaryTemplate = override?.namespacePattern?.also {
        requireDiscoveredTemplate(
            strategy = strategy,
            template = it,
            allowed = strategy.namespaces,
            kind = "namespacePattern",
        )
    } ?: strategy.defaultNamespace
    if (strategy.type.requiresSession()) {
        require(primaryTemplate.contains("{sessionId}")) {
            "Strategy '${strategy.strategyId}' (${strategy.type}) requires a session-scoped namespace " +
                "but discovered template '$primaryTemplate' has no {sessionId} placeholder."
        }
    }
    val primaryResolver = AgentcoreNamespaceResolver.fromAwsTemplate(primaryTemplate)

    when (strategy.type) {
        AgentcoreDiscoveredStrategyType.SEMANTIC -> {
            out += AgentcoreSearchSubrequest.similarity(
                strategyType = AgentcoreMemoryStrategy.SEMANTIC,
                memoryStrategyId = strategy.strategyId,
                namespace = primaryResolver.resolve(
                    AgentcoreNamespaceScope.Actor(strategy.strategyId, actorId),
                ),
                limit = override?.topK ?: DEFAULT_SEMANTIC_TOP_K,
                minScore = override?.minScore,
                filterExpression = override?.filterExpression,
            )
        }

        AgentcoreDiscoveredStrategyType.USER_PREFERENCE -> {
            out += AgentcoreSearchSubrequest.listing(
                strategyType = AgentcoreMemoryStrategy.PREFERENCE,
                memoryStrategyId = strategy.strategyId,
                namespace = primaryResolver.resolve(
                    AgentcoreNamespaceScope.Actor(strategy.strategyId, actorId),
                ),
                limit = override?.topK ?: DEFAULT_USER_PREFERENCE_LIMIT,
            )
        }

        AgentcoreDiscoveredStrategyType.SUMMARY -> {
            // sessionId presence is enforced up front in agentcoreDiscovered(...).
            val session = checkNotNull(sessionId) { "sessionId is required for SUMMARY strategy '${strategy.strategyId}'" }
            out += AgentcoreSearchSubrequest.similarity(
                strategyType = AgentcoreMemoryStrategy.SUMMARY,
                memoryStrategyId = strategy.strategyId,
                namespace = primaryResolver.resolve(
                    AgentcoreNamespaceScope.Session(strategy.strategyId, actorId, session),
                ),
                limit = override?.topK ?: DEFAULT_SUMMARY_TOP_K,
                minScore = override?.minScore,
                filterExpression = override?.filterExpression,
            )
        }

        AgentcoreDiscoveredStrategyType.EPISODIC -> {
            val session = checkNotNull(sessionId) {
                "sessionId is required for EPISODIC strategy '${strategy.strategyId}'"
            }
            // Episodes — session-scoped.
            out += AgentcoreSearchSubrequest.similarity(
                strategyType = AgentcoreMemoryStrategy.EPISODES,
                memoryStrategyId = strategy.strategyId,
                namespace = primaryResolver.resolve(
                    AgentcoreNamespaceScope.Session(strategy.strategyId, actorId, session),
                ),
                limit = override?.episodesTopK ?: DEFAULT_EPISODES_TOP_K,
                minScore = override?.minScore,
                filterExpression = override?.filterExpression,
            )

            // Reflections — actor-scoped; emitted only when a reflections template is available.
            val reflectionsTemplate = override?.reflectionsNamespacePattern?.also {
                requireDiscoveredTemplate(
                    strategy = strategy,
                    template = it,
                    allowed = strategy.reflectionsNamespaces,
                    kind = "reflectionsNamespacePattern",
                )
            } ?: strategy.defaultReflectionsNamespace
            if (reflectionsTemplate != null) {
                val reflectionsResolver = AgentcoreNamespaceResolver.fromAwsTemplate(reflectionsTemplate)
                out += AgentcoreSearchSubrequest.similarity(
                    strategyType = AgentcoreMemoryStrategy.REFLECTIONS,
                    memoryStrategyId = strategy.strategyId,
                    namespace = reflectionsResolver.resolve(
                        AgentcoreNamespaceScope.Actor(strategy.strategyId, actorId),
                    ),
                    limit = override?.reflectionsTopK ?: DEFAULT_REFLECTIONS_TOP_K,
                    minScore = override?.minScore,
                    filterExpression = override?.filterExpression,
                )
            } else {
                logger.debug(
                    "EPISODIC strategy '{}': no reflection namespace discovered or configured; " +
                        "skipping reflections subrequest.",
                    strategy.strategyId,
                )
            }
        }
    }
}

/**
 * Normalise a namespace template so that the AWS-side placeholder
 * `{memoryStrategyId}` and the Koog-side alias `{strategyId}` are treated as
 * identical. Both forms are accepted by [AgentcoreNamespaceResolver.fromAwsTemplate]
 * and documented as equivalent in [AgentcoreStrategyOverride.namespacePattern].
 */
private fun String.normalizeTemplatePlaceholders(): String =
    replace("{memoryStrategyId}", "{strategyId}")

/**
 * Validate that an override-supplied namespace template matches one of the strategy's
 * discovered namespaces. This is the Kotlin DSL's analogue of the Java side's
 * `NamespaceRegistrar` check: an explicit namespace that doesn't appear in discovery is
 * almost always a typo and would silently route retrieval to a namespace AWS never
 * indexed. Failing fast with the discovered alternatives in the message makes the
 * typo obvious.
 *
 * Both `{memoryStrategyId}` and `{strategyId}` placeholders are treated as equivalent
 * during comparison (see [normalizeTemplatePlaceholders]).
 */
private fun requireDiscoveredTemplate(
    strategy: AgentcoreDiscoveredStrategy,
    template: String,
    allowed: List<String>,
    kind: String,
) {
    val normalizedTemplate = template.normalizeTemplatePlaceholders()
    val normalizedAllowed = allowed.map { it.normalizeTemplatePlaceholders() }
    if (normalizedTemplate in normalizedAllowed) return
    if (allowed.isEmpty()) {
        error(
            "$kind='$template' was provided for strategy '${strategy.strategyId}' (${strategy.type}) but the " +
                "strategy has no discovered namespaces of this kind. Either drop the override or use a discovery " +
                "result that includes the namespace."
        )
    }
    error(
        "$kind='$template' for strategy '${strategy.strategyId}' (${strategy.type}) does not match any discovered " +
            "namespace. Discovered: $allowed. Fix the override to one of those templates (typo?), or update the " +
            "AgentCore memory configuration so the desired namespace is registered. " +
            "Note: {memoryStrategyId} and {strategyId} are treated as equivalent placeholders."
    )
}

private val logger = LoggerFactory.getLogger("AgentcoreAutoDiscoveryDsl")

private const val DEFAULT_SEMANTIC_TOP_K = 5
private const val DEFAULT_SUMMARY_TOP_K = 3
private const val DEFAULT_USER_PREFERENCE_LIMIT = 50
private const val DEFAULT_EPISODES_TOP_K = 3
private const val DEFAULT_REFLECTIONS_TOP_K = 2

/**
 * Builder for `agentcoreDiscovered { }`: collects per-strategy overrides and exclusions,
 * and lets the caller swap the [augmenter] for the merged retrieval result.
 */
@AgentcoreLtmDsl
public class AgentcoreAutoDiscoveryBuilder internal constructor() {
    private val overrides: MutableMap<String, AgentcoreStrategyOverride> = mutableMapOf()
    private val excluded: MutableSet<String> = mutableSetOf()

    /**
     * Prompt augmentation strategy applied to the merged composite result.
     *
     * Defaults to [AgentcorePromptAugmenter], which routes each record to the appropriate
     * augmentation pathway based on its [AgentcoreMemoryStrategy].
     */
    public var augmenter: PromptAugmenter = AgentcorePromptAugmenter()

    /**
     * Apply [override] to the discovered strategy with the matching `strategyId`. If no
     * such strategy is discovered, the override is silently ignored — the helper does not
     * fabricate subrequests for absent strategies. Registering two overrides for the same
     * `strategyId` fails fast.
     */
    public fun configure(override: AgentcoreStrategyOverride) {
        val previous = overrides.put(override.strategyId, override)
        require(previous == null) {
            "configure(...) called twice for strategyId='${override.strategyId}'; only one override is allowed."
        }
    }

    /**
     * Convenience overload: create an [AgentcoreStrategyOverride] from a builder block.
     */
    public fun configure(strategyId: String, block: StrategyOverrideBuilder.() -> Unit) {
        val ob = StrategyOverrideBuilder(strategyId).apply(block)
        configure(ob.build())
    }

    /**
     * Skip the strategy with the given `strategyId` entirely. Useful for legacy strategies
     * the application doesn't want to retrieve from.
     */
    public fun exclude(strategyId: String) {
        require(strategyId.isNotBlank()) { "strategyId must not be blank" }
        excluded += strategyId
    }

    internal fun isExcluded(strategyId: String): Boolean = strategyId in excluded
    internal fun overrideFor(strategyId: String): AgentcoreStrategyOverride? = overrides[strategyId]
    internal fun excludedIds(): Set<String> = excluded.toSet()
    internal fun overrideIds(): Set<String> = overrides.keys.toSet()

    /**
     * Fluent builder for [AgentcoreStrategyOverride].
     *
     * Used by the `configure(strategyId) { ... }` Kotlin DSL convenience overload and
     * by Java callers via [AgentcoreStrategyOverride.builder].
     *
     * @param strategyId the strategy this override is bound to (must not be blank).
     */
    @AgentcoreLtmDsl
    public class StrategyOverrideBuilder(private val strategyId: String) {
        public var topK: Int? = null
        public var minScore: Double? = null
        public var filterExpression: String? = null
        public var episodesTopK: Int? = null
        public var reflectionsTopK: Int? = null
        public var namespacePattern: String? = null
        public var reflectionsNamespacePattern: String? = null

        /** Java-friendly fluent setter for [topK]. */
        public fun topK(value: Int): StrategyOverrideBuilder = apply { topK = value }

        /** Java-friendly fluent setter for [minScore]. */
        public fun minScore(value: Double): StrategyOverrideBuilder = apply { minScore = value }

        /** Java-friendly fluent setter for [filterExpression]. */
        public fun filterExpression(value: String): StrategyOverrideBuilder = apply { filterExpression = value }

        /** Java-friendly fluent setter for [episodesTopK]. */
        public fun episodesTopK(value: Int): StrategyOverrideBuilder = apply { episodesTopK = value }

        /** Java-friendly fluent setter for [reflectionsTopK]. */
        public fun reflectionsTopK(value: Int): StrategyOverrideBuilder = apply { reflectionsTopK = value }

        /** Java-friendly fluent setter for [namespacePattern]. */
        public fun namespacePattern(value: String): StrategyOverrideBuilder = apply { namespacePattern = value }

        /** Java-friendly fluent setter for [reflectionsNamespacePattern]. */
        public fun reflectionsNamespacePattern(value: String): StrategyOverrideBuilder =
            apply { reflectionsNamespacePattern = value }

        /** Builds and returns the configured [AgentcoreStrategyOverride]. */
        public fun build(): AgentcoreStrategyOverride = AgentcoreStrategyOverride(
            strategyId = strategyId,
            topK = topK,
            minScore = minScore,
            filterExpression = filterExpression,
            episodesTopK = episodesTopK,
            reflectionsTopK = reflectionsTopK,
            namespacePattern = namespacePattern,
            reflectionsNamespacePattern = reflectionsNamespacePattern,
        )
    }
}
