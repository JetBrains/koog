# Handling failures

This page describes how to handle failures for LLM clients and prompt executors using the built-in retry and timeout mechanisms.

## Retry functionality

When working with LLM providers, transient errors like rate limits or temporary service unavailability may occur.
The `RetryingLLMClient` decorator adds automatic retry logic to any LLM client.

### Basic usage

Wrap any existing client with the retry capability:

=== "Kotlin"

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

=== "Java"

    ```java
    // Wrap any client with the retry capability
    String apiKey = System.getenv("OPENAI_API_KEY");
    Prompt prompt = Prompt.builder("test").user("Hello").build();
    OpenAILLMClient client = new OpenAILLMClient(apiKey);
    RetryingLLMClient resilientClient = new RetryingLLMClient(client);

    // From Java, use a JavaPromptExecutor to call suspend APIs
    JavaPromptExecutor exec = Executors.promptExecutor(resilientClient);
    List<Message.Response> response = exec.executeAsync(prompt, OpenAIModels.Chat.GPT4o).get();
    ```

### Configuring retry behavior

By default, `RetryingLLMClient` configures an LLM client with the maximum of 3 retry attempts, a 1-second initial delay,
and a 30-second maximum delay.
You can specify a different retry configuration using a `RetryConfig` passed to `RetryingLLMClient`.
For example:

=== "Kotlin"

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

=== "Java"

    ```java
    String apiKey = System.getenv("OPENAI_API_KEY");
    OpenAILLMClient client = new OpenAILLMClient(apiKey);
    RetryingLLMClient conservativeClient = new RetryingLLMClient(
        client,
        RetryConfig.Companion.getCONSERVATIVE()
    );
    ```

Koog provides several predefined retry configurations:

| Configuration              | Max attempts | Initial delay | Max delay | Use case                                                                                                 |
|----------------------------|--------------|---------------|-----------|----------------------------------------------------------------------------------------------------------|
| `RetryConfig.DISABLED`     | 1 (no retry) | -             | -         | Development, testing, and debugging.                                                                     |
| `RetryConfig.CONSERVATIVE` | 3            | 2s            | 30s       | Background or scheduled tasks where reliability is more important than speed.                            |
| `RetryConfig.AGGRESSIVE`   | 5            | 500ms         | 20s       | Critical operations where fast recovery from transient errors is more important than reducing API calls. |
| `RetryConfig.PRODUCTION`   | 3            | 1s            | 20s       | General production use.                                                                                  |

You can use them directly or create custom configurations:

=== "Kotlin"

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

=== "Java"

    ```java
    // FAILED: Creating a custom RetryConfig from Java is not supported because
    // RetryConfig requires kotlin.time.Duration parameters and has no Java-friendly
    // constructors or builders (@JvmOverloads is not present). Java cannot easily
    // construct kotlin.time.Duration or use Kotlin default/named parameters.
    // Use predefined configs instead, e.g., RetryConfig.Companion.getPRODUCTION().

    String apiKey = System.getenv("OPENAI_API_KEY");
    OpenAILLMClient client = new OpenAILLMClient(apiKey);
    RetryingLLMClient customClient = new RetryingLLMClient(
        client,
        RetryConfig.Companion.getPRODUCTION() // closest Java-accessible alternative
    );
    ```

### Retry error patterns

By default, the `RetryingLLMClient` recognizes common transient errors.
This behavior is controlled by the [`RetryConfig.retryablePatterns`](api:prompt-executor-clients::ai.koog.prompt.executor.clients.retry.RetryConfig.retryablePatterns) patterns.
Each pattern is represented by
[`RetryablePattern`](api:prompt-executor-clients::ai.koog.prompt.executor.clients.retry.RetryablePattern)
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

These default patterns are defined in Koog as [`RetryConfig.DEFAULT_PATTERNS`](api:prompt-executor-clients::ai.koog.prompt.executor.clients.retry.RetryConfig.Companion.DEFAULT_PATTERNS).

#### Custom patterns

You can define custom patterns for your specific needs:

=== "Kotlin"

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

