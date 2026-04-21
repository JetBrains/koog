# W&B Weave exporter

Koog emits agent traces using [OpenTelemetry](https://opentelemetry.io/), an open standard for observability data.
To send those traces to [W&B Weave](https://wandb.ai/site/weave/), Koog includes a built-in OpenTelemetry exporter —
no manual instrumentation required.

Once connected, Weave’s [OpenTelemetry support](https://weave-docs.wandb.ai/guides/tracking/otel/) lets you visualize,
analyze, and debug how your agents interact with LLMs, tools, and external APIs.

---

## Setup instructions

1. Create a W&B account at [https://wandb.ai](https://wandb.ai).
2. Get your API key from [https://wandb.ai/authorize](https://wandb.ai/authorize).
3. Find your entity name at the [W&B Dashboard](https://wandb.ai/home) — it matches your username for personal accounts, or the team/organization name for shared workspaces.
4. Choose a project name. If the project doesn't exist yet, it will be created automatically when the first trace is sent.
5. Provide the entity, project name, and API key — either as parameters to [`addWeaveExporter()`](https://api.koog.ai/agents/agents-features/agents-features-opentelemetry/ai.koog.agents.features.opentelemetry.integration.weave/add-weave-exporter.html), or via environment variables:

```bash
export WEAVE_API_KEY="<your-api-key>"
export WEAVE_ENTITY="<your-entity>"
export WEAVE_PROJECT_NAME="koog-tracing"
```
<!--- KNIT example-weave-exporter-01.txt -->

## Configuration

Install the **OpenTelemetry feature** and call [`addWeaveExporter()`](https://api.koog.ai/agents/agents-features/agents-features-opentelemetry/ai.koog.agents.features.opentelemetry.integration.weave/add-weave-exporter.html) to enable Weave export.

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
        val entity = System.getenv()["WEAVE_ENTITY"] 
            ?: throw IllegalArgumentException("WEAVE_ENTITY is not set")
        
        val projectName = System.getenv()["WEAVE_PROJECT_NAME"] 
            ?: "koog-tracing"
        
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            systemPrompt = "You are a code assistant. Provide concise code examples."
        ) {
            install(OpenTelemetry) {
                addWeaveExporter()
            }
        }
    
        println("Running agent with Weave tracing")
    
        val result = agent.run("Tell me a joke about programming")
        println("Result: $result\nSee traces on https://wandb.ai/$entity/$projectName/weave/traces")
    }
    ```
    <!--- KNIT example-weave-exporter-01.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry;
    import ai.koog.prompt.executor.clients.openai.OpenAIModels;
    import ai.koog.prompt.executor.model.PromptExecutor;
    import java.util.Optional;
    public class exampleWeaveExporterJava01 {
        static PromptExecutor promptExecutor = PromptExecutor.builder()
            .openAI("openai-api-key")
            .build();
    -->
    <!--- SUFFIX
    }
    -->
    ```java
    public static void main(String[] args) {
        var entity = Optional.ofNullable(System.getenv("WEAVE_ENTITY"))
            .filter(env -> !env.isBlank())
            .orElseThrow(() -> new IllegalArgumentException("WEAVE_ENTITY is not set"));

        var projectName = Optional.ofNullable(System.getenv("WEAVE_PROJECT_NAME"))
            .filter(env -> !env.isBlank())
            .orElse("koog-tracing");

        var agent = AIAgent.builder()
            .promptExecutor(promptExecutor)
            .llmModel(OpenAIModels.Chat.GPT4oMini)
            .systemPrompt("You are a helpful assistant.")
            .install(OpenTelemetry.Feature, config ->
                config.addWeaveExporter(
                    null,   // OTel endpoint URL (falls back to WEAVE_URL, defaults to https://trace.wandb.ai)
                    entity,
                    projectName
                )
            )
            .build();

        System.out.println("Running agent with Weave tracing");

        var result = agent.run("Tell me a joke about programming");
        System.out.println("Result: " + result + "\nSee traces on https://wandb.ai/" + entity + "/" + projectName + "/weave/traces");
    }
    ```
    <!--- KNIT exampleWeaveExporterJava01.java -->

## What gets traced

The Weave exporter captures the same activity as Koog’s general OpenTelemetry integration.
For the full list of captured spans and how to include LLM prompt and response content, see [What gets traced](index.md#what-gets-traced).

When visualized in W&B Weave, the trace appears as follows:
![W&B Weave traces](../../img/opentelemetry-weave-exporter-light.png#only-light)
![W&B Weave traces](../../img/opentelemetry-weave-exporter-dark.png#only-dark)

For more details, see the official [Weave OpenTelemetry Docs](https://weave-docs.wandb.ai/guides/tracking/otel/).

---

## Troubleshooting

- **No traces**: confirm `WEAVE_API_KEY`, `WEAVE_ENTITY`, and `WEAVE_PROJECT_NAME` are set, and that your W&B account has access to the specified entity and project.
- **Authentication errors**: verify `WEAVE_API_KEY` is valid and has write permission for the selected entity.
- **Connection issues**: confirm your environment can reach W&B’s OpenTelemetry ingestion endpoints.

For general troubleshooting, see [Troubleshooting](index.md#troubleshooting).
