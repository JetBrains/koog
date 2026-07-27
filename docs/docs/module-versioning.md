# Versioning

Koog follows [Semantic Versioning](https://semver.org/) with the format `X.Y.Z` (e.g., `1.1.0`).

The framework is API-stable: once a public API is released, it will not be broken without a major version bump.

## Version Components

| Component | Name    | Format | Meaning |
|-----------|---------|--------|---------|
| `X`       | Major   | `X.y.z` | Breaking changes to existing APIs |
| `Y`       | Minor   | `x.Y.z` | New API additions and deprecations; all existing APIs continue to work |
| `Z`       | Bugfix  | `x.y.Z` | Bug fixes only; no API changes |

### Major (`X`)

- May introduce breaking changes to existing APIs.
- Old APIs may be removed.
- A migration guide will be provided.
- Released at most once per year.

### Minor (`Y`)

- May add new APIs.
- May deprecate existing APIs (with replacements provided), but deprecated APIs remain functional.
- No breaking changes — all code that compiled against the previous minor version continues to compile and work.
- Released at most once per month.

### Bugfix (`Z`)

- Contains bug fixes only.
- No API additions, removals, or deprecations.
- Released at most once per week.

## Deprecation Policy

APIs deprecated in a minor release (`Y`) will remain available until at least the next major release (`X`).
Deprecation warnings will indicate the recommended replacement.

## Stable and Beta Modules

Some modules are considered experimental and published with a `-beta` version suffix (e.g., `1.1.0-beta`) rather than the standard `X.Y.Z`. A module may be beta for one of several reasons:

- **External integrations** — the underlying LLM provider API or external framework (e.g., Spring AI) may itself be unstable or subject to frequent or expected change.
- **Experimental functionality** — the feature area is still being explored and the API shape may evolve (e.g., GOAP planning strategies).
- **Experimental protocols** — the module implements a protocol that is not yet stable itself (e.g., A2A, Kotlin MCP).

While every effort is made to keep beta modules stable, some API changes may occur across minor releases. Beta changes will not affect any stable module.

A stable module at version `X.Y.Z` is always compatible with a beta module at version `X.Y.Z-beta` (and vice versa). All modules can be updated in sync.

### Umbrella Modules

| Module | Version      | Contents |
|--------|--------------|----------|
| `koog-agents` | `1.1.1`      | All stable modules (transitive) — recommended starting point |
| `koog-agents-additions` | `1.1.1-beta` | Most beta/experimental modules (except standalone external integrations) |

### Module Versions

=== "Stable Modules (`1.1.1`)"
    
    | Module | Version |
    |--------|---------|
    | `agents` | `1.1.1` |
    | `agents-core` | `1.1.1` |
    | `agents-features` | `1.1.1` |
    | `agents-features-chat-history-jdbc` | `1.1.1` |
    | `agents-features-chat-memory-sql` | `1.1.1` |
    | `agents-features-event-handler` | `1.1.1` |
    | `agents-features-memory` | `1.1.1` |
    | `agents-features-opentelemetry` | `1.1.1` |
    | `agents-features-persistence-jdbc` | `1.1.1` |
    | `agents-features-snapshot` | `1.1.1` |
    | `agents-features-sql` | `1.1.1` |
    | `agents-features-tokenizer` | `1.1.1` |
    | `agents-features-trace` | `1.1.1` |
    | `agents-mcp-metadata` | `1.1.1` |
    | `agents-test` | `1.1.1` |
    | `agents-tools` | `1.1.1` |
    | `agents-utils` | `1.1.1` |
    | `embeddings` | `1.1.1` |
    | `embeddings-base` | `1.1.1` |
    | `embeddings-llm` | `1.1.1` |
    | `http-client` | `1.1.1` |
    | `http-client-core` | `1.1.1` |
    | `http-client-java` | `1.1.1` |
    | `http-client-ktor` | `1.1.1` |
    | `http-client-okhttp` | `1.1.1` |
    | `http-client-test` | `1.1.1` |
    | `koog-agents` | `1.1.1` |
    | `koog-spring-ai` | `1.1.1` |
    | `prompt` | `1.1.1` |
    | `prompt-cache` | `1.1.1` |
    | `prompt-cache-files` | `1.1.1` |
    | `prompt-cache-model` | `1.1.1` |
    | `prompt-executor` | `1.1.1` |
    | `prompt-executor-anthropic-client` | `1.1.1` |
    | `prompt-executor-bedrock-client` | `1.1.1` |
    | `prompt-executor-cached` | `1.1.1` |
    | `prompt-executor-clients` | `1.1.1` |
    | `prompt-executor-model` | `1.1.1` |
    | `prompt-executor-ollama-client` | `1.1.1` |
    | `prompt-executor-openai-client` | `1.1.1` |
    | `prompt-executor-openai-client-base` | `1.1.1` |
    | `prompt-executor-openrouter-client` | `1.1.1` |
    | `prompt-llm` | `1.1.1` |
    | `prompt-markdown` | `1.1.1` |
    | `prompt-model` | `1.1.1` |
    | `prompt-processor` | `1.1.1` |
    | `prompt-structure` | `1.1.1` |
    | `prompt-tokenizer` | `1.1.1` |
    | `prompt-xml` | `1.1.1` |
    | `rag-base` | `1.1.1` |
    | `serialization` | `1.1.1` |
    | `serialization-core` | `1.1.1` |
    | `serialization-jackson` | `1.1.1` |
    | `serialization-test` | `1.1.1` |
    | `test-tck` | `1.1.1` |
    | `test-utils` | `1.1.1` |
    | `utils` | `1.1.1` |

=== "Beta Modules (`1.1.1-beta`)"
    
    | Module | Version |
    |--------|---------|
    | `a2a-client` | `1.1.1-beta` |
    | `a2a-core` | `1.1.1-beta` |
    | `a2a-server` | `1.1.1-beta` |
    | `a2a-test` | `1.1.1-beta` |
    | `a2a-test-server-tck` | `1.1.1-beta` |
    | `a2a-transport-client-jsonrpc-http` | `1.1.1-beta` |
    | `a2a-transport-core-jsonrpc` | `1.1.1-beta` |
    | `a2a-transport-server-jsonrpc-http` | `1.1.1-beta` |
    | `agents-ext` | `1.1.1-beta` |
    | `agents-features-a2a-client` | `1.1.1-beta` |
    | `agents-features-a2a-core` | `1.1.1-beta` |
    | `agents-features-a2a-server` | `1.1.1-beta` |
    | `agents-features-acp` | `1.1.1-beta` |
    | `agents-features-chat-history-aws` | `1.1.1-beta` |
    | `agents-features-longterm-memory` | `1.1.1-beta` |
    | `agents-features-longterm-memory-aws` | `1.1.1-beta` |
    | `agents-mcp` | `1.1.1-beta` |
    | `agents-mcp-server` | `1.1.1-beta` |
    | `agents-planner` | `1.1.1-beta` |
    | `koog-agents-additions` | `1.1.1-beta` |
    | `koog-ktor` | `1.1.1-beta` |
    | `koog-spring-ai-common` | `1.1.1-beta` |
    | `koog-spring-ai-starter-chat-memory` | `1.1.1-beta` |
    | `koog-spring-ai-starter-model-chat` | `1.1.1-beta` |
    | `koog-spring-ai-starter-model-embedding` | `1.1.1-beta` |
    | `koog-spring-ai-starter-vector-store` | `1.1.1-beta` |
    | `koog-spring-boot-starter` | `1.1.1-beta` |
    | `prompt-cache-redis` | `1.1.1-beta` |
    | `prompt-executor-dashscope-client` | `1.1.1-beta` |
    | `prompt-executor-deepseek-client` | `1.1.1-beta` |
    | `prompt-executor-google-client` | `1.1.1-beta` |
    | `prompt-executor-litert-client` | `1.1.1-beta` |
    | `prompt-executor-llms-all` | `1.1.1-beta` |
    | `prompt-executor-mistralai-client` | `1.1.1-beta` |
    | `rag-vector` | `1.1.1-beta` |
