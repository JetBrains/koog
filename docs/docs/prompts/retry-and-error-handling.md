# Retry functionality

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

