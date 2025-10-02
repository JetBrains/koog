## Project Overview

This is the **simple-examples** project within the Koog Framework repository. It contains executable examples demonstrating various AI agent patterns and capabilities, from basic calculator agents to advanced features like persistence, streaming, and agent-to-agent communication.

## Environment Setup

Examples require API keys for LLM providers. Configure them via:

## Running Examples

Each example has a dedicated Gradle task:

```bash
# Run any example
./gradlew runExampleCalculator
./gradlew runExampleStreamingWithTools
./gradlew runExampleRoutingViaGraph

# Build the project
./gradlew assemble

# Run tests
./gradlew test
```

All available tasks follow the pattern `runExample*`. See `build.gradle.kts` for the complete list.

## Project Architecture

### Composite Build Setup

This project uses Gradle composite build to depend on the parent Koog framework (`settings.gradle.kts:`). Changes in the main framework are immediately available without publishing.

### Key Dependencies

- `ai.koog:koog-agents` - Meta-module with core agent dependencies
- `ai.koog:koog-ktor` - Ktor server integration
- `ai.koog:agents-features-sql` - SQL persistence feature
- `ai.koog:agents-features-a2a-server` - Agent-to-agent server
- `ai.koog:agents-features-a2a-client` - Agent-to-agent client
- `ai.koog:agents-test` - Testing utilities (test scope)

### Example Structure

Examples are organized by capability:

- **Core patterns**: `calculator/`, `guesser/`, `tone/` - Basic agent patterns with tools and strategies
- **Agent-to-agent (A2A)**: `a2a/` - Inter-agent communication examples
- **Advanced features**: `memory/`, `snapshot/`, `moderation/` - Memory, persistence, content filtering
- **External integration**: `websearch/`, `attachments/`, `client/` - External API integration
- **Structured output**: `structuredoutput/` - Schema-based output formatting and streaming
- **Banking routing**: `banking/` - Complex multi-agent routing patterns

### Common Patterns

1. **Tool Definition**: Use `@Tool` and `@LLMDescription` annotations on methods in classes extending `ToolSet`
2. **Strategy Creation**: Define agent behavior as state machine graphs using `strategy { }` DSL
3. **Agent Setup**: Combine `PromptExecutor`, strategy, `AIAgentConfig`, and `ToolRegistry`
4. **Event Handling**: Use `handleEvents { }` to observe tool calls, errors, and completion

Example from `calculator/Calculator.kt`:
```kotlin
val toolRegistry = ToolRegistry {
    tool(AskUser)
    tools(CalculatorTools().asTools())
}

val agent = AIAgent(
    promptExecutor = executor,
    strategy = CalculatorStrategy.strategy,
    agentConfig = agentConfig,
    toolRegistry = toolRegistry
) {
    handleEvents { /* ... */ }
}
```

## Common Development Tasks

```bash
# Build without running tests
./gradlew assemble

# Run a specific example
./gradlew runExampleCalculator

# Run tests
./gradlew test

# Clean build artifacts
./gradlew clean
```
