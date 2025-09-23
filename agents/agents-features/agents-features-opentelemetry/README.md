# OpenTelemetry support

This guide provides comprehensive instructions on how to use OpenTelemetry with the Koog agentic framework for tracing and monitoring your AI agents.

## Overview

OpenTelemetry is an observability framework that provides tools for generating, collecting, and exporting telemetry data (metrics, logs, and traces) from your applications. The Koog OpenTelemetry feature allows you to instrument your AI agents to collect telemetry data, which can help you:

- Monitor agent performance and behavior
- Debug issues in complex agent workflows
- Visualize the execution flow of your agents
- Track LLM calls and tool usage
- Count tokens (input, output, and total) for LLM calls to monitor usage and costs
- Analyze agent behavior patterns

Key OpenTelemetry concepts

- **Spans**: Spans represent individual units of work or operations within a distributed trace. They indicate the beginning and end of a specific activity in an application, such as an agent execution, a function call, an LLM call, or a tool call.
- **Exporters**: Exporters are components responsible for sending the collected telemetry data (spans, metrics, logs) to various backends or destinations.
- **Collectors**: An OpenTelemetry Collector receives, processes, and exports telemetry data. It acts as an intermediary between your applications and your observability backend.

The OpenTelemetry feature in Koog automatically creates spans for various agent events, including:

- Agent execution start and end
- Node execution
- LLM calls
- Tool calls

## Installation

To use OpenTelemetry with Koog, add the OpenTelemetry feature to your agent:

```kotlin
val agent = AIAgent(
    executor = simpleOpenAIExecutor(apiKey),
    llmModel = OpenAIModels.Chat.GPT4o,
    systemPrompt = "You are a helpful assistant.",
    installFeatures = {
        install(OpenTelemetry) {
            // Configuration options here
        }
    }
)
```

## Configuration

### Basic configuration

Here is an example of installing the OpenTelemetry feature with a basic set of configuration items:

```kotlin
install(OpenTelemetry) {
    setServiceInfo("my-agent-service", "1.0.0")   // Set your service configuration
    addSpanExporter(LoggingSpanExporter.create()) // Add Logging exporter
}
```

The example includes the following configuration methods:

| Name               | Data type | Required | Description                                                                                     |
|--------------------|-----------|----------|-------------------------------------------------------------------------------------------------|
| `setServiceInfo`   | Unit      | No       | Sets the information about the service being instrumented, including service name and version.  |
| `addSpanExporter`  | Unit      | No       | Adds a span exporter to send telemetry data to external systems or for logging purposes.        |

Please see below the full list of available configuration properties:

| Name               | Data type          | Default value | Description                                                                  |
|--------------------|--------------------|---------------|------------------------------------------------------------------------------|
| `serviceName`      | `String`           | `ai.koog`     | The name of the service being instrumented.                                  |
| `serviceVersion`   | `String`           | `0.0.0`       | The version of the service being instrumented.                               |
| `sdk`              | `OpenTelemetrySdk` |               | The OpenTelemetry SDK instance to use for telemetry collection.              |
| `isVerbose`        | `Boolean`          | `false`       | Whether to enable verbose logging for debugging OpenTelemetry configuration. |
| `tracer`           | `Tracer`           |               | The OpenTelemetry tracer instance used for creating spans.                   |

Configuration API:

| Name                    | Arguments                                           | Description                                                                       |
|-------------------------|-----------------------------------------------------|-----------------------------------------------------------------------------------|
| `setServiceInfo`        | `serviceName: String, serviceVersion: String`       | Sets the service information including name and version.                          |
| `addSpanExporter`       | `exporter: SpanExporter`                            | Adds a span exporter to send telemetry data to external systems.                  |
| `addSpanProcessor`      | `processor: (SpanExporter) -> SpanProcessor`        | Adds a span processor creator function to process spans before they are exported. |
| `addResourceAttributes` | `attributes: Map<AttributeKey<T>, T> where T : Any` | Adds resource attributes to provide additional context about the service.         |
| `setSampler`            | `sampler: Sampler`                                  | Sets the sampling strategy to control which spans are collected.                  |
| `setVerbose`            | `verbose: Boolean`                                  | Enables or disables verbose logging for debugging OpenTelemetry configuration.    |
| `setSdk`                | `sdk: OpenTelemetrySdk`                             | Injects a custom OpenTelemetry SDK instance for advanced configuration control.   |


### Advanced configuration

For more advanced configuration, you can also customize the following configuration options:

- Sampler: configure the sampling strategy to adjust the frequency and amount of collected data.
- Resource attributes: add resource information, either through standard or custom resource attributes.

```kotlin
install(OpenTelemetry) {
    setServiceInfo("my-agent-service", "1.0.0")   // Set your service configuration
    addSpanExporter(LoggingSpanExporter.create()) // Add Logging exporter
    
    // Add resource attributes
    addResourceAttributes(mapOf(
        AttributeKey.stringKey("deployment.environment") to "production",
        AttributeKey.stringKey("custom.attribute") to "custom-value"
    ))
}
```

### Sdk configuration

