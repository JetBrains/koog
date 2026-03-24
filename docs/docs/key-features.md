# Key features

Key features of Koog include:

- **Idiomatic Kotlin and Java support**: Choose between a type-safe Kotlin DSL or a dedicated, fluent Java builder API. The Java API is designed to feel natural to Java teams, using standard thread pool executors instead of exposing coroutines.
- **Reliability and fault-tolerance**: Handle failures with built-in retries and restore the agent state at specific points during execution with the agent persistence feature.
- **Intelligent history compression**: Optimize token usage while maintaining context in long-running conversations using advanced built-in history compression techniques.
- **Enterprise-ready integrations**: Utilize integration with popular JVM frameworks such as Spring Boot and Ktor to embed Koog into your applications.
- **Observability with OpenTelemetry exporters**: Monitor and debug applications with built-in support for popular observability providers (W&B Weave, Langfuse).
- **LLM switching and seamless history adaptation**: Switch to a different LLM at any point without losing the existing conversation history or reroute between multiple LLM providers.
- **Multiplatform development**: For agents written in Kotlin, deploy agents across JVM, JS, WasmJS, Android, and iOS targets using Kotlin Multiplatform.
- **Model Context Protocol integration**: Use Model Context Protocol (MCP) tools in AI agents.
- **Knowledge retrieval and memory**: Retain and retrieve knowledge across conversations using vector embeddings, RAG, and shared agent memory.
- **Powerful Streaming API**: Process responses in real-time with streaming support and parallel tool calls.
- **Modular feature system**: Customize agent capabilities through a composable architecture.
- **Flexible graph workflows**: Design complex agent behaviors using intuitive graph-based workflows.
- **Custom tool creation**: Enhance your agents with tools that access external systems and APIs.
- **Comprehensive tracing**: Debug and monitor agent execution with detailed, configurable tracing.
