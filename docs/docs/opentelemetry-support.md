# OpenTelemetry support

This page provides details about the support for OpenTelemetry with the Koog agentic framework for tracing and
monitoring your AI agents.

## Overview

OpenTelemetry is an observability framework that provides tools for generating, collecting, and exporting telemetry data
(traces and metrics) from your applications. The Koog OpenTelemetry feature allows you to instrument your AI agents to
collect
telemetry data, which can help you:

- Monitor agent performance and behavior
- Debug issues in complex agent workflows
- Visualize the execution flow of your agents
- Track LLM calls and tool usage
- Analyze agent behavior patterns

## Key OpenTelemetry concepts

- **Spans**: spans represent individual units of work or operations within a distributed trace. They indicate the
  beginning and end of a specific activity in an application, such as an agent execution, a function call, an LLM call,
  or a tool call.
- **Attributes**: attributes provide metadata about a telemetry-related item such as a span. Attributes are represented
  as key-value pairs.
- **Events**: events are specific points in time during the lifetime of a span (span-related events) that represent
  something potentially noteworthy that happened.
- **Metrics**: metrics are numerical measurements that represent the system's performance or state over time, such as
  token usage.
  They include a name, a value, and attributes for the context.
- **Exporters**: exporters are components responsible for sending the collected telemetry data to various backends or
  destinations.
- **Collectors**: collectors receive, process, and export telemetry data. They act as intermediaries between your
  applications and your observability backend.
- **Samplers**: samplers determine whether a trace should be recorded based on the sampling strategy. They are used to
  manage the volume of telemetry data.
- **Resources**: resources represent entities that produce telemetry data. They are identified by resource attributes,
  which are key-value pairs that provide information about the resource.

The OpenTelemetry feature in Koog automatically creates spans for various agent events, including:

- Agent execution start and end
- Node execution
- LLM calls
- Tool calls

Additionally, OpenTelemetry feature sends metrics including token usage consumption,
operation duration, and number of tool calls

## Installation

To use OpenTelemetry with Koog, add the OpenTelemetry feature to your agent:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor

const val apiKey = ""
-->

```kotlin
val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(apiKey),
    llmModel = OpenAIModels.Chat.GPT4o,
    systemPrompt = "You are a helpful assistant.",
    installFeatures = {
        install(OpenTelemetry) {
            // Configuration options go here
        }
    }
)
```

<!--- KNIT example-opentelemetry-support-01.kt -->

## Configuration

### Basic configuration

Here is the full list of available properties that you set when configuring the OpenTelemetry feature in an agent:

| Name             | Data type          | Default value                | Description                                                                  |
|------------------|--------------------|------------------------------|------------------------------------------------------------------------------|
| `serviceName`    | `String`           | `ai.koog`                    | The name of the service being instrumented.                                  |
| `serviceVersion` | `String`           | Current Koog library version | The version of the service being instrumented.                               |
| `isVerbose`      | `Boolean`          | `false`                      | Whether to enable verbose logging for debugging OpenTelemetry configuration. |
| `sdk`            | `OpenTelemetrySdk` |                              | The OpenTelemetry SDK instance to use for telemetry collection.              |
| `tracer`         | `Tracer`           |                              | The OpenTelemetry tracer instance used for creating spans.                   |
| `meter`          | `Meter`            |                              | The OpenTelemetry meter instance used for creating metrics.                  |

!!! note
The `sdk`, `tracer`, and `meter` properties are public properties that you can access, but you can only set them
using the public methods listed below.

The `OpenTelemetryConfig` class also includes methods that represent actions related to different configuration
items. Here is an example of installing the OpenTelemetry feature with a basic set of configuration items:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import io.opentelemetry.exporter.logging.LoggingMetricExporter
import io.opentelemetry.exporter.logging.LoggingSpanExporter

const val apiKey = ""

val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(apiKey),
    llmModel = OpenAIModels.Chat.GPT4o,
    systemPrompt = "You are a helpful assistant."
) {
-->
<!--- SUFFIX
}
-->