=== "Java"

    ```java
    // Prepare custom patterns
    List<RetryablePattern> patterns = List.of(
        new RetryablePattern.Status(429),
        new RetryablePattern.Keyword("quota"),
        new RetryablePattern.Regex(new kotlin.text.Regex("ERR_\\\d+")),
        new RetryablePattern.Custom(new Function1<String, Boolean>() {
            @Override public Boolean invoke(String error) {
                return error.contains("temporary") && error.length() > 20;
            }
        })
    );

    // FAILED: Constructing RetryConfig with custom patterns from Java is not supported
    // because RetryConfig does not expose Java-friendly constructors and relies on
    // Kotlin default parameters. There is no builder or @JvmOverloads constructor.
    // Use predefined RetryConfig via RetryConfig.Companion (e.g., getPRODUCTION()) instead.
    // RetryConfig config = new RetryConfig(/* not available from Java */);
    ```

You can also append custom patterns to the default `RetryConfig.DEFAULT_PATTERNS`:

=== "Kotlin"

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

=== "Java"

    ```java
    // Start from defaults and append your own
    List<RetryablePattern> defaults = RetryConfig.Companion.getDEFAULT_PATTERNS();
    List<RetryablePattern> augmented = new ArrayList<>(defaults);
    augmented.add(new RetryablePattern.Keyword("custom_error"));

    // FAILED: As above, Java cannot construct a new RetryConfig instance with a custom
    // list due to Kotlin-only constructor defaults and lack of a Java builder.
    // RetryConfig config = new RetryConfig(/* not available from Java */);
    ```


### Streaming with retry

Streaming operations can optionally be retried. This feature is disabled by default.

=== "Kotlin"

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

=== "Java"

    ```java
    // FAILED: Streaming with retry from Java is not directly supported:
    // 1) Creating a custom RetryConfig in Java is not available (no Java builder/overloads).
    // 2) executeStreaming(...) returns a Kotlin Flow<StreamFrame>, and Koog does not
    //    currently expose a Java-friendly streaming wrapper.
    // As a workaround, prefer non-streaming execution via JavaPromptExecutor.
    OpenAILLMClient baseClient = new OpenAILLMClient(System.getenv("OPENAI_API_KEY"));
    Prompt prompt = Prompt.builder("test").user("Generate a story").build();
    RetryingLLMClient client = new RetryingLLMClient(baseClient, RetryConfig.Companion.getPRODUCTION());
    // Flow<StreamFrame> stream = client.executeStreaming(prompt, OpenAIModels.Chat.GPT4o); // not Java-friendly
    ```

!!!note
    Streaming retries only apply to connection failures that occur before the first token is received.
    Once streaming has started, the retry logic is disabled.
    If an error occurs during streaming, the operation is terminated.

### Retry with prompt executors

