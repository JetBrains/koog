# Chat Memory

## Feature overview

The ChatMemory feature gives AI agents persistent conversation history across multiple runs.
When installed, the agent automatically loads previous messages at the start of each run and
stores the updated conversation when the run completes — enabling natural multi-turn chat.

> **ChatMemory vs AgentMemory**: ChatMemory stores raw message history (the actual user/assistant
> messages). [AgentMemory](agent-memory.md) extracts and stores structured *facts* from conversations.
> They serve different purposes and can be used together.

### Key capabilities

- Automatic load/store of conversation history per session ID
- Pluggable storage backend via `ChatHistoryProvider`
- Built-in preprocessors to limit history size and filter messages
- Custom preprocessor support for arbitrary message transformations

## Configuration and initialization

### Basic setup (Kotlin)

Install ChatMemory using the `installChatMemory` DSL shortcut inside an agent block.
By default it uses an in-memory provider with no preprocessors:

```kotlin
val agent = AIAgent(
    promptExecutor = executor,
    llmModel = OpenAIModels.Chat.GPT4oMini,
    systemPrompt = "You are a helpful assistant.",
) {
    install(ChatMemory)
}
```

To configure a custom provider and preprocessors:

```kotlin
val agent = AIAgent(
    promptExecutor = executor,
    llmModel = OpenAIModels.Chat.GPT4oMini,
    systemPrompt = "You are a helpful assistant.",
) {
    install(ChatMemory) {
        chatHistoryProvider = MyDatabaseProvider()
        windowSize(20)
        filterMessages { it is Message.User || it is Message.Assistant }
    }
}
```

### Running with session IDs

The second argument to `agent.run()` is the session ID that ChatMemory uses to key conversations:

```kotlin
// First turn
agent.run("What is the capital of France?", "session-1")

// Second turn — the agent sees the previous exchange
agent.run("And what about Germany?", "session-1")
```

Different session IDs produce fully isolated histories.

## Preprocessors

Preprocessors transform the message list at both load time (before the agent sees it)
and store time (before persisting). They run sequentially in the order they were added.

### Built-in preprocessors

| Config method | Preprocessor class | Behavior |
|---|---|---|
| `windowSize(n)` | `WindowSizePreProcessor` | Keeps only the last `n` messages |
| `filterMessages { ... }` | `FilterMessagesPreProcessor` | Keeps messages matching the predicate |

### Ordering matters

Preprocessors are chained — each one's output is the next one's input:

```kotlin
// Effect: keep last 10 messages, then filter short ones from those 10
windowSize(10)
filterMessages { it.content.length <= 100 }

// Effect: filter short messages first, then keep last 10 of the survivors
filterMessages { it.content.length <= 100 }
windowSize(10)
```

### Custom preprocessors

Implement the `ChatMemoryPreProcessor` interface:

```kotlin
class RedactEmailsPreProcessor : ChatMemoryPreProcessor {
    override fun preprocess(messages: List<Message>): List<Message> {
        return messages.map { message ->
            // Replace email addresses in message content
            Message.User(message.content.replace(Regex("[\\w.]+@[\\w.]+"), "[REDACTED]"))
        }
    }
}
```

Then add it to the config:

```kotlin
install(ChatMemory) {
    addPreProcessor(RedactEmailsPreProcessor())
    windowSize(50)
}
```

## Custom history providers

The default `InMemoryChatHistoryProvider` is thread-safe but non-persistent.
For production use, implement `ChatHistoryProvider`:

```kotlin
class DatabaseChatHistoryProvider(private val db: Database) : ChatHistoryProvider {
    override suspend fun store(conversationId: String, messages: List<Message>) {
        db.saveMessages(conversationId, messages)
    }

    override suspend fun load(conversationId: String): List<Message> {
        return db.loadMessages(conversationId) ?: emptyList()
    }
}
```

Key contract:
- `store` replaces the entire history for the given `conversationId` (not append-only)
- `load` returns an empty list when no history exists (never null)
- Both methods are `suspend`, so async I/O is safe

## Java API

All config methods return `ChatMemoryConfig` for fluent chaining:

```java
AIAgent<String, String> agent = AIAgent.builder()
    .promptExecutor(executor)
    .llmModel(OpenAIModels.Chat.GPT4oMini)
    .systemPrompt("You are a helpful assistant.")
    .install(ChatMemory.Feature, config -> config
            .chatHistoryProvider(new MyDatabaseProvider())
            .windowSize(20)
            .filterMessages(msg -> msg instanceof Message.User))
    .build();
```

`MessageFilter` is a `fun interface`, so Java lambdas work directly.

## Best practices

1. **Always set a window size** — without one, prompt size grows unboundedly with conversation length.
2. **Choose preprocessor order carefully** — filtering before windowing and windowing before filtering produce different results.
3. **Use meaningful session IDs** — these are the keys for history isolation. User IDs, chat thread IDs, or UUIDs all work well.
4. **Implement a persistent provider for production** — `InMemoryChatHistoryProvider` loses history on restart.