```kotlin
install(OpenTelemetry) {
    // Set your service configuration
    setServiceInfo("my-agent-service", "1.0.0")

    // Add the Logging exporter
    addSpanExporter(LoggingSpanExporter.create())

    // Add the Metric exporter
    addMetricExporter(LoggingMetricExporter.create())
}
```

<!--- KNIT example-opentelemetry-support-02.kt -->

For a reference of available methods, see the sections below.

#### setServiceInfo

Sets the service information including name and version. Takes the following arguments:

| Name             | Data type | Required | Default value | Description                                    |
|------------------|-----------|----------|---------------|------------------------------------------------|
| `serviceName`    | String    | Yes      |               | The name of the service being instrumented.    |
| `serviceVersion` | String    | Yes      |               | The version of the service being instrumented. |

#### addSpanExporter

Adds a span exporter to send telemetry data to external systems. Takes the following argument:

| Name       | Data type      | Required | Default value | Description                                                                   |
|------------|----------------|----------|---------------|-------------------------------------------------------------------------------|
| `exporter` | `SpanExporter` | Yes      |               | The `SpanExporter` instance to be added to the list of custom span exporters. |

#### addMetricExporter

Adds a metric exporter to send runtime metrics to external systems. Takes the following arguments:

| Name            | Data type        | Required | Default value            | Description                                                                       |
|-----------------|------------------|----------|--------------------------|-----------------------------------------------------------------------------------|
| `exporter`      | `MetricExporter` | Yes      |                          | The `MetricExporter` instance to be added to the list of custom metric exporters. |
| `meterInterval` | `Duration`       | No       | `Duration.ofSeconds(1)`  | The interval for processing metrics.                                              |

#### addMetricFilter

Adds a metric filter to the OpenTelemetry configuration. This filter is used to specify which attribute keys should be retained for a specific metric during telemetry data processing. Takes the following arguments:

| Name           | Data type     | Required | Default value | Description                                                                         |
|----------------|---------------|----------|---------------|-------------------------------------------------------------------------------------|
| `metricName`   | `String`      | Yes      |               | The name of the metric to which the filter will be applied.                         |
| `keysToRetain` | `Set<String>` | Yes      |               | A set of attribute keys that should be retained for the specified metric.           |

#### addSpanProcessor

Adds a span processor factory to process spans before they are exported. Takes the following argument:

| Name        | Data type                         | Required | Default value | Description                                                                                                |
|-------------|-----------------------------------|----------|---------------|------------------------------------------------------------------------------------------------------------|
| `processor` | `(SpanExporter) -> SpanProcessor` | Yes      |               | A function that creates a span processor for a given exporter. Lets you customize processing per exporter. |

#### addResourceAttributes

Adds resource attributes to provide additional context about the service. Takes the following argument:

| Name         | Data type                 | Required | Default value | Description                                                            |
|--------------|---------------------------|----------|---------------|------------------------------------------------------------------------|
| `attributes` | `Map<AttributeKey<T>, T>` | Yes      |               | The key-value pairs that provide additional details about the service. |

#### setSampler

Sets the sampling strategy to control which spans are collected. Takes the following argument:

| Name      | Data type | Required | Default value | Description                                                      |
|-----------|-----------|----------|---------------|------------------------------------------------------------------|
| `sampler` | `Sampler` | Yes      |               | The sampler instance to set for the OpenTelemetry configuration. |

#### setVerbose

Enables or disables verbose logging. Takes the following argument:

| Name      | Data type | Required | Default value | Description                                                     |
|-----------|-----------|----------|---------------|-----------------------------------------------------------------|
| `verbose` | `Boolean` | Yes      | `false`       | If true, the application collects more detailed telemetry data. |

!!! note

    Some content of OpenTelemetry spans is masked by default for security reasons. For example, LLM messages are masked as `HIDDEN:non-empty` instead of the actual message content. To get the content, set the value of the `verbose` argument to `true`.

#### setSdk

