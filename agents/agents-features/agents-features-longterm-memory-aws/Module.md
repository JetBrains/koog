# Module features-longterm-memory-aws

AWS Bedrock AgentCore integration for the `LongTermMemory` feature.
Provides an `agentcore { }` DSL extension on `LongTermMemory.RetrievalSettingsBuilder` that wires
an `AgentcoreSearchStorage` and an `AgentcoreCompositeSearchStrategy` into the feature in one step.

## Setup

Add the dependency and create a `BedrockAgentCoreClient`, then install `LongTermMemory` with an
`agentcore { }` retrieval block.

```kotlin
val client = BedrockAgentCoreClient { region = "us-east-1" }

install(LongTermMemory) {
    retrieval {
        agentcore(client, memoryId = "mem-abc123") {
            // one or more subrequest helpers go here
        }
    }
}
```

## Use-case 1 — Combining two strategies (USER_PREFERENCE + SEMANTIC)

Use `userPreferences` and `semantic` helpers together inside one `agentcore { }` block.
Both subrequests are sent in a single composite call; results are merged and injected into the
system prompt before every LLM call.

```kotlin
install(LongTermMemory) {
    retrieval {
        agentcore(client, memoryId = "mem-abc123") {
            // Retrieve stored user preferences (listing, actor-scoped)
            userPreferences(strategyId = "user-pref-strategy", actorId = "alice", limit = 50)

            // Retrieve semantically similar past interactions (similarity, actor-scoped)
            semantic(strategyId = "semantic-strategy", actorId = "alice", topK = 5)
        }
    }
}
```

## Use-case 2 — Single EPISODIC strategy querying two namespaces

The `episodic` helper appends two subrequests under the same `strategyId`: extracted episodes
(session-scoped namespace) and long-term reflections (actor-scoped namespace).
AgentCore distinguishes them purely by namespace, so a single strategy id is sufficient.

```kotlin
install(LongTermMemory) {
    retrieval {
        agentcore(client, memoryId = "mem-abc123") {
            episodic(
                strategyId       = "episodic-strategy",
                actorId          = "alice",
                sessionId        = "session-42",
                episodesTopK     = 3,   // recent session episodes
                reflectionsTopK  = 2,   // actor-level reflections
            )
        }
    }
}
```

If the episodes and reflections are stored under different strategy ids, pass
`reflectionsStrategyId` explicitly:

```kotlin
episodic(
    strategyId            = "episodic-episodes-strategy",
    reflectionsStrategyId = "episodic-reflections-strategy",
    actorId               = "alice",
    sessionId             = "session-42",
)
```

Alternatively, call `episodes` and `reflections` separately for full control:

```kotlin
agentcore(client, memoryId = "mem-abc123") {
    episodes(strategyId = "episodic-strategy", actorId = "alice", sessionId = "session-42", topK = 3)
    reflections(strategyId = "episodic-strategy", actorId = "alice", topK = 2)
}
```

## Use-case 3 — Custom namespace layout

By default every helper (`semantic`, `summary`, `userPreferences`, `episodes`, `reflections`,
`episodic`) produces AgentCore's documented namespace layout:

```
actor-scoped:   /strategies/{strategyId}/actors/{actorId}/
session-scoped: /strategies/{strategyId}/actors/{actorId}/sessions/{sessionId}/
```

If your memory store was created with a different namespace pattern, override the
`namespaceResolver` once at the top of the block — the helpers will route all namespace
construction through it. For most cases `AgentcoreNamespaceResolver.template(...)` is
enough:

```kotlin
agentcore(client, memoryId = "mem-abc123") {
    namespaceResolver = AgentcoreNamespaceResolver.template(
        actorScoped   = "/tenants/acme/users/{actorId}/{strategyId}/",
        sessionScoped = "/tenants/acme/users/{actorId}/{strategyId}/sessions/{sessionId}/",
    )

    userPreferences(strategyId = "user-pref-strategy", actorId = "alice", limit = 50)
    semantic(strategyId = "semantic-strategy", actorId = "alice", topK = 5)
}
```

For fully custom logic, provide an `AgentcoreNamespaceResolver` directly (it's a `fun interface`,
so a lambda works):

```kotlin
agentcore(client, memoryId = "mem-abc123") {
    namespaceResolver = AgentcoreNamespaceResolver { scope ->
        when (scope) {
            is AgentcoreNamespaceScope.Actor ->
                "/tenants/$tenantId/users/${scope.actorId}/${scope.strategyId}/"
            is AgentcoreNamespaceScope.Session ->
                "/tenants/$tenantId/users/${scope.actorId}/${scope.strategyId}/sessions/${scope.sessionId}/"
        }
    }
    semantic(strategyId = "semantic-strategy", actorId = "alice", topK = 5)
}
```

The `subrequest(...)` escape hatch is unaffected by the resolver — raw subrequest templates
keep their own namespace verbatim. Java callers can configure the resolver the same way via
`AgentcoreRetrieval.builder(...).namespaceResolver(...)`.