If you already have an initialized OpenTelemetrySdk-for example, configured elsewhere in your application or provided by a framework-you can pass it directly using `setSdk`
When using `setSdk` any other configuration methods like `addSpanExporter`, `addSpanProcessor`, `addResourceAttributes` or `setSample` will be ignored, since the SDK is assumed to be fully configured.
```kotlin
val sdk: OpenTelemetrySdk = createPreconfiguredSdk() // Your preconfigured sdk

install(OpenTelemetry) {
    setSdk(sdk) // Use your existing OpenTelemetrySdk instance
}
```

#### Sampler

To define a sampler, use a corresponding method of the `Sampler` class that represents the sampling strategy you want
to use. The available method is:

- `alwaysOn()`: The default sampling strategy where every span (trace) is sampled.

#### Resource attributes

Resource attributes represent additional information about a process producing telemetry. This information can be
included in an OpenTelemetry configuration in Koog using the `addResourceAttributes()` method that takes a map of
`AttributeKey<T>` to values of type `T`. 

The following default resource attributes are automatically added:

- `service.name`: The name of the service being instrumented. Set to the value of `serviceName`.
- `service.version`: The version of the service being instrumented. Set to the value of `serviceVersion`.
- `service.instance.time`: The timestamp when the service instance was created.
- `os.type`: The operating system type.
- `os.version`: The operating system version.
- `os.arch`: The operating system architecture.

The OpenTelemetry feature automatically adds various attributes to spans following the [OpenTelemetry Semantic Convention for GenAI](https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-spans/):

### Common Attributes

- `gen_ai.operation.name`: the type of operation being performed (e.g., "create_agent", "invoke_agent", "chat", "execute_tool")
- `gen_ai.system`: the LLM provider being used
- `gen_ai.agent.id`: the ID of the agent
- `gen_ai.agent.name`: the name of the agent (when available)
- `gen_ai.conversation.id`: the ID of the conversation/run
- `gen_ai.request.model`: the LLM model being used
- `gen_ai.request.temperature`: the temperature parameter for the LLM request
- `error.type`: the type of error that occurred (when applicable)

### Custom Attributes

Some custom attributes specific to Koog are also added:

- `koog.agent.strategy.name`: the name of the agent strategy
- `koog.node.name`: the name of the node being executed

### Span Types

The OpenTelemetry feature creates different types of spans for various operations. Each span type has a distinct purpose, parent–child relationship, and set of attributes and events.

1. **Create Agent Span**
    - Purpose: Long‑lived span representing the lifecycle of an agent instance (created once per agent ID).
    - Emitted: When an agent is first started. Closed when the agent is closed.
    - Parent: none. Children: **Invoke Agent Span** (one per run).
    - Useful for: Grouping all agent runs and capturing static agent configuration.
    - Key attributes:
        - `gen_ai.operation.name` = `create_agent`
        - `gen_ai.agent.id`
        - `gen_ai.request.model`

2. **Invoke Agent Span**
    - Purpose: One concrete execution (run) of an agent.
    - Emitted: At the start of a run. Closed on successful finish or error.
    - Parent: Parent: **Create Agent Span**. Children: **Node Execute Span** instances, and, indirectly, LLM and Tool spans within nodes.
    - Useful for: Measuring run‑level timing, status, and grouping of all node, tool, and LLM activity for a run.
    - Key attributes:
        - `gen_ai.operation.name` = `invoke_agent`
        - `gen_ai.agent.id`
        - `gen_ai.conversation.id`
        - `gen_ai.system` (LLM provider)
        - `gen_ai.response.finish_reasons` (on error)

3. **Node Execute Span**
    - Purpose: Execution of a single node in the agent strategy.
    - Emitted: Immediately before a node runs. Closed after the node completes or errors.
    - Parent: **Invoke Agent Span**. Children: **Inference Spans** and **Execute Tool Span** instances created by the node.
    - Useful for: Understanding strategy flow and attributing LLM or tool work to a specific node.
    - Key attributes:
        - `gen_ai.conversation.id`
        - `koog.node.name`

4. **Inference Span**
    - Purpose: A single LLM call (prompt execution).
    - Emitted: Before the LLM is invoked. Closed after responses are received.
    - Parent: **Node Execute Span**.
    - Events added:
        - system, user, and assistant messages from the prompt
        - tool choice and tool result messages
        - moderation response (when present)
    - Useful for: Tracking model usage, prompt parameters, messages, token usage, and finish reasons.
    - Key attributes:
        - `gen_ai.operation.name` = `chat`
        - `gen_ai.system` (LLM provider)
        - `gen_ai.conversation.id`
        - `gen_ai.request.model`
        - `gen_ai.request.temperature`
        - `gen_ai.request.max_tokens` (when available)
        - `gen_ai.usage.input_tokens` (when available)
        - `gen_ai.usage.output_tokens` (when available)
        - `gen_ai.usage.total_tokens` (when available)
        - `gen_ai.response.finish_reasons` (Stop, ToolCalls, etc.)

