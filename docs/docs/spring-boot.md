---
status: beta
---

# Spring Boot Integration

--8<-- "versioning-snippets.md:beta"

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
- Mistral
- Ollama

## Getting Started

### 1. Add Dependency

Add the Koog Spring Boot starter to your Gradle build configuration:

```kotlin
dependencies {
    implementation("ai.koog:koog-spring-boot-starter:$koogVersion")
}
```
<!--- KNIT example-spring-boot-01.txt -->

or for Maven
```xml
<dependency>
    <groupId>ai.koog</groupId>
    <artifactId>koog-spring-boot-starter</artifactId>
    <version>$koogVersion</version>
</dependency>
```
<!--- KNIT example-spring-boot-02.txt -->

Make sure that your Kotlin or Java project has:
- Spring Boot 3 (it requires Java 17 or higher)
- Kotlin version 2.3.10+
- kotlinx-serialization version 1.10.0 (namely, kotlinx-serialization-core-jvm and kotlinx-serialization-json-jvm)

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
# Mistral Configuration
ai.koog.mistral.enabled=true
ai.koog.mistral.api-key=${MISTRALAI_API_KEY}
ai.koog.mistral.base-url=https://api.mistral.ai
# Ollama Configuration (local - no API key required)
ai.koog.ollama.enabled=true
ai.koog.ollama.base-url=http://127.0.0.1:11434
```
<!--- KNIT example-spring-boot-03.txt -->

Or using YAML format (`application.yml`):

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
        mistral:
            enabled: true
            api-key: ${MISTRALAI_API_KEY}
            base-url: https://api.mistral.ai
        ollama:
            enabled: true # Set it to `true` explicitly to activate !!!
            base-url: http://127.0.0.1:11434
```
<!--- KNIT example-spring-boot-04.txt -->

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
    For example, setting the environment variable `OPENAI_API_KEY` is enough for OpenAI Spring configuration to activate.

| LLM Provider | Environment Variables |
|--------------|-----------------------|
| Open AI      | `OPENAI_API_KEY`      |
| Anthropic    | `ANTHROPIC_API_KEY`   |
| Google       | `GOOGLE_API_KEY`      |
| OpenRouter   | `OPENROUTER_API_KEY`  |
| DeepSeek     | `DEEPSEEK_API_KEY`    |
| Mistral      | `MISTRALAI_API_KEY`   |

### 3. Use in your project

Below is a usage example of an auto-configured executor in Spring MVC RestController. It requires the following:
- spring-boot-starter-web dependency
- for Kotlin kotlinx-coroutines-core and kotlinx-coroutines-reactor dependencies should be added (Java version calls blocking `execute` method)
- Anthropic is enabled via property (ai.koog.anthropic.enabled=true)

=== "Kotlin"

    ```kotlin
    import ai.koog.prompt.dsl.prompt
    import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
    import ai.koog.prompt.executor.model.PromptExecutor
    import org.springframework.http.ResponseEntity
    import org.springframework.web.bind.annotation.PostMapping
    import org.springframework.web.bind.annotation.RequestBody
    import org.springframework.web.bind.annotation.RequestMapping
    import org.springframework.web.bind.annotation.RestController

    @RestController
    @RequestMapping("/api/chat")
    class ChatController(private val anthropicExecutor: PromptExecutor) {

        @PostMapping
        suspend fun chat(@RequestBody request: ChatRequest): ResponseEntity<ChatResponse> {
            return try {
                val prompt = prompt("chat") {
                    system("You are a helpful assistant")
                    user(request.message)
                }

                val result = anthropicExecutor.execute(prompt, AnthropicModels.Haiku_4_5)
                ResponseEntity.ok(ChatResponse(result.first().content))
            } catch (e: Exception) {
                ResponseEntity.internalServerError()
                    .body(ChatResponse("Error processing request"))
            }
        }
    }

    data class ChatRequest(val message: String)
    data class ChatResponse(val response: String)
    ```
    <!--- KNIT example-spring-boot-kotlin-01.txt -->

