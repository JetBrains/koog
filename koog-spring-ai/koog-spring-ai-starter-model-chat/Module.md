# Module koog-spring-ai-starter-model-chat

Adapts a Spring AI `ChatModel` (with optional `ModerationModel`) into a Koog `LLMClient` and `PromptExecutor`.

### Overview

This starter bridges Spring AI's chat model abstraction with the Koog agent framework.
It auto-configures:

- A Koog `LLMClient` (`SpringAILLMClient`) that delegates to a Spring AI `ChatModel`
- A `PromptExecutor` (`MultiLLMPromptExecutor`) assembled from all available `LLMClient` beans

Tools are always executed by the Koog agent framework — Spring AI receives only tool
definitions/schema. The `internalToolExecutionEnabled` flag is set to `false` on all
tool-carrying requests.

### Using in your project

Add the dependency alongside any Spring AI model starter (e.g., Ollama):

```kotlin
// build.gradle.kts
dependencies {
    implementation("ai.koog:koog-agents-jvm:$koogVersion")
    implementation("ai.koog:koog-spring-ai-starter-model-chat:$koogVersion")
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
}
```

Modify your Spring Boot configuration:

```properties
# application.properties
spring.application.name=testapp
spring.ai.model.chat=ollama
spring.ai.ollama.chat.options.model=llama3.2:1b
koog.spring-ai.chat.enabled=true
```

If you have a single `ChatModel` bean, everything works automatically —
the adapter wraps it into a Koog `LLMClient` and creates a ready-to-use `PromptExecutor`.

### Example of usage

Inject the `PromptExecutor` and use it to run a Koog agent:

```kotlin
@Service
class MyAgentService(private val promptExecutor: PromptExecutor) {

    suspend fun askAgent(userMessage: String): String {
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = promptExecutor.models().first(),
            systemPrompt = "You are a helpful assistant."
        )

        return agent.run(userMessage)
    }
}
```

Or provide your own `PromptExecutor` bean to override the auto-configured one entirely.

### Configuration properties (`koog.spring-ai.chat`)

| Property | Type | Default | Description |
|---|---|---|---|
| `enabled` | `Boolean` | `true` | Enable/disable the chat auto-configuration |
| `chat-model-bean-name` | `String?` | `null` | Bean name of the `ChatModel` to use (for multi-model contexts) |
| `moderation-model-bean-name` | `String?` | `null` | Bean name of the `ModerationModel` to use (for multi-model contexts) |
| `dispatcher.type` | `AUTO` / `IO` / `FIXED_THREAD_POOL` | `AUTO` | Dispatcher for blocking model calls |
| `dispatcher.parallelism` | `Int` | `0` (= CPU count) | Thread pool size (for `FIXED_THREAD_POOL`) |

### Dispatcher types

- **`AUTO`** (default): Uses a Spring-managed `AsyncTaskExecutor` if available (e.g., when `spring.threads.virtual.enabled=true` in Spring Boot 3.2+), otherwise falls back to `Dispatchers.IO`. This lets you opt into virtual threads with a single standard Spring Boot property.
- **`IO`**: Always uses `Dispatchers.IO`.
- **`FIXED_THREAD_POOL`**: Uses a fixed-size thread pool with `dispatcher.parallelism` threads. The pool is shut down automatically when the application context closes.

### Multi-model contexts

When multiple `ChatModel` or `ModerationModel` beans are registered, specify which one to use:

```properties
koog.spring-ai.chat.chat-model-bean-name=openAiChatModel
koog.spring-ai.chat.moderation-model-bean-name=openAiModerationModel
```

Without a selector, the auto-configuration activates only when a single candidate exists.

### Extension points

- **`ChatOptionsCustomizer`**: Register a Spring bean implementing this functional interface to apply provider-specific `ChatOptions` tuning:

  ```kotlin
  @Bean
  fun chatOptionsCustomizer() = ChatOptionsCustomizer { options, params, model ->
      // Apply custom options based on the model or request parameters
      options
  }
  ```

  The auto-configuration picks it up automatically via optional injection.

- **Custom `LLMClient`**: Register your own `LLMClient` bean to override the auto-configured adapter entirely.
- **Custom `PromptExecutor`**: Register your own `PromptExecutor` bean to override the auto-configured `MultiLLMPromptExecutor`.
