package ai.koog.agents.features.longtermmemory.aws

/**
 * Scope descriptor passed to [AgentcoreNamespaceResolver] to build an AgentCore memory
 * namespace string.
 *
 * AgentCore namespaces identify the "folder" in the memory store that a given retrieval or
 * ingestion operation targets. They are always strategy- and actor-scoped; some strategies
 * (e.g. EPISODIC episodes, SUMMARY) additionally require a session scope.
 */
public sealed interface AgentcoreNamespaceScope {
    /** The memory strategy id the namespace belongs to (never blank). */
    public val strategyId: String

    /** The actor (end-user) id the namespace belongs to (never blank). */
    public val actorId: String

    /**
     * An actor-scoped namespace — used by listing strategies (PREFERENCE) and by
     * actor-level similarity lookups (SEMANTIC, REFLECTIONS, ...).
     */
    public data class Actor(
        override val strategyId: String,
        override val actorId: String,
    ) : AgentcoreNamespaceScope

    /**
     * A session-scoped namespace — used by strategies that partition memory per conversation
     * session (SUMMARY, EPISODES, ...).
     */
    public data class Session(
        override val strategyId: String,
        override val actorId: String,
        public val sessionId: String,
    ) : AgentcoreNamespaceScope
}

/**
 * Resolves AgentCore memory namespaces from [AgentcoreNamespaceScope] descriptors.
 *
 * The default [AgentcoreNamespaceResolver.Default] reproduces AWS's documented layout:
 * ```
 * actor-scoped:   /strategies/{strategyId}/actors/{actorId}/
 * session-scoped: /strategies/{strategyId}/actors/{actorId}/sessions/{sessionId}/
 * ```
 *
 * Provide a custom implementation (or use [AgentcoreNamespaceResolver.template]) when your
 * memory store was created with a different namespace pattern.
 *
 * The resolver is configured once on the retrieval builder (Kotlin DSL:
 * `namespaceResolver = ...`; Java: `.namespaceResolver(...)`) and is consulted by every
 * helper (`semantic`, `summary`, `userPreferences`, `episodes`, `reflections`, `episodic`)
 * to build the namespace for each generated subrequest. The `subrequest(...)` escape hatch
 * bypasses the resolver — raw subrequest templates carry their own namespace verbatim.
 */
