# Module prompt-executor-router

Routing capabilities for distributing LLM requests across multiple LLM Clients.

### Overview

The `prompt-executor-router` module enables distributing requests to given LLModel across multiple LLMClient instances. This helps avoid rate limits, improve throughput, and implement failover strategies.

Key features include:
- Extensible interface for custom routing strategies (see [LLMClientRouter](src/main/kotlin/ai/koog/prompt/executor/router/LLMClientRouter.kt))
- Predefined routing strategies (see [RoutingStrategy](src/main/kotlin/ai/koog/prompt/executor/router/RoutingStrategy.kt))

### Using in your project

To use the `prompt-executor-router` module in your project, add the following dependency:

```kotlin
dependencies {
    implementation("ai.koog.prompt:prompt-executor-router:$version")
}
```

Then, create an executor with load balancing:
``` kotlin
val openAI1 = OpenAILLMClient(apiKey = "openai-key-1")
val openAI2 = OpenAILLMClient(apiKey = "openai-key-2")
val anthropic = AnthropicLLMClient(apiKey = "anthropic-key")

// Create executor with round-robin router
val executor = MultiLLMPromptExecutor(
    openAI1, openAI2, anthropic,
    routingStrategy = RoutingStrategy.ROUND_ROBIN
)

// Requests to OpenAI models alternate between openAI1 and openAI2
executor.execute(prompt, OpenAIModels.GPT_4, tools)
```