Injects a pre-configured OpenTelemetrySdk instance.

- When you call setSdk(sdk), the provided SDK is used as-is, and any custom configuration applied via addSpanExporter,
  addMeterExporter, addSpanProcessor, addResourceAttributes, or setSampler is ignored.
- The tracer’s instrumentation scope name/version are aligned with your service info.

| Name  | Data type          | Required | Description                           |
|-------|--------------------|----------|---------------------------------------|
| `sdk` | `OpenTelemetrySdk` | Yes      | The SDK instance to use in the agent. |

### Advanced configuration

For more advanced configuration, you can also customize the following configuration options:

- Sampler: configure the sampling strategy to adjust the frequency and amount of collected data.
- Resource attributes: add more information about the process that is producing telemetry data.

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.exporter.logging.LoggingMetricExporter
import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.sdk.trace.samplers.Sampler
import java.time.Duration

const val apiKey = ""

val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(apiKey),
    llmModel = OpenAIModels.Chat.GPT4o,
    systemPrompt = "You are a helpful assistant."
) {
-->
<!--- SUFFIX
}
-->

```kotlin
install(OpenTelemetry) {
    // Set your service configuration
    setServiceInfo("my-agent-service", "1.0.0")

    // Add the Logging exporter
    addSpanExporter(LoggingSpanExporter.create())

    // Add the Metric exporter
    addMetricExporter(
        exporter = LoggingMetricExporter.create(),
        meterInterval = Duration.ofSeconds(30)
    )

    // Add metric filter
    addMetricFilter(
        metricName = "gen_ai.client.token.usage",
        keysToRetain = setOf("gen_ai.operation.name", "gen_ai.token.type")
    )

    // Set the sampler 
    setSampler(Sampler.traceIdRatioBased(0.5))

    // Add resource attributes
    addResourceAttributes(
        mapOf(
            AttributeKey.stringKey("custom.attribute") to "custom-value"
        )
    )
}
```

<!--- KNIT example-opentelemetry-support-03.kt -->

#### Sampler

To define a sampler, use a corresponding method of the `Sampler` class (`io.opentelemetry.sdk.trace.samplers.Sampler`)
from the `opentelemetry-java` SDK that represents the sampling strategy you want to use.

The default sampling strategy is as follows:

- `Sampler.alwaysOn()`: The default sampling strategy where every span (trace) is sampled.

For more information about available samplers and sampling strategies, see the
OpenTelemetry [Sampler](https://opentelemetry.io/docs/languages/java/sdk/#sampler) documentation.

#### Resource attributes

Resource attributes represent additional information about a process producing telemetry data. Koog includes a set of
resource attributes that are set by default:

- `service.name`
- `service.version`
- `service.instance.time`
- `os.type`
- `os.version`
- `os.arch`

The default value of the `service.name` attribute is `ai.koog`, while the default `service.version` value is the
currently used Koog library version.

In addition to default resource attributes, you can also add custom attributes. To add a custom attribute to an
OpenTelemetry configuration in Koog, use the `addResourceAttributes()` method in an OpenTelemetry configuration that
takes a key and a value as its arguments.

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import io.opentelemetry.api.common.AttributeKey


const val apiKey = "api-key"
val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(apiKey),
    llmModel = OpenAIModels.Chat.GPT4o,
    systemPrompt = "You are a helpful assistant.",
    installFeatures = {
        install(OpenTelemetry) {
-->
<!--- SUFFIX
        }
    }
)
-->

```kotlin
addResourceAttributes(
    mapOf(
        AttributeKey.stringKey("custom.attribute") to "custom-value"
    )
)
```

<!--- KNIT example-opentelemetry-support-04.kt -->

## Span types and attributes

The OpenTelemetry feature automatically creates different types of spans to track various operations in your agent:

