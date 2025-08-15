# Prompt API

The Prompt API provides a comprehensive toolkit for interacting with Large Language Models (LLMs) in production applications. It offers:

- **Kotlin DSL** for creating structured prompts with type safety
- **Multi-provider support** for OpenAI, Anthropic, Google, and other LLM providers
- **Production features** like retry logic, error handling, and timeout configuration
- **Multimodal capabilities** for working with text, images, audio, and documents

## Architecture Overview

The Prompt API consists of three main layers:

1. **LLM Clients** - Low-level interfaces to specific providers (OpenAI, Anthropic, etc.)
2. **Decorators** - Optional wrappers that add functionality like retry logic
3. **Prompt Executors** - High-level abstractions that manage client lifecycle and simplify usage

## Create a prompt

The Prompt API uses Kotlin DSL to create prompts. It supports the following types of messages:

- `system`: sets the context and instructions for the LLM.
- `user`: represents user input.
- `assistant`: represents LLM responses.

Here's an example of a simple prompt:

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

## Execute a prompt

To execute a prompt with a specific LLM, you need to the following:

1. Create a corresponding LLM client that handles the connection between your application and LLM providers. For example:
<!--- INCLUDE
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
const val apiKey = "apikey"
-->
```kotlin
// Create an OpenAI client
val client = OpenAILLMClient(apiKey)
```
<!--- KNIT example-prompt-api-02.kt -->

2. Call the `execute` method with the prompt and LLM as arguments.
<!--- INCLUDE
import ai.koog.agents.example.examplePromptApi01.prompt
import ai.koog.agents.example.examplePromptApi02.client
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
<!--- KNIT example-prompt-api-03.kt -->


The following LLM clients are available:

* [OpenAILLMClient](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-openai-client/ai.koog.prompt.executor.clients.openai/-open-a-i-l-l-m-client/index.html)
* [AnthropicLLMClient](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-anthropic-client/ai.koog.prompt.executor.clients.anthropic/-anthropic-l-l-m-client/index.html)
* [GoogleLLMClient](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-google-client/ai.koog.prompt.executor.clients.google/-google-l-l-m-client/index.html)
* [OpenRouterLLMClient](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-openrouter-client/ai.koog.prompt.executor.clients.openrouter/-open-router-l-l-m-client/index.html)
* [OllamaClient](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-ollama-client/ai.koog.prompt.executor.ollama.client/-ollama-client/index.html)
* [BedrockLLMClient](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-bedrock-client/ai.koog.prompt.executor.clients.bedrock/-bedrock-l-l-m-client/index.html) (JVM only)


Here's a simple example of using the Prompt API:

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
<!--- KNIT example-prompt-api-04.kt -->

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
<!--- KNIT example-prompt-api-05.kt -->

#### Configuring Retry Behavior

Koog provides several predefined retry configurations:

| Configuration | Max Attempts | Initial Delay | Max Delay | Use Case |
|--------------|-------------|---------------|-----------|----------|
| `DISABLED` | 1 (no retry) | - | - | Development/testing |
| `CONSERVATIVE` | 3 | 2s | 30s | Normal production use |
| `AGGRESSIVE` | 5 | 500ms | 20s | Critical operations |
| `PRODUCTION` | 3 | 1s | 20s | Recommended default |

Use them directly or create custom configurations:

<!--- INCLUDE
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import kotlin.time.Duration.Companion.seconds

val apiKey = System.getenv("OPENAI_API_KEY")
val client = OpenAILLMClient(apiKey)
-->
```kotlin
// Use predefined configuration
val conservativeClient = RetryingLLMClient(
    delegate = client,
    config = RetryConfig.CONSERVATIVE
)

// Or create custom configuration
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
<!--- KNIT example-prompt-api-06.kt -->

#### Retryable Error Patterns

By default, the retry mechanism recognizes common transient errors:

- **HTTP Status Codes**: 429 (Rate Limit), 500, 502, 503, 504
- **Error Keywords**: "rate limit", "timeout", "connection reset", "overloaded"

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
<!--- KNIT example-prompt-api-07.kt -->

#### Retry with Prompt Executors

When using prompt executors, wrap the underlying client before creating the executor:

<!--- INCLUDE
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider

-->
```kotlin
// Single provider executor with retry
val resilientClient = RetryingLLMClient(
    OpenAILLMClient(System.getenv("OPENAI_API_KEY")),
    RetryConfig.PRODUCTION
)
val executor = SingleLLMPromptExecutor(resilientClient)

