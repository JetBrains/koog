# Datadog exporter

Koog emits agent traces using [OpenTelemetry](https://opentelemetry.io/), an open standard for observability data.
To ship those traces to [Datadog](https://www.datadoghq.com/), Koog includes a built-in OpenTelemetry exporter —
no manual instrumentation required.

Once connected, Datadog's [OpenTelemetry support](https://docs.datadoghq.com/opentelemetry/) lets you visualize,
analyze, and debug how your agents interact with LLMs, tools, and external APIs.

---

## Setup instructions

1. Create a Datadog account at [https://www.datadoghq.com/](https://www.datadoghq.com/)

2. Get your API key from [Organization Settings > API Keys](https://app.datadoghq.com/organization-settings/api-keys)

3. Provide your API key — either as a parameter to [`addDatadogExporter()`](https://api.koog.ai/agents/agents-features/agents-features-opentelemetry/ai.koog.agents.features.opentelemetry.integration.datadog/add-datadog-exporter.html), or via an environment variable:
```bash
export DD_API_KEY="<your-api-key>"
```
4. (Optional) To use a Datadog region other than US1 (`datadoghq.com`), pass the site as a parameter to [`addDatadogExporter()`](https://api.koog.ai/agents/agents-features/agents-features-opentelemetry/ai.koog.agents.features.opentelemetry.integration.datadog/add-datadog-exporter.html), or set an environment variable:
```bash
export DD_SITE="datadoghq.eu"
```
Supported sites:

| Site | Region |
|------|--------|
| `datadoghq.com` | US1 (default) |
| `datadoghq.eu` | EU1 |
| `us3.datadoghq.com` | US3 |
| `us5.datadoghq.com` | US5 |
| `ap1.datadoghq.com` | AP1 (Japan) |

<!--- KNIT example-datadog-exporter-01.txt -->

## Configuration

To enable Datadog export, install the **OpenTelemetry feature** and call [`addDatadogExporter()`](https://api.koog.ai/agents/agents-features/agents-features-opentelemetry/ai.koog.agents.features.opentelemetry.integration.datadog/add-datadog-exporter.html).

### Basic example

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
    <!--- KNIT example-datadog-exporter-01.kt -->

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
    <!--- KNIT exampleDatadogExporterJava01.java -->

## Trace attributes

When Koog sends agent activity to Datadog, it does so as a series of *spans* — individual records of work, such as
an LLM call or a tool execution. Related spans are grouped into a *trace*, which represents a complete agent run
from start to finish.

[`addDatadogExporter()`](https://api.koog.ai/agents/agents-features/agents-features-opentelemetry/ai.koog.agents.features.opentelemetry.integration.datadog/add-datadog-exporter.html) accepts a `traceAttributes` parameter — a map of key-value pairs describing
the application emitting the traces. These are attached to every span, making it easy to filter and group traces in
Datadog by properties such as environment or version.

Common attributes to include:

- **env**: Environment name (for example, `production`, `staging`, or `development`)
- **service.name**: Name of your service or application
- **version**: Application version, useful for comparing behavior across deployments

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
    <!--- KNIT example-datadog-exporter-02.kt -->

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
    <!--- KNIT exampleDatadogExporterJava02.java -->

## Custom exporter wrapping

Use [`buildDatadogExporter()`](https://api.koog.ai/agents/agents-features/agents-features-opentelemetry/ai.koog.agents.features.opentelemetry.integration.datadog/build-datadog-exporter.html) when you need direct access to the exporter object to wrap it with additional processing logic before registering it.
For example, use `SpanExporter.composite()` to send traces to multiple backends at once:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
    import ai.koog.agents.features.opentelemetry.integration.datadog.buildDatadogExporter
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
    import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
    import io.opentelemetry.sdk.trace.export.SpanExporter
    import kotlinx.coroutines.runBlocking
    val promptExecutor = simpleOpenAIExecutor("openai-api-key")
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
        val datadogExporter = buildDatadogExporter()
        val localExporter = OtlpHttpSpanExporter.builder()
            .setEndpoint("http://localhost:4318/v1/traces")
            .build()
        addSpanExporter(SpanExporter.composite(datadogExporter, localExporter))
    }
    ```
    <!--- KNIT example-datadog-exporter-03.kt -->

## What gets traced

The Datadog exporter captures the same activity as Koog's general OpenTelemetry integration.
For the full list of captured spans and how to include LLM prompt and response content, see [What gets traced](index.md#what-gets-traced).

For more details on Datadog's OpenTelemetry support, see [Datadog OTLP API Intake](https://docs.datadoghq.com/opentelemetry/guide/otlp_api/).

---

## Troubleshooting

- **No traces**: confirm `DD_API_KEY` and `DD_SITE` are set correctly (see [Setup instructions](#setup-instructions)).
- **Authentication errors**: verify your key is active in [Organization Settings > API Keys](https://app.datadoghq.com/organization-settings/api-keys).
- **Connection issues**: confirm your environment can reach `https://otlp.<DD_SITE>/v1/traces` — for example, `https://otlp.datadoghq.com/v1/traces` for US1.

For general troubleshooting, see [Troubleshooting](index.md#troubleshooting).
