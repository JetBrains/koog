# Spring AI Integration

Koog provides Spring AI integration starters that bridge Spring AI's model abstractions with the Koog agent framework.
If you already use Spring AI for model access, these starters let you plug Koog's agent orchestration on top —
without replacing your existing Spring AI configuration.

## How it differs from `koog-spring-boot-starter`

| | `koog-spring-boot-starter` | `koog-spring-ai` starters |
|---|---|---|
| **LLM transport** | Koog's own HTTP clients (one per provider: OpenAI, Anthropic, Google, etc.) | Delegates to Spring AI's `ChatModel` / `EmbeddingModel` — any provider that Spring AI supports works automatically |
| **Configuration** | `ai.koog.*` properties per provider | Standard `spring.ai.*` properties managed by Spring AI starters |
| **When to use** | You want Koog to manage LLM connections directly | You already use Spring AI for model access and want to plug Koog's agent orchestration on top |

Both approaches are independent — pick one based on how you prefer to manage LLM connectivity.
For the direct Koog starter approach, see [Spring Boot Integration](spring-boot.md).

## Available Starters

| Module | Purpose |
|---|---|
| `koog-spring-ai-starter-model-chat` | Adapts a Spring AI `ChatModel` (with optional `ModerationModel`) into a Koog `LLMClient` and `PromptExecutor` |
| `koog-spring-ai-starter-model-embedding` | Adapts a Spring AI `EmbeddingModel` into a Koog `LLMEmbeddingProvider` |

Each starter is a fully independent Spring Boot starter with its own auto-configuration, configuration properties, and dispatcher management.

## Chat Model Starter

### Overview

The `koog-spring-ai-starter-model-chat` starter bridges Spring AI's chat model abstraction with the Koog agent framework.
It auto-configures:

- A Koog `LLMClient` (`SpringAiLLMClient`) that delegates to a Spring AI `ChatModel`
- A `PromptExecutor` (`MultiLLMPromptExecutor`) assembled from all available `LLMClient` beans

Tools are always executed by the Koog agent framework — Spring AI receives only tool
definitions/schema. The `internalToolExecutionEnabled` flag is set to `false` on all
tool-carrying requests.

### Add Dependency

Add the dependency alongside any Spring AI model starter (e.g., for Google):

=== "Gradle (Kotlin DSL)"

    ```kotlin
    // build.gradle.kts
    dependencies {
        implementation("ai.koog:koog-agents-jvm:$koogVersion")
        implementation("ai.koog:koog-spring-ai-starter-model-chat:$koogVersion")
        implementation("org.springframework.ai:spring-ai-starter-model-google-genai")
    }
    ```

=== "Maven"

    ```xml
    <dependencies>
        <dependency>
            <groupId>ai.koog</groupId>
            <artifactId>koog-agents-jvm</artifactId>
            <version>${koog.version}</version>
        </dependency>
        <dependency>
            <groupId>ai.koog</groupId>
            <artifactId>koog-spring-ai-starter-model-chat</artifactId>
            <version>${koog.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-model-google-genai</artifactId>
        </dependency>
    </dependencies>
    ```

Make sure that your project has:

- Spring Boot 3 (it requires Java 17 or higher)
- Kotlin libraries with version 2.3.10+ (kotlin-stdlib)
- A Spring AI model starter for your chosen provider

### Available providers
Anthropic, Azure OpenAI, Bedrock Converse, Deepseek, Google GenAI, HuggingFace, MiniMax, Mistral AI, OCI GenAI, Ollama, OpenAI, Vertex AI, ZhiPu AI

### Configure

Modify your Spring Boot properties if needed:

```properties
# put your API key for Gemini Developer API or pass it via an environment variable
spring.ai.google.genai.api-key=YOUR_GOOGLE_API_KEY
# default values
spring.ai.model.chat=google-genai
koog.spring.ai.chat.enabled=true
koog.spring.ai.chat.dispatcher.type=AUTO
```

If you have a single `ChatModel` bean, everything works automatically —
the adapter wraps it into a Koog `LLMClient` and creates a ready-to-use `PromptExecutor`.

### Usage Example

Inject the `PromptExecutor` and use it to run a Koog agent:

