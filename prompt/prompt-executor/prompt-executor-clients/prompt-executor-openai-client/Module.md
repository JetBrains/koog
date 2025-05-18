# Module prompt-executor-openai-client

A client implementation for executing prompts using OpenAI's GPT models.

### Overview

This module provides a client implementation for the OpenAI API, allowing you to execute prompts using GPT models. It handles authentication, request formatting, and response parsing specific to OpenAI's API requirements.

### Using in your project

Add the dependency to your project:

```kotlin
dependencies {
    implementation("ai.koog.prompt:prompt-executor-openai-client:$version")
}
```

Configure the client with your API key:

```kotlin
val openaiClient = OpenAILLMClient(
    apiKey = "your-openai-api-key",
)
```

### Example of usage

```kotlin
suspend fun main() {
    val client = OpenAILLMClient(
        apiKey = System.getenv("OPENAI_API_KEY"),
    )

    val response = client.execute(
        prompt = prompts("test") {
            system("You are helpful assistant")
            user("What time is it now?")
        },
        model = OpenAIModels.Chat.GPT4o
    )

    println(response)
}
```
