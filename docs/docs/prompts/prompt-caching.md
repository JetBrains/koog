# Cached prompt executors

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
