# Handling failures

This page describes how to handle failures for LLM clients and prompt executors using the built-in retry and timeout mechanisms.

## Retry functionality

When working with LLM providers, transient errors like rate limits or temporary service unavailability may occur.
The `RetryingLLMClient` decorator adds automatic retry logic to any LLM client.

### Basic usage

Wrap any existing client with the retry capability:

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
// Wrap any client with the retry capability
val client = OpenAILLMClient(apiKey)
val resilientClient = RetryingLLMClient(client)

// Now all operations will automatically retry on transient errors
val response = resilientClient.execute(prompt, OpenAIModels.Chat.GPT4o)
```
<!--- KNIT example-handling-failures-01.kt -->

### Configuring retry behavior

By default, `RetryingLLMClient` configures an LLM client with the maximum of 3 retry attempts, a 1-second initial delay,
and a 30-second maximum delay.
You can specify a different retry configuration using a `RetryConfig` passed to `RetryingLLMClient`.
For example:

<!--- INCLUDE
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient

val apiKey = System.getenv("OPENAI_API_KEY")
val client = OpenAILLMClient(apiKey)
-->
```kotlin
// Use the predefined configuration
val conservativeClient = RetryingLLMClient(
    delegate = client,
    config = RetryConfig.CONSERVATIVE
)
```
<!--- KNIT example-handling-failures-02.kt -->

Koog provides several predefined retry configurations:

| Configuration              | Max attempts | Initial delay | Max delay | Use case                                                                                                 |
|----------------------------|--------------|---------------|-----------|----------------------------------------------------------------------------------------------------------|
| `RetryConfig.DISABLED`     | 1 (no retry) | -             | -         | Development, testing, and debugging.                                                                     |
| `RetryConfig.CONSERVATIVE` | 3            | 2s            | 30s       | Background or scheduled tasks where reliability is more important than speed.                            |
| `RetryConfig.AGGRESSIVE`   | 5            | 500ms         | 20s       | Critical operations where fast recovery from transient errors is more important than reducing API calls. |
| `RetryConfig.PRODUCTION`   | 3            | 1s            | 20s       | General production use.                                                                                  |

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
<!--- KNIT example-handling-failures-03.kt -->

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

If any pattern returns `true`, the error is considered retryable, and the LLM client retries the request.

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
<!--- KNIT example-handling-failures-04.kt -->

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
<!--- KNIT example-handling-failures-05.kt -->


### Streaming with retry

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
<!--- KNIT example-handling-failures-06.kt -->

!!!note
    Streaming retries only apply to connection failures that occur before the first token is received.
    Once streaming has started, the retry logic is disabled.
    If an error occurs during streaming, the operation is terminated.

### Retry with prompt executors

When working with prompt executors, you can wrap the underlying LLM client with a retry mechanism before creating the executor.
To learn more about prompt executors, see [Prompt executors](prompt-executors.md).

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
<!--- KNIT example-handling-failures-07.kt -->

## Timeout configuration

All LLM clients support timeout configuration to prevent hanging requests.
You can specify timeout values for network connections when creating the client using
the [`ConnectionTimeoutConfig`](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/ai.koog.prompt.executor.clients/-connection-timeout-config/index.html) class.

`ConnectionTimeoutConfig` has the following properties:

| Property               | Default Value        | Description                                                   |
|------------------------|----------------------|---------------------------------------------------------------|
| `connectTimeoutMillis` | 60 seconds (60,000)  | Maximum time to establish a connection to the server.         |
| `requestTimeoutMillis` | 15 minutes (900,000) | Maximum time for the entire request to complete.              |
| `socketTimeoutMillis`  | 15 minutes (900,000) | Maximum time to wait for data over an established connection. |

You can customize these values for your specific needs. For example:

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
            requestTimeoutMillis = 60000,    // 60 seconds for the entire request
            socketTimeoutMillis = 120000   // 120 seconds for data on the socket
        )
    )
)
```
<!--- KNIT example-handling-failures-08.kt -->

!!! tip
    For long-running or streaming calls, set higher values for `requestTimeoutMillis` and `socketTimeoutMillis`.

## Error handling

When working with LLMs in production, you need to implement error handling, including:

- **Try-catch blocks** to handle unexpected errors.
- **Logging errors with context** for debugging.
- **Fallbacks** for critical operations.
- **Monitoring retry patterns** to identify recurring issues.

Here is an example of error handling:

<!--- INCLUDE
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.dsl.prompt
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
-->
```kotlin
fun main() {
    runBlocking {
        val logger = LoggerFactory.getLogger("Example")
        val resilientClient = RetryingLLMClient(
            OpenAILLMClient(System.getenv("OPENAI_API_KEY")),
            RetryConfig.PRODUCTION
        )
        val prompt = prompt("test") { user("Hello") }
        val model = OpenAIModels.Chat.GPT4o

        fun processResponse(response: Any) { /* implmenentation */ }
        fun scheduleRetryLater() { /* implmenentation */ }
        fun notifyAdministrator() { /* implmenentation */ }
        fun useDefaultResponse() { /* implmenentation */ }

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
    }
}
```
<!--- KNIT example-handling-failures-09.kt -->
