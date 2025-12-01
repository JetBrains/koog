# Tracing Koog Agents to Langfuse with OpenTelemetry

[:material-github: Open on GitHub](
https://github.com/JetBrains/koog/blob/develop/examples/notebooks/Langfuse.ipynb
){ .md-button .md-button--primary }
[:material-download: Download .ipynb](
https://raw.githubusercontent.com/JetBrains/koog/develop/examples/notebooks/Langfuse.ipynb
){ .md-button }

This notebook shows how to export Koog agent traces to your Langfuse instance using OpenTelemetry. You'll set up environment variables, run a simple agent, and then inspect spans and traces in Langfuse.

## What you'll learn

- How Koog integrates with OpenTelemetry to emit traces
- How to configure the Langfuse exporter via environment variables
- How to run an agent and view its trace in Langfuse

## Prerequisites

- A Langfuse project (host URL, public key, secret key)
- An OpenAI API key for the LLM executor
- Environment variables set in your shell:

```bash
export OPENAI_API_KEY=sk-...
export LANGFUSE_HOST=https://cloud.langfuse.com # or your self-hosted URL
export LANGFUSE_PUBLIC_KEY=pk_...
export LANGFUSE_SECRET_KEY=sk_...
```



```kotlin
%useLatestDescriptors
//%use koog
```


```kotlin
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.integration.langfuse.addLangfuseExporter
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor

/**
 * Example of Koog agents tracing to [Langfuse](https://langfuse.com/)
 *
 * Agent traces are exported to:
 * - Langfuse OTLP endpoint instance using [OtlpHttpSpanExporter]
 *
 * To run this example:
 *  1. Set up a Langfuse project and credentials as described [here](https://langfuse.com/docs/get-started#create-new-project-in-langfuse)
 *  2. Get Langfuse credentials as described [here](https://langfuse.com/faq/all/where-are-langfuse-api-keys)
 *  3. Set `LANGFUSE_HOST`, `LANGFUSE_PUBLIC_KEY`, and `LANGFUSE_SECRET_KEY` environment variables
 *
 * @see <a href="https://langfuse.com/docs/opentelemetry/get-started#opentelemetry-endpoint">Langfuse OpenTelemetry Docs</a>
 */
val agent = AIAgent(
    executor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
    llmModel = OpenAIModels.Chat.GPT4oMini,
    systemPrompt = "You are a code assistant. Provide concise code examples."
) {
    install(OpenTelemetry) {
        addLangfuseExporter()
    }
}
```

## Configure the agent and Langfuse exporter

In the next cell, we:

- Create an AIAgent that uses OpenAI as the LLM executor
- Install the OpenTelemetry feature and add the Langfuse exporter
- Rely on environment variables for Langfuse configuration

Under the hood, Koog emits spans for agent lifecycle, LLM calls, and tool execution (if any). The Langfuse exporter ships those spans to your Langfuse instance via the OpenTelemetry endpoint.



```kotlin
import kotlinx.coroutines.runBlocking

println("Running agent with Langfuse tracing")

runBlocking {
    val result = agent.run("Tell me a joke about programming")
    "Result: $result\nSee traces on the Langfuse instance"
}

```

## Run the agent and view traces

Execute the next cell to trigger a simple prompt. This will generate spans that are exported to your Langfuse project.

### Where to look in Langfuse

1. Open your Langfuse dashboard and select your project
2. Navigate to the Traces/Spans view
3. Look for recent entries around the time you ran this cell
4. Drill down into spans to see:
   - Agent lifecycle events
   - LLM request/response metadata
   - Errors (if any)

### Troubleshooting

- No traces showing up?
  - Double-check LANGFUSE_HOST, LANGFUSE_PUBLIC_KEY, LANGFUSE_SECRET_KEY
  - Ensure your network allows outbound HTTPS to the Langfuse endpoint
  - Verify your Langfuse project is active and keys belong to the correct project
- Authentication errors
  - Regenerate keys in Langfuse and update env vars
- OpenAI issues
  - Confirm OPENAI_API_KEY is set and valid

