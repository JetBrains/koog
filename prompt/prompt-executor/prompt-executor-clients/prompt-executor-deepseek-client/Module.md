# Module prompt-executor-deepseek-client

A client implementation for executing prompts using DeepSeek's GPT models.

### Overview

This module provides a client implementation for the DeepSeek API.

### Supported Models

| Name               | Speed  | Price       | Input       | Output      |
|--------------------|--------|-------------|-------------|-------------|
| [DeepSeekChat]     | Fast   | $0.27-$1.1  | Text, Tools | Text, Tools |
| [DeepSeekReasoner] | Medium | $0.55-$2.19 | Text, Tools | Text, Tools |


### Using in your project

Add the dependency to your project:

```kotlin
dependencies {
    implementation("ai.koog.prompt:prompt-executor-deepseek-client:$version")
}
```

Configure the client with your API key:

```kotlin
val deepseekClient = DeepSeekLLMClient(
    apiKey = "your-deepseek-api-key",
)
```

### Example of usage

```kotlin
suspend fun main() {
    val client = DeepSeekLLMClient(
        apiKey = System.getenv("DEEPSEEK_API_KEY"),
    )

    val response = client.execute(
        prompt = prompt {
            system("You are helpful assistant")
            user("What time is it now?")
        },
        model = DeepSeekModels.DeepSeekChat,
    )

    println(response)
}
```