5. **Execute Tool Span**
    - Purpose: Execution of a tool or function call triggered by the agent or LLM.
    - Emitted: When a tool is called. Closed after the tool returns a result or fails with an error during validation or execution.
    - Parent: **Node Execute Span**.
    - Useful for: Auditing tool usage, inputs and outputs, and failure causes.
    - Key attributes:
        - `gen_ai.tool.name`
        - `gen_ai.tool.description`
        - `gen_ai.tool.arguments` (when available)
        - `gen_ai.tool.call_id` (when available)
        - `gen_ai.tool.output` (when available)
        - `error.type` (on failure or validation error)

## Exporters

Exporters send collected telemetry data to an OpenTelemetry Collector or other types of destinations or backend implementations.

### OTLP Exporter example

The OTLP (OpenTelemetry Protocol) exporter sends telemetry data to an OpenTelemetry Collector. This is useful for integrating with systems like Jaeger, Zipkin, or Prometheus.

To add an OpenTelemetry Exporter, use the `addSpanExporter` function to a custom exporter:
```kotlin
install(OpenTelemetry) {
    // The default endpoint is http://localhost:4317
    addSpanExporter(
        OtlpGrpcSpanExporter.builder()
            .setEndpoint("http://localhost:4317")
            .build()
    )
}
```

### Logging Exporter example

A logging exporter that outputs trace information to the console is included by default. This type of export is useful
for development and debugging purposes.

```kotlin
install(OpenTelemetry) {
    // The logging exporter is added by default
    addSpanExporter(LoggingSpanExporter.create())
}
```

## Integration with Jaeger

Jaeger is a popular distributed tracing system that works with OpenTelemetry. To use Jaeger with Koog:

1. Start a Jaeger container:
```yaml
# docker-compose.yaml
services:
  jaeger-all-in-one:
    image: jaegertracing/all-in-one:1.39
    container_name: jaeger-all-in-one
    environment:
      - COLLECTOR_OTLP_ENABLED=true
    ports:
      - "4317:4317"  # OTLP gRPC port
      - "16686:16686"  # Jaeger UI port
```

2. Configure your agent to use the OTLP exporter:
```kotlin
install(OpenTelemetry) {
    addSpanExporter(
        OtlpGrpcSpanExporter.builder()
            .setEndpoint("http://localhost:4317")
            .build()
    )
}
```

3. Access the Jaeger UI at `http://localhost:16686` to view your traces.

## Examples

This section includes two examples of implementing OpenTelemetry support in your agent workflow. The basic example
includes quick OpenTelemetry setup with minimal configuration, while the advanced example shows how you can create a
more elaborate and customized OpenTelemetry configuration.

### Basic example

```kotlin
suspend fun main() {
    val apiKey = "your-api-key"
    val agent = AIAgent(
        executor = simpleGoogleAIExecutor(apiKey),
        llmModel = GoogleModels.Gemini2_0Flash,
        systemPrompt = "You are a code assistant. Provide concise code examples.",
        installFeatures = {
            install(OpenTelemetry) {
                setServiceInfo("my-otlp-agent", "1.0.0")
                addSpanExporter(LoggingSpanExporter.create())
            }
        }
    )

    val result = agent.run("Create python function that prints hello world")
    println(result)
    
    // Wait for telemetry data to be exported
    TimeUnit.SECONDS.sleep(10)
}
```

### Advanced example with custom exporters and attributes

```kotlin
suspend fun main() {
    val apiKey = "your-api-key"
    val agent = AIAgent(
        executor = simpleOpenAIExecutor(apiKey),
        llmModel = OpenAIModels.Chat.GPT4o,
        systemPrompt = "You are a helpful assistant.",
        installFeatures = {
            install(OpenTelemetry) {
                // Configure service info
                setServiceInfo("advanced-agent", "2.0.0")
                
                // Configure sampling
                setSampler(Sampler.alwaysOn())
                
                // Add resource attributes
                addResourceAttributes(mapOf(
                    AttributeKey.stringKey("deployment.environment") to "production",
                    AttributeKey.stringKey("custom.attribute") to "custom-value"
                ))
                
                // Add exporters
                addSpanExporter(LoggingSpanExporter.create())
            }
        }
    )

    val result = agent.run("Tell me about OpenTelemetry")
    println(result)
    
    // Wait for telemetry data to be exported
    TimeUnit.SECONDS.sleep(10)
}
```

## Troubleshooting

### Common Issues

1. **No traces appearing in Jaeger UI**
    - Ensure the Jaeger container is running and the OTLP port (4317) is accessible
    - Check that the OTLP exporter is configured with the correct endpoint
    - Make sure to wait a few seconds after agent execution for traces to be exported

2. **Missing spans or incomplete traces**
    - Verify that the agent execution completes successfully
    - Ensure that you're not closing the application too quickly after agent execution
    - Add a delay (e.g., `TimeUnit.SECONDS.sleep(10)`) after agent execution to allow time for spans to be exported

3. **Excessive number of spans**
    - Consider using a different sampling strategy by configuring the `sampler` property
    - For example, use `Sampler.traceIdRatioBased(0.1)` to sample only 10% of traces
