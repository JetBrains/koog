# Koog Framework Simple Examples for Java

Welcome to the **Koog Framework Simple Examples for Java** collection. This project showcases how to build AI agents with Koog using Java APIs, including graph strategies, functional strategies, chat memory, and JDBC-backed persistence.

## Quick Start

### Prerequisites
- Java 17+
- API keys for providers you want to use (at minimum OpenAI for most examples)
- Optional:
  - Ollama running locally for local model examples
  - PostgreSQL for JDBC chat memory and persistence examples

### Setup

1. Set environment variables:

```bash
# macOS/Linux
export OPENAI_API_KEY=your_openai_key
export ANTHROPIC_API_KEY=your_anthropic_key
export GOOGLE_API_KEY=your_google_key
export OPENROUTER_API_KEY=your_openrouter_key
export MISTRALAI_API_KEY=your_mistral_key
```

2. Or create an `env.properties` file in the project root:

```properties
OPENAI_API_KEY=your_openai_key
ANTHROPIC_API_KEY=your_anthropic_key
GOOGLE_API_KEY=your_google_key
OPENROUTER_API_KEY=your_openrouter_key
MISTRALAI_API_KEY=your_mistral_key
```

`env.properties` is loaded automatically by Gradle run tasks via the local credentials resolver plugin.

### Run

```bash
./gradlew runExampleCalculator
```

## Examples

| Example | Description | Gradle Task |
|---|---|---|
| Calculator | Graph-based calculator with tools and event handling (OpenAI by default) | `runExampleCalculator` |
| Calculator (Local) | Same calculator example using local Ollama (`llama3.2`) | `runExampleCalculatorLocal` |
| Functional Agent Chat | Interactive functional strategy chat loop (`/bye` to exit) | `runExampleFunctionalAgentChat` |
| Functional Strategy | Multi-step functional workflow with typed subtasks and verification loop | `runExampleFunctionalStrategy` |
| GOAP Strategy | Planner-based agent using GOAP actions and state transitions | `runExampleGoapStrategy` |
| Graph Strategy | Graph strategy with identify/solve/verify/fix loop | `runExampleGraphStrategy` |
| Chat Memory (JDBC) | PostgreSQL-backed chat history with the `ChatMemory` feature | `runExampleChatMemoryJdbc` |
| Persistence (JDBC) | PostgreSQL-backed agent snapshots/checkpoint persistence | `runExamplePersistenceJdbc` |

## Available Gradle Tasks

Run any example with:

```bash
./gradlew [task-name]
```

**Core Examples:**
- `runExampleCalculator` - Graph-based calculator agent
- `runExampleCalculatorLocal` - Calculator with local Ollama model
- `runExampleFunctionalAgentChat` - Interactive functional chat

**Strategy Examples:**
- `runExampleFunctionalStrategy` - Functional strategy with typed subtasks
- `runExampleGoapStrategy` - GOAP/planner strategy example
- `runExampleGraphStrategy` - Graph strategy with verification loop

**Persistence Examples:**
- `runExampleChatMemoryJdbc` - JDBC PostgreSQL chat history
- `runExamplePersistenceJdbc` - JDBC PostgreSQL persistence checkpoints

## JDBC Examples Setup (PostgreSQL)

`runExampleChatMemoryJdbc` and `runExamplePersistenceJdbc` expect PostgreSQL on `localhost:5432` with:
- database: `koog`
- user: `postgres`
- password: `postgres`

Quick local start with Docker:

```bash
docker run -d --name koog-pg \
  -e POSTGRES_DB=koog \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:16
```

## Related Resources

- [Koog Documentation](https://docs.koog.ai/)
- [Koog API Reference](https://api.koog.ai/)
- [Kotlin simple examples (sibling project)](../simple-examples/README.md)
