# Prompt caching control

Prompt caching control lets you instruct a supported LLM provider to store a portion of
your prompt server-side, so that subsequent requests that share the same prefix can be
served from the cache instead of reprocessing the tokens.
This reduces both latency and cost for repetitive workloads such as multi-turn conversations,
large system prompts, or fixed tool definitions.

!!! note "Prompt caching vs. response caching"
    Prompt caching control is a **provider-side** feature: the provider stores the prompt prefix,
    not the response. This is different from [`CachedPromptExecutor`](../llm-response-caching.md),
    which stores complete LLM responses locally so that identical prompts skip the network call entirely.

Koog supports prompt caching control for **Anthropic** and **Amazon Bedrock**.

## Anthropic

Anthropic supports two complementary approaches to prompt caching.

### Automatic caching (request-level)

Set the `cacheControl` property on [`AnthropicParams`](../../llm-parameters.md) and pass it to your prompt.
Anthropic will automatically place the cache breakpoint at the last cacheable block in the
request, without you having to annotate individual messages.
This is the recommended approach for multi-turn conversations.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.prompt.dsl.prompt
    import ai.koog.prompt.executor.clients.anthropic.AnthropicCacheControl
    import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
    import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
    import ai.koog.prompt.executor.clients.anthropic.AnthropicParams
    import kotlinx.coroutines.runBlocking

    fun main() = runBlocking {
        val client = AnthropicLLMClient(System.getenv("ANTHROPIC_API_KEY"))
    -->
    <!--- SUFFIX
    }
    -->

    ```kotlin
    // Enable automatic caching with the default 5-minute TTL
    val params = AnthropicParams(cacheControl = AnthropicCacheControl.Default)

    val prompt = prompt("assistant", params = params) {
        system("You are a helpful assistant with a very long system prompt...")
        user("What can you help me with?")
    }

    val response = client.execute(prompt, AnthropicModels.Sonnet_4)
    println(response)
    ```
    <!--- KNIT example-cache-control-01.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    // Enable automatic caching with the default 5-minute TTL
    AnthropicParams params = new AnthropicParams(
        null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null,
        AnthropicCacheControl.Default.INSTANCE
    );

    Prompt prompt = Prompt.builder("assistant")
        .system("You are a helpful assistant with a very long system prompt...")
        .user("What can you help me with?")
        .build()
        .withParams(params);
    ```
    <!--- KNIT example-cache-control-java-01.java -->

### Manual caching (block-level)

Attach a `cacheControl` argument to individual messages or tool definitions to place the
cache breakpoint at a specific position. Everything up to and including the annotated block
is eligible for caching.

#### System messages

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.prompt.dsl.prompt
    import ai.koog.prompt.executor.clients.anthropic.AnthropicCacheControl
    import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
    import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
    import kotlinx.coroutines.runBlocking

    fun main() = runBlocking {
        val client = AnthropicLLMClient(System.getenv("ANTHROPIC_API_KEY"))
    -->
    <!--- SUFFIX
    }
    -->

    ```kotlin
    val prompt = prompt("assistant") {
        // Cache the system prompt for 1 hour
        system("You are a knowledgeable assistant...", AnthropicCacheControl.OneHour)
        user("Summarize the latest AI research.")
    }

    val response = client.execute(prompt, AnthropicModels.Sonnet_4)
    println(response)
    ```
    <!--- KNIT example-cache-control-02.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    Prompt prompt = Prompt.builder("assistant")
        // Cache the system prompt for 1 hour
        .system("You are a knowledgeable assistant...", AnthropicCacheControl.OneHour.INSTANCE)
        .user("Summarize the latest AI research.")
        .build();
    ```
    <!--- KNIT example-cache-control-java-02.java -->

