# OpenTelemetry with Koog: Tracing your AI agent

[:material-github: Open on GitHub](
https://github.com/JetBrains/koog/blob/develop/examples/notebooks/OpenTelemetry.ipynb
){ .md-button .md-button--primary }
[:material-download: Download .ipynb](
https://raw.githubusercontent.com/JetBrains/koog/develop/examples/notebooks/OpenTelemetry.ipynb
){ .md-button }

This notebook demonstrates how to add OpenTelemetry-based tracing to a Koog AI agent. We will:
- Emit spans to the console for quick local debugging.
- Export spans to an OpenTelemetry Collector and view them in Jaeger.

Prerequisites:
- Docker/Docker Compose installed
- An OpenAI API key available in environment variable `OPENAI_API_KEY`

Start the local OpenTelemetry stack (Collector + Jaeger) before running the notebook:
```bash
./docker-compose up -d
```
After the agent runs, open Jaeger UI:
- http://localhost:16686

To stop the services later:
```bash
docker-compose down
```

---


```kotlin
%useLatestDescriptors
// %use koog
```


```kotlin
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter

```

## Configure OpenTelemetry exporters

In the next cell, we:
- Create a Koog AIAgent
- Install the OpenTelemetry feature
- Add two span exporters:
  - LoggingSpanExporter for console logs
  - OTLP gRPC exporter to http://localhost:4317 (Collector)

This mirrors the example description: console logs for local debugging and OTLP for viewing traces in Jaeger.



```kotlin
val agent = AIAgent(
    executor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
    llmModel = OpenAIModels.Chat.GPT4oMini,
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
```

## Run the agent and view traces in Jaeger

Execute the next cell to trigger a simple prompt. You should see:
- Console span logs from the LoggingSpanExporter
- Traces exported to your local OpenTelemetry Collector and visible in Jaeger at http://localhost:16686

Tip: Use the Jaeger search to find recent traces after you run the cell.



```kotlin
import ai.koog.agents.utils.use
import kotlinx.coroutines.runBlocking

runBlocking {
    agent.use { agent ->
        println("Running agent with OpenTelemetry tracing...")

        val result = agent.run("Tell me a joke about programming")

        "Agent run completed with result: '$result'.\nCheck Jaeger UI at http://localhost:16686 to view traces"
    }
}
```

## Cleanup and troubleshooting

When you're done:

- Stop services:
  ```bash
  docker-compose down
  ```

- If you don't see traces in Jaeger:
  - Ensure the stack is running: `./docker-compose up -d` and give it a few seconds to start.
  - Verify ports:
    - Collector (OTLP gRPC): http://localhost:4317
    - Jaeger UI: http://localhost:16686
  - Check container logs: `docker-compose logs --tail=200`
  - Confirm your `OPENAI_API_KEY` is set in the environment where the notebook runs.
  - Make sure the endpoint in the exporter matches the collector: `http://localhost:4317`.

- What spans to expect:
  - Koog agent lifecycle
  - LLM request/response metadata
  - Any tool execution spans (if you add tools)

You can now iterate on your agent and observe changes in your tracing pipeline.

