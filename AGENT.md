Welcome to Koog repository. 
This file contains the main points for agents when working with code in this repository.

## Project Overview

Koog is a Kotlin multiplatform framework for building AI agents with graph-based workflows.
It supports JVM and JS targets and integrates with multiple LLM providers
(OpenAI, Anthropic, Google, OpenRouter, Ollama) and Model Context Protocol (MCP).

## Building and Testing

### Basic Commands

```bash
# Full build including tests
./gradlew build

# Build without tests
./gradlew assemble

# Run all JVM tests
./gradlew jvmTest

# Run all JS tests  
./gradlew jsTest

# Test specific module
./gradlew :agents:agents-core:jvmTest

# Run specific test class
./gradlew jvmTest --tests "ai.koog.agents.test.SimpleAgentIntegrationTest"

# Run specific test method
./gradlew jvmTest --tests "ai.koog.agents.test.SimpleAgentIntegrationTest.integration_simpleSingleRunAgentShouldNotCallToolsByDefault"

# Compile test classes only (for faster iteration)
./gradlew jvmTestClasses jsTestClasses
```

### Development Requirements

- JDK 17+ for JVM target
- Uses Gradle version catalogs for dependency management

## Architecture Overview

### Core Framework Components

**AIAgent** — Main orchestrator that executes strategies in coroutine scopes, manages tools via ToolRegistry,
runs features through AIAgentPipeline, and handles LLM communication via PromptExecutor.

**AIAgentStrategy** — Graph-based execution logic that defines workflows as subgraphs with start/finish nodes,
manages tool selection strategy, and handles termination/error reporting.

**ToolRegistry** — Centralized, type-safe tool management using a builder pattern: `ToolRegistry { tool(MyTool()) }`.
Supports registry merging with `+` operator.

**AIAgentFeature** — Extensible capabilities installed into AIAgentPipeline with configuration.
Features have unique storage keys and can intercept agent lifecycle events.

### Module Organization

1. **agents-core**: Core abstractions (`AIAgent`, `AIAgentStrategy`, `AIAgentEnvironment`)
2. **agents-tools**: Tool infrastructure (`Tool<TArgs, TResult>`, `ToolRegistry`, `AIAgentTool`)
3. **agents-features-***: Feature implementations (memory, tracing, event handling)
4. **agents-mcp**: Model Context Protocol integration
5. **prompt-***: LLM interaction layer (executors, models, structured data)
6. **embeddings-***: Vector embedding support
7. **examples**: Reference implementations and usage patterns

### Key Architectural Patterns

- **State Machine Graphs**: Agents execute as node graphs with typed edges
- **Feature Pipeline**: Extensible behavior via installable features with lifecycle hooks
- **Environment Abstraction**: Safe tool execution context preventing direct tool calls
- **Type Safety**: Generics ensure compile-time correctness for tool arguments/results
- **Builder Patterns**: Fluent APIs for configuration throughout the framework

## Testing Framework

The framework provides comprehensive testing utilities in `agents-test` module:

### LLM Response Mocking
```kotlin
val mockLLMApi = getMockExecutor(toolRegistry, eventHandler) {
    mockLLMAnswer("Hello!") onRequestContains "Hello"
    mockLLMToolCall(CreateTool, CreateTool.Args("solve")) onRequestEquals "Solve task"
    mockLLMAnswer("Default response").asDefaultResponse
}
```

### Tool Behavior Mocking
```kotlin
// Simple return value
mockTool(PositiveToneTool) alwaysReturns "The text has a positive tone."

// With additional actions
mockTool(NegativeToneTool) alwaysTells {
    println("Tool called")
    "The text has a negative tone."
}

// Conditional responses
mockTool(SearchTool) returns SearchTool.Result("Found") onArgumentsMatching { 
    args.query.contains("important") 
}
```

### Graph Structure Testing
```kotlin
AIAgent(...) {
    withTesting()
    
    testGraph("test") {
        val firstSubgraph = assertSubgraphByName<String, String>("first")
        val secondSubgraph = assertSubgraphByName<String, String>("second")
        
        assertEdges {
            startNode() alwaysGoesTo firstSubgraph
            firstSubgraph alwaysGoesTo secondSubgraph
        }
        
        verifySubgraph(firstSubgraph) {
            val askLLM = assertNodeByName<String, Message.Response>("callLLM")
            assertNodes {
                askLLM withInput "Hello" outputs Message.Assistant("Hello!")
            }
        }
    }
}
```

For comprehensive testing examples, see `agents/agents-test/TESTING.md`.

## Development Workflow

### Branch Strategy
- **develop**: All development (features and bug fixes)
- **main**: Released versions only
- Base PRs against `develop` branch

### Code Style
- Follow Kotlin Coding Conventions
- Use 4 spaces for indentation
- Name test functions as `testXxx` (no backticks)

### Integration Testing
Integration tests require environment variables:
- `ANTHROPIC_API_TEST_KEY`
- `OPEN_AI_API_TEST_KEY` 
- `GEMINI_API_TEST_KEY`
- `OPEN_ROUTER_API_TEST_KEY`
- `OLLAMA_IMAGE_URL`