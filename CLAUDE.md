# CLAUDE.md — Koog

## What this project is

Koog is a Kotlin Multiplatform framework for building AI agents with graph-based workflows.
See [AGENT.md](AGENT.md) for full architecture, build commands, and code style.

## Key conventions for Claude

- All development goes on the **`develop`** branch. PRs target `develop`, not `main`.
- Run `./gradlew build` before considering any change done.
- Test naming: `testXxx` (no backticks).
- Never suppress compiler warnings without justification.
- Never commit API keys; use environment variables.

## Navigating the codebase

| Layer | Module path |
|-------|------------|
| Core agent abstractions | `agents/agents-core/` |
| Tool system | `agents/agents-tools/` |
| Feature plugins | `agents/agents-features-*/` |
| LLM executors | `prompt/prompt-executor/` |
| LLM provider clients | `prompt/prompt-executor/clients/` |
| MCP integration | `agents/agents-mcp/` |
| A2A communication | `a2a/` |
| Test utilities | `agents/agents-test/` |
| Examples | `examples/` |

## Running tests

```bash
./gradlew jvmTest                          # all unit tests (JVM)
./gradlew :agents:agents-core:jvmTest      # single module
./gradlew jvmTest --tests "ClassName.methodName"
./gradlew jvmIntegrationTest               # requires API keys
```

Integration tests need env vars: `ANTHROPIC_API_TEST_KEY`, `OPEN_AI_API_TEST_KEY`, `GEMINI_API_TEST_KEY`, etc.

## Key source sets (Kotlin Multiplatform)

- `commonMain` / `commonTest` — shared logic
- `jvmMain` / `jvmTest` — JVM-specific
- `jsMain` / `jsTest` — JS target
- `nativeMain` — native targets

## Further reading

- [AGENT.md](AGENT.md) — architecture deep-dive, patterns, security
- [TESTING.md](agents/agents-test/TESTING.md) — testing guide with examples
- [CONTRIBUTING.md](CONTRIBUTING.md) — contribution workflow
