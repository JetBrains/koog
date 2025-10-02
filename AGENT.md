# Koog AI Agent Framework

Koog is a Kotlin multiplatform framework for building AI agents with graph-based workflows.
It supports JVM and JS targets and integrates with multiple LLM providers
(OpenAI, Anthropic, Google, OpenRouter, Ollama) and Model Context Protocol (MCP).

## Project Structure

The project follows a modular architecture with a clear separation of concerns:

```
koog/
├── agents/
│   ├── agents-core/           # Core abstractions (AIAgent, AIAgentStrategy, AIAgentEnvironment)
│   ├── agents-tools/          # Tool infrastructure (Tool<TArgs, TResult>, ToolRegistry, AIAgentTool)
│   ├── agents-features-*/     # Feature implementations (memory, tracing, event handling)
│   ├── agents-mcp/           # Model Context Protocol integration
│   └── agents-test/          # Testing utilities and framework
├── prompt-*/                 # LLM interaction layer (executors, models, structured data)
├── embeddings-*/             # Vector embedding support
├── examples/                 # Reference implementations and usage patterns
└── build.gradle.kts          # Root build configuration
```

## Build & Commands

### Development Commands

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
./gradlew jvmTest --tests "ai.koog.agents.test.SimpleAgentMockedTest"

# Run specific test method  
./gradlew jvmTest --tests "ai.koog.agents.test.SimpleAgentMockedTest.test AIAgent doesn't call tools by default"

# Compile test classes only (for faster iteration)
./gradlew jvmTestClasses jsTestClasses
```

### Development Environment

- **JDK**: 17+ required for JVM target
- **Build System**: Gradle with version catalogs for dependency management
- **Targets**: JVM, JavaScript (Kotlin Multiplatform), WASM
- **IDE**: IntelliJ IDEA recommended with Kotlin plugin

## Code Style

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use four spaces for indentation (consistent across all files)
- Name test functions as `testXxx` (no backticks for readability)
- Use descriptive variable and function names
- Prefer functional programming patterns where appropriate
- Use type-safe builders and DSLs for configuration
- Document public APIs with KDoc comments
- NEVER suppress compiler warnings without a good reason

## Architecture

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

## Testing

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

## Security

### API Key Management
- **NEVER** commit API keys or secrets to the repository
- Use environment variables for all sensitive configuration
- Store test API keys in a local environment only
- Required environment variables for integration tests:
  - `ANTHROPIC_API_TEST_KEY`
  - `OPEN_AI_API_TEST_KEY`
  - `GEMINI_API_TEST_KEY` 
  - `OPEN_ROUTER_API_TEST_KEY`
  - `OLLAMA_IMAGE_URL`

### Tool Execution Safety
- Tools execute within controlled `AIAgentEnvironment` contexts
- Direct tool calls are prevented outside agent execution
- Use type-safe tool arguments to prevent injection attacks
- Validate all external inputs in tool implementations

### Dependency Security
- Regularly update dependencies using Gradle version catalogs
- Use specific version ranges to avoid supply chain attacks
- Review dependencies for known vulnerabilities
- Follow the principle of the least privilege in tool implementations

## Configuration

### Environment Setup
Set environment variables for integration testing (never commit API keys):
```bash
# Export in your shell or IDE run configuration
export ANTHROPIC_API_TEST_KEY=your_key_here
export OPEN_AI_API_TEST_KEY=your_key_here
export GEMINI_API_TEST_KEY=your_key_here
export OPEN_ROUTER_API_TEST_KEY=your_key_here
export OLLAMA_IMAGE_URL=http://localhost:11434

# Or add to ~/.bashrc, ~/.zshrc, or IDE environment variables
```

### Gradle Configuration
- Uses version catalogs (`gradle/libs.versions.toml`) for dependency management
- Multiplatform configuration in `build.gradle.kts`
- Test configuration supports both JVM and JS targets

### Development Environment Requirements
- **JDK**: 17+ (OpenJDK recommended)
- **IDE**: IntelliJ IDEA with Kotlin Multiplatform plugin
- **Optional**: Docker for Ollama local testing

## Development Workflow

### Branch Strategy
- **develop**: All development (features and bug fixes)
- **main**: Released versions only  
- Base all PRs against `develop` branch
- Use descriptive branch names: `feature/agent-memory`, `fix/tool-registry-bug`

### Code Quality
- **ALWAYS** run `./gradlew build` before submitting PRs
- Ensure all tests pass on JVM, JS, WASM targets
- Follow established patterns in existing code
- Add tests for new functionality
- Update documentation for API changes

### Commit Guidelines
- Use conventional commit format: `feat:`, `fix:`, `docs:`, `test:`
- Include issue references where applicable
- Keep commits focused and atomic