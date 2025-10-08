# Module spring-boot-starter

Spring Boot auto-configuration and integration for Koog AI agents framework.

### Overview

The spring-boot-starter module provides seamless integration between the Koog AI agents framework and Spring Boot applications. It includes:

- Auto-configuration for LLM clients (Anthropic, Google, MistralAI, OpenAI, OpenRouter, DeepSeek, Ollama)
- Configuration properties for easy setup through application.properties/yml
- Conditional bean creation based on configuration presence
- Ready-to-use `SingleLLMPromptExecutor` beans for dependency injection

### Using in your project

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("ai.koog:koog-spring-boot-starter:$koogVersion")
}
```

Configure LLM providers in your `application.properties`:

```properties
# Anthropic configuration
ai.koog.anthropic.api-key=your-anthropic-api-key
ai.koog.anthropic.base-url=https://api.anthropic.com

# OpenAI configuration  
ai.koog.openai.api-key=your-openai-api-key
ai.koog.openai.base-url=https://api.openai.com

# Google configuration
ai.koog.google.api-key=your-google-api-key
ai.koog.google.base-url=https://generativelanguage.googleapis.com

# MistralAI configuration
ai.koog.mistral.api-key=your-mistral-api-key
ai.koog.mistral.base-url=https://api.mistral.ai

# Ollama configuration (local)
ai.koog.ollama.base-url=http://localhost:11434

# OpenRouter configuration
ai.koog.openrouter.api-key=your-openrouter-api-key
ai.koog.openrouter.base-url=https://openrouter.ai

# DeepSeek configuration
ai.koog.deepseek.api-key=your-deepseek-api-key
ai.koog.deepseek.base-url=https://api.deepseek.com
```

### Using in tests

For testing, you can use in-memory configurations or mock the `SingleLLMPromptExecutor` beans:

```kotlin
@TestConfiguration
class TestKoogConfig {
    @Bean
    @Primary
    fun mockLLMExecutor(): SingleLLMPromptExecutor {
        return mockk<SingleLLMPromptExecutor>()
    }
}
```

### Example of usage

```kotlin
@Service
class MyAIService(
    private val anthropicExecutor: SingleLLMPromptExecutor, // Auto-injected if configured
    private val openAIExecutor: SingleLLMPromptExecutor     // Auto-injected if configured
) {
    
    suspend fun processWithAnthropic(input: String): String {
        val prompt = Prompt {
            text(input)
        }
        
        val result = anthropicExecutor.execute(prompt)
        return result.text
    }
    
    suspend fun processWithOpenAI(input: String): String {
        val prompt = Prompt {
            text(input)
        }
        
        val result = openAIExecutor.execute(prompt)
        return result.text
    }
}
```

Advanced usage with custom configuration:

```kotlin
@RestController
class ChatController(
    private val anthropicExecutor: SingleLLMPromptExecutor?
) {
    
    @PostMapping("/chat")
    suspend fun chat(@RequestBody message: String): ResponseEntity<String> {
        return if (anthropicExecutor != null) {
            val response = anthropicExecutor.execute(Prompt { text(message) })
            ResponseEntity.ok(response.text)
        } else {
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("AI service not configured")
        }
    }
}
```
