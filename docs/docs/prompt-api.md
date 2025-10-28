# Prompt API

The Prompt API provides a comprehensive toolkit for interacting with Large Language Models (LLMs) in production applications. It offers:

- **Kotlin DSL** for creating structured prompts with type safety.
- **Multi-provider support** for OpenAI, Anthropic, Google, and other LLM providers.
- **Production features** such as retry logic, error handling, and timeout configuration.
- **Multimodal capabilities** for working with text, images, audio, and documents.

## Architecture overview

The Prompt API consists of three main layers:

- **LLM clients**: Low-level interfaces to specific providers (OpenAI, Anthropic, etc.).
- **Decorators**: Optional wrappers that add functionality like retry logic.
- **Prompt executors**: High-level abstractions that manage client lifecycle and simplify usage.

## Creating a prompt

The Prompt API uses Kotlin DSL to create prompts. It supports the following types of messages:

- `system`: Sets the context and instructions for the LLM.
- `user`: Represents user input.
- `assistant`: Represents LLM responses.

Here is an example of a simple prompt:

<!--- INCLUDE
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.params.LLMParams
-->
```kotlin
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
```
<!--- KNIT example-prompt-api-01.kt -->

## Multimodal inputs

In addition to providing text messages within prompts, Koog also lets you send images, audio, video, and files to LLMs along with `user` messages.
As with standard text-only prompts, you also add media to the prompt using the DSL structure for prompt construction.

<!--- INCLUDE
import ai.koog.prompt.dsl.prompt
import kotlinx.io.files.Path
-->
```kotlin
val prompt = prompt("multimodal_input") {
    system("You are a helpful assistant.")

    user {
        +"Describe these images"
        
        image("https://example.com/test.png")
        image(Path("/User/koog/image.png"))
    }
}
```
<!--- KNIT example-prompt-api-02.kt -->

### Textual prompt content
The general format of a user message that includes a text message and a list of attachments is as follows:

<!--- INCLUDE
import ai.koog.prompt.dsl.prompt

val prompt = prompt("prompt") {
-->
<!--- SUFFIX
}
-->
```kotlin
user {
    +"This is the text part of the user message"
    // Add attachment
    image("https://example.com/capture.png")
    file("https://example.com/data.pdf", "application/pdf")
}
```
<!--- KNIT example-prompt-api-03.kt -->

### File attachments

To include an attachment, provide the file following the format below:

<!--- INCLUDE
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart

val prompt = prompt("prompt") {
-->
<!--- SUFFIX
}
-->
```kotlin
user {
    +"Describe this image"
    image(
        ContentPart.Image(
            content = AttachmentContent.URL("https://example.com/capture.png"),
            format = "png",
            mimeType = "image/png",
            fileName = "capture.png"
        )
    )
}
```
<!--- KNIT example-prompt-api-04.kt -->

The `attachments` parameter takes a list of file inputs, where each item is an instance of one of the following classes:

- `Attachment.Image`: image attachments, such as `jpg` or `png` files.
- `Attachment.Audio`: audio attachments, such as `mp3` or `wav` files.
- `Attachment.Video`: video attachments, such as `mpg` or `avi` files.
- `Attachment.File`: file attachments, such as `pdf` or `txt` files.

Each of the classes above takes the following parameters:

