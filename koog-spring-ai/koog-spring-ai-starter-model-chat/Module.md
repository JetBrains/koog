# Module koog-spring-ai-starter-model-chat

Adapts a Spring AI `ChatModel` (with optional `ModerationModel`) into a Koog `LLMClient` and `PromptExecutor`.

### Overview

This starter bridges Spring AI's chat model abstraction with the Koog agent framework.
It auto-configures:

- A Koog `LLMClient` (`SpringAiLLMClient`) that delegates to a Spring AI `ChatModel`
- A `PromptExecutor` (`MultiLLMPromptExecutor`) assembled from all available `LLMClient` beans

Tools are always executed by the Koog agent framework — Spring AI receives only tool
definitions/schema. The `internalToolExecutionEnabled` flag is set to `false` on all
tool-carrying requests.

### Using in your project

Add the dependency alongside any Spring AI model starter (e.g., for Ollama):

```kotlin
// build.gradle.kts
dependencies {
    implementation("ai.koog:koog-agents-jvm:$koogVersion")
    implementation("ai.koog:koog-spring-ai-starter-model-chat:$koogVersion")
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
}
```

Modifying your Spring Boot properties is not necessary, below are the default settings:

```properties
# application.properties defaults
spring.ai.model.chat=ollama
koog.spring.ai.chat.enabled=true
koog.spring.ai.chat.dispatcher.type=AUTO
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
            llmModel = OllamaModels.Meta.LLAMA_3_2,
            systemPrompt = "You are a helpful assistant."
        )

        return agent.run(userMessage)
    }
}
```

Or provide your own `PromptExecutor` bean to override the auto-configured one entirely.

### Configuration properties (`koog.spring.ai.chat`)

| Property | Type | Default | Description |
|---|---|---|---|
| `enabled` | `Boolean` | `true` | Enable/disable the chat auto-configuration |
| `chat-model-bean-name` | `String?` | `null` | Bean name of the `ChatModel` to use (for multi-model contexts) |
| `moderation-model-bean-name` | `String?` | `null` | Bean name of the `ModerationModel` to use (for multi-model contexts) |
| `provider` | `String?` | `null` | LLM provider id (e.g. `openai`, `anthropic`, `google`). When set, overrides auto-detection from the `ChatModel` class name. Falls back to `spring-ai` if auto-detection fails. |
| `dispatcher.type` | `AUTO` / `IO` | `AUTO` | Dispatcher for blocking model calls |
| `dispatcher.parallelism` | `Int` | `0` (= unbounded) | Max concurrency for `IO` dispatcher (0 = no limit) |

### Dispatcher types

- **`AUTO`** (default): Uses a Spring-managed `AsyncTaskExecutor` if available (e.g., when `spring.threads.virtual.enabled=true` in Spring Boot 3.2+), otherwise falls back to `Dispatchers.IO`. This lets you opt into virtual threads with a single standard Spring Boot property.
- **`IO`**: Always uses `Dispatchers.IO`. When `dispatcher.parallelism` is greater than 0, uses `Dispatchers.IO.limitedParallelism(parallelism)` to cap concurrency.

### Multi-model contexts

When multiple `ChatModel` or `ModerationModel` beans are registered, specify which one to use:

```properties
koog.spring.ai.chat.chat-model-bean-name=openAiChatModel
koog.spring.ai.chat.moderation-model-bean-name=openAiModerationModel
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
