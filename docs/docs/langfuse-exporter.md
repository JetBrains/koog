# Langfuse Exporter

Koog provides built-in support for exporting agent traces to [Langfuse](https://langfuse.com/), a platform for observability and analytics of AI applications.  
With Langfuse integration, you can visualize, analyze, and debug how your Koog agents interact with LLMs, APIs, and other components.

For background on Koog’s OpenTelemetry support, see the [OpenTelemetry support](https://docs.koog.ai/opentelemetry-support/).

---

### Setup Instructions

1. Create a Langfuse project. Follow the setup guide at [Create new project in Langfuse](https://langfuse.com/docs/get-started#create-new-project-in-langfuse)
2. Obtain API credentials. Retrieve your Langfuse `public key` and `secret key` as described in [Where are Langfuse API keys?](https://langfuse.com/faq/all/where-are-langfuse-api-keys)
3. Set environment variables. Add the following variables to your environment:

```bash
   export LANGFUSE_HOST="https://cloud.langfuse.com"
   export LANGFUSE_PUBLIC_KEY="<your-public-key>"
   export LANGFUSE_SECRET_KEY="<your-secret-key>"
```

Once configured, Koog automatically forwards OpenTelemetry traces to your Langfuse instance.

## Configuration

To enable Langfuse export, install the **OpenTelemetry feature** and add the `LangfuseExporter`.  
The exporter uses `OtlpHttpSpanExporter` under the hood to send traces to Langfuse’s OpenTelemetry endpoint.

### Example: Agent with Langfuse Tracing

```kotlin
fun main() = runBlocking {
    val agent = AIAgent(
        executor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey),
        llmModel = OpenAIModels.CostOptimized.GPT4oMini,
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



## What Gets Traced

When enabled, the Langfuse exporter captures the same spans as Koog’s general OpenTelemetry integration, including:

- **Agent lifecycle events** – agent start, stop, errors
- **LLM interactions** – prompts, responses, token usage, latency
- **Tool and API calls** – execution traces for function/tool invocations
- **System context** – metadata such as model name, environment, Koog version

Koog also captures span attributes required by Langfuse to show [Agent Graphs](https://langfuse.com/docs/observability/features/agent-graphs). 

This allows you to correlate agent reasoning with API calls and user inputs in a structured way within Langfuse.

For more details on Langfuse OTLP tracing, see:  
[Langfuse OpenTelemetry Docs](https://langfuse.com/docs/opentelemetry/get-started#opentelemetry-endpoint)

---

## Troubleshooting

### No traces appear in Langfuse
- Double-check that `LANGFUSE_HOST`, `LANGFUSE_PUBLIC_KEY`, and `LANGFUSE_SECRET_KEY` are set in your environment.
- If running on self-hosted Langfuse, confirm that the `LANGFUSE_HOST` is reachable from your application environment.
- Verify that the public/secret key pair belongs to the correct project.