// Multi-provider executor with flexible client configuration
val multiExecutor = MultiLLMPromptExecutor(
    LLMProvider.OpenAI to RetryingLLMClient(
        OpenAILLMClient(System.getenv("OPENAI_API_KEY")),
        RetryConfig.CONSERVATIVE
    ),
    LLMProvider.Anthropic to RetryingLLMClient(
        AnthropicLLMClient(System.getenv("ANTHROPIC_API_KEY")),
        RetryConfig.AGGRESSIVE  
    ),
    // Bedrock client already has AWS SDK retry built-in
    LLMProvider.Bedrock to BedrockLLMClient(
        awsAccessKeyId = System.getenv("AWS_ACCESS_KEY_ID"),
        awsSecretAccessKey = System.getenv("AWS_SECRET_ACCESS_KEY"),
        awsSessionToken = System.getenv("AWS_SESSION_TOKEN"),
    ))
```
<!--- KNIT example-prompt-api-08.kt -->

#### Streaming with Retry

Streaming operations can optionally be retried (disabled by default):

<!--- INCLUDE
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.dsl.prompt
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        val baseClient = OpenAILLMClient(System.getenv("OPENAI_API_KEY"))
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
<!--- KNIT example-prompt-api-09.kt -->

> **Note**: Streaming retry only applies to connection failures before the first token is received. Once streaming begins, errors are passed through to preserve content integrity.

### Timeout Configuration

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
<!--- KNIT example-prompt-api-10.kt -->

### Error Handling Best Practices

When working with LLMs in production:

1. **Always wrap operations in try-catch blocks** to handle unexpected errors
2. **Log errors with context** for debugging
3. **Implement fallback strategies** for critical operations
4. **Monitor retry patterns** to identify systemic issues

Example of comprehensive error handling:

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
            OpenAILLMClient(System.getenv("OPENAI_API_KEY"))
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
            // Fall back to alternative solution
            useDefaultResponse()
        }
    }
}
```
<!--- KNIT example-prompt-api-11.kt -->

## Multimodal inputs

In addition to providing text messages within prompts, Koog also lets you send images, audio, video, and files to LLMs along with `user` messages. As with standard text-only prompts, you also add media to the prompt using the DSL structure for prompt construction.

<!--- INCLUDE
import ai.koog.prompt.dsl.prompt
import kotlinx.io.files.Path
-->
```kotlin
val prompt = prompt("multimodal_input") {
    system("You are a helpful assistant.")

    user {
        +"Describe these images"

        attachments {
            image("https://example.com/test.png")
            image(Path("/User/koog/image.png"))
        }
    }
}
```
<!--- KNIT example-prompt-api-12.kt -->

### Textual prompt content

To accommodate for the support for various attachment types and create a clear distinction between text and file inputs in a prompt, you put text messages in a dedicated `content` parameter within a user prompt. 
To add file inputs, provide them as a list within the `attachments` parameter. 

The general format of a user message that includes a text message and a list of attachments is as follows:

<!--- INCLUDE
import ai.koog.prompt.dsl.prompt

val prompt = prompt("prompt") {
-->
<!--- SUFFIX
}
-->
```kotlin
user(
    content = "This is the user message",
    attachments = listOf(
        // Add attachments
    )
)
```
<!--- KNIT example-prompt-api-13.kt -->

### File attachments

To include an attachment, provide the file in the `attachments` parameter, following the format below:

<!--- INCLUDE
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Attachment
import ai.koog.prompt.message.AttachmentContent

val prompt = prompt("prompt") {
-->
<!--- SUFFIX
}
-->
```kotlin
user(
    content = "Describe this image",
    attachments = listOf(
        Attachment.Image(
            content = AttachmentContent.URL("https://example.com/capture.png"),
            format = "png",
            mimeType = "image/png",
            fileName = "capture.png"
        )
    )
)
```
<!--- KNIT example-prompt-api-14.kt -->

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

#### AttachmentContent

`AttachmentContent` defines the type and source of content that is provided as an input to the LLM. The following 
classes are supported:

`AttachmentContent.URL(val url: String)`

Provides file content from the specified URL. Takes the following parameter:

| Name   | Data type | Required | Description                      |
|--------|-----------|----------|----------------------------------|
| `url`  | String    | Yes      | The URL of the provided content. |

`AttachmentContent.Binary.Bytes(val data: ByteArray)`

Provides file content as a byte array. Takes the following parameter:

| Name   | Data type | Required | Description                                |
|--------|-----------|----------|--------------------------------------------|
| `data` | ByteArray | Yes      | The file content provided as a byte array. |

`AttachmentContent.Binary.Base64(val base64: String)`

Provides file content encoded as a Base64 string. Takes the following parameter:

