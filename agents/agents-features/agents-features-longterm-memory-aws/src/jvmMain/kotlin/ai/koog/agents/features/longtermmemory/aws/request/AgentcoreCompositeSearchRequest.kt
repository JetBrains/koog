package ai.koog.agents.features.longtermmemory.aws.request

/**
 * A composite AgentCore search request that bundles several independent
 * [AgentcoreSearchRequest]s (its [entries]) to be executed as a single logical
 * retrieval step.
 *
 * Each subrequest carries:
 * - its own [AgentcoreSearchRequest] (either [AgentcoreSimilaritySearchRequest]
 *   or [AgentcoreListingSearchRequest]), which in turn holds its own
 *   `memoryStrategyId`, `limit`, etc.;
 * - its own [Entry.namespace] to scope the call — so one composite can span
 *   multiple AgentCore strategies and multiple namespace scopes (e.g.,
 *   actor-scoped user preferences + session-scoped episodes).
 *
 * Semantics:
 * - The `namespace` argument passed to
 *   [ai.koog.agents.features.longtermmemory.aws.AgentcoreSearchStorage.search]
 *   is ignored when the request is a composite; each subrequest's own namespace is used.
 * - Nested composites are not allowed.
 *
 * @property entries the list of sub-requests to execute; must be non-empty and
 *   contain no nested composites.
 */
public data class AgentcoreCompositeSearchRequest(
    public val entries: List<Entry>,
) : AgentcoreSearchRequest {

    init {
        require(entries.isNotEmpty()) { "AgentcoreCompositeSearchRequest must contain at least one subrequest" }
        require(entries.none { it.request is AgentcoreCompositeSearchRequest }) {
            "Nested AgentcoreCompositeSearchRequest is not supported"
        }
    }

    /**
     * A representative strategy id for logging / diagnostics only. Real strategy
     * routing happens per-subrequest via [Entry.request]'s `memoryStrategyId`.
     */
    override val memoryStrategyId: String
        get() = entries.joinToString(",") { it.request.memoryStrategyId }

    /**
     * Maximum number of results summed across subrequests (informational upper bound).
     * The storage returns the concatenation of per-subrequest results; apply any
     * additional truncation at the augmentation layer if needed.
     */
    override val limit: Int
        get() = entries.sumOf { it.request.limit }

    /**
     * Composite requests are not paginated at the subrequest level uniformly; offset
     * is reported as 0 here and ignored. Per-subrequest pagination (when supported)
     * must be set on each subrequest's request.
     */
    override val offset: Int
        get() = 0

    /**
     * A single entry of an [AgentcoreCompositeSearchRequest]: an AgentCore search
     * request paired with the namespace to use when executing it.
     *
     * @property request the per-subrequest AgentCore search request; must not itself
     *   be an [AgentcoreCompositeSearchRequest].
     * @property namespace the AgentCore namespace to scope [request] to.
     */
    public data class Entry(
        public val request: AgentcoreSearchRequest,
        public val namespace: String,
    ) {
        init {
            require(request !is AgentcoreCompositeSearchRequest) {
                "AgentcoreCompositeSearchRequest.Entry.request must not itself be a composite"
            }
            require(namespace.isNotBlank()) { "AgentcoreCompositeSearchRequest.Entry.namespace must not be blank" }
        }
    }
}
