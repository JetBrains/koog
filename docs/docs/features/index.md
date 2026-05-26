# Features

Agent features provide a way to extend and enhance the functionality of AI agents.
With features, you can:

- Add new capabilities to agents
- Intercept and modify agent behavior
- Log and monitor agent execution
- Register multiple handlers for the same event type within a single feature

The Koog framework has the following features available out of the box:

<div class="grid cards" markdown>

-   :material-flash:{ .lg .middle } [Event handling](agent-event-handlers.md)

    ---

    Monitor and respond to specific events during the agent execution

-   :material-routes:{ .lg .middle } [Tracing](tracing.md)

    ---

    Capture detailed information about agent runs

-   :material-message-text-clock:{ .lg .middle } [Chat memory](chat-memory/index.md)

    ---

    Store and retrieve chat message history between agent runs

-   :material-database-clock:{ .lg .middle } [Long-term memory](long-term-memory.md)

    ---

    Add persistent memory to AI agents

-   :material-content-save-cog:{ .lg .middle } [Agent persistence](agent-persistence.md)

    ---

    Save and restore the state of an agent at specific points during execution

-   :simple-opentelemetry:{ .lg .middle } [OpenTelemetry](open-telemetry/index.md)

    ---

    Generate, collect, and export telemetry data (traces) from your agent

</div>

To learn how to implement your own features, see [Custom features](custom-features.md).
