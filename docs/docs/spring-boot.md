# Spring Boot Integration

Koog provides seamless Spring Boot integration through its auto-configuration starter, making it easy to incorporate AI
agents into your Spring Boot applications with minimal setup.

## Overview

The `koog-spring-boot-starter` automatically configures LLM clients based on your application properties and provides
ready-to-use beans for dependency injection. It supports all major LLM providers including:

- OpenAI
- Anthropic
- Google
- OpenRouter
- DeepSeek
- Ollama

## Getting Started

### 1. Add Dependency

Add the Koog Spring Boot starter and [Ktor Client Engine](https://ktor.io/docs/client-engines.html#jvm) to your build configuration:

<!--- INCLUDE
/**
-->
<!--- SUFFIX
**/
-->
```kotlin
dependencies {
    implementation("ai.koog:koog-spring-boot-starter:$koogVersion")
    implementation("io.ktor:ktor-client-okhttp-jvm:$ktorVersion")
}
```
<!--- KNIT example-spring-boot-01.txt -->

### 2. Configure Providers

Configure your preferred LLM providers in `application.properties`:

```properties
# OpenAI Configuration
ai.koog.openai.enabled=true
ai.koog.openai.api-key=${OPENAI_API_KEY}
ai.koog.openai.base-url=https://api.openai.com
# Anthropic Configuration  
ai.koog.anthropic.enabled=true
ai.koog.anthropic.api-key=${ANTHROPIC_API_KEY}
ai.koog.anthropic.base-url=https://api.anthropic.com
# Google Configuration
ai.koog.google.enabled=true
ai.koog.google.api-key=${GOOGLE_API_KEY}
ai.koog.google.base-url=https://generativelanguage.googleapis.com
# OpenRouter Configuration
ai.koog.openrouter.enabled=true
ai.koog.openrouter.api-key=${OPENROUTER_API_KEY}
ai.koog.openrouter.base-url=https://openrouter.ai
# DeepSeek Configuration
ai.koog.deepseek.enabled=true
ai.koog.deepseek.api-key=${DEEPSEEK_API_KEY}
ai.koog.deepseek.base-url=https://api.deepseek.com
# Ollama Configuration (local - no API key required)
ai.koog.ollama.enabled=true
ai.koog.ollama.base-url=http://localhost:11434
```
<!--- KNIT example-spring-boot-02.txt -->

Or using YAML format (`application.yml`):

<!--- INCLUDE
/**
-->
<!--- SUFFIX
**/
-->
```yaml
ai:
    koog:
        openai:
            enabled: true
            api-key: ${OPENAI_API_KEY}
            base-url: https://api.openai.com
        anthropic:
            enabled: true
            api-key: ${ANTHROPIC_API_KEY}
            base-url: https://api.anthropic.com
        google:
            enabled: true
            api-key: ${GOOGLE_API_KEY}
            base-url: https://generativelanguage.googleapis.com
        openrouter:
            enabled: true
            api-key: ${OPENROUTER_API_KEY}
            base-url: https://openrouter.ai
        deepseek:
            enabled: true
            api-key: ${DEEPSEEK_API_KEY}
            base-url: https://api.deepseek.com
        ollama:
            enabled: true # Set it to `true` explicitly to activate !!!
            base-url: http://localhost:11434
```
<!--- KNIT example-spring-boot-java-01.txt -->

Both `ai.koog.PROVIDER.api-key` and `ai.koog.PROVIDER.enabled` properties are used to activate the provider.

If the provider supports the API Key (like OpenAI, Anthropic, Google), then `ai.koog.PROVIDER.enabled` is set to `true`
by default.

If the provider does not support the API Key, like Ollama, `ai.koog.PROVIDER.enabled` is set to `false` by default,
and provider should be enabled explicitly in the application configuration.

Provider's base urls are set to their default values in the Spring Boot starter, but you may override it in your
application.

!!! tip "Environment Variables"
It's recommended to use environment variables for API keys to keep them secure and out of version control.
Spring configuration uses LLM provider's well-known environment variables.
For example, setting the environment variable `OPENAI_API_KEY` is enough for OpenAI spring configuration to activate.

| LLM Provider | Environment Variables |
|--------------|-----------------------|
| Open AI      | `OPENAI_API_KEY`      |
| Anthropic    | `ANTHROPIC_API_KEY`   |
| Google       | `GOOGLE_API_KEY`      |
| OpenRouter   | `OPENROUTER_API_KEY`  |
| DeepSeek     | `DEEPSEEK_API_KEY`    |

### 3. Inject and Use

Inject the auto-configured executors into your services:

=== "Kotlin"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```kotlin
    @Service
    class AIService(
        private val openAIExecutor: MultiLLMPromptExecutor?,
        private val anthropicExecutor: MultiLLMPromptExecutor?
    ) {

        suspend fun generateResponse(input: String): String {
            val prompt = prompt {
                system("You are a helpful AI assistant")
                user(input)
            }

            return when {
                openAIExecutor != null -> {
                    val result = openAIExecutor.execute(prompt)
                    result.text
                }
                anthropicExecutor != null -> {
                    val result = anthropicExecutor.execute(prompt)
                    result.text
                }
                else -> throw IllegalStateException("No LLM provider configured")
            }
        }
    }
    ```
    <!--- KNIT example-spring-boot-01.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    @Service
    public class AIService {
        private final MultiLLMPromptExecutor openAIExecutor;
        private final MultiLLMPromptExecutor anthropicExecutor;

        public AIService(MultiLLMPromptExecutor openAIExecutor, MultiLLMPromptExecutor anthropicExecutor) {
            this.openAIExecutor = openAIExecutor;
            this.anthropicExecutor = anthropicExecutor;
        }

        public String generateResponse(String input) {
            Prompt prompt = Prompt.builder("ai-service")
                .system("You are a helpful AI assistant")
                .user(input)
                .build();

            if (openAIExecutor != null) {
                List<Message.Response> result = openAIExecutor.execute(prompt, OpenAIModels.Chat.GPT4o);
                return result.get(0).getContent();
            } else if (anthropicExecutor != null) {
                List<Message.Response> result = anthropicExecutor.execute(prompt, AnthropicModels.Haiku_4_5);
                return result.get(0).getContent();
            } else {
                throw new IllegalStateException("No LLM provider configured");
            }
        }
    }
    ```
    <!--- KNIT example-spring-boot-java-01.java -->

## Advanced Usage

### REST Controller Example

Create a chat endpoint using auto-configured executors:

=== "Kotlin"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```kotlin
    @RestController
    @RequestMapping("/api/chat")
    class ChatController(
        private val anthropicExecutor: MultiLLMPromptExecutor?
    ) {

        @PostMapping
        suspend fun chat(@RequestBody request: ChatRequest): ResponseEntity<ChatResponse> {
            return if (anthropicExecutor != null) {
                try {
                    val prompt = prompt {
                        system("You are a helpful assistant")
                        user(request.message)
                    }

                    val result = anthropicExecutor.execute(prompt)
                    ResponseEntity.ok(ChatResponse(result.text))
                } catch (e: Exception) {
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ChatResponse("Error processing request"))
                }
            } else {
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ChatResponse("AI service not configured"))
            }
        }
    }

    data class ChatRequest(val message: String)
    data class ChatResponse(val response: String)
    ```
    <!--- KNIT example-spring-boot-02.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    @RestController
    @RequestMapping("/api/chat")
    public class ChatController {
        private final MultiLLMPromptExecutor anthropicExecutor;

        public ChatController(MultiLLMPromptExecutor anthropicExecutor) {
            this.anthropicExecutor = anthropicExecutor;
        }

        @PostMapping
        public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
            if (anthropicExecutor != null) {
                try {
                    Prompt prompt = Prompt.builder("chat")
                        .system("You are a helpful assistant")
                        .user(request.message)
                        .build();

                    List<Message.Response> result = anthropicExecutor.execute(prompt, AnthropicModels.Haiku_4_5);
                    return ResponseEntity.ok(new ChatResponse(result.get(0).getContent()));
                } catch (Exception e) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new ChatResponse("Error processing request"));
                }
            } else {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ChatResponse("AI service not configured"));
            }
        }
    }

    class ChatRequest {
        public String message;
    }
    class ChatResponse {
        public final String response;
        public ChatResponse(String response) { this.response = response; }
    }
    ```
    <!--- KNIT example-spring-boot-java-02.java -->

