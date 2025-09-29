# Module agents:agents-features

Provides implementations of useful features of AI agents, such as Tracing, Debugger, EventHandler, Memory, OpenTelemetry, Snapshot, and more.

### Overview

Features integrate with the agent pipeline via interceptor hooks and consume standardized Feature Events emitted during agent execution. These events are defined in the agents-core module under `ai.koog.agents.core.feature.model.events`. After the 0afb32b refactor, event and interceptor names are unified across the system.

### Standard Feature Events

The canonical description and definitions of Feature Events now live in the agents-core module. See:

- agents-core module docs: ../agents-core/Module.md (section "Standard Feature Events")
- Kotlin definitions: package `ai.koog.agents.core.feature.model.events`

Features in this module (Tracing, Debugger, EventHandler, etc.) consume these events to provide logging, tracing, monitoring, and remote inspection.

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
