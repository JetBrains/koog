# OpenTelemetry support

This page provides details about the support for OpenTelemetry with the Koog agentic framework for tracing and
monitoring your AI agents.

## Overview

OpenTelemetry is an observability framework that provides tools for generating, collecting, and exporting telemetry data
(traces) from your applications. The Koog OpenTelemetry feature allows you to instrument your AI agents to collect
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

## Installation

To use OpenTelemetry with Koog, add the OpenTelemetry feature to your agent:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
    val promptExecutor = simpleOpenAIExecutor("openai-api-key")
    -->
    ```kotlin
    val agent = AIAgent(
        promptExecutor = promptExecutor,
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

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry;
    import ai.koog.prompt.executor.clients.openai.OpenAIModels;
    import ai.koog.prompt.executor.model.PromptExecutor;
    public class exampleOpentelemetrySupportJava01 {
        public static void main(String[] args) {
            var promptExecutor = PromptExecutor.builder()
                .openAI("openai-api-key")
                .build();
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    var agent = AIAgent.builder()
        .promptExecutor(promptExecutor)
        .llmModel(OpenAIModels.Chat.GPT4o)
        .systemPrompt("You are a helpful assistant.")
        .install(OpenTelemetry.Feature, config -> {
            // Configuration options go here
        })
        .build();
    ```
    <!--- KNIT exampleOpentelemetrySupportJava01.java -->

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

!!! note
The `sdk` and `tracer` properties are public properties that you can access, but you can only set them using the
public methods listed below.

