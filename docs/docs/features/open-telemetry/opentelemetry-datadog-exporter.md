# Datadog exporter

Koog provides built-in support for exporting agent traces to [Datadog](https://www.datadoghq.com/), a monitoring and analytics platform with dedicated LLM Observability capabilities.
With Datadog integration, you can visualize, analyze, and debug how your Koog agents interact with LLMs, APIs, and other components.

For background on Koog's OpenTelemetry support, see the [OpenTelemetry support](https://docs.koog.ai/opentelemetry-support/).

---

## Setup instructions

1) Create a Datadog account at [https://www.datadoghq.com/](https://www.datadoghq.com/)

2) Get your API key from [Organization Settings > API Keys](https://app.datadoghq.com/organization-settings/api-keys)

3) Pass the Datadog API key to the Datadog exporter.
This can be done by providing it as a parameter to the `addDatadogExporter()` function, or by setting an environment variable:

```bash
export DD_API_KEY="<your-api-key>"
```

4) (Optional) Configure the Datadog site. Datadog operates in multiple regions. By default, the exporter sends traces to `datadoghq.com` (US1 region).
To use a different region, set the `DD_SITE` environment variable or pass the `datadogSite` parameter to `addDatadogExporter()`:

```bash
export DD_SITE="datadoghq.eu"
```

Common site values:

| Site | Region |
|------|--------|
| `datadoghq.com` | US1 (default) |
| `datadoghq.eu` | EU1 |
| `us3.datadoghq.com` | US3 |
| `us5.datadoghq.com` | US5 |
| `ap1.datadoghq.com` | AP1 (Japan) |

<!--- KNIT example-datadog-exporter-01.txt -->

## Configuration

To enable Datadog export, install the **OpenTelemetry feature** and add the `DatadogExporter`.
The exporter uses `OtlpHttpSpanExporter` under the hood to send traces directly to Datadog's OTLP intake endpoint.

### Example: agent with Datadog tracing

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
    import kotlinx.coroutines.runBlocking
    val promptExecutor = simpleOpenAIExecutor("openai-api-key")
    -->
    ```kotlin
    fun main() = runBlocking {
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            systemPrompt = "You are a code assistant. Provide concise code examples."
        ) {
            install(OpenTelemetry) {
                addDatadogExporter()
            }
        }

        println("Running agent with Datadog tracing")

        val result = agent.run("Tell me a joke about programming")
        println("Result: $result\nSee traces in Datadog LLM Observability")
    }
    ```
    <!--- TODO: Enable KNIT after PR #1591 is merged: KNIT example-datadog-exporter-01.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry;
    import ai.koog.prompt.executor.clients.openai.OpenAIModels;
    import ai.koog.prompt.executor.model.PromptExecutor;
    public class exampleDatadogExporterJava01 {
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
            .llmModel(OpenAIModels.Chat.GPT4oMini)
            .systemPrompt("You are a code assistant. Provide concise code examples.")
            .install(OpenTelemetry.Feature, config ->
                config.addDatadogExporter()
            )
            .build();

        System.out.println("Running agent with Datadog tracing");

        var result = agent.run("Tell me a joke about programming");
        System.out.println("Result: " + result + "\nSee traces in Datadog LLM Observability");
    }
    ```
    <!--- TODO: Enable KNIT after PR #1591 is merged: KNIT exampleDatadogExporterJava01.java -->

## Trace attributes

The `addDatadogExporter` function supports a `traceAttributes` parameter that accepts a map of resource-level attributes.
These attributes are added to all exported spans and are useful for tagging traces with application-specific metadata.

Common attributes:
- **env**: Environment name (e.g., `production`, `staging`, `development`)
- **service.name**: Name of your service or application
- **version**: Application version for tracking deployments

### Example with trace attributes

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
    import kotlinx.coroutines.runBlocking
    val promptExecutor = simpleOpenAIExecutor("openai-api-key")
    -->
    ```kotlin
    fun main() = runBlocking {
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            systemPrompt = "You are a helpful assistant."
        ) {
            install(OpenTelemetry) {
                addDatadogExporter(
                    datadogSite = "datadoghq.eu",  // Use EU region
                    traceAttributes = mapOf(
                        "env" to "production",
                        "service.name" to "my-agent",
                        "version" to "1.0.0"
                    )
                )
            }
        }

        println("Running agent with Datadog tracing")

        agent.run("What is Kotlin?")
    }
    ```
    <!--- TODO: Enable KNIT after PR #1591 is merged: KNIT example-datadog-exporter-02.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry;
    import ai.koog.prompt.executor.clients.openai.OpenAIModels;
    import ai.koog.prompt.executor.model.PromptExecutor;
    import java.util.Map;
    public class exampleDatadogExporterJava02 {
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
            .systemPrompt("You are a helpful assistant.")
            .llmModel(OpenAIModels.Chat.GPT4oMini)
            .install(OpenTelemetry.Feature, config ->
                config.addDatadogExporter(
                    null,                           // Use DD_API_KEY env var
                    "datadoghq.eu",                 // Use EU region
                    null,                           // Default timeout
                    Map.of(
                        "env", "production",
                        "service.name", "my-agent",
                        "version", "1.0.0"
                    )
                ))
            .build();

        System.out.println("Running agent with Datadog tracing");

        agent.run("What is Kotlin?");
    }
    ```
    <!--- TODO: Enable KNIT after PR #1591 is merged: KNIT exampleDatadogExporterJava02.java -->

