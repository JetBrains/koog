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

## Quality Gates (MANDATORY)

These rules are non-negotiable and apply to **every** change that touches production code.
Claude MUST enforce them automatically without being asked.

### Definition of Done for any code change

A change is NOT done until ALL of the following are true:

1. **Tests exist for every newly added or modified code path.**
    - New public function/class → new unit test covering happy path + at least one edge case.
    - Bug fix → a regression test that fails WITHOUT the fix and passes WITH it.
    - Behavior change → existing tests updated to reflect the new contract.
2. **Tests actually run and pass locally** via `./gradlew jvmTest` (and `jsTest` if the module has a JS target).
   Do NOT report a task as complete based on "it compiles". Compilation ≠ tests pass.
3. **`./gradlew build` succeeds** for the affected modules before the change is handed back to the user.
4. **No suppressed warnings, no `@Ignore`, no `@Disabled`, no commented-out assertions** were introduced to make tests pass.
5. **Public API changes** (new/removed/renamed public symbols) are documented with KDoc.

If any gate fails, fix the underlying issue. Do NOT weaken the test, disable it, or mark
the task complete with a caveat. If the gate genuinely cannot be satisfied, stop and ask the user.

### Automatic workflow triggers

Claude MUST follow this workflow without waiting for the user to ask:

| Trigger                                                      | Required action                                                                 |
|--------------------------------------------------------------|---------------------------------------------------------------------------------|
| User asks for a non-trivial feature / refactor (>1 file)     | Invoke the `create_plan` skill BEFORE writing code                              |
| User asks for a bug fix                                      | First write a failing regression test, then fix, then confirm test passes       |
| Any edit under `src/commonMain`, `src/jvmMain`, `src/jsMain` | Add/update tests in the corresponding `src/*Test` source set in the SAME change |
| Before reporting task complete                               | Run `./gradlew <affectedModule>:jvmTest` and paste the result summary           |
| After finishing implementation                               | Invoke the `simplify` skill to review the diff for quality issues               |
| Touching code shared between JVM and non-JVM targets         | Consider the `split-jvm-nonjvm` skill if platform-specific code is needed       |

These triggers are **defaults**, not suggestions. Skip one only if the user explicitly says so
in the current conversation.

### Test planning checklist (use before writing any test)

Before writing a test, Claude must answer in 1–2 lines each:

1. **What behavior is under test?** (not "what function" — what observable behavior)
2. **What are the inputs / preconditions?** Include the boundary and error cases.
3. **What is the expected observable outcome?** Return value, thrown exception, state change, emitted event.
4. **What is the minimal test double setup?** Prefer the framework's `getMockExecutor` / `mockTool` over hand-rolled
   mocks.
5. **Which source set does the test belong in?** (`commonTest` when platform-agnostic, else `jvmTest` / `jsTest`.)

If any of these is unclear, the implementation itself is probably under-specified — pause and clarify.

### Self-verification before handing back

Before telling the user "done", Claude MUST explicitly confirm, in the final message:

- [ ] Which tests were added or modified (file paths + test names).
- [ ] The exact Gradle command that was run and its result (pass/fail count).
- [ ] Whether any Quality Gate was skipped, and why.

If this checklist cannot be filled in truthfully, the task is not done.

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
    - `GEMINI_API_TEST_KEY`
    - `MISTRAL_AI_API_TEST_KEY`
    - `OLLAMA_IMAGE_URL`
    - `OPEN_AI_API_TEST_KEY`
    - `OPEN_ROUTER_API_TEST_KEY`

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
export DEEPSEEK_API_TEST_KEY=your_key_here
export GEMINI_API_TEST_KEY=your_key_here
export MISTRAL_AI_API_TEST_KEY=your_key_here
export OLLAMA_IMAGE_URL=http://localhost:11434
export OPEN_AI_API_TEST_KEY=your_key_here
export OPEN_ROUTER_API_TEST_KEY=your_key_here

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
- Add tests for new functionality — see the **Quality Gates** section above; it is mandatory, not advisory
- Update documentation for API changes
- Run the `simplify` skill on your diff before opening a PR

### Commit Guidelines

- Use conventional commit format: `feat:`, `fix:`, `docs:`, `test:`
- Include issue references where applicable
- Keep commits focused and atomic