The `OpenTelemetryConfig` class also includes methods that represent actions related to different configuration
items. Here is an example of installing the OpenTelemetry feature with a basic set of configuration items:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
    import io.opentelemetry.exporter.logging.LoggingSpanExporter
    val promptExecutor = simpleOpenAIExecutor("openai-api-key")
    val agent = AIAgent(
        promptExecutor = promptExecutor,
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
    }
    ```
    <!--- KNIT example-opentelemetry-support-02.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry;
    import ai.koog.prompt.executor.clients.openai.OpenAIModels;
    import ai.koog.prompt.executor.model.PromptExecutor;
    import io.opentelemetry.exporter.logging.LoggingSpanExporter;
    public class exampleOpentelemetrySupportJava02 {
        public static void main(String[] args) {
            var promptExecutor = PromptExecutor.builder()
                .openAI("openai-api-key")
                .build();
            
            var agent = AIAgent.builder()
                .promptExecutor(promptExecutor)
                .llmModel(OpenAIModels.Chat.GPT4o)
                .systemPrompt("You are a helpful assistant.")
                .
    -->
    <!--- SUFFIX
                .build();
        }
    }
    -->
    ```java
    install(OpenTelemetry.Feature, config -> {
        // Set your service configuration
        config.setServiceInfo("my-agent-service", "1.0.0");

        // Add the Logging exporter
        config.addSpanExporter(LoggingSpanExporter.create());
    })
    ```
    <!--- KNIT exampleOpentelemetrySupportJava02.java -->

For a reference of available methods, see the sections below.

#### setServiceInfo

Sets the service information including name and version. Takes the following arguments:

| Name               | Data type | Required | Default value | Description                                                 |
|--------------------|-----------|----------|---------------|-------------------------------------------------------------|
| `serviceName`      | String    | Yes      |               | The name of the service being instrumented.                 |
| `serviceVersion`   | String    | Yes      |               | The version of the service being instrumented.              |

#### addSpanExporter

Adds a span exporter to send telemetry data to external systems. Takes the following argument:

| Name       | Data type      | Required | Default value | Description                                                                   |
|------------|----------------|----------|---------------|-------------------------------------------------------------------------------|
| `exporter` | `SpanExporter` | Yes      |               | The `SpanExporter` instance to be added to the list of custom span exporters. |

#### addSpanProcessor

Adds a span processor factory to process spans before they are exported. Takes the following argument:

| Name        | Data type                         | Required | Default value | Description                                                                                                  |
|-------------|-----------------------------------|----------|---------------|--------------------------------------------------------------------------------------------------------------|
| `processor` | `(SpanExporter) -> SpanProcessor` | Yes      |               | A function that creates a span processor for a given exporter. Lets you customize processing per exporter.   |

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

- When you call setSdk(sdk), the provided SDK is used as-is, and any custom configuration applied via addSpanExporter, addSpanProcessor, addResourceAttributes, or setSampler is ignored.
- The tracer’s instrumentation scope name/version are aligned with your service info.

| Name  | Data type          | Required | Description                           |
|-------|--------------------|----------|---------------------------------------|
| `sdk` | `OpenTelemetrySdk` | Yes      | The SDK instance to use in the agent. |

#### addMetricExporter

Adds a metric exporter to send metric data to external systems. Takes the following arguments:

| Name            | Data type        | Required | Default value | Description                                                                  |
|-----------------|------------------|----------|---------------|------------------------------------------------------------------------------|
| `exporter`      | `MetricExporter` | Yes      |               | The `MetricExporter` instance to register with a periodic metric reader.     |
| `meterInterval` | `Duration`       | No       | `1s`          | The interval between metric reads. Also available as a `java.time.Duration`. |

If no metric exporter is registered, Koog falls back to the console `LoggingMetricExporter` so that metrics are visible during local development.

#### addMetricFilter

Restricts the attribute keys that are reported for a specific metric instrument. This installs an OpenTelemetry `View` that drops any attribute not listed. Takes the following arguments:

| Name            | Data type     | Required | Default value | Description                                                 |
|-----------------|---------------|----------|---------------|-------------------------------------------------------------|
| `metricName`    | `String`      | Yes      |               | The name of the metric instrument to apply the filter to.   |
| `keysToRetain`  | `Set<String>` | Yes      |               | The attribute keys that should be retained for this metric. |

Use this to keep high-cardinality attributes (for example, request identifiers) from blowing up your metric backend while still exporting the metric itself.

### Advanced configuration

For more advanced configuration, you can also customize the following configuration options:

- Sampler: configure the sampling strategy to adjust the frequency and amount of collected data.
- Resource attributes: add more information about the process that is producing telemetry data.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
    import io.opentelemetry.api.common.AttributeKey
    import io.opentelemetry.exporter.logging.LoggingSpanExporter
    import io.opentelemetry.sdk.trace.samplers.Sampler
    val promptExecutor = simpleOpenAIExecutor("openai-api-key")
    val agent = AIAgent(
        promptExecutor = promptExecutor,
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
        
        // Set the sampler 
        setSampler(Sampler.traceIdRatioBased(0.5)) 
    
        // Add resource attributes
        addResourceAttributes(mapOf(
            AttributeKey.stringKey("custom.attribute") to "custom-value")
        )
    }
    ```
    <!--- KNIT example-opentelemetry-support-03.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry;
    import ai.koog.prompt.executor.clients.openai.OpenAIModels;
    import ai.koog.prompt.executor.model.PromptExecutor;
    import io.opentelemetry.api.common.AttributeKey;
    import io.opentelemetry.exporter.logging.LoggingSpanExporter;
    import io.opentelemetry.sdk.trace.samplers.Sampler;
    import java.util.Map;
    public class exampleOpentelemetrySupportJava03 {
        public static void main(String[] args) {
            var promptExecutor = PromptExecutor.builder()
                .openAI("openai-api-key")
                .build();
            var agent = AIAgent.builder()
                .promptExecutor(promptExecutor)
                .llmModel(OpenAIModels.Chat.GPT4o)
                .systemPrompt("You are a helpful assistant.")
                .
    -->
    <!--- SUFFIX
                .build();
        }
    }
    -->
    ```java
    install(OpenTelemetry.Feature, config -> {
        // Set your service configuration
        config.setServiceInfo("my-agent-service", "1.0.0");

        // Add the Logging exporter
        config.addSpanExporter(LoggingSpanExporter.create());

        // Set the sampler
        config.setSampler(Sampler.traceIdRatioBased(0.5));

        // Add resource attributes
        config.addResourceAttributes(Map.of(
            AttributeKey.stringKey("custom.attribute"), "custom-value"
        ));
    })
    ```
    <!--- KNIT exampleOpentelemetrySupportJava03.java -->

#### Sampler

To define a sampler, use a corresponding method of the `Sampler` class (`io.opentelemetry.sdk.trace.samplers.Sampler`)
from the `opentelemetry-java` SDK that represents the sampling strategy you want to use.

The default sampling strategy is as follows:

- `Sampler.alwaysOn()`: The default sampling strategy where every span (trace) is sampled.