### Multiple Provider Support

Handle multiple providers with fallback logic:

=== "Kotlin"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```kotlin
    @Service
    class RobustAIService(
        private val openAIExecutor: MultiLLMPromptExecutor?,
        private val anthropicExecutor: MultiLLMPromptExecutor?,
        private val openRouterExecutor: MultiLLMPromptExecutor?
    ) {

        suspend fun generateWithFallback(input: String): String {
            val prompt = prompt {
                system("You are a helpful AI assistant")
                user(input)
            }

            val executors = listOfNotNull(openAIExecutor, anthropicExecutor, openRouterExecutor)

            for (executor in executors) {
                try {
                    val result = executor.execute(prompt)
                    return result.text
                } catch (e: Exception) {
                    logger.warn("Executor failed, trying next: ${e.message}")
                    continue
                }
            }

            throw IllegalStateException("All AI providers failed")
        }

        companion object {
            private val logger = LoggerFactory.getLogger(RobustAIService::class.java)
        }
    }
    ```
    <!--- KNIT example-spring-boot-03.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    @Service
    public class RobustAIService {
        private static final Logger logger = LoggerFactory.getLogger(RobustAIService.class);

        private static class ProviderConfig {
            final String name;
            final MultiLLMPromptExecutor executor;
            final LLModel model;

            ProviderConfig(String name, MultiLLMPromptExecutor executor, LLModel model) {
                this.name = name;
                this.executor = executor;
                this.model = model;
            }
        }

        private final List<ProviderConfig> providers;

        public RobustAIService(MultiLLMPromptExecutor openAIExecutor,
                               MultiLLMPromptExecutor anthropicExecutor,
                               MultiLLMPromptExecutor openRouterExecutor) {
            providers = new ArrayList<>();
            if (openAIExecutor != null) {
                providers.add(new ProviderConfig("OpenAI", openAIExecutor, OpenAIModels.Chat.GPT4oMini));
            }
            if (anthropicExecutor != null) {
                providers.add(new ProviderConfig("Anthropic", anthropicExecutor, AnthropicModels.Haiku_4_5));
            }
            if (openRouterExecutor != null) {
                providers.add(new ProviderConfig("OpenRouter", openRouterExecutor, OpenRouterModels.Claude3Haiku));
            }
        }

        public String generateWithFallback(String input) {
            Prompt prompt = Prompt.builder("robust")
                .system("You are a helpful AI assistant")
                .user(input)
                .build();

            for (ProviderConfig provider : providers) {
                try {
                    List<Message.Response> result = provider.executor.execute(prompt, provider.model);
                    return result.get(0).getContent();
                } catch (Exception e) {
                    logger.warn("{} executor failed, trying next: {}", provider.name, e.getMessage());
                }
            }

            throw new IllegalStateException("All AI providers failed");
        }
    }
    ```
    <--- KNIT example-spring-boot-java-03.java -->

### Configuration Properties

You can also inject configuration properties for custom logic:

=== "Kotlin"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```kotlin
    @Service
    class ConfigurableAIService(
        private val openAIExecutor: MultiLLMPromptExecutor?,
        @Value("\${ai.koog.openai.api-key:}") private val openAIKey: String
    ) {

        fun isOpenAIConfigured(): Boolean = openAIKey.isNotBlank() && openAIExecutor != null

        suspend fun processIfConfigured(input: String): String? {
            return if (isOpenAIConfigured()) {
                val result = openAIExecutor!!.execute(prompt { user(input) })
                result.text
            } else {
                null
            }
        }
    }
    ```
    <!--- KNIT example-spring-boot-04.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    @Service
    public class ConfigurableAIService {
        private final MultiLLMPromptExecutor openAIExecutor;
        private final String openAIKey;

        public ConfigurableAIService(MultiLLMPromptExecutor openAIExecutor,
                                     @Value("${ai.koog.openai.api-key:}") String openAIKey) {
            this.openAIExecutor = openAIExecutor;
            this.openAIKey = openAIKey;
        }

        public boolean isOpenAIConfigured() {
            return openAIKey != null && !openAIKey.isBlank() && openAIExecutor != null;
        }

        public String processIfConfigured(String input) {
            if (!isOpenAIConfigured()) return null;

            Prompt prompt = Prompt.builder("configurable").user(input).build();

            List<Message.Response> result = openAIExecutor.execute(prompt, OpenAIModels.Chat.GPT4o);
            return result.get(0).getContent();
        }
    }
    ```
    <!--- KNIT example-spring-boot-java-03.java -->

