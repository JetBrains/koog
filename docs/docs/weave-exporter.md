# W&B Weave Exporter

Koog provides built-in support for exporting agent traces to [W&B Weave](https://wandb.ai/site/weave/),
a developer tool from Weights & Biases for observability and analytics of AI applications.  
With the Weave integration, you can capture prompts, completions, system context, and execution traces 
and visualize them directly in your W&B workspace.

For background on Koog’s OpenTelemetry support, see the [OpenTelemetry support](https://docs.koog.ai/opentelemetry-support/).

---

## Setup Instructions

1. Set up a Weave account at [https://wandb.ai](https://wandb.ai)
2. Get your API key from [https://wandb.ai/authorize](https://wandb.ai/authorize) and expose it as a `WEAVE_API_KEY` environment variable.
3. Find your entity name by visiting your W&B dashboard at [https://wandb.ai/home](https://wandb.ai/home) and checking the Teams field in the left sidebar, expose it as a `WEAVE_ENTITY` environment variable.
4. Define a name for your project and expose it as a `WEAVE_PROJECT_NAME` environment variable. You don't have to create a project beforehand, it will be created automatically when the first trace is sent.
5. Set environment variables. Add the following variables to your environment:

```bash
export WEAVE_API_KEY="<your-api-key>"
export WEAVE_ENTITY="<your-entity>"
export WEAVE_PROJECT_NAME="koog-tracing"
```

Once configured, Koog automatically forwards OpenTelemetry traces to your Weave entity.

## Configuration

To enable Weave export, install the **OpenTelemetry feature** and add the `WeaveExporter`.  
The exporter uses Weave’s OpenTelemetry endpoint via `OtlpHttpSpanExporter`.

### Example: Agent with Weave Tracing

```kotlin
fun main() = runBlocking {
    val entity = System.getenv()["WEAVE_ENTITY"] ?: throw IllegalArgumentException("WEAVE_ENTITY is not set")
    val projectName = System.getenv()["WEAVE_PROJECT_NAME"] ?: "koog-tracing"

    val agent = AIAgent(
        executor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey),
        llmModel = OpenAIModels.CostOptimized.GPT4oMini,
        systemPrompt = "You are a code assistant. Provide concise code examples."
    ) {
        install(OpenTelemetry) {
            addWeaveExporter(
                weaveEntity = entity,
                weaveProjectName = projectName
            )
        }
    }

    println("Running agent with Weave tracing")

    val result = agent.run("Tell me a joke about programming")

    println("Result: $result\nSee traces on https://wandb.ai/$entity/$projectName/weave/traces")
}
```

## What Gets Traced

When enabled, the Weave exporter captures the same spans as Koog’s general OpenTelemetry integration, including:

- **Agent lifecycle events** – agent start, stop, errors
- **LLM interactions** – prompts, completions, token usage, latency
- **Tool and API calls** – function and external API executions
- **System context** – metadata such as model name, Koog version, environment

You can visualize these traces in the **Weave dashboard**
and use W&B’s observability tools to analyze performance and quality.

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