- **CreateAgentSpan**: created when you run an agent, closed when the agent is closed or the process is terminated.
- **InvokeAgentSpan**: the invocation of an agent.
- **StrategySpan**: the execution of an agent's strategy (the top-level execution flow).
- **NodeExecuteSpan**: the execution of a node in the agent's strategy. This is a custom, Koog-specific span.
- **SubgraphExecuteSpan**: the execution of a subgraph within the agent strategy. This is a custom, Koog-specific span.
- **InferenceSpan**: an LLM call.
- **ExecuteToolSpan**: a tool call.

Spans are organized in a nested, hierarchical structure. Here is an example of a span structure:

```text
CreateAgentSpan
    InvokeAgentSpan
        StrategySpan
            NodeExecuteSpan
                InferenceSpan
            NodeExecuteSpan
                ExecuteToolSpan
            SubgraphExecuteSpan
                NodeExecuteSpan
                    InferenceSpan
```

### Span attributes

Span attributes provide metadata related to a span. Each span has its set of attributes, while some spans can also
repeat attributes.

Koog supports a list of predefined attributes that follow
OpenTelemetry's [Semantic conventions for generative AI events](https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-spans/).
For example, the conventions define an attribute named
`gen_ai.conversation.id`, which is usually a required attribute for a span. In Koog, the value of this attribute is the
unique identifier for an agent run, that is automatically set when you call the `agent.run()` method.

In addition, Koog also includes custom, Koog-specific attributes. You can recognize most of these attributes by the
`koog.` prefix. Here are the available custom attributes:

- `koog.strategy.name`: the name of the agent strategy. A strategy is a Koog-related entity that describes the
  purpose of the agent. Used in the `StrategySpan` span.
- `koog.node.id`: the identifier (name) of the node being executed. Used in the `NodeExecuteSpan` span.
- `koog.node.input`: the input passed to the node at the beginning of execution. Present on `NodeExecuteSpan` when node
  starts.
- `koog.node.output`: the output produced by the node upon completion. Present on `NodeExecuteSpan` when node completes
  successfully.
- `koog.subgraph.id`: the identifier (name) of the subgraph being executed. Used in the `SubgraphExecuteSpan` span.
- `koog.subgraph.input`: the input passed to the subgraph at the beginning of execution. Present on`SubgraphExecuteSpan`
  when subgraph starts.
- `koog.subgraph.output`: the output produced by the subgraph upon completion. Present on `SubgraphExecuteSpan` when
  subgraph completes successfully.

### Events

A span can also have an _event_ attached to the span. Events describe a specific point in time when something relevant
happened. For example, when an LLM call started or finished. Events also have attributes and additionally include event
_body fields_.

The following event types are supported in line with
OpenTelemetry's [Semantic conventions for generative AI events](https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-events/):

- **SystemMessageEvent**: the system instructions passed to the model.
- **UserMessageEvent**: the user message passed to the model.
- **AssistantMessageEvent**: the assistant message passed to the model.
- **ToolMessageEvent**: the response from a tool or function call passed to the model.
- **ChoiceEvent**: the response message from a model.
- **ModerationResponseEvent**: the model moderation result or signal.

