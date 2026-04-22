# Module prompt-executor-lmstudio-client

A client implementation for executing prompts against a locally running [LM Studio](https://lmstudio.ai/) server via its OpenAI-compatible API.

### Overview

LM Studio hosts local LLMs and exposes an [OpenAI-compatible REST API](https://lmstudio.ai/docs/app/api/endpoints/openai). This module wraps that endpoint with a thin subclass of `OpenAILLMClient`, so any model loaded into LM Studio can be used as a Koog `LLModel`. Because authentication is not required by the server, the API key is a placeholder and may be omitted.

### Supported Models

LM Studio loads arbitrary user-downloaded models (GGUF, MLX, etc.), so there is no fixed list. Use the `lmStudioModel(id)` helper to describe the loaded model, passing the id reported by `GET /v1/models`:

```kotlin
val model = lmStudioModel(
    id = "qwen/qwen3-1.7b",
    capabilities = listOf(
        LLMCapability.Completion,
        LLMCapability.Temperature,
        LLMCapability.OpenAIEndpoint.Completions,
    ),
)
```

`LLMCapability.OpenAIEndpoint.Completions` is included by default; add extra capabilities (e.g. `Tools`, `Vision.Image`, `Schema.JSON.Full`) for models that support them.

### Using in your project

Add the dependency:

```kotlin
dependencies {
    implementation("ai.koog.prompt:prompt-executor-lmstudio-client:$version")
}
```

Point the client at the local LM Studio server (defaults to `http://localhost:1234`):

```kotlin
val client = LMStudioLLMClient()
```

Or use the aggregated executor:

```kotlin
val executor = simpleLMStudioExecutor()
```

### Example of usage

```kotlin
suspend fun main() {
    val client = LMStudioLLMClient()

    val response = client.execute(
        prompt = prompt {
            system("You are a helpful assistant")
            user("Give me a one-line summary of LM Studio")
        },
        model = lmStudioModel(id = "qwen/qwen3-1.7b"),
    )

    println(response)
}
```

### Custom base URL

```kotlin
val client = LMStudioLLMClient(
    settings = LMStudioClientSettings(baseUrl = "http://192.168.1.10:1234"),
)
```
