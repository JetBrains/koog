# Module agents:agents-features

Provides implementations of useful features of AI agents, such as Tracing, Debugger, EventHandler, Memory, OpenTelemetry, Snapshot, and more.

### Overview

Features integrate with the agent pipeline via interceptor hooks and consume standardized Feature Events emitted during agent execution. After the 0afb32b refactor, event and interceptor names are unified across the system.

### Standard Feature Events

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

- LLM call events:
  - LLMCallStartingEvent
  - LLMCallCompletedEvent

- Tool call events:
  - ToolCallStartingEvent
  - ToolValidationFailedEvent
  - ToolCallFailedEvent
  - ToolCallCompletedEvent

These events are produced by features such as Tracing and Debugger to enable logging, tracing, monitoring, and remote inspection.

### Using in your project

Add the desired feature dependency, for example:

```kotlin
dependencies {
    implementation("ai.koog.agents:agents-features-trace:$version")
    implementation("ai.koog.agents:agents-features-debugger:$version")
}
```

Install a feature in the agent builder:

```kotlin
val agent = createAgent(/* ... */) {
    install(Tracing)
    install(Debugger)
}
```

### Using in unit tests

Most features can be installed in tests; they honor testing configuration and can be pointed to test writers/ports.

### Example of usage

See each feature's Module/README in its submodule for concrete examples (Tracing, Debugger, EventHandler, Memory, OpenTelemetry, Snapshot).
