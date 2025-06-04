# Module prompt-executor-ollama-client

A client implementation for executing prompts using local Ollama models with limited multimodal support.

### Overview

This module provides a client implementation for the Ollama API, allowing you to execute prompts using locally hosted
models. Ollama enables running large language models locally on your machine. The client currently supports text and
basic image processing capabilities.

### Supported Models

#### Predefined Models (Groq)

| Model                    | Speed  | Input Support | Output Support | Requirements |
|--------------------------|--------|---------------|----------------|--------------|
| llama3-groq-tool-use:8b  | Fast   | Text, Tools   | Text, Tools    | 8GB+ RAM     |
| llama3-groq-tool-use:70b | Medium | Text, Tools   | Text, Tools    | 32GB+ RAM    |

#### Predefined Models (Meta)

| Model         | Speed     | Input Support | Output Support | Requirements |
|---------------|-----------|---------------|----------------|--------------|
| llama3.2:3b   | Very Fast | Text, Tools   | Text, Tools    | 4GB+ RAM     |
| llama3.2      | Fast      | Text, Tools   | Text, Tools    | 8GB+ RAM     |
| llama4:latest | Medium    | Text, Tools   | Text, Tools    | 16GB+ RAM    |

#### Predefined Models (Alibaba)

| Model             | Speed     | Input Support | Output Support | Requirements |
|-------------------|-----------|---------------|----------------|--------------|
| qwen2.5:0.5b      | Very Fast | Text, Tools   | Text, Tools    | 2GB+ RAM     |
| qwen3:0.6b        | Very Fast | Text, Tools   | Text, Tools    | 2GB+ RAM     |
| qwq               | Fast      | Text, Tools   | Text, Tools    | 8GB+ RAM     |
| qwen2.5-coder:32b | Medium    | Text, Tools   | Text, Tools    | 32GB+ RAM    |

#### Dynamic Models

Any model available in your local Ollama installation can be used by creating a dynamic model.

### Media Content Support

| Content Type | Supported Formats | Max Size | Notes                              |
|--------------|-------------------|----------|------------------------------------|
| Images       | ❌ Not implemented | -        | API supports it but client doesn't |
| Audio        | ❌ Not supported   | -        | -                                  |
| Video        | ❌ Not supported   | -        | -                                  |
| Documents    | ❌ Not supported   | -        | Use text extraction                |

**Important:** While the Ollama API supports images via the `images` field, this client implementation does not
currently process `MediaContent.Image`. Only text content is supported.

### Using in your project

Add the dependency to your project:

```kotlin
dependencies {
    implementation("ai.koog.prompt:prompt-executor-ollama-client:$version")
}
```

Configure the client with your Ollama server:

```kotlin
val ollamaClient = OllamaLLMClient(
    baseUrl = "http://localhost:11434", // Default Ollama server
)
```

### Example of usage

```kotlin
suspend fun main() {
    val client = OllamaLLMClient(
        baseUrl = "http://localhost:11434",
    )

    // Text-only example
    val response = client.execute(
        prompt = prompt {
            system("You are helpful assistant")
            user("What time is it now?")
        },
        model = OllamaModels.Meta.LLAMA_3_2_3B
    )

    println(response)
}
```

### Usage Examples

```kotlin
// Tool usage example
val toolResponse = client.execute(
    prompt = prompt {
        user("What's the weather like?")
    },
    model = OllamaModels.Meta.LLAMA_3_2,
    tools = listOf(weatherTool)
)

// Code generation
val codeResponse = client.execute(
    prompt = prompt {
        user("Write a Python function to calculate fibonacci numbers")
    },
    model = OllamaModels.Alibaba.QWEN_CODER_2_5_32B
)

// Dynamic model usage
val dynamicModel = client.createDynamicModel("codellama:13b")
val dynamicResponse = client.execute(
    prompt = prompt {
        user("Explain recursion")
    },
    model = dynamicModel
)

// Embedding example
val embeddingModel = client.createDynamicModel("nomic-embed-text")
val embedding = client.embed(
    text = "This is sample text for embedding",
    model = embeddingModel
)
```

### Setup Instructions

1. Install Ollama: https://ollama.ai/
2. Pull a model: `ollama pull llama3.1:8b`
3. Start Ollama server: `ollama serve`
4. Use the client to connect to your local instance
