# Module agents:agents-features

A collection of plug-and-play features for Koog AI agents. These features hook into the agent execution pipeline to observe, enrich, and extend behavior.

## What is inside

Each feature lives in its own submodule, you only depend on what you need. Commonly used features include:
- Tracing: End-to-end spans for agent runs and LLM, or Tool calls. Great for local dev and production observability;
- EventHandler: Subscribe to standardized agent events and react (log, metrics, custom side effects);
- Memory: Pluggable memory interfaces for storing and retrieving agent context;
- OpenTelemetry: OTel exporters and wiring for spans emitted by the agent pipeline;
- Snapshot: Persist and restore agent snapshots for reproducibility and time-travel debugging.

Check each feature’s own README/Module docs for details and advanced configuration.

## How features integrate

Features integrate via interceptor hooks and consume standardized events emitted during an agent execution. These events are defined in the agents-core module under:
- ai.koog.agents.core.feature.model.events

Typical events include:
- AgentStarting/Completed
- LLMCallStarting/Completed
- ToolCallStarting/Completed

Features can listen to these events, mutate context when appropriate, and publish additional events for downstream consumers.

## Installing features

Install features when constructing your agent. Multiple features can be installed together; they remain decoupled and communicate via events.

```kotlin
val agent = createAgent(/* ... */) {
    install(Tracing) {
        // Tracing configuration
    }
    install(OpenTelemetry) {
        // OTel configuration
    }
}
```

Consult each feature’s README for exact configuration options and defaults.

## Using in unit tests

Features are test-friendly. They honor testing configurations and can be directed to in-memory writers/ports.
- Install the same feature in tests to capture events deterministically.
- Point outputs to test stubs to assert behavior (e.g., assert a specific sequence of Feature Events).
- Prefer higher sampling in tests so important transitions are recorded.

Example (pseudo):
```kotlin
@Test
fun testAgentEmitsExpectedEvents() {
    val events = MutableListWriter<FeatureEvent>()
    createAgent { 
        install(EventHandler) { 
            writer = events 
        } 
    }.use { agent ->
        agent.run("Hello")
        assertTrue(events.any { it is LlmCallRequested })
    }
}
```

## Where to learn more
See each feature’s Module/README in its submodule for concrete examples and advanced setup:
- Tracing
- EventHandler
- Memory
- OpenTelemetry
- Snapshot
