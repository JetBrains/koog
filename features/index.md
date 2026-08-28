# Features

Agent features provide a way to extend and enhance the functionality of AI agents. With features, you can:

- Add new capabilities to agents
- Intercept and modify agent behavior
- Log and monitor agent execution
- Register multiple handlers for the same event type within a single feature

The Koog framework has the following features available out of the box:

- [Event handling](agent-event-handlers/)

  ______________________________________________________________________

  Monitor and respond to specific events during the agent execution

- [Tracing](tracing/)

  ______________________________________________________________________

  Capture detailed information about agent runs

- [Chat memory](chat-memory/)

  ______________________________________________________________________

  Store and retrieve chat message history between agent runs

- [Long-term memory](long-term-memory/)

  ______________________________________________________________________

  Add persistent memory to AI agents

- [Agent persistence](agent-persistence/)

  ______________________________________________________________________

  Save and restore the state of an agent at specific points during execution

- [OpenTelemetry](open-telemetry/)

  ______________________________________________________________________

  Generate, collect, and export telemetry data (traces) from your agent

To learn how to implement your own features, see [Custom features](custom-features/).