## Configuration Reference

### Available Properties

| Property                      | Description         | Bean Condition                                                  | Default                                     |
|-------------------------------|---------------------|-----------------------------------------------------------------|---------------------------------------------|
| `ai.koog.openai.api-key`      | OpenAI API key      | Required for `openAIExecutor` bean                              | -                                           |
| `ai.koog.openai.base-url`     | OpenAI base URL     | Optional                                                        | `https://api.openai.com`                    |
| `ai.koog.anthropic.api-key`   | Anthropic API key   | Required for `anthropicExecutor` bean                           | -                                           |
| `ai.koog.anthropic.base-url`  | Anthropic base URL  | Optional                                                        | `https://api.anthropic.com`                 |
| `ai.koog.google.api-key`      | Google API key      | Required for `googleExecutor` bean                              | -                                           |
| `ai.koog.google.base-url`     | Google base URL     | Optional                                                        | `https://generativelanguage.googleapis.com` |
| `ai.koog.openrouter.api-key`  | OpenRouter API key  | Required for `openRouterExecutor` bean                          | -                                           |
| `ai.koog.openrouter.base-url` | OpenRouter base URL | Optional                                                        | `https://openrouter.ai`                     |
| `ai.koog.deepseek.api-key`    | DeepSeek API key    | Required for `deepSeekExecutor` bean                            | -                                           |
| `ai.koog.deepseek.base-url`   | DeepSeek base URL   | Optional                                                        | `https://api.deepseek.com`                  |
| `ai.koog.ollama.base-url`     | Ollama base URL     | Any `ai.koog.ollama.*` property activates `ollamaExecutor` bean | `http://localhost:11434`                    |

