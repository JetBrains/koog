package ai.koog.agents.features.longtermmemory.aws.discovery

/**
 * Discovered AWS Bedrock AgentCore memory strategy type.
 *
 * Mirrors the subset of `MemoryStrategyType` values for which Koog's AgentCore LTM
 * retrieval has a built-in materialization path. `CUSTOM` and unknown/unsupported
 * strategy types are intentionally absent: [AgentcoreStrategyDiscovery] filters them
 * out and never produces a [AgentcoreDiscoveredStrategy] with one of those types.
 *
 * This enum is deliberately independent from
 * [ai.koog.agents.features.longtermmemory.aws.augmentation.AgentcoreMemoryStrategy] —
 * that one drives *augmentation* and splits EPISODIC into `EPISODES`/`REFLECTIONS`
 * subrequests, whereas AWS exposes EPISODIC as one strategy whose
 * [AgentcoreDiscoveredStrategy.reflectionsNamespaces] carry the reflection namespaces
 * separately.
 */
public enum class AgentcoreDiscoveredStrategyType {
    SEMANTIC,
    USER_PREFERENCE,
    SUMMARY,
    EPISODIC,
}

/**
 * Descriptor for one memory strategy discovered on an AgentCore memory.
 *
 * Built by [AgentcoreStrategyDiscovery.discover] from the AWS `GetMemory` response;
 * consumed by `agentcoreDiscovered { }` to materialize the matching retrieval
 * subrequests. The data is intentionally close to the AWS representation so callers
 * may also use it for diagnostics or custom materialization without re-querying the
 * control plane.
 *
 * @param strategyId AWS strategy ID (never blank — blank/null IDs are filtered out by
 *   [AgentcoreStrategyDiscovery]).
 * @param type strategy type (never `CUSTOM` or unknown — those are filtered out).
 * @param namespaces namespace templates registered on the strategy. For [AgentcoreDiscoveredStrategyType.EPISODIC]
 *   these are the *episodes* (session-scoped) namespaces; for the other types these
 *   are the strategy's only namespaces. Non-empty by construction.
 * @param reflectionsNamespaces reflection namespace templates registered on the
 *   strategy. Populated only for [AgentcoreDiscoveredStrategyType.EPISODIC] strategies
 *   that have an episodic reflection configuration; empty otherwise.
 */
public data class AgentcoreDiscoveredStrategy(
    val strategyId: String,
    val type: AgentcoreDiscoveredStrategyType,
    val namespaces: List<String>,
    val reflectionsNamespaces: List<String> = emptyList(),
) {
    init {
        require(strategyId.isNotBlank()) { "strategyId must not be blank" }
        require(namespaces.isNotEmpty()) { "namespaces must not be empty for strategy '$strategyId'" }
    }

    /** Primary namespace template (the first one, matching the Java reference's behavior). */
    public val defaultNamespace: String get() = namespaces.first()

    /** Primary reflections namespace template, or `null` when none are configured. */
    public val defaultReflectionsNamespace: String? get() = reflectionsNamespaces.firstOrNull()
}