| Name       | Data type                               | Required                   | Description                                                                                                 |
|------------|-----------------------------------------|----------------------------|-------------------------------------------------------------------------------------------------------------|
| `content`  | [AttachmentContent](#attachmentcontent) | Yes                        | The source of the provided file content. For more information, see [AttachmentContent](#attachmentcontent). |
| `format`   | String                                  | Yes                        | The format of the provided file. For example, `png`.                                                        |
| `mimeType` | String                                  | Only for `Attachment.File` | The MIME Type of the provided file. For example, `image/png`.                                               |
| `fileName` | String                                  | No                         | The name of the provided file including the extension. For example, `screenshot.png`.                       |

For more details, see [API reference](https://api.koog.ai/prompt/prompt-model/ai.koog.prompt.message/-attachment/index.html).

#### AttachmentContent

`AttachmentContent` defines the type and source of content that is provided as an input to the LLM. The following
classes are supported:

* `AttachmentContent.URL(val url: String)`

  Provides file content from the specified URL. Takes the following parameter:

  | Name   | Data type | Required | Description                      |
  |--------|-----------|----------|----------------------------------|
  | `url`  | String    | Yes      | The URL of the provided content. |

  See also [API reference](https://api.koog.ai/prompt/prompt-model/ai.koog.prompt.message/-attachment-content/-u-r-l/index.html).

* `AttachmentContent.Binary.Bytes(val data: ByteArray)`

  Provides file content as a byte array. Takes the following parameter:

  | Name   | Data type | Required | Description                                |
  |--------|-----------|----------|--------------------------------------------|
  | `data` | ByteArray | Yes      | The file content provided as a byte array. |

  See also [API reference](https://api.koog.ai/prompt/prompt-model/ai.koog.prompt.message/-attachment-content/-binary/index.html).

* `AttachmentContent.Binary.Base64(val base64: String)`

  Provides file content encoded as a Base64 string. Takes the following parameter:

  | Name     | Data type | Required | Description                             |
  |----------|-----------|----------|-----------------------------------------|
  | `base64` | String    | Yes      | The Base64 string containing file data. |

  See also [API reference](https://api.koog.ai/prompt/prompt-model/ai.koog.prompt.message/-attachment-content/-binary/index.html).

* `AttachmentContent.PlainText(val text: String)`

!!! tip
    Applies only if the attachment type is `Attachment.File`.

  Provides content from a plain text file (such as the `text/plain` MIME type). Takes the following parameter:

  | Name   | Data type | Required | Description              |
  |--------|-----------|----------|--------------------------|
  | `text` | String    | Yes      | The content of the file. |

  See also [API reference](https://api.koog.ai/prompt/prompt-model/ai.koog.prompt.message/-attachment-content/-plain-text/index.html).

### Mixed attachment content

In addition to providing different types of attachments in separate prompts or messages, you can also provide multiple and mixed types of attachments in a single `user` message:

<!--- INCLUDE
import ai.koog.prompt.dsl.prompt
import kotlinx.io.files.Path
-->
```kotlin
val prompt = prompt("mixed_content") {
    system("You are a helpful assistant.")

    user {
        +"Compare the image with the document content."
        image(Path("/User/koog/page.png"))
        binaryFile(Path("/User/koog/page.pdf"), "application/pdf")
        +"Structure the result as a table"
    }
}
```
<!--- KNIT example-prompt-api-05.kt -->

## Choosing between LLM clients and prompt executors

When working with the Prompt API, you can run prompts by using either LLM clients or prompt executors.
To choose between clients and executors, consider the following factors:

- Use LLM clients directly if you work with a single LLM provider and do not require advanced lifecycle management. To learm more, see [Running prompts with LLM clients](#running-prompts-with-llm-clients).
- Use prompt executors if you need a higher level of abstraction for managing LLMs and their lifecycle, or if you want to run prompts with a consistent API across multiple providers and dynamically switch between them.
  To learn more, see [Runnning prompts with prompt executors](#running-prompts-with-prompt-executors).

!!!note
    Both the LLM clients and prompt executors let you stream responses, generate multiple choices, and run content moderation.
    For more information, refer to the [API Reference](https://api.koog.ai/index.html) for the specific client or executor.


## Running prompts with LLM clients

You can use LLM clients to run prompts if you work with a single LLM provider and do not require advanced lifecycle management.
Koog provides the following LLM clients:

* [OpenAILLMClient](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-openai-client/ai.koog.prompt.executor.clients.openai/-open-a-i-l-l-m-client/index.html)
* [AnthropicLLMClient](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-anthropic-client/ai.koog.prompt.executor.clients.anthropic/-anthropic-l-l-m-client/index.html)
* [GoogleLLMClient](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-google-client/ai.koog.prompt.executor.clients.google/-google-l-l-m-client/index.html)
* [OpenRouterLLMClient](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-openrouter-client/ai.koog.prompt.executor.clients.openrouter/-open-router-l-l-m-client/index.html)
* [DeepSeekLLMClient](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-deepseek-client/ai.koog.prompt.executor.clients.deepseek/-deep-seek-l-l-m-client/index.html)
* [OllamaClient](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-ollama-client/ai.koog.prompt.executor.ollama.client/-ollama-client/index.html)
* [BedrockLLMClient](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-bedrock-client/ai.koog.prompt.executor.clients.bedrock/-bedrock-l-l-m-client/index.html) (JVM only)

To run a prompt using an LLM client, perform the following:

1) Create the LLM client that handles the connection between your application and LLM providers. For example:

<!--- INCLUDE
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
const val apiKey = "apikey"
-->
```kotlin
// Create an OpenAI client
val client = OpenAILLMClient(apiKey)
```
<!--- KNIT example-prompt-api-06.kt -->

2) Call the `execute` method with the prompt and LLM as arguments.

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
<!--- KNIT example-prompt-api-07.kt -->

Here is an example that uses the OpenAI client to run a prompt:

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
<!--- KNIT example-prompt-api-08.kt -->

!!!note
    The LLM clients let you stream responses, generate multiple choices, and run content moderation.
    For more information, refer to the API Reference for the specific client.
    To learn more about content moderation, see [Content moderation](content-moderation.md).

## Running prompts with prompt executors

While LLM clients provide direct access to providers, prompt executors offer a higher-level abstraction that simplifies common use cases and handles client lifecycle management.
They are ideal when you need to:

- Quickly prototype without managing client configuration.
- Work with multiple providers through a unified interface.
- Simplify dependency injection in larger applications.
- Abstract away provider-specific details.

### Executor types

Koog provides two main prompt executors:

| <div style="width:175px">Name</div> | Description                                                                                                                                                                                                                             |
|-------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`SingleLLMPromptExecutor`](https://api.koog.ai/prompt/prompt-executor/prompt-executor-llms/ai.koog.prompt.executor.llms/-single-l-l-m-prompt-executor/index.html)       | Wraps a single LLM client for one provider. Use this executor if your agent only requires the ability to switch between models within a single LLM provider.                                                                            |
| [`MultiLLMPromptExecutor`](https://api.koog.ai/prompt/prompt-executor/prompt-executor-llms/ai.koog.prompt.executor.llms/-multi-l-l-m-prompt-executor/index.html)        | Routes to multiple LLM clients by a provider, with optional fallbacks for each provider to be used when a requested provider is not available. Use this executor if your agent needs to switch between models from different providers. |

These are implementations of the [`PromtExecutor`](https://api.koog.ai/prompt/prompt-executor/prompt-executor-model/ai.koog.prompt.executor.model/-prompt-executor/index.html) interface for executing prompts with LLMs.

### Creating a single provider executor

To create a prompt executor for a specific LLM provider, perform the following:

1) Configure an LLM client for a specific provider with the corresponding API key:
<!--- INCLUDE
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
-->
```kotlin
val openAIClient = OpenAILLMClient(System.getenv("OPENAI_KEY"))
```
<!--- KNIT example-prompt-api-09.kt -->
2) Create a prompt executor using [`SingleLLMPromptExecutor`](https://api.koog.ai/prompt/prompt-executor/prompt-executor-llms/ai.koog.prompt.executor.llms/-single-l-l-m-prompt-executor/index.html):
<!--- INCLUDE
import ai.koog.agents.example.examplePromptApi09.openAIClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
-->
```kotlin
val promptExecutor = SingleLLMPromptExecutor(openAIClient)
```
<!--- KNIT example-prompt-api-10.kt -->

### Creating a multi-provider executor

To create a prompt executor that works with multiple LLM providers, do the following:

1) Configure clients for the required LLM providers with the corresponding API keys:
<!--- INCLUDE
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.ollama.client.OllamaClient
-->
```kotlin
val openAIClient = OpenAILLMClient(System.getenv("OPENAI_KEY"))
val ollamaClient = OllamaClient()
```
<!--- KNIT example-prompt-api-11.kt -->

2) Pass the configured clients to the `MultiLLMPromptExecutor` class constructor to create a prompt executor with multiple LLM providers:
<!--- INCLUDE
import ai.koog.agents.example.examplePromptApi11.openAIClient
import ai.koog.agents.example.examplePromptApi11.ollamaClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider
-->
```kotlin
val multiExecutor = MultiLLMPromptExecutor(
    LLMProvider.OpenAI to openAIClient,
    LLMProvider.Ollama to ollamaClient
)
```
<!--- KNIT example-prompt-api-12.kt -->

### Pre-defined prompt executors

For faster setup, Koog provides the following ready-to-use executor implementations for common providers:

- Single provider executors that return `SingleLLMPromptExecutor` configured with a certain LLM client:
    - `simpleOpenAIExecutor` for executing prompts with OpenAI models.
    - `simpleAzureOpenAIExecutor` for executing prompts using Azure OpenAI Service.
    - `simpleAnthropicExecutor` for executing prompts with Anthropic models.
    - `simpleGoogleAIExecutor` for executing prompts with Google models.
    - `simpleOpenRouterExecutor` for executing prompts with OpenRouter.
    - `simpleOllamaAIExecutor` for executing prompts with Ollama.

- Multi-provider executor:
    - `DefaultMultiLLMPromptExecutor` which is an implementation of `MultiLLMPromptExecutor` that supports OpenAI, Anthropic, and Google providers.

Here is an example of creating pre-defined single and multi-provider executors:

<!--- INCLUDE
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.llms.all.DefaultMultiLLMPromptExecutor
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
-->
<!--- SUFFIX
    }
}
-->
```kotlin
// Create an OpenAI executor
val promptExecutor = simpleOpenAIExecutor("OPENAI_KEY")

// Create a DefaultMultiLLMPromptExecutor with OpenAI, Anthropic, and Google LLM clients
val openAIClient = OpenAILLMClient("OPENAI_KEY")
val anthropicClient = AnthropicLLMClient("ANTHROPIC_KEY")
val googleClient = GoogleLLMClient("GOOGLE_KEY")
val multiExecutor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient, googleClient)
```
<!--- KNIT example-prompt-api-13.kt -->

### Executing a prompt

The prompt executors provide methods to run prompts using various capabilities, such as streaming, multiple choices generation, and content moderation.

Here is an example of how to run a prompt with a specific LLM using the `execute` method:

<!--- INCLUDE
import ai.koog.agents.example.examplePromptApi04.prompt
import ai.koog.agents.example.examplePromptApi10.promptExecutor
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
// Execute a prompt
val response = promptExecutor.execute(
    prompt = prompt,
    model = OpenAIModels.Chat.GPT4o
)
```
<!--- KNIT example-prompt-api-14.kt -->

This will run the prompt with the `GPT4o` model and return the response.

!!!note
    The prompt executors let you stream responses, generate multiple choices, and run content moderation.
    For more information, refer to the API Reference for the specific executor.
    To learn more about content moderation, see [Content moderation](content-moderation.md).

## Cached prompt executors

For repeated requests, you can cache LLM responses to optimize performance and reduce costs.
Koog provides the `CachedPromptExecutor`, which is a wrapper around the `PromptExecutor` that adds caching functionality.
It lets you store responses from previously executed prompts and retrieve them when the same prompts are run again.

To create a cached prompt executor, perform the following:

1) Create a prompt executor for which you want to cache responses:
<!--- INCLUDE
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
-->
```kotlin
val client = OpenAILLMClient(System.getenv("OPENAI_KEY"))
val promptExecutor = SingleLLMPromptExecutor(client)
```
<!--- KNIT example-prompt-api-15.kt -->

2) Create a `CachedPromptExecutor` instance with the desired cache and provide the created prompt executor:
<!--- INCLUDE
import ai.koog.agents.example.examplePromptApi15.promptExecutor
import ai.koog.prompt.cache.files.FilePromptCache
import ai.koog.prompt.executor.cached.CachedPromptExecutor
import kotlin.io.path.Path
import kotlinx.coroutines.runBlocking
--> 
```kotlin
val cachedExecutor = CachedPromptExecutor(
    cache = FilePromptCache(Path("/cache_directory")),
    nested = promptExecutor
)
```
<!--- KNIT example-prompt-api-16.kt -->

3) Run the cached prompt executor with the desired prompt and model:
<!--- INCLUDE
import ai.koog.prompt.dsl.prompt
import ai.koog.agents.example.examplePromptApi16.cachedExecutor
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        val prompt = prompt("test") {
            user("Hello")
        }

-->
<!--- SUFFIX
    }
}
--> 
```kotlin
val response = cachedExecutor.execute(prompt, OpenAIModels.Chat.GPT4o)
```
<!--- KNIT example-prompt-api-17.kt -->

Now you can run the same prompt with the same model multiple times, the response will be retrieved from the cache.

!!!note
    * If you call `executeStreaming()` with the cached prompt executor, it produces a response as a single chunk.
    * If you call `moderate()` with the cached prompt executor, it forwards the request to the nested prompt executor and does not use the cache.
    * Caching of multiple choice responses is not supported.

## Retry functionality

When working with LLM providers, you may encounter transient errors like rate limits or temporary service unavailability. The `RetryingLLMClient` decorator adds automatic retry logic to any LLM client.

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
<!--- KNIT example-prompt-api-18.kt -->

#### Configuring retry behavior

Koog provides several predefined retry configurations:

| Configuration  | Max Attempts | Initial Delay | Max Delay | Use Case             |
|----------------|--------------|---------------|-----------|----------------------|
| `DISABLED`     | 1 (no retry) | -             | -         | Development and testing |
| `CONSERVATIVE` | 3            | 2s            | 30s       | Normal production use |
| `AGGRESSIVE`   | 5            | 500ms         | 20s       | Critical operations  |
| `PRODUCTION`   | 3            | 1s            | 20s       | Recommended default  |

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
<!--- KNIT example-prompt-api-19.kt -->

#### Retryable error patterns

By default, the retry mechanism recognizes common transient errors:

* **HTTP status codes**:
    * `429`: Rate limit
    * `500`: Internal server error
    * `502`: Bag gateway
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

You can define custom patterns for your specific needs:

<!--- INCLUDE
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryablePattern
-->
```kotlin
val config = RetryConfig(
    retryablePatterns = listOf(
        RetryablePattern.Status(429),           // Specific status code
        RetryablePattern.Keyword("quota"),      // Keyword in error message
        RetryablePattern.Regex(Regex("ERR_\\d+")), // Custom regex pattern
        RetryablePattern.Custom { error ->      // Custom logic
            error.contains("temporary") && error.length > 20
        }
    )
)
```
<!--- KNIT example-prompt-api-20.kt -->

#### Retry with prompt executors

When working with prompt executors, you can wrap the underlying LLM client with a retry mechanism before creating the executor:

<!--- INCLUDE
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider

-->
```kotlin
// Single provider executor with retry
val resilientClient = RetryingLLMClient(
    OpenAILLMClient(System.getenv("OPENAI_KEY")),
    RetryConfig.PRODUCTION
)
val executor = SingleLLMPromptExecutor(resilientClient)

// Multi-provider executor with flexible client configuration
val multiExecutor = MultiLLMPromptExecutor(
    LLMProvider.OpenAI to RetryingLLMClient(
        OpenAILLMClient(System.getenv("OPENAI_KEY")),
        RetryConfig.CONSERVATIVE
    ),
    LLMProvider.Anthropic to RetryingLLMClient(
        AnthropicLLMClient(System.getenv("ANTHROPIC_API_KEY")),
        RetryConfig.AGGRESSIVE  
    ),
    // The Bedrock client already has a built-in AWS SDK retry 
    LLMProvider.Bedrock to BedrockLLMClient(
        identityProvider = StaticCredentialsProvider {
            accessKeyId = System.getenv("AWS_ACCESS_KEY_ID")
            secretAccessKey = System.getenv("AWS_SECRET_ACCESS_KEY")
            sessionToken = System.getenv("AWS_SESSION_TOKEN")
        },
    ),
)
```
<!--- KNIT example-prompt-api-21.kt -->

#### Streaming with retry

Streaming operations can optionally be retried. This feature is disabled by default.

<!--- INCLUDE
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.dsl.prompt
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        val baseClient = OpenAILLMClient(System.getenv("OPENAI_KEY"))
        val prompt = prompt("test") {
            user("Generate a story")
        }
-->
<!--- SUFFIX
    }
}
-->
```kotlin
val config = RetryConfig(
    maxAttempts = 3
)

val client = RetryingLLMClient(baseClient, config)
val stream = client.executeStreaming(prompt, OpenAIModels.Chat.GPT4o)
```
<!--- KNIT example-prompt-api-22.kt -->

!!!note
    Streaming retry only applies to the connection failures before the first token is received. Once streaming begins, errors are passed through to preserve content integrity.


### Timeout configuration

All LLM clients support timeout configuration to prevent hanging requests:

<!--- INCLUDE
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient

val apiKey = System.getenv("OPENAI_API_KEY")
-->
```kotlin
val client = OpenAILLMClient(
    apiKey = apiKey,
    settings = OpenAIClientSettings(
        timeoutConfig = ConnectionTimeoutConfig(
            connectTimeoutMillis = 5000,    // 5 seconds to establish connection
            requestTimeoutMillis = 60000    // 60 seconds for the entire request
        )
    )
)
```
<!--- KNIT example-prompt-api-23.kt -->

### Error handling

When working with LLMs in production, you need to imlement error-handling strategies:

- **Use try-catch blocks** to handle unexpected errors.
- **Log errors with context** for debugging.
- **Implement fallback strategies** for critical operations.
- **Monitor retry patterns** to identify recurring or systemic issues.

Here is an example of comprehensive error handling:

<!--- INCLUDE
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.dsl.prompt
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

fun main() {
    runBlocking {
        val logger = LoggerFactory.getLogger("Example")
        val resilientClient = RetryingLLMClient(
            OpenAILLMClient(System.getenv("OPENAI_KEY"))
        )
        val prompt = prompt("test") { user("Hello") }
        val model = OpenAIModels.Chat.GPT4o
        
        fun processResponse(response: Any) { /* ... */ }
        fun scheduleRetryLater() { /* ... */ }
        fun notifyAdministrator() { /* ... */ }
        fun useDefaultResponse() { /* ... */ }
-->
<!--- SUFFIX
    }
}
-->
```kotlin
try {
    val response = resilientClient.execute(prompt, model)
    processResponse(response)
} catch (e: Exception) {
    logger.error("LLM operation failed", e)
    
    when {
        e.message?.contains("rate limit") == true -> {
            // Handle rate limiting specifically
            scheduleRetryLater()
        }
        e.message?.contains("invalid api key") == true -> {
            // Handle authentication errors
            notifyAdministrator()
        }
        else -> {
            // Fall back to an alternative solution
            useDefaultResponse()
        }
    }
}
```
<!--- KNIT example-prompt-api-24.kt -->