=== "Kotlin"

    ```kotlin
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.prompt.executor.clients.google.GoogleModels
    import ai.koog.prompt.executor.model.PromptExecutor
    import org.springframework.stereotype.Service

    @Service
    class MyAgentService(private val promptExecutor: PromptExecutor) {

        suspend fun askAgent(userMessage: String): String {
            val agent = AIAgent(
                promptExecutor = promptExecutor,
                llmModel = GoogleModels.Gemini2_5Flash,
                systemPrompt = "You are a helpful assistant."
            )

            return agent.run(userMessage)
        }
    }
    ```

=== "Java"

    ```java
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.prompt.executor.clients.google.GoogleModels;
    import ai.koog.prompt.executor.model.PromptExecutor;
    import org.springframework.stereotype.Service;

    @Service
    public class MyAgentService {
        private final PromptExecutor promptExecutor;

        public MyAgentService(PromptExecutor promptExecutor) {
            this.promptExecutor = promptExecutor;
        }

        public String askAgent(String userMessage) {
            var agent = AIAgent.builder()
                    .promptExecutor(promptExecutor)
                    .llmModel(GoogleModels.Gemini2_5Flash)
                    .systemPrompt("You are a helpful assistant.")
                    .build();

            return agent.run(userMessage);
        }
    }
    ```

Or provide your own `PromptExecutor` bean to override the auto-configured one entirely.

### Configuration Properties (`koog.spring.ai.chat`)

| Property | Type | Default | Description |
|---|---|---|---|
| `enabled` | `Boolean` | `true` | Enable/disable the chat auto-configuration |
| `chat-model-bean-name` | `String?` | `null` | Bean name of the `ChatModel` to use (for multi-model contexts) |
| `moderation-model-bean-name` | `String?` | `null` | Bean name of the `ModerationModel` to use (for multi-model contexts) |
| `provider` | `String?` | `null` | LLM provider id (e.g. `openai`, `anthropic`, `google`). When set, overrides auto-detection from the `ChatModel` class name. Falls back to `spring-ai` if auto-detection fails. |
| `dispatcher.type` | `AUTO` / `IO` | `AUTO` | Dispatcher for blocking model calls |
| `dispatcher.parallelism` | `Int` | `0` (= unbounded) | Max concurrency for `IO` dispatcher (0 = no limit) |

### Dispatcher Types

- **`AUTO`** (default): Uses a Spring-managed `AsyncTaskExecutor` if available (e.g., when `spring.threads.virtual.enabled=true` in Spring Boot 3.2+), otherwise falls back to `Dispatchers.IO`. This lets you opt into virtual threads with a single standard Spring Boot property.
- **`IO`**: Always uses `Dispatchers.IO`. When `dispatcher.parallelism` is greater than 0, uses `Dispatchers.IO.limitedParallelism(parallelism)` to cap concurrency.

### Multi-model Contexts

When multiple `ChatModel` or `ModerationModel` beans are registered, specify which one to use:

```properties
koog.spring.ai.chat.chat-model-bean-name=openAiChatModel
koog.spring.ai.chat.moderation-model-bean-name=openAiModerationModel
```

Without a selector, the auto-configuration activates only when a single candidate exists.

### Extension Points

- **`ChatOptionsCustomizer`**: Register a Spring bean implementing this functional interface to apply provider-specific `ChatOptions` tuning:

=== "Kotlin"

    ```kotlin
    @Bean
    fun chatOptionsCustomizer() = ChatOptionsCustomizer { options, params, model ->
        // Apply custom options based on the model or request parameters
        options
    }
    ```

=== "Java"

    ```java
    @Bean
    public ChatOptionsCustomizer chatOptionsCustomizer() {
        return (options, params, model) -> {
            // Apply custom options based on the model or request parameters
            return options;
        };
    }
    ```

  The auto-configuration picks it up automatically via optional injection.

- **Custom `LLMClient`**: Register your own `LLMClient` bean to override the auto-configured adapter entirely.
- **Custom `PromptExecutor`**: Register your own `PromptExecutor` bean to override the auto-configured `MultiLLMPromptExecutor`.

## Next Steps

- Learn about the [basic agents](agents/basic-agents.md) to build minimal AI workflows
- Explore [graph-based agents](agents/graph-based-agents.md) for advanced use cases
- See the [tools overview](tools-overview.md) to extend your agents' capabilities
- Check out [examples](examples.md) for real-world implementations
- Read the [Spring Boot Integration](spring-boot.md) guide for the direct Koog starter approach