=== "Java"

    ```java
    import ai.koog.prompt.Prompt;
    import ai.koog.prompt.executor.clients.anthropic.AnthropicModels;
    import ai.koog.prompt.executor.model.PromptExecutor;
    import ai.koog.prompt.message.Message;
    import org.springframework.http.ResponseEntity;
    import org.springframework.web.bind.annotation.PostMapping;
    import org.springframework.web.bind.annotation.RequestBody;
    import org.springframework.web.bind.annotation.RequestMapping;
    import org.springframework.web.bind.annotation.RestController;

    import java.util.List;

    @RestController
    @RequestMapping("/api/chat")
    public class ChatController {
        private final PromptExecutor anthropicExecutor;

        public ChatController(PromptExecutor anthropicExecutor) {
            this.anthropicExecutor = anthropicExecutor;
        }

        @PostMapping
        public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
            try {
                Prompt prompt = Prompt.builder("chat")
                        .system("You are a helpful assistant")
                        .user(request.message())
                        .build();

                List<Message.Response> result = anthropicExecutor.execute(prompt, AnthropicModels.Haiku_4_5);
                return ResponseEntity.ok(new ChatResponse(result.get(0).getContent()));
            } catch (Exception e) {
                return ResponseEntity.internalServerError()
                        .body(new ChatResponse("Error processing request"));
            }
        }
    }

    record ChatRequest(String message) {
    }

    record ChatResponse(String response) {
    }
    ```
    <!--- KNIT example-spring-boot-java-01.txt -->

Spring Framework injected the executor for Anthropic by bean name (`anthropicExecutor`),
but you can also inject multiple `PromptExecutor` beans using `@Qualifier` annotation (see "Multiple beans error" below).

## Advanced usage
### LLM Provider Fallback

After configuring multiple LLM providers you can send request to multiple LLMs via `MultiLLMPromptExecutor`:

=== "Kotlin"

    ```kotlin
    import ai.koog.prompt.dsl.prompt
    import ai.koog.prompt.executor.clients.anthropic.AnthropicModels.Haiku_4_5
    import ai.koog.prompt.executor.clients.openai.OpenAIModels.Chat.GPT4oMini
    import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.Claude3Haiku
    import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
    import org.slf4j.Logger
    import org.slf4j.LoggerFactory
    import org.springframework.stereotype.Service

    @Service
    class RobustAIService(private val multiLLMPromptExecutor: MultiLLMPromptExecutor) {

        private val llms = listOf(GPT4oMini, Haiku_4_5, Claude3Haiku)

        suspend fun generateWithFallback(input: String): String {
            val prompt = prompt("robust") {
                system("You are a helpful AI assistant")
                user(input)
            }

            for (llm in llms) {
                try {
                    val result = multiLLMPromptExecutor.execute(prompt, llm)
                    return result.first().content
                } catch (e: Exception) {
                    logger.warn("{} executor failed, trying next: {}", llm.id, e.message)
                }
            }

            throw IllegalStateException("All AI providers failed")
        }

        companion object {
            private val logger = LoggerFactory.getLogger(RobustAIService::class.java)
        }
    }
    ```
    <!--- KNIT example-spring-boot-kotlin-02.txt -->

=== "Java"

    ```java
    import ai.koog.prompt.Prompt;
    import ai.koog.prompt.executor.clients.anthropic.AnthropicModels;
    import ai.koog.prompt.executor.clients.openai.OpenAIModels;
    import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels;
    import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
    import ai.koog.prompt.llm.LLModel;
    import ai.koog.prompt.message.Message;
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;
    import org.springframework.stereotype.Service;

    import java.util.List;

    @Service
    public class RobustAIService {
        private static final Logger logger = LoggerFactory.getLogger(RobustAIService.class);

        private final List<LLModel> llms = List.of(OpenAIModels.Chat.GPT4oMini, AnthropicModels.Haiku_4_5, OpenRouterModels.Claude3Haiku);

        private final MultiLLMPromptExecutor multiLLMPromptExecutor;

        public RobustAIService(MultiLLMPromptExecutor multiLLMPromptExecutor) {
            this.multiLLMPromptExecutor = multiLLMPromptExecutor;
        }

        public String generateWithFallback(String input) {
            Prompt prompt = Prompt.builder("robust")
                .system("You are a helpful AI assistant")
                .user(input)
                .build();

            for (LLModel llm : llms) {
                try {
                    List<Message.Response> result = multiLLMPromptExecutor.execute(prompt, llm);
                    return result.get(0).getContent();
                } catch (Exception e) {
                    logger.warn("{} executor failed, trying next: {}", llm.getId(), e.getMessage());
                }
            }

            throw new IllegalStateException("All AI providers failed");
        }
    }
    ```
    <!--- KNIT example-spring-boot-java-02.txt -->

You can also register your own `MultiLLMPromptExecutor` bean and pass a `FallbackPromptExecutorSettings` to it.
To override the auto-configuration for your beans you can use `@Primary` annotation.

## Configuration Reference

### Available Properties

