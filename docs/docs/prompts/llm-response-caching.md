# LLM response caching

For repeated requests that you run with a prompt executor,
you can cache LLM responses to optimize performance and reduce costs.
In Koog, caching is available for all prompt executors through `CachedPromptExecutor`, 
which is a wrapper around `PromptExecutor` that adds caching functionality.
It lets you store responses from previously executed prompts and retrieve them when the same prompts are run again.

To create a cached prompt executor, perform the following:

1. Create a prompt executor for which you want to cache responses.
2. Create a `CachedPromptExecutor` instance by providing the desired cache and the prompt executor you created.
3. Run the created `CachedPromptExecutor` with the desired prompt and model.

Here is an example:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
    import ai.koog.prompt.executor.cached.CachedPromptExecutor
    import ai.koog.prompt.cache.files.FilePromptCache
    import kotlin.system.measureTimeMillis
    import ai.koog.prompt.dsl.prompt
    import kotlin.io.path.Path
    
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
    // Create a prompt executor
    val client = OpenAILLMClient(System.getenv("OPENAI_API_KEY"))
    val promptExecutor = MultiLLMPromptExecutor(client)

    // Create a cached prompt executor
    val cachedExecutor = CachedPromptExecutor(
        cache = FilePromptCache(Path("path/to/your/cache/directory")),
        nested = promptExecutor
    )

    // Run cached prompt executor for the first time
    // This will perform an actual LLM request
    val firstTime = measureTimeMillis {
        val firstResponse = cachedExecutor.execute(prompt, OpenAIModels.Chat.GPT4o)
        println("First response: ${firstResponse.first().content}")
    }
    println("First execution took: ${firstTime}ms")

    // Run cached prompt executor for the second time
    // This will return the result immediately from the cache
    val secondTime = measureTimeMillis {
        val secondResponse = cachedExecutor.execute(prompt, OpenAIModels.Chat.GPT4o)
        println("Second response: ${secondResponse.first().content}")
    }
    println("Second execution took: ${secondTime}ms")
    ```
    <!--- KNIT example-llm-response-caching-01.kt -->

=== "Java"

    ```java
    // Create a prompt
    Prompt prompt = Prompt.builder("test")
        .user("Hello")
        .build();

    // Create a prompt executor (OpenAI client)
    OpenAILLMClient client = new OpenAILLMClient(System.getenv("OPENAI_API_KEY"));
    MultiLLMPromptExecutor promptExecutor = new MultiLLMPromptExecutor(client);

    // Create a cached prompt executor
    // Note: In Java, Kotlin default parameters are not available.
    // FilePromptCache requires both (Path storage, Integer maxFiles). Pass null to keep default behavior, or a number like 3000.
    FilePromptCache cache = new FilePromptCache(Path.of("path/to/your/cache/directory"), null);
    // CachedPromptExecutor requires (PromptCache cache, PromptExecutor nested, Clock clock) in Java.
    CachedPromptExecutor cachedExecutor = new CachedPromptExecutor(cache, promptExecutor, Clock.System.INSTANCE);

    // Kotlin sample uses cachedExecutor.execute() because it’s inside runBlocking { ... } and 
    // can call suspend functions. Java cannot call suspend APIs directly, so you need to use JavaPromptExecutor.

    // Convert to Java-friendly executor for async execution
    JavaPromptExecutor javaExecutor = JavaPromptExecutorKt.asJava(cachedExecutor);

    // Run cached prompt executor for the first time (async, then block to get the result)
    long start1 = System.nanoTime();
    CompletableFuture<List<Message.Response>> future1 = javaExecutor.executeAsync(prompt, OpenAIModels.Chat.GPT4o);
    List<Message.Response> firstResponse = future1.get();
    long firstTimeMs = (System.nanoTime() - start1) / 1_000_000L;
    System.out.println("First response: " + firstResponse.getFirst().getContent());
    System.out.println("First execution took: " + firstTimeMs + "ms");

    // Run cached prompt executor for the second time (should be fast due to cache)
    long start2 = System.nanoTime();
    CompletableFuture<List<Message.Response>> future2 = javaExecutor.executeAsync(prompt, OpenAIModels.Chat.GPT4o);
    List<Message.Response> secondResponse = future2.get();
    long secondTimeMs = (System.nanoTime() - start2) / 1_000_000L;
    System.out.println("Second response: " + secondResponse.getFirst().getContent());
    System.out.println("Second execution took: " + secondTimeMs + "ms");
    ```

<!--TODO: Check if cachedPromptExecutor.execute() works. If it is not, continue using JavaPromptExecutor.executeAsync(...) -->

The example produces the following output:

```
First response: Hello! It seems like we're starting a new conversation. What can I help you with today?
First execution took: 48ms
Second response: Hello! It seems like we're starting a new conversation. What can I help you with today?
Second execution took: 1ms
```
The second response is retrieved from the cache, which took only 1ms.

!!!note
    * If you call `executeStreaming()` with the cached prompt executor, it produces a response as a single chunk.
    * If you call `moderate()` with the cached prompt executor, it forwards the request to the nested prompt executor and does not use the cache.
    * Caching of multiple choice responses (`executeMultipleChoice()`) is not supported.
