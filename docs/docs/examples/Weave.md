# Weave tracing for Koog agents

[:material-github: Open on GitHub](
https://github.com/JetBrains/koog/blob/develop/examples/notebooks/Weave.ipynb
){ .md-button .md-button--primary }
[:material-download: Download .ipynb](
https://raw.githubusercontent.com/JetBrains/koog/develop/examples/notebooks/Weave.ipynb
){ .md-button }

This notebook demonstrates how to trace Koog agents to W&B Weave using OpenTelemetry (OTLP).
You will create a simple Koog `AIAgent`, enable the Weave exporter, run a prompt, and view
rich traces in the Weave UI.

For background, see Weave OpenTelemetry docs: https://weave-docs.wandb.ai/guides/tracking/otel/


## Prerequisites

Before running the example, make sure you have:

- A Weave/W&B account: https://wandb.ai
- Your API key from https://wandb.ai/authorize exposed as an environment variable: `WEAVE_API_KEY`
- Your Weave entity (team or user) name exposed as `WEAVE_ENTITY`
  - Find it on your W&B dashboard: https://wandb.ai/home (left sidebar "Teams")
- A project name exposed as `WEAVE_PROJECT_NAME` (if not set, this example uses `koog-tracing`)
- An OpenAI API key exposed as `OPENAI_API_KEY` to run the Koog agent

Example (macOS/Linux):
```bash
export WEAVE_API_KEY=...  # required by Weave
export WEAVE_ENTITY=your-team-or-username
export WEAVE_PROJECT_NAME=koog-tracing
export OPENAI_API_KEY=...
```


## Notebook setup

We use the latest Kotlin Jupyter descriptors. If you have Koog preconfigured as a `%use` plugin,
you can uncomment the line below.



```kotlin
%useLatestDescriptors
//%use koog

```

## Create an agent and enable Weave tracing

We construct a minimal `AIAgent` and install the `OpenTelemetry` feature with the Weave exporter.
The exporter sends OTLP spans to Weave using your environment configuration:
- `WEAVE_API_KEY` — authentication to Weave
- `WEAVE_ENTITY` — which team/user owns the traces
- `WEAVE_PROJECT_NAME` — the Weave project to store traces in



```kotlin
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.integration.weave.addWeaveExporter
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor

val entity = System.getenv()["WEAVE_ENTITY"] ?: throw IllegalArgumentException("WEAVE_ENTITY is not set")
val projectName = System.getenv()["WEAVE_PROJECT_NAME"] ?: "koog-tracing"

val agent = AIAgent(
    executor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
    llmModel = OpenAIModels.Chat.GPT4oMini,
    systemPrompt = "You are a code assistant. Provide concise code examples."
) {
    install(OpenTelemetry) {
        addWeaveExporter(
            weaveEntity = entity,
            weaveProjectName = projectName
        )
    }
}

```

## Run the agent and view traces in Weave

Execute a simple prompt. After completion, open the printed link to view the trace in Weave.
You should see spans for the agent’s run, model calls, and other instrumented operations.



```kotlin
import kotlinx.coroutines.runBlocking

println("Running agent with Weave tracing")

runBlocking {
    val result = agent.run("Tell me a joke about programming")
    "Result: $result\nSee traces on https://wandb.ai/$entity/$projectName/weave/traces"
}

```

## Troubleshooting

- If you don't see traces, verify `WEAVE_API_KEY`, `WEAVE_ENTITY`, and `WEAVE_PROJECT_NAME` are set in your environment.
- Ensure your network allows outbound HTTPS to Weave's OTLP endpoint.
- Confirm your OpenAI key is valid and the selected model is accessible from your account.
