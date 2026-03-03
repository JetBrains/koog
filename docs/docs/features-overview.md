# Overview

Agent features provide a way to extend and enhance the functionality of AI agents. Features can:

- Add new capabilities to agents
- Intercept and modify agent behavior
- Log and monitor agent execution
- Register multiple handlers for the same event type within a single feature

The Koog framework implements both features that are available out of the box and lets you implement your own custom features. Readily available features include:

- [Event Handler](agent-event-handlers.md)
- [Tracing](tracing.md)
- [Agent Memory](agent-memory.md)
- [OpenTelemetry](opentelemetry-support.md)
- [Agent Persistence (Snapshots)](agent-persistence.md)
- Debugger
- Tokenizer
- SQL Persistence Providers

To learn how to implement your own features, see [Custom features](custom-features.md).