### Bean Names

The auto-configuration creates the following beans (when configured):

- `openAIExecutor` - OpenAI executor (requires `ai.koog.openai.api-key`)
- `anthropicExecutor` - Anthropic executor (requires `ai.koog.anthropic.api-key`)
- `googleExecutor` - Google executor (requires `ai.koog.google.api-key`)
- `openRouterExecutor` - OpenRouter executor (requires `ai.koog.openrouter.api-key`)
- `deepSeekExecutor` - DeepSeek executor (requires `ai.koog.deepseek.api-key`)
- `ollamaExecutor` - Ollama executor (requires any `ai.koog.ollama.*` property)

## Troubleshooting

### Common Issues

**Bean not found error:**

```
No qualifying bean of type 'MultiLLMPromptExecutor' available
```
<!--- KNIT example-spring-boot-03.txt -->

**Solution:** Ensure you have configured at least one provider in your properties file.

**Multiple beans error:**

```
Multiple qualifying beans of type 'MultiLLMPromptExecutor' available
```
<!--- KNIT example-spring-boot-04.txt -->

**Solution:** Use `@Qualifier` to specify which bean you want:

=== "Kotlin"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```kotlin
    @Service
    class MyService(
        @Qualifier("openAIExecutor") private val openAIExecutor: MultiLLMPromptExecutor,
        @Qualifier("anthropicExecutor") private val anthropicExecutor: MultiLLMPromptExecutor
    ) {
        // ...
    }
    ```
    <!--- KNIT example-spring-boot-05.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    @Service
    public class MyService {
        private final MultiLLMPromptExecutor openAIExecutor;
        private final MultiLLMPromptExecutor anthropicExecutor;

        public MyService(@Qualifier("openAIExecutor") MultiLLMPromptExecutor openAIExecutor,
                         @Qualifier("anthropicExecutor") MultiLLMPromptExecutor anthropicExecutor) {
            this.openAIExecutor = openAIExecutor;
            this.anthropicExecutor = anthropicExecutor;
        }
        // ...
    }
    ```
    <!--- KNIT example-spring-boot-java-04.java -->

**API key not loaded:**

```
API key is required but not provided
```
<!--- KNIT example-spring-boot-05.txt -->

**Solution:** Check that your environment variables are properly set and accessible to your Spring Boot application.

## Best Practices

1. **Environment Variables**: Always use environment variables for API keys
2. **Nullable Injection**: Use nullable types (`MultiLLMPromptExecutor?`) to handle cases where providers aren't
   configured
3. **Fallback Logic**: Implement fallback mechanisms when using multiple providers
4. **Error Handling**: Always wrap executor calls in try-catch blocks for production code
5. **Testing**: Use mocks in tests to avoid making actual API calls
6. **Configuration Validation**: Check if executors are available before using them

## Next Steps

- Learn about the [basic agents](agents/basic-agents.md) to build minimal AI workflows
- Explore [graph-based agents](agents/graph-based-agents.md) for advanced use cases
- See the [tools overview](tools-overview.md) to extend your agents' capabilities
- Check out [examples](examples.md) for real-world implementations
- Read the [glossary](glossary.md) to understand the framework better
