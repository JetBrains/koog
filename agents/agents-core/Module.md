# Module agents-core

Core library for building and executing AI agents with a graph-based architecture.

### Overview

The agents-core module provides the fundamental components for creating, configuring, and executing AI agents. It implements a graph-based architecture where agents are represented as state machines with nodes and edges. Each node processes inputs and produces outputs, and the execution flows through the graph based on conditional edges.

Key features include:
- Agent definition and configuration
- Graph-based execution model
- Exception handling for common agent issues
- Interceptors for agent lifecycle, strategy, LLM, tool, and node events
- Integration with LLM services
- Tool registration and execution

### Using in your project

To use the agents-core module in your project, add the following dependency:

```kotlin
dependencies {
    implementation("ai.koog.agents:agents-core:$version")
}
```

Then, you can create an AI agent by following these steps:
1. Define the tools your agent will use
2. Register the tools with a tool registry
3. Select the appropriate agent type
4. Configure the agent with the necessary parameters
5. Subscribe to agent events if needed
6. Run the agent with the desired input

### Using in unit tests

The agents-core module provides utilities for testing AI agents, including:
- Mocking LLM responses for deterministic behavior
- Mocking tool calls and their results
- Testing graph structure and node connections
- Validating agent behavior in different scenarios

To enable testing mode on an agent, use the `withTesting()` function within the agent constructor block:

```kotlin
AIAgent(
    // constructor arguments
) {
    withTesting()
}
```

### Example of usage

```kotlin
// Create a tool registry
val toolRegistry = ToolRegistry { }

// Register tools
toolRegistry.register(CalculatorTool)
toolRegistry.register(SearchTool)

// Configure the agent
val agentConfig = AgentConfig(
    maxAgentIterations = 10,
    // other configuration parameters
)

// Create the agent
val agent = AIAgent(
    promptExecutor = llmExecutor,
    toolRegistry = toolRegistry,
    strategy = simpleSingleRunStrategy(),
    eventHandler = eventHandler,
    agentConfig = agentConfig
)

// Run the agent
val result = agent.execute("Calculate the square root of 16")
```


### Passing metadata to tool calls

Caller code and installed features can thread per-call metadata (for example a trace span id) into
`Tool.execute` through the environment, without modifying the tool's argument schema.

Callers can pass metadata directly:

```kotlin
val safeTool = context.findTool(MyTool)
safeTool.execute(args, serializer, ToolCallMetadata.of("trace.span.id" to currentSpan().id))
```

Or via the environment:

```kotlin
environment.executeTool(toolCall, ToolCallMetadata.of("trace.span.id" to currentSpan().id))
```

Features can contribute metadata for every tool call in an agent run by registering a handler
during installation:

```kotlin
override fun install(config: MyConfig, pipeline: AIAgentGraphPipeline) {
    pipeline.provideToolCallMetadata(this) { eventContext ->
        mapOf("trace.span.id" to currentSpan()?.id)
    }
}
```

Contributions from multiple features are merged in installation order; later features overwrite
earlier ones on key collision. Caller-supplied metadata then takes precedence over every feature
contribution, so an explicit call-site override is never silently replaced.

After the merge, the framework injects the live `AIAgentContext` under a reserved key. Tools that
need access to the agent's full state (LLM context, run id, configuration, storage, ...) read it
through the typed `agentContext` extension on `ToolCallMetadata`:

```kotlin
override suspend fun execute(args: Args, metadata: ToolCallMetadata): Result {
    val runId = metadata.agentContext?.runId
    // ...
}
```

The framework's value always wins over caller and feature entries, so the property reflects the
real context driving the current call. When `Tool.execute(args, metadata)` is invoked outside an
agent run (for example from a unit test), `metadata.agentContext` returns `null`.

### Standard Feature Events

Features in the Koog ecosystem consume standardized Feature Events emitted by agents-core during agent execution. These events are defined in this module under the package `ai.koog.agents.core.feature.model.events`.

- Agent events:
  - AgentStartingEvent
  - AgentCompletedEvent
  - AgentExecutionFailedEvent
  - AgentClosingEvent

- Strategy events:
  - GraphStrategyStartingEvent
  - FunctionalStrategyStartingEvent
  - StrategyCompletedEvent

- Node execution events:
  - NodeExecutionStartingEvent
  - NodeExecutionCompletedEvent
  - NodeExecutionFailedEvent

- Subgraph execution events:
  - SubgraphExecutionStartingEvent
  - SubgraphExecutionCompletedEvent
  - SubgraphExecutionFailedEvent

- LLM call events:
  - LLMCallStartingEvent
  - LLMCallCompletedEvent

- LLM streaming events:
  - LLMStreamingStartingEvent
  - LLMStreamingFrameReceivedEvent
  - LLMStreamingFailedEvent
  - LLMStreamingCompletedEvent

- Tool execution events:
  - ToolCallStartingEvent
  - ToolValidationFailedEvent
  - ToolCallFailedEvent
  - ToolCallCompletedEvent

These events are emitted by the agents-core runtime and consumed by features such as Tracing, Debugger, and EventHandler to enable logging, tracing, monitoring, and remote inspection.