## Custom exporter wrapping

The `buildDatadogExporter()` function is available if you need to wrap the exporter with custom decorators before registering it:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
    import io.opentelemetry.sdk.trace.export.SpanExporter
    import kotlinx.coroutines.runBlocking
    val promptExecutor = simpleOpenAIExecutor("openai-api-key")
    class MyCustomSpanExporter(private val delegate: SpanExporter) : SpanExporter by delegate
    fun main() = runBlocking {
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            systemPrompt = "You are a helpful assistant."
        ) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```kotlin
    install(OpenTelemetry) {
        val exporter = buildDatadogExporter()
        val wrapped = MyCustomSpanExporter(exporter) // e.g. attribute post-processing
        addSpanExporter(wrapped)
    }
    ```
    <!--- TODO: Enable KNIT after PR #1591 is merged: KNIT example-datadog-exporter-04.kt -->

## What gets traced

When enabled, the Datadog exporter captures the same spans as Koog's general OpenTelemetry integration, including:

- **Agent lifecycle events**: agent start, stop, errors
- **LLM interactions**: prompts, responses, token usage, latency
- **Tool calls**: execution traces for tool invocations
- **System context**: metadata such as model name, environment, Koog version

The exporter includes the `dd-otlp-source: llmobs` header to route spans to Datadog's LLM Observability product.

For security reasons, some content of OpenTelemetry spans is masked by default.
To make the content available in Datadog, use the [setVerbose](opentelemetry-support.md#setverbose) method in the OpenTelemetry configuration and set its `verbose` argument to `true` as follows:

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
        addDatadogExporter()
        setVerbose(true)
    }
    ```
    <!--- TODO: Enable KNIT after PR #1591 is merged: KNIT example-datadog-exporter-03.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry;
    import ai.koog.prompt.executor.clients.openai.OpenAIModels;
    import ai.koog.prompt.executor.model.PromptExecutor;
    public class exampleDatadogExporterJava03 {
        public static void main(String[] args) {
            var promptExecutor = PromptExecutor.builder()
                .openAI("openai-api-key")
                .build();
            var agent = AIAgent.builder()
                .promptExecutor(promptExecutor)
                .systemPrompt("You are a helpful assistant.")
                .llmModel(OpenAIModels.Chat.GPT4oMini)
                .
    -->
    <!--- SUFFIX
            .build();
        }
    }
    -->
    ```java
    install(OpenTelemetry.Feature, config -> {
        config.addDatadogExporter();
        config.setVerbose(true);
    })
    ```
    <!--- TODO: Enable KNIT after PR #1591 is merged: KNIT exampleDatadogExporterJava03.java -->

For more details on Datadog's LLM Observability and OpenTelemetry support, see:

- [Datadog LLM Observability Docs](https://docs.datadoghq.com/llm_observability/)
- [Datadog OTLP API Intake](https://docs.datadoghq.com/opentelemetry/guide/otlp_api/)

---

## Troubleshooting

### No traces appear in Datadog
- Double-check that `DD_API_KEY` is set in your environment.
- Verify that you're using the correct `DD_SITE` for your Datadog region (`datadoghq.com` for US, `datadoghq.eu` for EU).
- Ensure that your API key has the necessary permissions to send traces.

### Authentication errors
- Check that your `DD_API_KEY` is valid and active.
- API keys can be verified in [Organization Settings > API Keys](https://app.datadoghq.com/organization-settings/api-keys).

### Connection issues
- Make sure your environment has network access to the Datadog OTLP intake endpoint (`https://otlp.<site>/v1/traces`).
- Check for any firewall or proxy settings that might block outbound connections.
