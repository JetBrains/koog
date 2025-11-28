# W&B Weave exporter

Koog provides built-in support for exporting agent traces to [W&B Weave](https://wandb.ai/site/weave/),
a developer tool from Weights & Biases for observability and analytics of AI applications.  
With the Weave integration, you can capture prompts, completions, system context, and execution traces 
and visualize them directly in your W&B workspace.

For background on Koog’s OpenTelemetry support, see the [OpenTelemetry support](https://docs.koog.ai/opentelemetry-support/).

---

## Setup instructions

1. Get up a W&B account at [https://wandb.ai](https://wandb.ai)
2. Get your API key from [https://wandb.ai/authorize](https://wandb.ai/authorize).
3. Find your entity name by visiting your W&B dashboard at [https://wandb.ai/home](https://wandb.ai/home). 
Your entity is usually your username if it's a personal account or your team/org name.
4. Define a name for your project. You don't have to create a project beforehand, it will be created automatically when the first trace is sent.
5. Pass the Weave entity, project name, and API key to the Weave exporter.
   This can be done by providing them as parameters to the `addWeaveExporter()` function,
   or by setting environment variables as shown below:

```bash
export WEAVE_API_KEY="<your-api-key>"
export WEAVE_ENTITY="<your-entity>"
export WEAVE_PROJECT_NAME="koog-tracing"
```

## Configuration

To enable Weave export, install the **OpenTelemetry feature** and add the `WeaveExporter`.  
The exporter uses Weave’s OpenTelemetry endpoint via `OtlpHttpSpanExporter`.

### Example: agent with Weave tracing

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.integration.weave.addWeaveExporter
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking
-->
```kotlin
fun main() = runBlocking {
    val apiKey = "api-key"
    val entity = System.getenv()["WEAVE_ENTITY"] ?: throw IllegalArgumentException("WEAVE_ENTITY is not set")
    val projectName = System.getenv()["WEAVE_PROJECT_NAME"] ?: "koog-tracing"
    
    val agent = AIAgent(
        promptExecutor = simpleOpenAIExecutor(apiKey),
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

## What gets traced

When enabled, the Weave exporter captures the same spans as Koog’s general OpenTelemetry integration, including:

- **Agent lifecycle events**: agent start, stop, errors
- **LLM interactions**: prompts, completions, latency
- **Tool calls**: execution traces for tool invocations
- **System context**: metadata such as model name, environment, Koog version

For security reasons, some content of OpenTelemetry spans is masked by default.
To make the content available in Weave, use the [setVerbose](opentelemetry-support.md#setverbose) method in the OpenTelemetry configuration and set its `verbose` argument to `true` as follows:

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
    addWeaveExporter()
    setVerbose(true)
}
```
<!--- KNIT example-weave-exporter-02.kt -->

When visualized in W&B Weave, the trace appears as follows:
![W&B Weave traces](img/opentelemetry-weave-exporter-light.png#only-light)
![W&B Weave traces](img/opentelemetry-weave-exporter-dark.png#only-dark)

For more details, see the official [Weave OpenTelemetry Docs](https://weave-docs.wandb.ai/guides/tracking/otel/).

---

## Troubleshooting

### No traces appear in Weave
- Confirm that `WEAVE_API_KEY`, `WEAVE_ENTITY`, and `WEAVE_PROJECT_NAME` are set in your environment.
- Ensure that your W&B account has access to the specified entity and project.

### Authentication errors
- Check that your `WEAVE_API_KEY` is valid.
- API key must have permission to write traces for the selected entity.

### Connection issues
- Make sure your environment has network access to W&B’s OpenTelemetry ingestion endpoints.