When working with prompt executors, you can wrap the underlying LLM client with a retry mechanism before creating the executor.
To learn more about prompt executors, see [Prompt executors](prompt-executors.md).

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
    import ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient
    import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
    import ai.koog.prompt.executor.clients.retry.RetryConfig
    import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
    import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
    import ai.koog.prompt.llm.LLMProvider
    import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
    
    -->
    ```kotlin
    // Single provider executor with retry
    val resilientClient = RetryingLLMClient(
        OpenAILLMClient(System.getenv("OPENAI_API_KEY")),
        RetryConfig.PRODUCTION
    )
    val executor = MultiLLMPromptExecutor(resilientClient)

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

=== "Java"

    ```java
    // Single provider executor with retry (Java)
    RetryingLLMClient resilientClient = new RetryingLLMClient(
        new OpenAILLMClient(System.getenv("OPENAI_API_KEY")),
        RetryConfig.Companion.getPRODUCTION()
    );
    JavaPromptExecutor executor = Executors.promptExecutor(resilientClient);

    // Multi-provider executor with flexible client configuration (Java)
    LLMClient openai = new RetryingLLMClient(
        new OpenAILLMClient(System.getenv("OPENAI_API_KEY")),
        RetryConfig.Companion.getCONSERVATIVE()
    );
    LLMClient anthropic = new RetryingLLMClient(
        new AnthropicLLMClient(System.getenv("ANTHROPIC_API_KEY")),
        RetryConfig.Companion.getAGGRESSIVE()
    );
    // FAILED: Creating BedrockLLMClient from Java with StaticCredentialsProvider (AWS Kotlin SDK)
    // is not Java-first and requires Kotlin-specific identity providers; no simple Java overloads.
    // LLMClient bedrock = new BedrockLLMClient(/* identityProvider = ... */);

    Map<LLMProvider, LLMClient> clients = Map.of(
        LLMProvider.OpenAI, openai,
        LLMProvider.Anthropic, anthropic
        // , LLMProvider.Bedrock, bedrock
    );
    JavaPromptExecutor multiExecutor = Executors.promptExecutor(clients);
    ```

## Timeout configuration

All LLM clients support timeout configuration to prevent hanging requests.
You can specify timeout values for network connections when creating the client using
the [`ConnectionTimeoutConfig`](api:prompt-executor-clients::ai.koog.prompt.executor.clients.ConnectionTimeoutConfig) class.

`ConnectionTimeoutConfig` has the following properties:

| Property               | Default Value        | Description                                                   |
|------------------------|----------------------|---------------------------------------------------------------|
| `connectTimeoutMillis` | 60 seconds (60,000)  | Maximum time to establish a connection to the server.         |
| `requestTimeoutMillis` | 15 minutes (900,000) | Maximum time for the entire request to complete.              |
| `socketTimeoutMillis`  | 15 minutes (900,000) | Maximum time to wait for data over an established connection. |

You can customize these values for your specific needs. For example:

=== "Kotlin"

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

=== "Java"

    ```java
    String apiKey = System.getenv("OPENAI_API_KEY");
    ConnectionTimeoutConfig timeouts = new ConnectionTimeoutConfig(
        5000L,   // connectTimeoutMillis
        60000L,  // requestTimeoutMillis
        120000L  // socketTimeoutMillis
    );
    OpenAIClientSettings settings = new OpenAIClientSettings(
        "https://api.openai.com", // baseUrl
        timeouts,
        "v1/chat/completions",    // chatCompletionsPath
        "v1/responses",           // responsesAPIPath
        "v1/embeddings",          // embeddingsPath
        "v1/moderations",         // moderationsPath
        "v1/models"               // modelsPath
    );
    OpenAILLMClient client = new OpenAILLMClient(apiKey, settings);
    ```

!!! tip
    For long-running or streaming calls, set higher values for `requestTimeoutMillis` and `socketTimeoutMillis`.

## Error handling

When working with LLMs in production, you need to implement error handling, including:

- **Try-catch blocks** to handle unexpected errors.
- **Logging errors with context** for debugging.
- **Fallbacks** for critical operations.
- **Monitoring retry patterns** to identify recurring issues.

Here is an example of error handling:

=== "Kotlin"

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

=== "Java"

    ```java
    Logger logger = LoggerFactory.getLogger("Example");
    RetryingLLMClient resilientClient1 = new RetryingLLMClient(
        new OpenAILLMClient(System.getenv("OPENAI_API_KEY")),
        RetryConfig.Companion.getPRODUCTION()
    );
    Prompt prompt1 = Prompt.builder("test")
        .user("Hello")
        .build();
    MultiLLMPromptExecutor exec = new MultiLLMPromptExecutor(resilientClient1);

    // Example helpers (stubs)
    java.util.function.Consumer<List<Message.Response>> processResponse = (resp) -> { /* implementation */ };
    Runnable scheduleRetryLater = () -> { /* implementation */ };
    Runnable notifyAdministrator = () -> { /* implementation */ };
    Runnable useDefaultResponse = () -> { /* implementation */ };

    // FAILED
    // Note: execute() is a suspend function in Kotlin
    // In a Java-first environment, use a blocking wrapper or an asynchronous approach
    /*try {
        List<Message.Response> response = exec.execute(prompt1, OpenAIModels.Chat.GPT4o).get();
        processResponse.accept(response);
    } catch (Exception e) {
        logger.error("LLM operation failed", e);
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (msg.contains("rate limit")) {
            scheduleRetryLater.run();
        } else if (msg.contains("invalid api key")) {
            notifyAdministrator.run();
        } else {
            useDefaultResponse.run();
        }
    }*/
    ```
