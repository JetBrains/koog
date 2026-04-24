# Module prompt-executor-retrollmfit

An annotation-driven factory that generates a working `LLMClient` from a pair of request/response data classes —
no custom client code required.

### Overview

RetroLLMFit (inspired by Retrofit) lets you connect to any HTTP LLM endpoint by annotating your request and response
classes instead of writing boilerplate. The factory reads the annotations at runtime via Kotlin reflection and wires up
the HTTP layer automatically.

### Usage

```kotlin
@LLMEndpoint(url = "https://my-llm-server/api/prompt",
             authHeaderName = "X-Api-Key", authHeaderValue = "my-key")
@Serializable
data class MyRequest(@PromptField val prompt: String, val stream: Boolean = false)

@Serializable
data class MyResponse(@ResponseTextField val text: String)

val client = RetroLLMFit.create<MyRequest, MyResponse>()
val executor = SimplePromptExecutor(client)
```

### Annotations

| Annotation | Target | Purpose |
|---|---|---|
| `@LLMEndpoint` | Request class | Base URL and optional auth header / query-param |
| `@PromptField` | Request constructor parameter | The field that receives the flattened prompt text |
| `@MessagesField` | Request constructor parameter | The field that receives the full message array |
| `@ResponseTextField` | Response constructor parameter or property | The field holding the model's reply text |
