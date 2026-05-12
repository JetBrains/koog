# Module agents-features-opentelemetry

Provides [OpenTelemetry](https://opentelemetry.io) integration for monitoring and tracing AI agents in the Koog framework.

### Overview

The agents-features-opentelemetry module enables observability for AI agents by integrating with OpenTelemetry, allowing you to:

- Monitor agent performance and behavior
- Debug issues in complex agent workflows
- Visualize the execution flow of your agents
- Track LLM calls and tool usage
- Analyze agent behavior patterns

Key features include:
- Automatic span creation for agent events (agent execution, node execution, LLM calls, tool calls)
- Support for various exporters (OTLP, Logging) and integrations (Langfuse, W&B Weave, Datadog)
- Customizable sampling strategies
- Resource attributes following the [OpenTelemetry Semantic Convention for GenAI](https://opentelemetry.io/docs/specs/semconv/gen-ai/)
- Integration with observability systems like Jaeger

For installation, configuration, and runnable examples (including Jaeger and the Langfuse, W&B Weave, and Datadog exporters),
see the [OpenTelemetry support](https://docs.koog.ai/features/open-telemetry/) documentation.
