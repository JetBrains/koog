# Langfuse exporter

Koog provides built-in support for exporting agent traces to [Langfuse](https://langfuse.com/), a platform for observability and analytics of AI applications.
With Langfuse integration, you can visualize, analyze, and debug how your Koog agents interact with LLMs, APIs, and other components.

For background on Koog's OpenTelemetry support, see the [OpenTelemetry support](https://docs.koog.ai/opentelemetry-support/).

---

## Setup instructions

1. Create a Langfuse project. Follow the setup guide at [Create new project in Langfuse](https://langfuse.com/docs/get-started#create-new-project-in-langfuse)
2. Get API credentials. Retrieve your Langfuse `public key` and `secret key` as described in [Where are Langfuse API keys?](https://langfuse.com/faq/all/where-are-langfuse-api-keys)
3. Pass the Langfuse host, private key, and secret key to the Langfuse exporter. 
This can be done by providing them as parameters to the `addLangfuseExporter()` function, or by setting environment variables as shown below:

```bash
   export LANGFUSE_HOST="https://cloud.langfuse.com"
   export LANGFUSE_PUBLIC_KEY="<your-public-key>"
   export LANGFUSE_SECRET_KEY="<your-secret-key>"
```

## Configuration

To enable Langfuse export, install the **OpenTelemetry feature** and add the `LangfuseExporter`.  
The exporter uses `OtlpHttpSpanExporter` under the hood to send traces to Langfuse’s OpenTelemetry endpoint.

### Example: agent with Langfuse tracing

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.integration.langfuse.addLangfuseExporter
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking
-->
```kotlin
fun main() = runBlocking {
    val apiKey = "api-key"
    
    val agent = AIAgent(
        promptExecutor = simpleOpenAIExecutor(apiKey),
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

## Trace attributes

Langfuse uses trace-level attributes to enhance observability with features like sessions, environments, tags and other metadata.
The `addLangfuseExporter` function supports a `traceAttributes` parameter that accepts a list of `CustomAttribute` objects.

These attributes are added to the root `InvokeAgentSpan` span of each trace and enable Langfuse's advanced features. You can pass
any attributes supported by Langfuse - see the [complete list in Langfuse's OpenTelemetry documentation](https://langfuse.com/integrations/native/opentelemetry#trace-level-attributes).

Common attributes:
- **Sessions** (`langfuse.session.id`): Group related traces for aggregated metrics, cost analysis, and scoring
- **Environments**: Isolate production traces from development and staging for cleaner analysis
- **Tags** (`langfuse.trace.tags`): Label traces with feature names, experiment IDs, or customer segments (array of strings)

### Example with session and tags

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.integration.langfuse.addLangfuseExporter
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking
import java.util.UUID
-->
```kotlin
fun main() = runBlocking {
    val apiKey = "api-key"
    val sessionId = UUID.randomUUID().toString()

    val agent = AIAgent(
        promptExecutor = simpleOpenAIExecutor(apiKey),
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

    // Multiple runs with the same session ID will be grouped in Langfuse
    agent.run("What is Kotlin?")
    agent.run("Show me a coroutine example")
}
```
<!--- KNIT example-langfuse-exporter-02.kt -->

## What gets traced

When enabled, the Langfuse exporter captures the same spans as Koog’s general OpenTelemetry integration, including:

- **Agent lifecycle events**: agent start, stop, errors
- **LLM interactions**: prompts, responses, token usage, latency
- **Tool calls**: execution traces for tool invocations
- **System context**: metadata such as model name, environment, Koog version

Koog also captures span attributes required by Langfuse to show [Agent Graphs](https://langfuse.com/docs/observability/features/agent-graphs).

For security reasons, some content of OpenTelemetry spans is masked by default. 
To make the content available in Langfuse, use the [setVerbose](opentelemetry-support.md#setverbose) method in the OpenTelemetry configuration and set its `verbose` argument to `true` as follows:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.integration.langfuse.addLangfuseExporter
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
    addLangfuseExporter()
    setVerbose(true)
}
```
<!--- KNIT example-langfuse-exporter-03.kt -->

When visualized in Langfuse, the trace appears as follows:
![Langfuse traces](img/opentelemetry-langfuse-exporter-light.png#only-light)
![Langfuse traces](img/opentelemetry-langfuse-exporter-dark.png#only-dark)

For more details on Langfuse OpenTelemetry tracing, see:  
[Langfuse OpenTelemetry Docs](https://langfuse.com/integrations/native/opentelemetry#opentelemetry-endpoint).

---

## Troubleshooting

### No traces appear in Langfuse
- Double-check that `LANGFUSE_HOST`, `LANGFUSE_PUBLIC_KEY`, and `LANGFUSE_SECRET_KEY` are set in your environment.
- If running on self-hosted Langfuse, confirm that the `LANGFUSE_HOST` is reachable from your application environment.
- Verify that the public/secret key pair belongs to the correct project.