!!! note   
The `optentelemetry-java` SDK does not support the event body fields parameter when adding an event. Therefore, in
the OpenTelemetry support in Koog, event body fields are a separate attribute whose key is `body` and value type is
string. The string includes the content or payload for the event body field, which is usually a JSON-like object. For
examples of event body fields, see
the [OpenTelemetry documentation](https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-events/#examples). For the
state of support for event body
fields in `opentelemetry-java`, see the
related [GitHub issue](https://github.com/open-telemetry/semantic-conventions/issues/1870).

## Metrics

Koog also emits runtime metrics alongside traces to help you monitor agent behavior in real time.

### Available metrics

- gen_ai.client.token.usage
    - Instrument: Counter (LongCounter)
    - Unit: `token`
    - Description: Total token count per operation and token type
    - When emitted: after an LLM call finishes; recorded separately for input and output tokens
    - Key attributes:
        - `gen_ai.operation.name` (required)
        - `gen_ai.provider.name` (required)
        - `gen_ai.response.model` (recommended)
        - `gen_ai.token.type` (recommended) — `INPUT` or `OUTPUT`
    - Spec: see OpenTelemetry semantic
      conventions, [gen_ai.client.token.usage](https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-metrics/#metric-gen_aiclienttokenusage)

- gen_ai.client.operation.duration
    - Instrument: Histogram (DoubleHistogram)
    - Unit: seconds (`s`)
    - Description: Operation duration distribution; in Koog it is recorded for tool calls
    - Recommended explicit bucket boundaries (seconds): 0.01, 0.02, 0.04, 0.08, 0.16, 0.32, 0.64, 1.28, 2.56, 5.12,
      10.24, 20.48, 40.96, 81.92
    - Key attributes:
        - `gen_ai.operation.name` (required)
        - `gen_ai.tool.name` (recommended, if `gen_ai.operation.name` is `EXECUTE_TOOL`)
        - `gen_ai.tool.call.status` (custom, if `gen_ai.operation.name` is `EXECUTE_TOOL`) — `SUCCESS`, `ERROR`, or
          `VALIDATION_FAILED`
    - Spec: see OpenTelemetry semantic
      conventions, [gen_ai.client.operation.duration](https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-metrics/#metric-gen_aiclientoperationduration)

- gen_ai.client.tool.count
    - Instrument: Counter (LongCounter)
    - Unit: `tool call`
    - Description: Custom metric that counts tool calls and their statuses
    - When emitted: on tool completion, failure, or validation failure
    - Key attributes:
        - `gen_ai.operation.name` (required) — `EXECUTE_TOOL`
        - `gen_ai.tool.name` (recommended)
        - `gen_ai.tool.call.status` (custom) — `SUCCESS`, `ERROR`, or `VALIDATION_FAILED`

!!! note
Metrics are reported via the OpenTelemetry `Meter` created for your service. If no metric exporters are configured,
Koog uses a console `LoggingMetricExporter` by default (with a 1-second `PeriodicMetricReader` interval).
You can customize the metric reading interval when adding a custom exporter using the `meterInterval` parameter.

## Exporters

Exporters send collected telemetry data to an OpenTelemetry Collector or other types of destinations or backend
implementations.

### Span exporters

To export traces, use the `addSpanExporter()` method when installing the OpenTelemetry feature. The method takes the
following argument:

| Name       | Data type    | Required | Default | Description                                                                 |
|------------|--------------|----------|---------|-----------------------------------------------------------------------------|
| `exporter` | SpanExporter | Yes      |         | The SpanExporter instance to be added to the list of custom span exporters. |

### Metric exporters

To export metrics, use the `addMetricExporter()` method when installing the OpenTelemetry feature. The method takes the
following argument:

| Name       | Data type        | Required | Default | Description                                                                     |
|------------|------------------|----------|---------|---------------------------------------------------------------------------------|
| `exporter` | `MetricExporter` | Yes      |         | The MetricExporter instance to be added to the list of custom metric exporters. |

The sections below provide information about some of the most commonly used span exporters from the `opentelemetry-java`
SDK.

!!! note
If you do not configure any custom exporters, Koog will use a console `LoggingSpanExporter` for traces and a console
`LoggingMetricExporter` for metrics by default. This helps during local development and debugging.

### Logging exporter

A logging exporter that outputs trace information to the console. `LoggingSpanExporter`
(`io.opentelemetry.exporter.logging.LoggingSpanExporter`) is a part of the `opentelemetry-java` SDK.

This type of export is useful for development and debugging purposes.

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import io.opentelemetry.exporter.logging.LoggingSpanExporter

const val apiKey = ""

val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(apiKey),
    llmModel = OpenAIModels.Chat.GPT4o,
    systemPrompt = "You are a helpful assistant."
) {
-->
<!--- SUFFIX
}
-->

```kotlin
install(OpenTelemetry) {
    // Add the logging exporter
    addSpanExporter(LoggingSpanExporter.create())
    // Add more exporters as needed
}
```

<!--- KNIT example-opentelemetry-support-05.kt -->

### OpenTelemetry HTTP exporter

OpenTelemetry HTTP exporter (`OtlpHttpSpanExporter`) is a part of the `opentelemetry-java` SDK
(`io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter`) and sends span data to a backend through HTTP.

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter
import java.util.concurrent.TimeUnit

const val apiKey = ""
const val AUTH_STRING = ""


val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(apiKey),
    llmModel = OpenAIModels.Chat.GPT4o,
    systemPrompt = "You are a helpful assistant."
) {
-->
<!--- SUFFIX
}
-->

```kotlin
install(OpenTelemetry) {
    // Add OpenTelemetry HTTP Span exporter
    addSpanExporter(
        OtlpHttpSpanExporter.builder()
            // Set the maximum time to wait for the collector to process an exported batch of spans 
            .setTimeout(30, TimeUnit.SECONDS)
            // Set the OpenTelemetry endpoint to connect to
            .setEndpoint("http://localhost:3000/api/public/otel/v1/traces")
            // Add the authorization header
            .addHeader("Authorization", "Basic $AUTH_STRING")
            .build()
    )
    addMetricExporter(
        OtlpHttpMetricExporter.builder()
            // Set the maximum time to wait for the collector to process an exported batch of metrics 
            .setTimeout(30, TimeUnit.SECONDS)
            // Set the OpenTelemetry endpoint to connect to
            .setEndpoint("http://localhost:17011")
            // Add the authorization header
            .addHeader("Authorization", "Basic $AUTH_STRING")
            .build()
    )
}
```

<!--- KNIT example-opentelemetry-support-06.kt -->

### OpenTelemetry gRPC exporter

OpenTelemetry gRPC exporter (`OtlpGrpcSpanExporter`) is a part of the `opentelemetry-java` SDK
(`io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter`). It exports telemetry data to a backend through gRPC and
lets you define the host and port of the backend, collector, or endpoint that receives the data. The default port is
`4317`.

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter

const val apiKey = ""

val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(apiKey),
    llmModel = OpenAIModels.Chat.GPT4o,
    systemPrompt = "You are a helpful assistant."
) {
-->
<!--- SUFFIX
}
-->

```kotlin
install(OpenTelemetry) {
    addSpanExporter(
        // Add OpenTelemetry gRPC exporter
        OtlpGrpcSpanExporter.builder()
            // Set the host and the port
            .setEndpoint("http://localhost:4317")
            .build()
    )
    addMetricExporter(
        // Add OpenTelemetry gRPC exporter
        OtlpGrpcMetricExporter.builder()
            // Set the host and the port
            .setEndpoint("http://localhost:4317")
            .build()
    )
}
```

<!--- KNIT example-opentelemetry-support-07.kt -->

## Integration with Langfuse

Langfuse provides trace visualization and analytics for LLM/agent workloads.

You can configure Koog to export OpenTelemetry traces directly to Langfuse using a helper function:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.integration.langfuse.addLangfuseExporter
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor

const val apiKey = ""

val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(apiKey),
    llmModel = OpenAIModels.Chat.GPT4o,
    systemPrompt = "You are a helpful assistant."
) {
-->
<!--- SUFFIX
}
-->

