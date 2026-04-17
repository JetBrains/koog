# Module koog-spring-ai-starter-chat-memory

Adapts a Spring AI `ChatMemoryRepository` into a Koog `ChatHistoryProvider` for **conversation text memory**.

### Overview

This starter bridges Spring AI's chat memory abstraction with the Koog agent framework,
providing **text-only conversation persistence** — not full Koog prompt-state persistence.

It auto-configures:

- A Koog `ChatHistoryProvider` (`SpringAiChatHistoryProvider`) that delegates to a Spring AI `ChatMemoryRepository`

This lets you reuse any Spring AI–compatible chat memory store (JDBC, Redis, Cassandra, etc.)
as the backing storage for Koog agent conversation history.

### Text-only contract

Only plain-text System, User, and Assistant messages are persisted. The following are
**silently dropped on store** (they are non-persistable transport/runtime events):

- `Message.Tool.Call`
- `Message.Tool.Result`
- `Message.Reasoning`
- Any message carrying attachments (images, audio, video, files)

On **load**, Spring AI `TOOL` rows (e.g., from JDBC repositories that previously stored
tool traffic) are silently skipped rather than causing an error.

Metadata fields (timestamps, token counts, finish reasons, custom metadata) are **not**
preserved through the round-trip.

> **Note:** If your agent uses tools, the tool call/result exchange will not appear in
> the persisted history. The adapter is designed for conversational text recall, not for
> replaying full agent execution traces.

### Using in your project

Add the dependency alongside any Spring AI chat memory repository implementation:

```kotlin
// build.gradle.kts
dependencies {
    implementation("ai.koog:koog-agents-jvm:$koogVersion")
    implementation("ai.koog:koog-spring-ai-starter-chat-memory:$koogVersion")
    // e.g. JDBC-backed chat memory
    implementation("org.springframework.ai:spring-ai-starter-model-chat-memory-repository-jdbc")
}
```

Modifying your Spring Boot properties is not necessary, below are the default settings:

```properties
# application.properties defaults
koog.spring.ai.chat-memory.enabled=true
koog.spring.ai.chat-memory.dispatcher.type=AUTO
```

If you have a single `ChatMemoryRepository` bean, everything works automatically —
the adapter wraps it into a Koog `ChatHistoryProvider`.

### Example of usage

Install the `ChatMemory` feature on your agent using the auto-configured `ChatHistoryProvider`:

```kotlin
@Service
class MyAgentService(
    private val promptExecutor: PromptExecutor,
    private val chatHistoryProvider: ChatHistoryProvider,
) {

    suspend fun askAgent(userMessage: String): String {
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = OllamaModels.Meta.LLAMA_3_2,
            systemPrompt = "You are a helpful assistant.",
        ) {
            install(ChatMemory) {
                historyProvider = chatHistoryProvider
            }
        }

        return agent.run(userMessage)
    }
}
```

### Configuration properties (`koog.spring.ai.chat-memory`)

| Property | Type | Default | Description |
|---|---|---|---|
| `enabled` | `Boolean` | `true` | Enable/disable the chat memory auto-configuration |
| `chat-memory-repository-bean-name` | `String?` | `null` | Bean name of the `ChatMemoryRepository` to use (for multi-repository contexts) |
| `dispatcher.type` | `AUTO` / `IO` | `AUTO` | Dispatcher for blocking repository calls |
| `dispatcher.parallelism` | `Int` | `0` (= unbounded) | Max concurrency for `IO` dispatcher (0 = no limit) |

### Dispatcher types

- **`AUTO`** (default): Uses a Spring-managed `AsyncTaskExecutor` if available (e.g., when `spring.threads.virtual.enabled=true` in Spring Boot 3.2+), otherwise falls back to `Dispatchers.IO`. This lets you opt into virtual threads with a single standard Spring Boot property.
- **`IO`**: Always uses `Dispatchers.IO`. When `dispatcher.parallelism` is greater than 0, uses `Dispatchers.IO.limitedParallelism(parallelism)` to cap concurrency.

### Multi-repository contexts

When multiple `ChatMemoryRepository` beans are registered, specify which one to use:

```properties
koog.spring.ai.chat-memory.chat-memory-repository-bean-name=jdbcChatMemoryRepository
```

Without a selector, the auto-configuration activates only when a single candidate exists.