#### User and assistant messages

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.prompt.dsl.prompt
    import ai.koog.prompt.executor.clients.anthropic.AnthropicCacheControl
    import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
    import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
    import ai.koog.prompt.message.ContentPart
    import kotlinx.coroutines.runBlocking

    fun main() = runBlocking {
        val client = AnthropicLLMClient(System.getenv("ANTHROPIC_API_KEY"))
    -->
    <!--- SUFFIX
    }
    -->

    ```kotlin
    val prompt = prompt("conversation") {
        system("You are a helpful assistant.")
        // Cache after a large user message (e.g. document content)
        user(listOf(ContentPart.Text("Here is a long document: ...")), AnthropicCacheControl.Default)
        assistant("I have read the document.", AnthropicCacheControl.Default)
        user("Summarize it.")
    }

    val response = client.execute(prompt, AnthropicModels.Sonnet_4)
    println(response)
    ```
    <!--- KNIT example-cache-control-03.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    Prompt prompt = Prompt.builder("conversation")
        .system("You are a helpful assistant.")
        // Cache after a large user message (e.g. document content)
        .user(List.of(new ContentPart.Text("Here is a long document: ...")), AnthropicCacheControl.Default.INSTANCE)
        .assistant("I have read the document.", AnthropicCacheControl.Default.INSTANCE)
        .user("Summarize it.")
        .build();
    ```
    <!--- KNIT example-cache-control-java-03.java -->

#### Tool definitions

When a tool list is fixed across many requests, caching the last tool definition means all
tool schemas are cached together.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.tools.ToolDescriptor
    import ai.koog.agents.core.tools.ToolParameterDescriptor
    import ai.koog.agents.core.tools.ToolParameterType
    import ai.koog.prompt.executor.clients.anthropic.AnthropicCacheControl
    -->

    ```kotlin
    val searchTool = ToolDescriptor(
        name = "web_search",
        description = "Search the web for information.",
        requiredParameters = listOf(
            ToolParameterDescriptor("query", "Search query", ToolParameterType.String)
        ),
        // Cache all tool definitions up to and including this one
        cacheControl = AnthropicCacheControl.Default
    )
    ```
    <!--- KNIT example-cache-control-04.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    ToolDescriptor searchTool = new ToolDescriptor(
        "web_search",
        "Search the web for information.",
        List.of(
            new ToolParameterDescriptor("query", "Search query", ToolParameterType.String.INSTANCE)
        ),
        Collections.emptyList(),
        // Cache all tool definitions up to and including this one
        AnthropicCacheControl.Default.INSTANCE
    );
    ```
    <!--- KNIT example-cache-control-java-04.java -->

### Cache TTL options

| Option                        | TTL      | Price multiplier        |
|-------------------------------|----------|-------------------------|
| `AnthropicCacheControl.Default`  | 5 minutes | 1.25× base input price  |
| `AnthropicCacheControl.OneHour`  | 1 hour    | 2× base input price     |

