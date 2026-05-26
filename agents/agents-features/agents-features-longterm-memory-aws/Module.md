# Module features-longterm-memory-aws

AWS Bedrock AgentCore integration for the `LongTermMemory` feature.
Provides storage, retrieval strategy, prompt augmentation, and namespace resolution
components that wire the AgentCore memory service into the long-term memory pipeline.

## Package ai.koog.agents.features.longtermmemory.aws

### AgentcoreSearchStorage

A `SearchStorage` implementation backed by AWS Bedrock AgentCore memory. Dispatches three
kinds of search requests to the AgentCore API: similarity search (`RetrieveMemoryRecords`),
listing (`ListMemoryRecords`), and composite search that fans out multiple subrequests
concurrently, isolating individual failures so that other subrequests still return results.

### AgentcoreCompositeSearchStrategy

A `SearchStrategy` that holds a fixed list of `AgentcoreSearchSubrequest` templates and
produces an `AgentcoreCompositeSearchRequest` at retrieval time. Supports mixing different
AgentCore strategy types (e.g. PREFERENCE listing together with SEMANTIC similarity) and
different namespace scopes (e.g. session-scoped episodes together with actor-scoped
reflections) in a single composite call.

### AgentcoreCompositeSearchStrategy.AgentcoreSearchSubrequest

A sealed template for one entry inside an `AgentcoreCompositeSearchStrategy`. Two variants
exist: `Similarity` (injects the per-turn query into a `RetrieveMemoryRecords` subrequest)
and `Listing` (produces a query-free `ListMemoryRecords` subrequest). Templates are resolved
into concrete requests at strategy creation time.

### AgentcoreMemoryRecord

A `TextDocument` that carries a single memory record retrieved from AgentCore, including its
textual content, optional unique identifier, key-value metadata, and the
`AgentcoreMemoryStrategy` that governs how the record is injected into the prompt.

### AgentcoreMemoryStrategy

An enum that classifies AgentCore memory strategy kinds and drives the augmentation pathway
used by `AgentcorePromptAugmenter`: `SEMANTIC` and `PREFERENCE` records are folded into the
system message; `EPISODES` and `REFLECTIONS` records are placed under dedicated labelled
sections in the system message; `SUMMARY` records are appended to the last user message as
an additional text part.

### AgentcorePromptAugmenter

A `PromptAugmenter` that routes each retrieved `AgentcoreMemoryRecord` to the correct
injection point based on its `AgentcoreMemoryStrategy`. SEMANTIC and PREFERENCE content is
appended to the system message; EPISODES and REFLECTIONS are rendered as distinct labelled
sections in the system message; SUMMARY content is appended as an extra text part at the
end of the last user message. A system message is created automatically when none is present.

### AgentcoreNamespaceScope

A sealed descriptor passed to `AgentcoreNamespaceResolver` to identify the memory "folder"
targeted by a retrieval or ingestion operation. `Actor` scope covers strategies that are
actor-scoped (PREFERENCE, SEMANTIC, REFLECTIONS); `Session` scope additionally carries a
session identifier for strategies that partition memory per conversation (SUMMARY, EPISODES).

### AgentcoreNamespaceResolver

A functional interface that converts an `AgentcoreNamespaceScope` into an AgentCore namespace
string. The built-in `Default` implementation reproduces AWS's documented layout
(`/strategies/{strategyId}/actors/{actorId}/` and the session-scoped variant). A
`template(...)` factory allows overriding the layout via placeholder strings without
implementing the interface directly.