For more information about available samplers and sampling strategies, see the OpenTelemetry [Sampler](https://opentelemetry.io/docs/languages/java/sdk/#sampler) documentation.

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

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
    import io.opentelemetry.api.common.AttributeKey
    val promptExecutor = simpleOpenAIExecutor("openai-api-key")
    val agent = AIAgent(
        promptExecutor = promptExecutor,
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
    addResourceAttributes(mapOf(
        AttributeKey.stringKey("custom.attribute") to "custom-value")
    )
    ```
    <!--- KNIT example-opentelemetry-support-04.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry;
    import ai.koog.prompt.executor.clients.openai.OpenAIModels;
    import ai.koog.prompt.executor.model.PromptExecutor;
    import io.opentelemetry.api.common.AttributeKey;
    import java.util.Map;
    public class exampleOpentelemetrySupportJava04 {
        public static void main(String[] args) {
            var promptExecutor = PromptExecutor.builder()
                .openAI("openai-api-key")
                .build();
            var agent = AIAgent.builder()
                .promptExecutor(promptExecutor)
                .llmModel(OpenAIModels.Chat.GPT4o)
                .systemPrompt("You are a helpful assistant.")
                .install(OpenTelemetry.Feature, config -> {
    -->
    <!--- SUFFIX
                })
                .build();
        }
    }
    -->
    ```java
    config.addResourceAttributes(Map.of(
        AttributeKey.stringKey("custom.attribute"), "custom-value"
    ));
    ```
    <!--- KNIT exampleOpentelemetrySupportJava04.java -->

## What gets traced

The OpenTelemetry feature captures the following agent activity:

- **Agent lifecycle events**: agent start, stop, errors
- **LLM interactions**: prompts, responses, token usage, latency
- **Tool calls**: execution traces for tool invocations
- **System context**: metadata such as model name, environment, Koog version

By default, the contents of LLM prompts and responses are masked in exported spans to avoid exposing sensitive data.
To include the full content, call [`setVerbose(true)`](#setverbose).

For a detailed breakdown of individual span types and attributes, see [Span types and attributes](#span-types-and-attributes).

## Span types and attributes

The OpenTelemetry feature automatically creates different types of spans to track various operations in your agent:

- **CreateAgentSpan**: created when you run an agent, closed when the agent is closed or the process is terminated.
- **InvokeAgentSpan**: the invocation of an agent.
- **StrategySpan**: the execution of an agent's strategy (the top-level execution flow).
- **NodeExecuteSpan**: the execution of a node in the agent's strategy. This is a custom, Koog-specific span.
- **SubgraphExecuteSpan**: the execution of a subgraph within the agent strategy. This is a custom, Koog-specific span.
- **InferenceSpan**: an LLM call.
- **ExecuteToolSpan**: a tool call.
- **McpClientSpan**: an MCP (Model Context Protocol) client operation. This span follows OpenTelemetry semantic conventions for MCP.

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
<!--- KNIT example-opentelemetry-support-01.txt -->

### Span attributes

Span attributes provide metadata related to a span. Each span has its set of attributes, while some spans can also
repeat attributes.

Koog supports a list of predefined attributes that follow OpenTelemetry's [Semantic conventions for generative AI events](https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-spans/). For example, the conventions define an attribute named
`gen_ai.conversation.id`, which is usually a required attribute for a span. In Koog, the value of this attribute is the
unique identifier for an agent run, that is automatically set when you call the `agent.run()` method.

In addition, Koog also includes custom, Koog-specific attributes. You can recognize most of these attributes by the
`koog.` prefix. Here are the available custom attributes:

- `koog.strategy.name`: the name of the agent strategy. A strategy is a Koog-related entity that describes the
  purpose of the agent. Used in the `StrategySpan` span.
- `koog.node.id`: the identifier (name) of the node being executed. Used in the `NodeExecuteSpan` span.
- `koog.node.input`: the input passed to the node at the beginning of execution. Present on `NodeExecuteSpan` when node starts.
- `koog.node.output`: the output produced by the node upon completion. Present on `NodeExecuteSpan` when node completes successfully.
- `koog.subgraph.id`: the identifier (name) of the subgraph being executed. Used in the `SubgraphExecuteSpan` span.
- `koog.subgraph.input`: the input passed to the subgraph at the beginning of execution. Present on `SubgraphExecuteSpan` when subgraph starts.
- `koog.subgraph.output`: the output produced by the subgraph upon completion. Present on `SubgraphExecuteSpan` when subgraph completes successfully.

### Events

A span can also have an _event_ attached to the span. Events describe a specific point in time when something relevant
happened. For example, when an LLM call started or finished. Events also have attributes and additionally include event
_body fields_.

The following event types are supported in line with OpenTelemetry's [Semantic conventions for generative AI events](https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-events/):

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
examples of event body fields, see the [OpenTelemetry documentation](https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-events/#examples). For the state of support for event body
fields in `opentelemetry-java`, see the related [GitHub issue](https://github.com/open-telemetry/semantic-conventions/issues/1870).

## Metrics

In addition to spans, the OpenTelemetry feature emits metrics that follow OpenTelemetry's [Semantic conventions for GenAI metrics](https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-metrics/). Metrics are exported through the meter provider configured via [addMetricExporter](#addmetricexporter); if no exporter is registered, a console `LoggingMetricExporter` is used by default.

The following instruments are registered:

| Name                                | Instrument | Unit    | Description                                                                                                 |
|-------------------------------------|------------|---------|-------------------------------------------------------------------------------------------------------------|
| `gen_ai.client.token.usage`         | Histogram  | `{token}` | Token usage reported for each LLM call, split by `gen_ai.token.type` (`input`/`output`).                    |
| `gen_ai.client.operation.duration`  | Histogram  | `s`     | Duration of GenAI operations — both `text_completion` (LLM calls) and `execute_tool` (tool invocations).    |
| `koog.gen_ai.client.tool.call.count`| Counter    | `{call}` | Koog-specific counter of tool calls performed by the agent, labelled by tool name and call status.          |

Explicit histogram bucket boundaries are provided as advice in line with the semantic conventions:

- `gen_ai.client.token.usage`: `[1, 4, 16, 64, 256, 1024, 4096, 16384, 65536, 262144, 1048576, 4194304, 16777216, 67108864]`
- `gen_ai.client.operation.duration`: `[0.01, 0.02, 0.04, 0.08, 0.16, 0.32, 0.64, 1.28, 2.56, 5.12, 10.24, 20.48, 40.96, 81.92]`

### gen_ai.provider.name

Every data point carries a `gen_ai.provider.name` attribute:

- For `text_completion` operations, the value is the LLM provider id (for example, `openai`, `anthropic`).
- For `execute_tool` operations, the value is `koog`, because tool execution happens in-process rather than against a third-party provider. MCP tool executions keep this value and surface MCP-specific details through separate `mcp.*` attributes on the corresponding span, so tool metrics stay at low cardinality.

### error.type

`error.type` is set only on failed `gen_ai.client.operation.duration` data points, per the GenAI semconv requirement. The value is the canonical Java class name of the error that caused the failure, so it is bounded by the exception hierarchy and safe to use as a metric dimension:

- Subclasses of `AIAgentError` — for `execute_tool` failures and tool validation failures.
- Any `Throwable` raised by the LLM client or agent runtime — for `text_completion` failures that surface through the agent-level failure hook.
- `_OTHER` — fallback when an in-flight operation is flushed at agent close without an associated error.

The attribute is not set on successful operations.

### restrictToolNameCardinality

Tool metrics are labeled with `gen_ai.tool.name`. If you expose tools whose names are dynamic or user-generated, the tool-name cardinality can grow without bound. Use `restrictToolNameCardinality` to map any name outside an allow-list to a single fallback value.

For metric-specific attribute filtering that applies to any instrument and any attribute key, use [addMetricFilter](#addmetricfilter).

## Exporters

Exporters send collected telemetry data to an OpenTelemetry Collector or other types of destinations or backend
implementations. To add an exporter, use the `addSpanExporter()` method when installing the OpenTelemetry feature. The
method takes the following argument:

| Name       | Data type    | Required | Default | Description                                                                 |
|------------|--------------|----------|---------|-----------------------------------------------------------------------------|
| `exporter` | SpanExporter | Yes      |         | The SpanExporter instance to be added to the list of custom span exporters. |

The sections below provide information about some of the most commonly used exporters from the `opentelemetry-java` SDK.

!!! note
If you do not configure any custom exporters, Koog will use a console LoggingSpanExporter by default. This helps during local development and debugging.

### Logging exporter

A logging exporter that outputs trace information to the console. `LoggingSpanExporter`
(`io.opentelemetry.exporter.logging.LoggingSpanExporter`) is a part of the `opentelemetry-java` SDK.

This type of export is useful for development and debugging purposes.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
    import io.opentelemetry.exporter.logging.LoggingSpanExporter
    val promptExecutor = simpleOpenAIExecutor("openai-api-key")
    val agent = AIAgent(
        promptExecutor = promptExecutor,
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

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry;
    import ai.koog.prompt.executor.clients.openai.OpenAIModels;
    import ai.koog.prompt.executor.model.PromptExecutor;
    import io.opentelemetry.exporter.logging.LoggingSpanExporter;
    public class exampleOpentelemetrySupportJava05 {
        public static void main(String[] args) {
            var promptExecutor = PromptExecutor.builder()
                .openAI("openai-api-key")
                .build();
            var agent = AIAgent.builder()
                .promptExecutor(promptExecutor)
                .llmModel(OpenAIModels.Chat.GPT4o)
                .systemPrompt("You are a helpful assistant.")
                .
    -->
    <!--- SUFFIX
                .build();
        }
    }
    -->
    ```java
    install(OpenTelemetry.Feature, config -> {
        // Add the logging exporter
        config.addSpanExporter(LoggingSpanExporter.create());
        // Add more exporters as needed
    })
    ```
    <!--- KNIT exampleOpentelemetrySupportJava05.java -->

### OpenTelemetry HTTP exporter

OpenTelemetry HTTP exporter (`OtlpHttpSpanExporter`) is a part of the `opentelemetry-java` SDK
(`io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter`) and sends span data to a backend through HTTP.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
    import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
    import java.util.concurrent.TimeUnit
    val promptExecutor = simpleOpenAIExecutor("openai-api-key")
    const val AUTH_STRING = ""
    val agent = AIAgent(
        promptExecutor = promptExecutor,
        llmModel = OpenAIModels.Chat.GPT4o,
        systemPrompt = "You are a helpful assistant."
    ) {
    -->
    <!--- SUFFIX
    }
    -->
    ```kotlin
    install(OpenTelemetry) {
        // Add OpenTelemetry HTTP exporter 
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
    }
    ```
    <!--- KNIT example-opentelemetry-support-06.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry;
    import ai.koog.prompt.executor.clients.openai.OpenAIModels;
    import ai.koog.prompt.executor.model.PromptExecutor;
    import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
    import java.util.concurrent.TimeUnit;
    public class exampleOpentelemetrySupportJava06 {
        public static void main(String[] args) {
            var promptExecutor = PromptExecutor.builder()
                .openAI("openai-api-key")
                .build();
            String AUTH_STRING = "";
            var agent = AIAgent.builder()
                .promptExecutor(promptExecutor)
                .llmModel(OpenAIModels.Chat.GPT4o)
                .systemPrompt("You are a helpful assistant.")
                .
    -->
    <!--- SUFFIX
                .build();
        }
    }
    -->
    ```java
    install(OpenTelemetry.Feature, config -> {
        // Add OpenTelemetry HTTP exporter
        config.addSpanExporter(
            OtlpHttpSpanExporter.builder()
                // Set the maximum time to wait for the collector to process an exported batch of spans
                .setTimeout(30, TimeUnit.SECONDS)
                // Set the OpenTelemetry endpoint to connect to
                .setEndpoint("http://localhost:3000/api/public/otel/v1/traces")
                // Add the authorization header
                .addHeader("Authorization", "Basic " + AUTH_STRING)
                .build()
        );
    })
    ```
    <!--- KNIT exampleOpentelemetrySupportJava06.java -->

### OpenTelemetry gRPC exporter

OpenTelemetry gRPC exporter (`OtlpGrpcSpanExporter`) is a part of the `opentelemetry-java` SDK
(`io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter`). It exports telemetry data to a backend through gRPC and
lets you define the host and port of the backend, collector, or endpoint that receives the data. The default port is
`4317`.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
    import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
    val promptExecutor = simpleOpenAIExecutor("openai-api-key")
    val agent = AIAgent(
        promptExecutor = promptExecutor,
        llmModel = OpenAIModels.Chat.GPT4o,
        systemPrompt = "You are a helpful assistant."
    ) {
    -->
    <!--- SUFFIX
    }
    -->
    ```kotlin
    install(OpenTelemetry) {
        // Add OpenTelemetry gRPC exporter 
        addSpanExporter(
            OtlpGrpcSpanExporter.builder()
                // Set the host and the port
                .setEndpoint("http://localhost:4317")
                .build()
        )
    }
    ```
    <!--- KNIT example-opentelemetry-support-07.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry;
    import ai.koog.prompt.executor.clients.openai.OpenAIModels;
    import ai.koog.prompt.executor.model.PromptExecutor;
    import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
    public class exampleOpentelemetrySupportJava07 {
        public static void main(String[] args) {
            var promptExecutor = PromptExecutor.builder()
                .openAI("openai-api-key")
                .build();
            var agent = AIAgent.builder()
                .promptExecutor(promptExecutor)
                .llmModel(OpenAIModels.Chat.GPT4o)
                .systemPrompt("You are a helpful assistant.")
                .
    -->
    <!--- SUFFIX
                .build();
        }
    }
    -->
    ```java
    install(OpenTelemetry.Feature, config -> {
        // Add OpenTelemetry gRPC exporter
        config.addSpanExporter(
            OtlpGrpcSpanExporter.builder()
                // Set the host and the port
                .setEndpoint("http://localhost:4317")
                .build()
        );
    })
    ```
    <!--- KNIT exampleOpentelemetrySupportJava07.java -->

## Integration with Langfuse

Langfuse provides trace visualization and analytics for LLM/agent workloads.

You can configure Koog to export OpenTelemetry traces directly to Langfuse using a helper function:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor 
    val promptExecutor = simpleOpenAIExecutor("openai-api-key")
    val agent = AIAgent(
        promptExecutor = promptExecutor,
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

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry;
    import ai.koog.prompt.executor.clients.openai.OpenAIModels;
    import ai.koog.prompt.executor.model.PromptExecutor;
    public class exampleOpentelemetrySupportJava08 {
        public static void main(String[] args) {
            var promptExecutor = PromptExecutor.builder()
                .openAI("openai-api-key")
                .build();
            var agent = AIAgent.builder()
                .promptExecutor(promptExecutor)
                .llmModel(OpenAIModels.Chat.GPT4o)
                .systemPrompt("You are a helpful assistant.")
                .
    -->
    <!--- SUFFIX
                .build();
        }
    }
    -->
    ```java
    install(OpenTelemetry.Feature, config -> {
        config.addLangfuseExporter(
            "https://cloud.langfuse.com",
            "...",
            "...",
            null,
            null
        );
    })
    ```
    <!--- KNIT exampleOpentelemetrySupportJava08.java -->

Please read the [full documentation](opentelemetry-langfuse-exporter.md) about integration with Langfuse.

## Integration with W&B Weave

W&B Weave provides trace visualization and analytics for LLM/agent workloads. Integration with W&B Weave can be configured via a predefined exporter:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
    val promptExecutor = simpleOpenAIExecutor("openai-api-key")
    val agent = AIAgent(
        promptExecutor = promptExecutor,
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

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry;
    import ai.koog.prompt.executor.clients.openai.OpenAIModels;
    import ai.koog.prompt.executor.model.PromptExecutor;
    public class exampleOpentelemetrySupportJava09 {
        public static void main(String[] args) {
            var promptExecutor = PromptExecutor.builder()
                .openAI("openai-api-key")
                .build();
            var agent = AIAgent.builder()
                .promptExecutor(promptExecutor)
                .llmModel(OpenAIModels.Chat.GPT4o)
                .systemPrompt("You are a helpful assistant.")
                .
    -->
    <!--- SUFFIX
                .build();
        }
    }
    -->
    ```java
    install(OpenTelemetry.Feature, config -> {
        config.addWeaveExporter(
            "https://trace.wandb.ai",
            "my-team",
            "my-project",
            "..."
        );
    })
    ```
    <!--- KNIT exampleOpentelemetrySupportJava09.java -->

Please read the [full documentation](opentelemetry-weave-exporter.md) about integration with W&B Weave.

## Integration with Datadog

Datadog provides monitoring, observability, and analytics for cloud-scale applications. Integration with Datadog can be configured via a predefined exporter:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
    import ai.koog.agents.features.opentelemetry.integration.datadog.addDatadogExporter
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
    val promptExecutor = simpleOpenAIExecutor("openai-api-key")
    val agent = AIAgent(
        promptExecutor = promptExecutor,
        llmModel = OpenAIModels.Chat.GPT4o,
        systemPrompt = "You are a helpful assistant."
    ) {
    -->
    <!--- SUFFIX
    }
    -->
    ```kotlin
    install(OpenTelemetry) {
        addDatadogExporter(
            datadogApiKey = "...",
            datadogSite = "datadoghq.com"
        )
    }
    ```
    <!--- KNIT example-opentelemetry-support-10.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry;
    import ai.koog.prompt.executor.clients.openai.OpenAIModels;
    import ai.koog.prompt.executor.model.PromptExecutor;
    public class exampleOpentelemetrySupportJava10 {
        public static void main(String[] args) {
            var promptExecutor = PromptExecutor.builder()
                .openAI("openai-api-key")
                .build();
            var agent = AIAgent.builder()
                .promptExecutor(promptExecutor)
                .llmModel(OpenAIModels.Chat.GPT4o)
                .systemPrompt("You are a helpful assistant.")
                .
    -->
    <!--- SUFFIX
                .build();
        }
    }
    -->
    ```java
    install(OpenTelemetry.Feature, config -> {
        config.addDatadogExporter(
            "...",           // datadogApiKey
            "datadoghq.com", // datadogSite
            null,
            null
        );
    })
    ```
    <!--- KNIT exampleOpentelemetrySupportJava10.java -->

Please read the [full documentation](opentelemetry-datadog-exporter.md) about integration with Datadog.

## Integration with Jaeger

Jaeger is a popular distributed tracing system that works with OpenTelemetry. The `opentelemetry` directory within
`examples` in the Koog repository includes an example of using OpenTelemetry with Jaeger and Koog agents.

### Prerequisites

To test OpenTelemetry with Koog and Jaeger, start the Jaeger OpenTelemetry all-in-one process using the provided
`docker-compose.yaml` file, by running the following command:

```bash
docker compose up -d
```
<!--- KNIT example-opentelemetry-support-02.txt -->

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
<!--- KNIT example-opentelemetry-support-03.txt -->

To access the Jaeger UI and view your traces, open `http://localhost:16686`.

### Example

To export telemetry data for use in Jaeger, the example uses `LoggingSpanExporter`
(`io.opentelemetry.exporter.logging.LoggingSpanExporter`) and `OtlpGrpcSpanExporter`
(`io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter`) from the `opentelemetry-java` SDK.

Here is the full code sample:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
    import ai.koog.utils.io.use
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
    import io.opentelemetry.exporter.logging.LoggingSpanExporter
    import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
    import kotlinx.coroutines.runBlocking
    val promptExecutor = simpleOpenAIExecutor("openai-api-key")
    -->
    <!--- SUFFIX
    --> 
    ```kotlin
    fun main() = runBlocking {
        val agent = AIAgent(
            promptExecutor = promptExecutor,
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
            }
        }

        agent.use { agent ->
            println("Running the agent with OpenTelemetry tracing...")

            val result = agent.run("Tell me a joke about programming")

            println("Agent run completed with result: '$result'." +
                    "\nCheck Jaeger UI at http://localhost:16686 to view traces")
        }
    }
    ```
    <!--- KNIT example-opentelemetry-support-11.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry;
    import ai.koog.prompt.executor.clients.openai.OpenAIModels;
    import ai.koog.prompt.executor.model.PromptExecutor;
    import io.opentelemetry.exporter.logging.LoggingSpanExporter;
    import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
    public class exampleOpentelemetrySupportJava11 {
        static PromptExecutor promptExecutor = PromptExecutor.builder()
            .openAI("openai-api-key")
            .build();
    -->
    <!--- SUFFIX
    }
    -->
    ```java
    public static void main(String[] args) {
        var agent = AIAgent.builder()
            .promptExecutor(promptExecutor)
            .llmModel(OpenAIModels.Chat.O4Mini)
            .systemPrompt("You are a code assistant. Provide concise code examples.")
            .install(OpenTelemetry.Feature, config -> {
                // Add a console logger for local debugging
                config.addSpanExporter(LoggingSpanExporter.create());

                // Send traces to OpenTelemetry collector
                config.addSpanExporter(
                    OtlpGrpcSpanExporter.builder()
                        .setEndpoint("http://localhost:4317")
                        .build()
                );
            })
            .build();

        System.out.println("Running the agent with OpenTelemetry tracing...");

        var result = agent.run("Tell me a joke about programming");

        System.out.println(
            "Agent run completed with result: '" + result + "'." +
                "\nCheck Jaeger UI at http://localhost:16686 to view traces"
        );
    }
    ```
    <!--- KNIT exampleOpentelemetrySupportJava11.java -->

## Troubleshooting

### Common issues

1. **No traces appearing in the backend**
    - Confirm all required environment variables are set and exported in your shell.
    - Verify that your API key or secret is valid, has not been revoked, and has write/trace permissions.
    - Ensure the service is running and the OpenTelemetry port (4317) is accessible.
    - Check that the exporter is configured with the correct endpoint.
    - Wait a few seconds after agent execution — traces may not appear instantly.

2. **Connection issues**
    - Confirm your environment can reach the exporter's intake endpoint.
    - Check for firewall or proxy settings that block outbound HTTPS traffic.

3. **Missing spans or incomplete traces**
    - Verify that the agent execution completes successfully.
    - Ensure that you're not closing the application too quickly after agent execution.
    - Add a delay after agent execution to allow time for spans to be exported.

4. **Excessive number of spans**
    - Consider using a different sampling strategy by configuring the `sampler` property.
    - For example, use `Sampler.traceIdRatioBased(0.1)` to sample only 10% of traces.

5. **Span adapters override each other**
    - Currently, the OpenTelemetry agent feature does not support applying multiple span adapters [KG-265](https://youtrack.jetbrains.com/issue/KG-265/Adding-Weave-exporter-breaks-Langfuse-exporter).

## MCP (Model Context Protocol) telemetry support

Koog provides comprehensive OpenTelemetry instrumentation for MCP operations following the [official OpenTelemetry semantic conventions for MCP](https://github.com/open-telemetry/semantic-conventions/pull/2083).

### Overview

The MCP telemetry support includes:

- **Automatic enrichment** of tool execution spans with MCP-specific attributes
- **Client-side instrumentation** for MCP client operations (tools/call)
- **Full semantic convention compliance** with all required, conditionally required, and recommended attributes

### MCP attributes

MCP telemetry follows OpenTelemetry semantic conventions and includes the following attribute groups:

**Required attributes:**
- `mcp.method.name`: The MCP method name (e.g., "tools/call")

**Conditionally required attributes:**
- `gen_ai.tool.name`: When operation involves a tool
- `gen_ai.prompt.name`: When operation involves a prompt
- `jsonrpc.request.id`: When executing a request (not a notification)
- `error.type`: When operation fails

**Recommended attributes:**
- `mcp.session.id`: Session identifier
- `mcp.protocol.version`: MCP protocol version (e.g., "2025-06-18")
- `network.transport`: Transport type ("pipe" for stdio, "tcp" for HTTP)
- `server.address` and `server.port`: For client operations

### Span naming convention

MCP spans follow the naming convention: `{mcp.method.name} {target}`

Where `{target}` is the tool name or prompt name when applicable. Examples:
- `"tools/call search"` - calling a tool named "search"

### Best practices

- **Always set session IDs** when working with persistent MCP sessions to enable session tracking
- **Propagate request IDs** from JSON-RPC requests for complete request tracing
- **Monitor metrics** to identify performance bottlenecks in MCP operations

### Example: Full MCP client with telemetry

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
    import ai.koog.agents.mcp.McpToolRegistryProvider
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
    import ai.koog.utils.io.use
    import io.opentelemetry.exporter.logging.LoggingSpanExporter
    import kotlinx.coroutines.runBlocking
    fun main() {
        runBlocking {
            val promptExecutor = simpleOpenAIExecutor("openai-api-key")
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```kotlin
    // Create MCP tools registry
    val toolRegistry = McpToolRegistryProvider.fromSseUrl("http://localhost:3000")
    
    // Create agent with OpenTelemetry enabled and pass the tool registry
    val agent = AIAgent(
        promptExecutor = promptExecutor,
        llmModel = OpenAIModels.Chat.GPT4o,
        systemPrompt = "You are a helpful assistant.",
        toolRegistry = toolRegistry
    ) {
        install(OpenTelemetry) {
            setServiceInfo("mcp-agent-service", "1.0.0")
            addSpanExporter(LoggingSpanExporter.create())
        }
    }
    
    // Run agent - MCP tool calls will be automatically instrumented
    agent.use {
        it.run("Use the search tool to find information")
    }
    ```
    <!--- KNIT example-opentelemetry-support-12.kt -->

This setup provides complete observability for MCP operations with minimal code changes, following OpenTelemetry best practices and semantic conventions.