```kotlin
install(OpenTelemetry) {
    addLangfuseExporter(
        langfuseUrl = "https://cloud.langfuse.com",
        langfusePublicKey = "...",
        langfuseSecretKey = "..."
    )
}
```

<!--- KNIT example-opentelemetry-support-08.kt -->

Please read the [full documentation](opentelemetry-langfuse-exporter.md) about integration with Langfuse.

## Integration with W&B Weave

W&B Weave provides trace visualization and analytics for LLM/agent workloads. Integration with W&B Weave can be
configured via a predefined exporter:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.integration.weave.addWeaveExporter
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor

const val apiKey = ""

val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(apiKey),
    llmModel = OpenAIModels.Chat.GPT4o,
    systemPrompt = "You are a helpful assistant."
) {
-->
<!--- SUFFIX
}
-->

```kotlin
install(OpenTelemetry) {
    addWeaveExporter(
        weaveOtelBaseUrl = "https://trace.wandb.ai",
        weaveEntity = "my-team",
        weaveProjectName = "my-project",
        weaveApiKey = "..."
    )
}
```

<!--- KNIT example-opentelemetry-support-09.kt -->

Please read the [full documentation](opentelemetry-weave-exporter.md) about integration with W&B Weave.

## Integration with Jaeger

Jaeger is a popular distributed tracing system that works with OpenTelemetry. The `opentelemetry` directory within
`examples` in the Koog repository includes an example of using OpenTelemetry with Jaeger and Koog agents.

