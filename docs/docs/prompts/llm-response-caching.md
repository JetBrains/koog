# LLM response caching

For repeated requests that you run with a prompt executor,
you can cache LLM responses to optimize performance and reduce costs in both Kotlin and Java.
In Koog, caching is available for all prompt executors through `CachedPromptExecutor`, 
which is a wrapper around `PromptExecutor` that adds caching functionality.
It lets you store responses from previously executed prompts and retrieve them when the same prompts are run again.

To create a cached prompt executor in Kotlin or Java, perform the following:

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
    import ai.koog.prompt.message.MessagePart
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
        val text = firstResponse.parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text }
        println("First response: $text")
    }
    println("First execution took: ${firstTime}ms")

    // Run cached prompt executor for the second time
    // This will return the result immediately from the cache
    val secondTime = measureTimeMillis {
        val secondResponse = cachedExecutor.execute(prompt, OpenAIModels.Chat.GPT4o)
        val text = secondResponse.parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text }
        println("Second response: $text")
    }
    println("Second execution took: ${secondTime}ms")
    ```
    <!--- KNIT example-llm-response-caching-01.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    // Create a prompt
    Prompt prompt = Prompt.builder("test")
            .user("Hello")
            .build();

    // Create a prompt executor
    OpenAILLMClient client = openAIClient(System.getenv("OPENAI_API_KEY"));
    MultiLLMPromptExecutor promptExecutor = new MultiLLMPromptExecutor(client);

    // Create a cached prompt executor
    FilePromptCache cache = new FilePromptCache(Path.of("path/to/your/cache/directory"), null);
    CachedPromptExecutor cachedExecutor = new CachedPromptExecutor(cache, promptExecutor, Clock.System.INSTANCE);

    // Run cached prompt executor for the first time
    // This will perform an actual LLM request
    long start1 = System.nanoTime();
    List<Message.Response> firstResponse = cachedExecutor.execute(prompt, OllamaModels.Meta.LLAMA_3_2);
    long firstTimeMs = (System.nanoTime() - start1) / 1_000_000L;
    System.out.println("First response: " + firstResponse.getFirst().getContent());
    System.out.println("First execution took: " + firstTimeMs + "ms");

    // Run cached prompt executor for the second time
    // This will return the result immediately from the cache
    long start2 = System.nanoTime();
    List<Message.Response> secondResponse = cachedExecutor.execute(prompt, OllamaModels.Meta.LLAMA_3_2);
    long secondTimeMs = (System.nanoTime() - start2) / 1_000_000L;
    System.out.println("Second response: " + secondResponse.getFirst().getContent());
    System.out.println("Second execution took: " + secondTimeMs + "ms");
    ```
    <!--- KNIT example-llm-response-caching-java-01.java -->

The example produces the following output:

```
First response: Hello! It seems like we're starting a new conversation. What can I help you with today?
First execution took: 48ms
Second response: Hello! It seems like we're starting a new conversation. What can I help you with today?
Second execution took: 1ms
```
The second response is retrieved from the cache, which took only 1ms.

!!!note
    * If you call `executeStreaming()` in Kotlin or `executeStreamingWithPublisher()` in Java with the cached prompt executor, it produces a response as a single chunk.
    * If you call `moderate()` with the cached prompt executor in either Kotlin or Java, it forwards the request to the nested prompt executor and does not use the cache.
    * Caching of multiple choice responses (`executeMultipleChoices()`) is not supported in either Kotlin or Java.
