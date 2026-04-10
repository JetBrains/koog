# Module prompt-executor-google-genai-client

Implementation of `LLMClient` for Google's Gemini API using the
[official Google GenAI Java SDK](https://github.com/googleapis/java-genai).

## Overview

This module provides `GoogleGenaiLLMClient` — a JVM-only client that delegates all API calls
to `com.google.genai.Client` async methods, bridging between Koog's internal types and the SDK's
native types. It supports text generation, streaming, tool calling, embeddings, multimodal content,
and thinking models (Gemini 3).

## Usage

```kotlin
val genaiClient = Client.builder()
    .apiKey("your-api-key")
    .vertexAI(false) // use Vertex API or not
    .build()
val llmClient = GoogleGenaiLLMClient(genaiClient)

// Simple text generation
val response = llmClient.execute(
    prompt = prompt("chat") {
        system("You are a helpful assistant")
        user("What is Kotlin?")
    },
    model = GoogleModels.Gemini2_5Flash
)
```

## Extensibility

The client is designed for subclassing. Key conversion methods are `protected open` and return
builders where applicable, so subclasses can customize request/response processing:

- `buildSdkContents` — Prompt to SDK Content conversion
- `buildConfig` — returns `GenerateContentConfig.Builder` for further modification
- `buildSdkTools` — returns `List<Tool.Builder>` for tool customization
- `processResponse` / `processCandidate` — response-to-Message conversion
- `extractResponseMetaInfo` — metadata enrichment

## Configuration

| Parameter                  | Description                                   | Default                     |
|----------------------------|-----------------------------------------------|-----------------------------|
| `client`                   | Configured `com.google.genai.Client` instance | Required                    |
| `fallbackThoughtSignature` | Thought signature for thinking models         | `DEFAULT_THOUGHT_SIGNATURE` |
| `ioDispatcher`             | Dispatcher for blocking stream iteration      | `Dispatchers.SuitableForIO` |
| `clock`                    | Clock for response metadata timestamps        | `Clock.System`              |