### Prerequisites

To test OpenTelemetry with Koog and Jaeger, start the Jaeger OpenTelemetry all-in-one process using the provided
`docker-compose.yaml` file, by running the following command:

```bash
docker compose up -d
```

The provided Docker Compose YAML file includes the following content:

```yaml
# docker-compose.yaml
services:
    jaeger-all-in-one:
        image: jaegertracing/all-in-one:1.39
        container_name: jaeger-all-in-one
        environment:
            - COLLECTOR_OTLP_ENABLED=true
        ports:
            - "4317:4317"
            - "16686:16686"
```

To access the Jaeger UI and view your traces, open `http://localhost:16686`.

### Example

To export telemetry data for use in Jaeger, the example uses `LoggingSpanExporter`
(`io.opentelemetry.exporter.logging.LoggingSpanExporter`) and `OtlpGrpcSpanExporter`
(`io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter`) from the `opentelemetry-java` SDK.

Here is the full code sample:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.utils.io.use
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import kotlinx.coroutines.runBlocking

const val openAIApiKey = "open-ai-api-key"

-->

```kotlin
fun main() {
    runBlocking {
        val agent = AIAgent(
            promptExecutor = simpleOpenAIExecutor(openAIApiKey),
            llmModel = OpenAIModels.Chat.O4Mini,
            systemPrompt = "You are a code assistant. Provide concise code examples."
        ) {
            install(OpenTelemetry) {
                // Add a console logger for local debugging
                addSpanExporter(LoggingSpanExporter.create())

                // Send traces to OpenTelemetry collector
                addSpanExporter(
                    OtlpGrpcSpanExporter.builder()
                        .setEndpoint("http://localhost:4317")
                        .build()
                )

                // Send metrics to OpenTelemetry collector
                addMetricExporter(
                    OtlpGrpcMetricExporter.builder()
                        // Set the host and the port
                        .setEndpoint("http://localhost:4317")
                        .build()
                )
            }
        }

        agent.use { agent ->
            println("Running the agent with OpenTelemetry tracing...")

            val result = agent.run("Tell me a joke about programming")

            println(
                "Agent run completed with result: '$result'." +
                    "\nCheck Jaeger UI at http://localhost:16686 to view traces"
            )
        }
    }
}
```

<!--- KNIT example-opentelemetry-support-10.kt -->

## Troubleshooting

### Common issues

1. **No traces appearing in Jaeger, Langfuse, or W&B Weave**
    - Ensure the service is running and the OpenTelemetry port (4317) is accessible.
    - Check that the OpenTelemetry exporter is configured with the correct endpoint.
    - Make sure to wait a few seconds after agent execution for traces to be exported.

2. **Missing spans or incomplete traces**
    - Verify that the agent execution completes successfully.
    - Ensure that you're not closing the application too quickly after agent execution.
    - Add a delay after agent execution to allow time for spans to be exported.

3. **Excessive number of spans**
    - Consider using a different sampling strategy by configuring the `sampler` property.
    - For example, use `Sampler.traceIdRatioBased(0.1)` to sample only 10% of traces.

4. **Span adapters override each other**
    - Currently, the OpenTelemetry agent feature does not support applying multiple span
      adapters [KG-265](https://youtrack.jetbrains.com/issue/KG-265/Adding-Weave-exporter-breaks-Langfuse-exporter).
