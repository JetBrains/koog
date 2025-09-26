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
