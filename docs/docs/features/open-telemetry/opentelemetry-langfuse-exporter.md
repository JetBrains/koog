# Langfuse exporter

Koog emits agent traces using [OpenTelemetry](https://opentelemetry.io/), an open standard for observability data.
To send those traces to [Langfuse](https://langfuse.com/), Koog includes a built-in OpenTelemetry exporter —
no manual instrumentation required.

Once connected, Langfuse's [OpenTelemetry support](https://langfuse.com/integrations/native/opentelemetry) lets you visualize,
analyze, and debug how your agents interact with LLMs, tools, and external APIs.

---

## Setup instructions

1. Create a Langfuse project using the [setup guide](https://langfuse.com/docs/get-started#create-new-project-in-langfuse).
2. Get your `public key` and `secret key` from [Organization Settings > API Keys](https://langfuse.com/faq/all/where-are-langfuse-api-keys).
3. Provide the host, public key, and secret key — either as parameters to [`addLangfuseExporter()`](https://api.koog.ai/agents/agents-features/agents-features-opentelemetry/ai.koog.agents.features.opentelemetry.integration.langfuse/add-langfuse-exporter.html), or via environment variables:

```bash
   export LANGFUSE_HOST="https://cloud.langfuse.com"
   export LANGFUSE_PUBLIC_KEY="<your-public-key>"
   export LANGFUSE_SECRET_KEY="<your-secret-key>"
```
<!--- KNIT example-langfuse-exporter-01.txt -->

## Configuration

Install the **OpenTelemetry feature** and call [`addLangfuseExporter()`](https://api.koog.ai/agents/agents-features/agents-features-opentelemetry/ai.koog.agents.features.opentelemetry.integration.langfuse/add-langfuse-exporter.html) to enable Langfuse export.

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
                addLangfuseExporter()
            }
        }
    
        println("Running agent with Langfuse tracing")
    
        val result = agent.run("Tell me a joke about programming")
        println("Result: $result\nSee traces on the Langfuse instance")
    }
    ```
    <!--- KNIT example-langfuse-exporter-01.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry;
    import ai.koog.prompt.executor.clients.openai.OpenAIModels;
    import ai.koog.prompt.executor.model.PromptExecutor;
    public class exampleLangfuseExporterJava01 {
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
                config.addLangfuseExporter()
            )
            .build();

        System.out.println("Running agent with Langfuse tracing");

        var result = agent.run("Tell me a joke about programming");
        System.out.println("Result: " + result + "\nSee traces on the Langfuse instance");
    }
    ```
    <!--- KNIT exampleLangfuseExporterJava01.java -->

## Trace attributes

When Koog sends agent activity to Langfuse, it does so as a series of *spans* — individual records of work, such as
an LLM call or a tool execution. Related spans are grouped into a *trace*, which represents a complete agent run
from start to finish.

[`addLangfuseExporter()`](https://api.koog.ai/agents/agents-features/agents-features-opentelemetry/ai.koog.agents.features.opentelemetry.integration.langfuse/add-langfuse-exporter.html) accepts a `traceAttributes` parameter — a list of key-value pairs attached to the
root of each trace. These enable Langfuse-specific features such as sessions, environments, and tags, making it
easy to filter and group traces in the Langfuse UI.

For the full list of supported attributes, see [Langfuse OpenTelemetry docs](https://langfuse.com/integrations/native/opentelemetry#trace-level-attributes).

Common attributes to include:

- **Session ID** (`langfuse.session.id`): Groups related traces for aggregated metrics, cost analysis, and scoring
- **Environment** (`langfuse.environment`): Isolates production traces from development and staging
- **Tags** (`langfuse.trace.tags`): Labels traces with feature names, experiment IDs, or customer segments (array of strings)

### Example with session and tags

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
    import kotlinx.coroutines.runBlocking
    import java.util.UUID
    val promptExecutor = simpleOpenAIExecutor("openai-api-key")
    -->
    ```kotlin
    fun main() = runBlocking {
        val sessionId = UUID.randomUUID().toString()
    
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            systemPrompt = "You are a helpful assistant."
        ) {
            install(OpenTelemetry) {
                addLangfuseExporter(
                    traceAttributes = listOf(
                        CustomAttribute("langfuse.session.id", sessionId),
                        CustomAttribute("langfuse.trace.tags", listOf("chat", "kotlin", "production"))
                    )
                )
            }
        }
    
        println("Running agent with Langfuse tracing")

        // Multiple runs with the same session ID will be grouped in Langfuse
        agent.run("What is Kotlin?")
        agent.run("Show me a coroutine example")
    }
    ```
    <!--- KNIT example-langfuse-exporter-02.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute;
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry;
    import ai.koog.prompt.executor.clients.openai.OpenAIModels;
    import ai.koog.prompt.executor.model.PromptExecutor;
    import java.util.List;
    import java.util.UUID;
    public class exampleLangfuseExporterJava02 {
        static PromptExecutor promptExecutor = PromptExecutor.builder()
            .openAI("openai-api-key")
            .build();
    -->
    <!--- SUFFIX
    }
    -->
    ```java
    public static void main(String[] args) {
        var sessionId = UUID.randomUUID().toString();

        var agent = AIAgent.builder()
            .promptExecutor(promptExecutor)
            .systemPrompt("You are a helpful assistant.")
            .llmModel(OpenAIModels.Chat.GPT4oMini)
            .install(OpenTelemetry.Feature, config ->
                config.addLangfuseExporter(
                    null,           // Langfuse host (falls back to LANGFUSE_HOST)
                    null,           // Public key (falls back to LANGFUSE_PUBLIC_KEY)
                    null,           // Secret key (falls back to LANGFUSE_SECRET_KEY)
                    null,           // Timeout (uses default)
                    List.of(
                        new CustomAttribute("langfuse.session.id", sessionId),
                        new CustomAttribute("langfuse.trace.tags", List.of("chat", "java", "production"))
                    )
                ))
            .build();

        System.out.println("Running agent with Langfuse tracing");

        // Multiple runs with the same session ID will be grouped in Langfuse
        agent.run("How to setup Langfuse integration in Koog agent?");
        agent.run("Show me a Java API example");
    }
    ```
    <!--- KNIT exampleLangfuseExporterJava02.java -->

## What gets traced

The Langfuse exporter captures the same activity as Koog’s general OpenTelemetry integration.
It also captures span attributes required by Langfuse to show [Agent Graphs](https://langfuse.com/docs/observability/features/agent-graphs).

For the full list of captured spans and how to include LLM prompt and response content, see [What gets traced](index.md#what-gets-traced).

When visualized in Langfuse, the trace appears as follows:
![Langfuse traces](../../img/opentelemetry-langfuse-exporter-light.png#only-light)
![Langfuse traces](../../img/opentelemetry-langfuse-exporter-dark.png#only-dark)

For more details on Langfuse OpenTelemetry tracing, see:  
[Langfuse OpenTelemetry Docs](https://langfuse.com/integrations/native/opentelemetry#opentelemetry-endpoint).

---

## Troubleshooting

- **No traces**: confirm `LANGFUSE_HOST`, `LANGFUSE_PUBLIC_KEY`, and `LANGFUSE_SECRET_KEY` are set, and that the key pair belongs to the correct project.
- **Connection issues**: if running self-hosted Langfuse, confirm `LANGFUSE_HOST` is reachable from your environment.

For general troubleshooting, see [Troubleshooting](index.md#troubleshooting).