public fun interface AgentcoreNamespaceResolver {
    /**
     * Resolve [scope] to a namespace string. Implementations must return a non-blank value;
     * AgentCore conventionally uses a leading and trailing `/`.
     */
    public fun resolve(scope: AgentcoreNamespaceScope): String

    public companion object {
        /**
         * Default resolver that produces AWS's documented namespace layout.
         */
        @JvmStatic
        public val Default: AgentcoreNamespaceResolver = AgentcoreNamespaceResolver { scope ->
            require(scope.strategyId.isNotBlank()) { "strategyId must not be blank" }
            require(scope.actorId.isNotBlank()) { "actorId must not be blank" }
            when (scope) {
                is AgentcoreNamespaceScope.Actor ->
                    "/strategies/${scope.strategyId}/actors/${scope.actorId}/"
                is AgentcoreNamespaceScope.Session -> {
                    require(scope.sessionId.isNotBlank()) { "sessionId must not be blank" }
                    "/strategies/${scope.strategyId}/actors/${scope.actorId}/sessions/${scope.sessionId}/"
                }
            }
        }

        @Suppress("RegExpRedundantEscape")
        private val PLACEHOLDER_REGEX: Regex = Regex("""\{[^{}]+}""")

        /**
         * Build a resolver from a single AWS-native namespace template as returned by
         * Bedrock AgentCore's `GetMemory` (e.g. `/strategies/{memoryStrategyId}/actors/{actorId}/`
         * or `/strategies/{memoryStrategyId}/actors/{actorId}/sessions/{sessionId}/`).
         *
         * AgentCore templates use the placeholder `{memoryStrategyId}` whereas this resolver
         * uses `{strategyId}`; both are accepted and treated as synonyms. The template is bound
         * to the [AgentcoreNamespaceScope] kind it can satisfy: a template that contains
         * `{sessionId}` is used as the *session-scoped* template, otherwise as the *actor-scoped*
         * template. Requests against the unsupported scope fail with a clear error.
         *
         * After substitution, any remaining `{...}` placeholder triggers an
         * [IllegalStateException] — this catches templates that reference variables the resolver
         * doesn't know about (e.g. `{tenantId}`) before they reach AgentCore as malformed
         * namespaces.
         */
        @JvmStatic
        public fun fromAwsTemplate(rawTemplate: String): AgentcoreNamespaceResolver {
            require(rawTemplate.isNotBlank()) { "rawTemplate must not be blank" }
            val normalized = rawTemplate.replace("{memoryStrategyId}", "{strategyId}")
            val isSessionScoped = normalized.contains("{sessionId}")
            return AgentcoreNamespaceResolver { scope ->
                require(scope.strategyId.isNotBlank()) { "strategyId must not be blank" }
                require(scope.actorId.isNotBlank()) { "actorId must not be blank" }
                val resolved = when (scope) {
                    is AgentcoreNamespaceScope.Actor -> {
                        check(!isSessionScoped) {
                            "AWS template '$rawTemplate' is session-scoped (contains {sessionId}); " +
                                "cannot resolve actor-scoped namespace for strategyId='${scope.strategyId}'."
                        }
                        normalized
                            .replace("{strategyId}", scope.strategyId)
                            .replace("{actorId}", scope.actorId)
                    }

                    is AgentcoreNamespaceScope.Session -> {
                        require(scope.sessionId.isNotBlank()) { "sessionId must not be blank" }
                        check(isSessionScoped) {
                            "AWS template '$rawTemplate' is actor-scoped (no {sessionId} placeholder); " +
                                "cannot resolve session-scoped namespace for strategyId='${scope.strategyId}'."
                        }
                        normalized
                            .replace("{strategyId}", scope.strategyId)
                            .replace("{actorId}", scope.actorId)
                            .replace("{sessionId}", scope.sessionId)
                    }
                }
                val leftover = PLACEHOLDER_REGEX.find(resolved)
                check(leftover == null) {
                    "AWS template '$rawTemplate' contains unresolved placeholder '${leftover!!.value}' " +
                        "after substitution; only {memoryStrategyId}/{strategyId}, {actorId} and {sessionId} are supported."
                }
                resolved
            }
        }

        /**
         * Build a resolver from two string templates. Placeholders `{strategyId}`,
         * `{actorId}`, and (for session scope) `{sessionId}` are substituted verbatim; any
         * other text is kept as-is. AgentCore expects namespaces to end with `/`.
         *
         * Example:
         * ```kotlin
         * AgentcoreNamespaceResolver.template(
         *     actorScoped   = "/tenants/acme/users/{actorId}/{strategyId}/",
         *     sessionScoped = "/tenants/acme/users/{actorId}/{strategyId}/sessions/{sessionId}/",
         * )
         * ```
         */
        @JvmStatic
        @JvmOverloads
        public fun template(
            actorScoped: String = "/strategies/{strategyId}/actors/{actorId}/",
            sessionScoped: String = "/strategies/{strategyId}/actors/{actorId}/sessions/{sessionId}/",
        ): AgentcoreNamespaceResolver {
            require(actorScoped.isNotBlank()) { "actorScoped template must not be blank" }
            require(sessionScoped.isNotBlank()) { "sessionScoped template must not be blank" }
            return AgentcoreNamespaceResolver { scope ->
                require(scope.strategyId.isNotBlank()) { "strategyId must not be blank" }
                require(scope.actorId.isNotBlank()) { "actorId must not be blank" }
                when (scope) {
                    is AgentcoreNamespaceScope.Actor ->
                        actorScoped
                            .replace("{strategyId}", scope.strategyId)
                            .replace("{actorId}", scope.actorId)
                    is AgentcoreNamespaceScope.Session -> {
                        require(scope.sessionId.isNotBlank()) { "sessionId must not be blank" }
                        sessionScoped
                            .replace("{strategyId}", scope.strategyId)
                            .replace("{actorId}", scope.actorId)
                            .replace("{sessionId}", scope.sessionId)
                    }
                }
            }
        }
    }
}