Cache writes are charged at a higher rate than regular input tokens, but cache reads are cheaper.
See the [Anthropic prompt caching docs](https://docs.anthropic.com/en/docs/build-with-claude/prompt-caching)
for current pricing.

### Monitoring cache usage

Anthropic reports cache statistics in the response usage. These are accessible via the
raw API response and can be observed through tracing or logging features.

| Field                       | Meaning                                              |
|-----------------------------|------------------------------------------------------|
| `cacheReadInputTokens`      | Tokens read from an existing cache entry             |
| `cacheCreationInputTokens`  | Tokens written to a new cache entry                  |

### Combining automatic and block-level caching

Both modes can be used simultaneously. Block-level `cacheControl` markers give you fine-grained
control over breakpoint positions, while the request-level `cacheControl` in `AnthropicParams`
handles the tail of the conversation automatically.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.prompt.dsl.prompt
    import ai.koog.prompt.executor.clients.anthropic.AnthropicCacheControl
    import ai.koog.prompt.executor.clients.anthropic.AnthropicParams
    -->

    ```kotlin
    // Block-level: pin the system prompt in the 1-hour cache tier
    // Automatic: let Anthropic manage breakpoints for the conversation tail
    val params = AnthropicParams(cacheControl = AnthropicCacheControl.Default)

    val prompt = prompt("combined", params = params) {
        system("You are a helpful assistant...", AnthropicCacheControl.OneHour)
        user("Hello!")
    }
    ```
    <!--- KNIT example-cache-control-05.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    // Block-level: pin the system prompt in the 1-hour cache tier
    // Automatic: let Anthropic manage breakpoints for the conversation tail
    AnthropicParams params = new AnthropicParams(
        AnthropicCacheControl.Default.INSTANCE
    );

    Prompt prompt = Prompt.builder("combined")
        .system("You are a helpful assistant...", AnthropicCacheControl.OneHour.INSTANCE)
        .user("Hello!")
        .build()
        .withParams(params);
    ```
    <!--- KNIT example-cache-control-java-05.java -->

---

## Amazon Bedrock

Amazon Bedrock uses a block-level caching model via the Converse API.
When `cacheControl` is set on a message or tool, Bedrock inserts a `CachePoint` block
immediately after the annotated element.

!!! note
    Bedrock prompt caching is a JVM-only feature, as the Bedrock client itself is JVM-only.

### System messages

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.prompt.dsl.prompt
    import ai.koog.prompt.executor.clients.bedrock.BedrockCacheControl
    import ai.koog.prompt.executor.clients.bedrock.BedrockClientSettings
    import ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient
    import ai.koog.prompt.executor.clients.bedrock.BedrockModels
    import ai.koog.prompt.executor.clients.bedrock.BedrockRegions
    import ai.koog.prompt.executor.clients.bedrock.StaticBearerTokenProvider
    import kotlinx.coroutines.runBlocking
    
    fun main() = runBlocking {
        val client = BedrockLLMClient(
            identityProvider = StaticBearerTokenProvider(token = "test-token"),
            settings = BedrockClientSettings(region = BedrockRegions.US_EAST_1.regionCode),
        )
    -->
    <!--- SUFFIX
    }
    -->

    ```kotlin
    val prompt = prompt("assistant") {
        // Cache the system prompt using the default TTL
        system("You are a knowledgeable assistant...", BedrockCacheControl.Default)
        user("What is prompt caching?")
    }

    val response = client.execute(prompt, BedrockModels.AnthropicClaude4Sonnet)
    println(response)
    ```
    <!--- KNIT example-cache-control-06.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    Prompt prompt = Prompt.builder("assistant")
        // Cache the system prompt using the default TTL
        .system("You are a knowledgeable assistant...", BedrockCacheControl.Default.INSTANCE)
        .user("What is prompt caching?")
        .build();
    ```
    <!--- KNIT example-cache-control-java-06.java -->

### User and assistant messages

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.prompt.dsl.prompt
    import ai.koog.prompt.executor.clients.bedrock.BedrockCacheControl
    import ai.koog.prompt.executor.clients.bedrock.BedrockClientSettings
    import ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient
    import ai.koog.prompt.executor.clients.bedrock.BedrockModels
    import ai.koog.prompt.executor.clients.bedrock.BedrockRegions
    import ai.koog.prompt.executor.clients.bedrock.StaticBearerTokenProvider
    import kotlinx.coroutines.runBlocking
    
    fun main() = runBlocking {
        val client = BedrockLLMClient(
            identityProvider = StaticBearerTokenProvider(token = "test-token"),
            settings = BedrockClientSettings(region = BedrockRegions.US_EAST_1.regionCode),
        )
    -->
    <!--- SUFFIX
    }
    -->

    ```kotlin
    val prompt = prompt("conversation") {
        system("You are a helpful assistant.")
        // Cache after the large context message
        user("Here is the document: ...", BedrockCacheControl.FiveMinutes)
        assistant("I have read the document.", BedrockCacheControl.Default)
        user("Summarize it.")
    }

    val response = client.execute(prompt, BedrockModels.AnthropicClaude4Sonnet)
    println(response)
    ```
    <!--- KNIT example-cache-control-07.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    Prompt prompt = Prompt.builder("conversation")
        .system("You are a helpful assistant.")
        // Cache after the large context message
        .user("Here is the document: ...", BedrockCacheControl.FiveMinutes.INSTANCE)
        .assistant("I have read the document.", BedrockCacheControl.Default.INSTANCE)
        .user("Summarize it.")
        .build();
    ```
    <!--- KNIT example-cache-control-java-07.java -->

### Tool definitions

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.tools.ToolDescriptor
    import ai.koog.agents.core.tools.ToolParameterDescriptor
    import ai.koog.agents.core.tools.ToolParameterType
    import ai.koog.prompt.executor.clients.bedrock.BedrockCacheControl
    -->

    ```kotlin
    val searchTool = ToolDescriptor(
        name = "web_search",
        description = "Search the web for information.",
        requiredParameters = listOf(
            ToolParameterDescriptor("query", "Search query", ToolParameterType.String)
        ),
        // Cache all tool definitions up to and including this one
        cacheControl = BedrockCacheControl.Default
    )
    ```
    <!--- KNIT example-cache-control-08.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    ToolDescriptor searchTool = new ToolDescriptor(
        "web_search",
        "Search the web for information.",
        List.of(
            new ToolParameterDescriptor("query", "Search query", ToolParameterType.String.INSTANCE)
        ),
        Collections.emptyList(),
        // Cache all tool definitions up to and including this one
        BedrockCacheControl.Default.INSTANCE
    );
    ```
    <!--- KNIT example-cache-control-java-08.java -->

### Cache TTL options

| Option                         | TTL       |
|--------------------------------|-----------|
| `BedrockCacheControl.Default`  | Provider default (no explicit TTL sent) |
| `BedrockCacheControl.FiveMinutes` | 5 minutes |
| `BedrockCacheControl.OneHour`  | 1 hour    |

See the [Amazon Bedrock prompt caching docs](https://docs.aws.amazon.com/bedrock/latest/userguide/prompt-caching.html)
for supported models and pricing.

---

## Choosing a caching strategy

| Situation                                         | Recommended approach                                        |
|---------------------------------------------------|-------------------------------------------------------------|
| Multi-turn chat with a large, fixed system prompt | Anthropic automatic caching or Bedrock block-level on system |
| Stable tool definitions reused across requests    | Block-level `cacheControl` on the last tool definition      |
| Long document passed as user context              | Block-level `cacheControl` on the user message              |
| Arbitrary multi-turn conversation (Anthropic)     | Automatic caching via `AnthropicParams.cacheControl`        |
| Need 1-hour cache retention                       | `AnthropicCacheControl.OneHour` / `BedrockCacheControl.OneHour` |
