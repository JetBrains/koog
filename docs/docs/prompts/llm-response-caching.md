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
val client = OpenAILLMClient(System.getenv("OPENAI_KEY"))
val promptExecutor = SingleLLMPromptExecutor(client)

// Create a cached prompt executor
val cachedExecutor = CachedPromptExecutor(
    cache = FilePromptCache(Path("/cache_directory")),
    nested = promptExecutor
)

// Run the cached prompt executor
val response = cachedExecutor.execute(prompt, OpenAIModels.Chat.GPT4o)
```
<!--- KNIT example-llm-response-caching-01.kt -->

Now you can run the same prompt with the same model multiple times. The response will be retrieved from the cache.

!!!note
    * If you call `executeStreaming()` with the cached prompt executor, it produces a response as a single chunk.
    * If you call `moderate()` with the cached prompt executor, it forwards the request to the nested prompt executor and does not use the cache.
    * Caching of multiple choice responses (`executeMultipleChoice()`) is not supported.
