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
3. Provide the host, public key, and secret key — either as parameters to [`addLangfuseExporter()`](api:agents-features-opentelemetry::ai.koog.agents.features.opentelemetry.integration.langfuse.addLangfuseExporter), or via environment variables:

```bash
   export LANGFUSE_HOST="https://cloud.langfuse.com"
   export LANGFUSE_PUBLIC_KEY="<your-public-key>"
   export LANGFUSE_SECRET_KEY="<your-secret-key>"
```
<!--- KNIT example-langfuse-exporter-01.txt -->

## Configuration

Install the **OpenTelemetry feature** and call [`addLangfuseExporter()`](api:agents-features-opentelemetry::ai.koog.agents.features.opentelemetry.integration.langfuse.addLangfuseExporter) to enable Langfuse export.

### Basic example

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
    import ai.koog.agents.features.opentelemetry.integration.langfuse.addLangfuseExporter
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
    import ai.koog.agents.features.opentelemetry.integration.langfuse.LangfuseKt;
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
                LangfuseKt.addLangfuseExporter(config)
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

[`addLangfuseExporter()`](api:agents-features-opentelemetry::ai.koog.agents.features.opentelemetry.integration.langfuse.addLangfuseExporter) accepts a `traceAttributes` parameter — a list of key-value pairs attached to the
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
    import ai.koog.agents.features.opentelemetry.integration.langfuse.addLangfuseExporter
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

    !!! note
        Setting `traceAttributes` from Java is currently not supported because the underlying Kotlin function carries a [`kotlin.time.Duration`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.time/-duration/) parameter (a value class) that causes JVM-name mangling on all overloads including parameters after it. Use the Kotlin example above when you need `traceAttributes`.

## What gets traced

The Langfuse exporter captures the same activity as Koog’s general OpenTelemetry integration.
It also captures span attributes required by Langfuse to show [Agent Graphs](https://langfuse.com/docs/observability/features/agent-graphs).

For the full list of captured spans and how to include LLM prompt and response content, see [What gets traced](index.md#what-gets-traced).

When visualized in Langfuse, the trace appears as follows:
![Langfuse traces](../../img/opentelemetry-langfuse-exporter-light.png#only-light)
![Langfuse traces](../../img/opentelemetry-langfuse-exporter-dark.png#only-dark)

For more details on Langfuse OpenTelemetry tracing, see:  
[Langfuse OpenTelemetry Docs](https://langfuse.com/integrations/native/opentelemetry#opentelemetry-endpoint).

### Langfuse v4

Langfuse v4 is observations-first: filtering and evaluation run per observation, and the request and response of a run
are read from the root observation of the trace. The exporter supports this data model without extra configuration:

- Every request carries the `x-langfuse-ingestion-version: 4` header, so Langfuse processes the spans on its real-time
  v4 path instead of the delayed legacy one. Older self-hosted Langfuse versions ignore the header.
- The root observation of each trace (the `create_agent` span) carries the input and output of the whole run as
  `langfuse.observation.input` and `langfuse.observation.output`, so observation-level evaluators that target the root
  have something to read. Both values use the same message shape as `gen_ai.prompt` and `gen_ai.completion`.
- Every node and subgraph observation carries its input and output as `langfuse.observation.input` and
  `langfuse.observation.output`, copied from the Koog-specific `koog.node.*` and `koog.subgraph.*` attributes that
  Langfuse does not read on its own. The values are masked unless verbose mode is on.
- Every observation resolves to the session of its agent run. Langfuse resolves the session of each observation on its
  own, with no inheritance from the parent. Koog spans carry `gen_ai.conversation.id` set to the run ID wherever the
  GenAI semantic conventions define it, which excludes `create_agent`, so the exporter sets `langfuse.session.id` on
  that root observation instead. A `langfuse.session.id` trace attribute overrides the run ID on every observation.

Two Langfuse conventions are worth knowing when reading the result:

- Langfuse ignores model, usage, and cost on every `invoke_agent` observation, so that aggregated agent usage is never
  counted twice. Token counts and costs of a run are read from its `chat` generations.
- The trace name falls back to the name of the root observation, which is `create_agent <agent id>`. Pass a
  `langfuse.trace.name` trace attribute to override it.

---

## Troubleshooting

- **No traces**: confirm `LANGFUSE_HOST`, `LANGFUSE_PUBLIC_KEY`, and `LANGFUSE_SECRET_KEY` are set, and that the key pair belongs to the correct project.
- **Connection issues**: if running self-hosted Langfuse, confirm `LANGFUSE_HOST` is reachable from your environment.

For general troubleshooting, see [Troubleshooting](index.md#troubleshooting).
