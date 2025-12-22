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

<!--- INCLUDE
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
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
val promptExecutor = SingleLLMPromptExecutor(client)

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