| Name     | Data type | Required | Description                             |
|----------|-----------|----------|-----------------------------------------|
| `base64` | String    | Yes      | The Base64 string containing file data. |

`AttachmentContent.PlainText(val text: String)`

_Applies only if the attachment type is `Attachment.File`_. Provides content from a plain text file (such as the `text/plain` MIME type). Takes the following parameter:

| Name   | Data type | Required | Description              |
|--------|-----------|----------|--------------------------|
| `text` | String    | Yes      | The content of the file. |

### Mixed attachment content

In addition to providing different types of attachments in separate prompts or messages, you can also provide multiple and mixed types of attachments in a single `user` message, as shown below:

<!--- INCLUDE
import ai.koog.prompt.dsl.prompt
import kotlinx.io.files.Path
-->
```kotlin
val prompt = prompt("mixed_content") {
    system("You are a helpful assistant.")

    user {
        +"Compare the image with the document content."

        attachments {
            image(Path("/User/koog/page.png"))
            binaryFile(Path("/User/koog/page.pdf"), "application/pdf")
        }
    }
}
```
<!--- KNIT example-prompt-api-15.kt -->

## Prompt Executors

While LLM clients provide direct access to providers, **Prompt Executors** offer a higher-level abstraction that simplifies common use cases and handles client lifecycle management. They're ideal when you want to:

- Quickly prototype without managing client configuration
- Work with multiple providers through a unified interface  
- Simplify dependency injection in larger applications
- Abstract away provider-specific details

### Executor Types

The Koog framework provides several prompt executors:

- **Single provider executors**:
    - `simpleOpenAIExecutor`: for executing prompts with OpenAI models.
    - `simpleAnthropicExecutor`: for executing prompts with Anthropic models.
    - `simpleGoogleExecutor`: for executing prompts with Google models.
    - `simpleOpenRouterExecutor`: for executing prompts with OpenRouter.
    - `simpleOllamaExecutor`: for executing prompts with Ollama.

- **Multi-provider executor**:
    - `DefaultMultiLLMPromptExecutor`: For working with multiple LLM providers

### Create a single provider executor

To create a prompt executor for a specific LLM provider, use the corresponding function.
For example, to create the OpenAI prompt executor, you need to call the `simpleOpenAIExecutor` function and provide it with the API key required for authentication with the OpenAI service:

1. Create a prompt executor:
<!--- INCLUDE
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
const val apiToken = "YOUR_API_TOKEN"
-->
```kotlin
// Create an OpenAI executor
val promptExecutor = simpleOpenAIExecutor(apiToken)
```
<!--- KNIT example-prompt-api-16.kt -->

2. Execute the prompt with a specific LLM:
<!--- INCLUDE
import ai.koog.agents.example.examplePromptApi12.prompt
import ai.koog.agents.example.examplePromptApi16.promptExecutor
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutorExt.execute
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
<!--- KNIT example-prompt-api-17.kt -->

### Create a multi-provider executor

To create a prompt executor that works with multiple LLM providers, do the following:

1. Configure clients for the required LLM providers with the corresponding API keys. For example:
<!--- INCLUDE
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
-->
```kotlin
val openAIClient = OpenAILLMClient(System.getenv("OPENAI_KEY"))
val anthropicClient = AnthropicLLMClient(System.getenv("ANTHROPIC_KEY"))
val googleClient = GoogleLLMClient(System.getenv("GOOGLE_KEY"))
```
<!--- KNIT example-prompt-api-18.kt -->

2. Pass the configured clients to the `DefaultMultiLLMPromptExecutor` class constructor to create a prompt executor with multiple LLM providers:
<!--- INCLUDE
import ai.koog.agents.example.examplePromptApi18.anthropicClient
import ai.koog.agents.example.examplePromptApi18.googleClient
import ai.koog.agents.example.examplePromptApi18.openAIClient
import ai.koog.prompt.executor.llms.all.DefaultMultiLLMPromptExecutor
-->
```kotlin
val multiExecutor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient, googleClient)
```
<!--- KNIT example-prompt-api-19.kt -->

3. Execute the prompt with a specific LLM:
<!--- INCLUDE
import ai.koog.agents.example.examplePromptApi12.prompt
import ai.koog.agents.example.examplePromptApi19.multiExecutor
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutorExt.execute
import kotlinx.coroutines.runBlocking


fun main() {
    runBlocking {
-->
<!--- SUFFIX
    }
}
-->
```kotlin
val response = multiExecutor.execute(
    prompt = prompt,
    model = OpenAIModels.Chat.GPT4o
)
```
<!--- KNIT example-prompt-api-20.kt -->

