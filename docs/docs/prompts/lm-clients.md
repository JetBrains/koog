# LLM clients

LLM clients are designed for direct interaction with LLM providers.
Each client implements the `LLMClient` interface, which provides methods for executing prompts and streaming responses.

You can use an LLM client when you work with a single LLM provider and don't need advanced lifecycle management.
If you need to manage multiple LLM providers, use a [prompt executor](prompt-executors.md).

The table below shows the available LLM clients and their capabilities.
The `*` symbol indicates additional notes available in the **Notes** column.

| LLM provider                                        | LLMClient                                                                                                                                                                                                   | Tool<br/>calling | Streaming | Multiple<br/>choices | Embeddings | Moderation | <div style="width:50px">Model<br/>listing</div> | <div style="width:200px">Notes</div>                                                                                        |
|-----------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------|-----------|----------------------|------------|------------|-------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| [OpenAI](https://platform.openai.com/docs/overview) | [OpenAILLMClient](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-openai-client/ai.koog.prompt.executor.clients.openai/-open-a-i-l-l-m-client/index.html)                | ✓                | ✓         | ✓                    | ✓          | ✓*         | ✓                                               | Supports moderation via the OpenAI Moderation API.                                                                          |
| [Anthropic](https://www.anthropic.com/)             | [AnthropicLLMClient](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-anthropic-client/ai.koog.prompt.executor.clients.anthropic/-anthropic-l-l-m-client/index.html)      | ✓                | ✓         | -                    | -          | -          | -                                               | -                                                                                                                           |
| [Google](https://ai.google.dev/)                    | [GoogleLLMClient](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-google-client/ai.koog.prompt.executor.clients.google/-google-l-l-m-client/index.html)                  | ✓                | ✓         | ✓                    | ✓          | -          | ✓                                               | -                                                                                                                           |
| [DeepSeek](https://www.deepseek.com/)               | [DeepSeekLLMClient](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-deepseek-client/ai.koog.prompt.executor.clients.deepseek/-deep-seek-l-l-m-client/index.html)         | ✓                | ✓         | ✓                    | -          | -          | ✓                                               | OpenAI-compatible chat client.                                                                                              |
| [OpenRouter](https://openrouter.ai/)                | [OpenRouterLLMClient](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-openrouter-client/ai.koog.prompt.executor.clients.openrouter/-open-router-l-l-m-client/index.html) | ✓                | ✓         | ✓                    | -          | -          | ✓                                               | OpenAI-compatible router client.                                                                                            |
| [Amazon Bedrock](https://aws.amazon.com/bedrock/)   | [BedrockLLMClient](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-bedrock-client/ai.koog.prompt.executor.clients.bedrock/-bedrock-l-l-m-client/index.html)              | ✓                | ✓         | -                    | ✓          | ✓*         | -                                               | JVM-only AWS SDK client that supports multiple model families. Moderation requires Guardrails configuration.                |
| [Mistral](https://mistral.ai/)                      | [MistralAILLMClient](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-mistralai-client/ai.koog.prompt.executor.clients.mistralai/-mistral-a-i-l-l-m-client/index.html)    | ✓                | ✓         | ✓                    | ✓          | ✓*         | ✓                                               | OpenAI-compatible client that supports moderation via the Mistral `v1/moderations` endpoint.                                |
| [Alibaba](https://www.alibabacloud.com/en?_p_lc=1)  | [DashScopeLLMClient](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-dashscope-client/ai.koog.prompt.executor.clients.dashscope/-dashscope-l-l-m-client/index.html)      | ✓                | ✓         | ✓                    | -          | -          | ✓                                               | OpenAI-compatible client that exposes provider-specific parameters (`enableSearch`, `parallelToolCalls`, `enableThinking`). |
| [Ollama](https://ollama.com/)                       | [OllamaClient](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-ollama-client/ai.koog.prompt.executor.ollama.client/-ollama-client/index.html)                            | ✓                | ✓         | -                    | ✓          | ✓          | -                                               | Local server client with model management APIs.                                                                             |

## Running a prompt

To run a prompt using an LLM client, perform the following:

1) Create an LLM client that handles the connection between your application and LLM providers. For example:

<!--- INCLUDE
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
const val apiKey = "apikey"
-->
```kotlin
// Create an OpenAI client
val client = OpenAILLMClient(apiKey)
```
<!--- KNIT example-llm-clients-01.kt -->

2) Call the `execute()` method with the prompt and LLM as arguments.

<!--- INCLUDE
import ai.koog.agents.example.examplePromptApi01.prompt
import ai.koog.agents.example.examplePromptApi06.client
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import kotlinx.coroutines.runBlocking

fun main() {
runBlocking {
-->
<!--- SUFFIX
}
}
-->
```kotlin
// Execute the prompt
val response = client.execute(
    prompt = prompt,
    model = OpenAIModels.Chat.GPT4o  // You can choose different models
)
```
<!--- KNIT example-llm-clients-02.kt -->

Here is an example that uses `OpenAILLMClient` to run prompts:

<!--- INCLUDE
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.params.LLMParams
import kotlinx.coroutines.runBlocking
-->
```kotlin
fun main() {
    runBlocking {
        // Set up the OpenAI client with your API key
        val token = System.getenv("OPENAI_API_KEY")
        val client = OpenAILLMClient(token)

        // Create a prompt
        val prompt = prompt("prompt_name", LLMParams()) {
            // Add a system message to set the context
            system("You are a helpful assistant.")

            // Add a user message
            user("Tell me about Kotlin")

            // You can also add assistant messages for few-shot examples
            assistant("Kotlin is a modern programming language...")

            // Add another user message
            user("What are its key features?")
        }

        // Execute the prompt and get the response
        val response = client.execute(prompt = prompt, model = OpenAIModels.Chat.GPT4o)
        println(response)
    }
}
```
<!--- KNIT example-llm-clients-03.kt -->

## Streaming responses

!!! note
    Available for all LLM clients.

When you need to process responses as they are generated,
you can use the `executeStreaming()` method to stream the model output:

```kotlin
// Set up the OpenAI client with your API key
val token = System.getenv("OPENAI_API_KEY")
val client = OpenAILLMClient(token)

val response = client.executeStreaming(
    prompt = prompt("stream_demo") { user("Stream this response in short chunks.") },
    model = OpenAIModels.Chat.GPT4_1
)

response.collect { event ->
    when (event) {
        is StreamFrame.Append -> println(event.text)
        is StreamFrame.ToolCall -> println("\nTool call: ${event.name}")
        is StreamFrame.End -> println("\n[done] Reason: ${event.finishReason}")
    }
}
```
<!--- KNIT example-llm-clients-04.kt -->

## Multiple choices

!!! note
    Available for all LLM clients except `GoogleLLMClient`, `BedrockLLMClient`, and `OllamaClient`.

You can use the `executeMultipleChoices()` method to request mutiple alternative responses from the model in a single call:

```kotlin
fun main() = runBlocking {
    val apiKey = System.getenv("OPENAI_API_KEY")
    val client = OpenAILLMClient(apiKey)

    val choices = client.executeMultipleChoices(
        prompt = prompt("n_best") {
            system("You are a creative assistant.")
            user("Give me three different opening lines for a story.")
        },
        model = OpenAIModels.Chat.GPT4o
    )

    choices.forEachIndexed { i, choice ->
        val text = choice.joinToString(" ") { it.content }
        println("Line #${i + 1}: $text")
    }
}
```
<!--- KNIT example-llm-clients-05.kt -->

!!! tip
    You can also request multiple choices by adding the [`numberOfChoices`](structured-prompts.md#prompt-parameters) LLM parameter into the prompt.

## Listing available models

!!! note
    Available for all LLM clients except `GoogleLLMClient`, `BedrockLLMClient`, and `OllamaClient`.

To get a list of available model IDs supported by the LLM client, use the `models()` method:    

```kotlin
fun main() = runBlocking {
    val apiKey = System.getenv("OPENAI_API_KEY")
    val client = OpenAILLMClient(apiKey)

    val ids: List<String> = client.models()
    ids.forEach { println(it) }
}
```
<!--- KNIT example-llm-clients-06.kt -->

## Embeddings

!!! note
    Available for `OpenAILLMClient`, `GoogleLLMClient`, `BedrockLLMClient`, `MistralAILLMClient`, `OllamaClient`.

You convert text into embedding vectors using the `embed()` method.
Choose an embedding model and pass your text to this method:

```kotlin
fun main() = runBlocking {
    val apiKey = System.getenv("OPENAI_API_KEY")
    val client = OpenAILLMClient(apiKey)

    val embedding = client.embed(
        text = "This is a sample text for embedding",
        model = OpenAIModels.Embeddings.TextEmbedding3Large
    )

    println("Embedding size: ${embedding.size}")
}
```
<!--- KNIT example-llm-clients-07.kt -->

## Moderation

!!! note
    Available for the following LLM clients: `OpenAILLMClient`, `BedrockLLMClient`, `MistralAILLMClient`, `OllamaClient`.

You can use the `moderate()` method with a moderation model to check whether a prompt contains inappropriate content:

```kotlin
fun main() = runBlocking {
    val apiKey = System.getenv("OPENAI_API_KEY")
    val client = OpenAILLMClient(apiKey)

    val result = client.moderate(
        prompt = prompt("moderation") {
            user("This is a test message that may contain offensive content.")
        },
        model = OpenAIModels.Moderation.Omni
    )

    println(result)
}
```
<!--- KNIT example-llm-clients-08.kt -->

## Retry functionality

When working with LLM providers, you may encounter transient errors like rate limits or temporary service unavailability. 
The `RetryingLLMClient` decorator adds automatic retry logic to any LLM client.

### Basic usage

Wrap any existing client with retry capability:

<!--- INCLUDE
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.dsl.prompt
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        val apiKey = System.getenv("OPENAI_API_KEY")
        val prompt = prompt("test") {
            user("Hello")
        }
-->
<!--- SUFFIX
    }
}
-->
```kotlin
// Wrap any client with retry capability
val client = OpenAILLMClient(apiKey)
val resilientClient = RetryingLLMClient(client)

// Now all operations will automatically retry on transient errors
val response = resilientClient.execute(prompt, OpenAIModels.Chat.GPT4o)
```
<!--- KNIT example-llm-clients-09.kt -->

### Configuring retry behavior

Koog provides several predefined retry configurations:

| Configuration              | Max attempts | Initial delay | Max delay | Use case                |
|----------------------------|--------------|---------------|-----------|-------------------------|
| `RetryConfig.DISABLED`     | 1 (no retry) | -             | -         | Development and testing |
| `RetryConfig.CONSERVATIVE` | 3            | 2s            | 30s       | Normal production use   |
| `RetryConfig.AGGRESSIVE`   | 5            | 500ms         | 20s       | Critical operations     |
| `RetryConfig.PRODUCTION`   | 3            | 1s            | 20s       | Recommended default     |

You can use them directly or create custom configurations:

<!--- INCLUDE
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import kotlin.time.Duration.Companion.seconds

val apiKey = System.getenv("OPENAI_API_KEY")
val client = OpenAILLMClient(apiKey)
-->
```kotlin
// Use the predefined configuration
val conservativeClient = RetryingLLMClient(
    delegate = client,
    config = RetryConfig.CONSERVATIVE
)

// Or create a custom configuration
val customClient = RetryingLLMClient(
    delegate = client,
    config = RetryConfig(
        maxAttempts = 5,
        initialDelay = 1.seconds,
        maxDelay = 30.seconds,
        backoffMultiplier = 2.0,
        jitterFactor = 0.2
    )
)
```
<!--- KNIT example-llm-clients-10.kt -->

### Retry error patterns

By default, the `RetryingLLMClient` recognizes common transient errors.
This behavior is controlled by the [`RetryConfig.retryablePatterns`](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/ai.koog.prompt.executor.clients.retry/-retry-config/retryable-patterns.html) patterns.
Each pattern is represented by
[`RetryablePattern`](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/ai.koog.prompt.executor.clients.retry/-retryable-pattern/index.html)
that checks the error message from a failed request and determines whether it should be retried.

Koog provides the predefined retry configurations and patterns that work across all the supported LLM providers.
You can keep the defaults or customize them for your specific needs.

#### Pattern types

You can use the following pattern types and combine any number of them:

* `RetryablePattern.Status`: Matches a specific HTTP status code in the error message (such as `429`, `500`,`502`, etc.).
* `RetryablePattern.Keyword`: Matches a keyword in the error message (such as `rate limit` or `request timeout`).
* `RetryablePattern.Regex`: Matches a regular expression in the error message.
* `RetryablePattern.Custom`: Matches a custom logic using a lambda function.

If any pattern returns `true`, the error is considered retryable, and the LLM client can retry the request.

#### Default patterns

Unless you customize the retry configuration, the following patterns are used by default:

* **HTTP status codes**:
    * `429`: Rate limit
    * `500`: Internal server error
    * `502`: Bad gateway
    * `503`: Service unavailable
    * `504`: Gateway timeout
    * `529`: Anthropic overloaded

* **Error keywords**:
    * rate limit
    * too many requests
    * request timeout
    * connection timeout
    * read timeout
    * write timeout
    * connection reset by peer
    * connection refused
    * temporarily unavailable
    * service unavailable

These default patterns are defined in Koog as [`RetryConfig.DEFAULT_PATTERNS`](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/ai.koog.prompt.executor.clients.retry/-retry-config/-companion/-d-e-f-a-u-l-t_-p-a-t-t-e-r-n-s.html).

#### Custom patterns

You can define custom patterns for your specific needs:

<!--- INCLUDE
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryablePattern
-->
```kotlin
val config = RetryConfig(
    retryablePatterns = listOf(
        RetryablePattern.Status(429),   // Specific status code
        RetryablePattern.Keyword("quota"),  // Keyword in error message
        RetryablePattern.Regex(Regex("ERR_\\d+")),  // Custom regex pattern
        RetryablePattern.Custom { error ->  // Custom logic
            error.contains("temporary") && error.length > 20
        }
    )
)
```
<!--- KNIT example-llm-clients-11.kt -->

You can also append custom patterns to the default `RetryConfig.DEFAULT_PATTERNS`:

<!--- INCLUDE
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryablePattern
-->
```kotlin
val config = RetryConfig(
    retryablePatterns = RetryConfig.DEFAULT_PATTERNS + listOf(
        RetryablePattern.Keyword("custom_error")
    )
)
```
<!--- KNIT example-llm-clients-12.kt -->


## Integration with prompt executors

[Prompt executors](prompt-executors.md) wrap LLM clients and provide additional functionality, such as routing, fallbacks, and unified usage across providers.
They are recommended for production use, as they provide resilience and multi‑provider flexibility.