| Property                      | Description         | Bean Condition                         | Default                                     |
|-------------------------------|---------------------|----------------------------------------|---------------------------------------------|
| `ai.koog.openai.api-key`      | OpenAI API key      | Required for `openAIExecutor` bean     | -                                           |
| `ai.koog.openai.base-url`     | OpenAI base URL     | Optional                               | `https://api.openai.com`                    |
| `ai.koog.anthropic.api-key`   | Anthropic API key   | Required for `anthropicExecutor` bean  | -                                           |
| `ai.koog.anthropic.base-url`  | Anthropic base URL  | Optional                               | `https://api.anthropic.com`                 |
| `ai.koog.google.api-key`      | Google API key      | Required for `googleExecutor` bean     | -                                           |
| `ai.koog.google.base-url`     | Google base URL     | Optional                               | `https://generativelanguage.googleapis.com` |
| `ai.koog.openrouter.api-key`  | OpenRouter API key  | Required for `openRouterExecutor` bean | -                                           |
| `ai.koog.openrouter.base-url` | OpenRouter base URL | Optional                               | `https://openrouter.ai`                     |
| `ai.koog.deepseek.api-key`    | DeepSeek API key    | Required for `deepSeekExecutor` bean   | -                                           |
| `ai.koog.deepseek.base-url`   | DeepSeek base URL   | Optional                               | `https://api.deepseek.com`                  |
| `ai.koog.mistral.api-key`     | Mistral API key     | Required for `mistralAIExecutor` bean  | -                                           |
| `ai.koog.mistral.base-url`    | Mistral base URL    | Optional                               | `https://api.mistral.ai`                    |
| `ai.koog.ollama.base-url`     | Ollama base URL     | Optional                               | `http://127.0.0.1:11434`                    |

### Bean Names

The auto-configuration creates the following beans (when configured):

- `openAIExecutor` - OpenAI executor (requires `ai.koog.openai.api-key`)
- `anthropicExecutor` - Anthropic executor (requires `ai.koog.anthropic.api-key`)
- `googleExecutor` - Google executor (requires `ai.koog.google.api-key`)
- `openRouterExecutor` - OpenRouter executor (requires `ai.koog.openrouter.api-key`)
- `deepSeekExecutor` - DeepSeek executor (requires `ai.koog.deepseek.api-key`)
- `mistralAIExecutor` - Mistral AI executor (requires `ai.koog.mistral.api-key`)
- `ollamaExecutor` - Ollama executor (requires `ai.koog.ollama.enabled=true`)
- `multiLLMPromptExecutor` - MultiLLMPromptExecutor

## Troubleshooting

### Common Issues

**Error: No qualifying bean of type 'PromptExecutor' available**

**Solution:** Ensure you have configured at least one provider in your properties file.

**Error: Multiple qualifying beans of type 'PromptExecutor' available**

**Solution:** Use `@Qualifier` to specify which bean you want:

=== "Kotlin"

    ```kotlin
    @Service
    class MyService(
        @Qualifier("openAIExecutor") private val openAIExecutor: PromptExecutor,
        @Qualifier("anthropicExecutor") private val anthropicExecutor: PromptExecutor
    ) {
        // ...
    }
    ```
    <!--- KNIT example-spring-boot-kotlin-03.txt -->

=== "Java"

    ```java
    @Service
    public class MyService {
        private final PromptExecutor openAIExecutor;
        private final PromptExecutor anthropicExecutor;

        public MyService(@Qualifier("openAIExecutor") PromptExecutor openAIExecutor,
                         @Qualifier("anthropicExecutor") PromptExecutor anthropicExecutor) {
            this.openAIExecutor = openAIExecutor;
            this.anthropicExecutor = anthropicExecutor;
        }
        // ...
    }
    ```
    <!--- KNIT example-spring-boot-java-03.txt -->

**Error: API key is required but not provided**

**Solution:** Check that your environment variables are properly set and accessible to your Spring Boot application.

## Best Practices

1. **Environment Variables**: Always use environment variables for API keys
2. **Nullable Injection**: Use nullable types to handle cases where providers aren't configured
3. **Fallback Logic**: Implement fallback mechanisms when using multiple providers
4. **Error Handling**: Always wrap executor calls in try-catch blocks for production code
5. **Testing**: Use mocks in tests to avoid making actual API calls
6. **Configuration Validation**: Check if executors are available before using them

## Next Steps

- Learn about the [basic agents](agents/basic-agents.md) to build minimal AI workflows
- Explore [graph-based agents](agents/graph-based-agents.md) for advanced use cases
- See the [tools overview](tools/index.md) to extend your agents' capabilities
- Check out [examples](examples.md) for real-world implementations
- Read the [glossary](glossary.md) to understand the framework better
