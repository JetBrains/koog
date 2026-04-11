# Module prompt-executor-siliconflow-client

A client implementation for executing prompts using SiliconFlow's API to access various LLM providers with multimodal
support and advanced custom parameters.

### Overview

This module provides a client implementation for the SiliconFlow API, allowing you to execute prompts using multiple LLM
providers through a single interface with extensive parameter customization. SiliconFlow gives access to models from
different providers including OpenAI, Anthropic, Google, and others. The client supports multimodal content including
images, audio, and documents, plus advanced routing and provider selection features.

### API Endpoints

The client connects to DashScope using OpenAI-compatible endpoints:

- `https://api.siliconflow.cn/v1`

### Supported Models

#### DeepSeek Models

| Model                     | Speed     | Input Support       | Output Support    | Pricing  |
|---------------------------|-----------|---------------------|-------------------|----------|
| DeepSeek-R1               | Medium    | Text, Tools         | Text, Tools, JSON | Variable |
| DeepSeek-R1-0528-Qwen3-8B | Very Fast | Text, Tools         | Text, Tools, JSON | Variable |
| DeepSeek-V3               | Medium    | Text, Tools         | Text, Tools, JSON | Variable |
| DeepSeek-V3.1-0820        | Medium    | Text, Tools         | Text, Tools, JSON | Variable |
| DeepSeek-V3.1-Terminus    | Medium    | Text, Tools         | Text, Tools, JSON | Variable |
| DeepSeek-V3.2             | Medium    | Text, Tools         | Text, Tools, JSON | Variable |
| DeepSeek-OCR              | Fast      | Text, Images, Tools | Text, Tools, JSON | Variable |

#### Qwen Models

| Model                         | Speed     | Input Support       | Output Support    | Pricing  |
|-------------------------------|-----------|---------------------|-------------------|----------|
| Qwen3-8B                      | Very Fast | Text, Tools         | Text, Tools, JSON | Variable |
| Qwen3-14B                     | Fast      | Text, Tools         | Text, Tools, JSON | Variable |
| Qwen3-32B                     | Fast      | Text, Tools         | Text, Tools, JSON | Variable |
| Qwen3-30B-A3B                 | Fast      | Text, Tools         | Text, Tools, JSON | Variable |
| Qwen3-30B-A3B-Instruct-2507   | Fast      | Text, Tools         | Text, Tools, JSON | Variable |
| Qwen3-30B-A3B-Thinking-2507   | Fast      | Text, Tools         | Text, Tools, JSON | Variable |
| Qwen3-235B-A22B               | Medium    | Text, Tools         | Text, Tools, JSON | Variable |
| Qwen3-235B-A22B-Instruct-2507 | Medium    | Text, Tools         | Text, Tools, JSON | Variable |
| Qwen3-235B-A22B-Thinking-2507 | Medium    | Text, Tools         | Text, Tools, JSON | Variable |
| Qwen3-Coder-30B-A3B-Instruct  | Fast      | Text, Tools         | Text, Tools, JSON | Variable |
| Qwen3.5-4B                    | Very Fast | Text, Images, Tools | Text, Tools, JSON | Variable |
| Qwen3.5-9B                    | Very Fast | Text, Images, Tools | Text, Tools, JSON | Variable |
| Qwen3.5-27B                   | Fast      | Text, Images, Tools | Text, Tools, JSON | Variable |
| Qwen3.5-122B-A10B             | Medium    | Text, Images, Tools | Text, Tools, JSON | Variable |
| Qwen3-VL-8B-Instruct          | Very Fast | Text, Images, Tools | Text, Tools, JSON | Variable |
| Qwen3-VL-8B-Thinking          | Very Fast | Text, Images, Tools | Text, Tools, JSON | Variable |
| Qwen3-VL-32B-Instruct         | Fast      | Text, Images, Tools | Text, Tools, JSON | Variable |
| Qwen3-VL-32B-Thinking         | Fast      | Text, Images, Tools | Text, Tools, JSON | Variable |

#### Moonshot (Kimi) Models

| Model                 | Speed  | Input Support | Output Support    | Pricing  |
|-----------------------|--------|---------------|-------------------|----------|
| Kimi-K2-Instruct-0905 | Medium | Text, Tools   | Text, Tools, JSON | Variable |
| Kimi-K2-Thinking      | Medium | Text, Tools   | Text, Tools, JSON | Variable |

#### MiniMax Models

| Model      | Speed  | Input Support | Output Support    | Pricing  |
|------------|--------|---------------|-------------------|----------|
| MiniMax-M2 | Medium | Text, Tools   | Text, Tools, JSON | Variable |

#### Zhipu (GLM) Models

| Model    | Speed  | Input Support       | Output Support    | Pricing  |
|----------|--------|---------------------|-------------------|----------|
| GLM-4.6  | Medium | Text, Tools         | Text, Tools, JSON | Variable |
| GLM-5    | Medium | Text, Tools         | Text, Tools, JSON | Variable |
| GLM-4.6V | Medium | Text, Images, Tools | Text, Tools, JSON | Variable |

#### Tencent (Hunyuan) Models

| Model                 | Speed | Input Support | Output Support    | Pricing  |
|-----------------------|-------|---------------|-------------------|----------|
| Hunyuan-A13B-Instruct | Fast  | Text, Tools   | Text, Tools, JSON | Variable |

#### PaddlePaddle Models

| Model        | Speed     | Input Support       | Output Support    | Pricing  |
|--------------|-----------|---------------------|-------------------|----------|
| PaddleOCR-VL | Very Fast | Text, Images, Tools | Text, Tools, JSON | Variable |

#### Embedding Models

| Model                      | Speed     | Input Support | Output Support | Pricing  |
|----------------------------|-----------|---------------|----------------|----------|
| text-embedding-3-small     | Very Fast | Text          | Embeddings     | Variable |
| text-embedding-3-large     | Very Fast | Text          | Embeddings     | Variable |
| text-embedding-ada-002     | Very Fast | Text          | Embeddings     | Variable |
| gemini-embedding-001       | Very Fast | Text          | Embeddings     | Variable |
| mistral-embed-2312         | Very Fast | Text          | Embeddings     | Variable |
| codestral-embed-2505       | Very Fast | Text          | Embeddings     | Variable |
| qwen3-embedding-8b         | Very Fast | Text          | Embeddings     | Variable |
| qwen3-embedding-4b         | Very Fast | Text          | Embeddings     | Variable |
| bge-base-en-v1.5           | Very Fast | Text          | Embeddings     | Variable |
| bge-large-en-v1.5          | Very Fast | Text          | Embeddings     | Variable |
| bge-m3                     | Very Fast | Text          | Embeddings     | Variable |
| gte-base                   | Very Fast | Text          | Embeddings     | Variable |
| gte-large                  | Very Fast | Text          | Embeddings     | Variable |
| e5-base-v2                 | Very Fast | Text          | Embeddings     | Variable |
| e5-large-v2                | Very Fast | Text          | Embeddings     | Variable |
| multilingual-e5-large      | Very Fast | Text          | Embeddings     | Variable |
| all-minilm-l6-v2           | Very Fast | Text          | Embeddings     | Variable |
| all-minilm-l12-v2          | Very Fast | Text          | Embeddings     | Variable |
| all-mpnet-base-v2          | Very Fast | Text          | Embeddings     | Variable |
| multi-qa-mpnet-base-dot-v1 | Very Fast | Text          | Embeddings     | Variable |
| paraphrase-minilm-l6-v2    | Very Fast | Text          | Embeddings     | Variable |

### Media Content Support

| Content Type  | Supported Formats      | Max Size          | Notes                                                                                                      |
|:--------------|:-----------------------|:------------------|:-----------------------------------------------------------------------------------------------------------|
| **Images**    | PNG, JPEG, WebP, BMP   | ~10MB recommended | Supported by multimodal models (e.g., Qwen-VL, GLM-4V) for visual understanding and OCR.                   |
| **Audio**     | MP3, WAV, FLAC, M4A    | ~30MB / 15 min    | Supports **ASR** (Speech-to-Text) via SenseVoice and **TTS** (Text-to-Speech) via FishSpeech.              |
| **Documents** | PDF, Word, TXT, Images | Varies by model   | Enhanced by **DeepSeek-OCR** and **PaddleOCR-VL** for high-precision document parsing and layout analysis. |
| **Video**     | MP4 (Output)           | Varies by model   | Supports **Video Generation** (Text-to-Video) through models like Vidu, Luma, and HunyuanVideo.            |

#### Key Updates:

* **Audio Support:** Previously marked as unsupported, now includes capabilities for both speech recognition (ASR) and
  synthesis (TTS) using specialized models found in the provider list.
* **Document Processing:** Expanded beyond simple PDF support to include advanced OCR capabilities for various document
  types and images.
* **Video Capability:** Newly added support for video generation models, enabling the creation of dynamic visual content
  from text prompts.
  **Important Notes:**

### Model-Specific Parameters Support

The client supports extensive SiliconFlow-specific parameters through `SiliconFlowParams` class:

```kotlin
val siliconFlowParams = SiliconFlowParams(
    temperature = 0.7,
    maxTokens = 1000,
    frequencyPenalty = 0.5,
    presencePenalty = 0.5,
    topP = 0.9,
    topK = 40,
    repetitionPenalty = 1.1,
    minP = 0.02,
    topA = 0.8,
    stop = listOf("\n", "END"),
    logprobs = true,
    topLogprobs = 5,
    transforms = listOf("middle-out"),
    route = "fallback",
)
```

**Key Parameters:**

- **temperature** (0.0-2.0): Controls randomness in generation
- **topP** (0.0-1.0): Nucleus sampling parameter
- **topK** (≥1): Top-K sampling parameter
- **repetitionPenalty** (0.0-2.0): Penalizes token repetition
- **minP** (0.0-1.0): Minimum cumulative probability for token inclusion
- **topA** (0.0-1.0): Temperature scaling based on probability gain
- **transforms**: Context transformation strategies when exceeding token limits
- **route**: Request routing strategy ("fallback", etc.)

**Advanced Routing Features:**

- **Model Fallbacks**: Specify multiple models for automatic fallback
- **Provider Preferences**: Control which providers to use and in what order
- **Context Transforms**: Handle long contexts with middle-out truncation
- **Route Selection**: Choose routing strategies for optimal performance

### Using in your project

Add the dependency to your project:

```kotlin
dependencies {
    implementation("ai.koog.prompt:prompt-executor-siliconflow-client:$version")
}
```

Configure the client with your API key:

```kotlin
val siliconFlowClient = SiliconFlowLLMClient(
    apiKey = "your-siliconflow-api-key",
)
```

### Using in tests

For testing, you can use a mock implementation:

```kotlin
val mockSiliconFlowClient = MockSiliconFlowClient(
    responses = listOf("Mocked response 1", "Mocked response 2")
)
```

### Example of usage

```kotlin
suspend fun main() {
    val client = SiliconFlowLLMClient(
        apiKey = System.getenv("SILICONFLOW_API_KEY"),
    )

    // Basic example
    val response = client.execute(
        prompt = prompt("hello-prompt") {
            system("You are helpful assistant")
            user("What time is it now?")
        },
        model = SiliconFlowModels.Qwen3_8B
    )

    println(response)
}
```

### Multimodal Examples

```kotlin
// Image analysis
val imageResponse = client.execute(
    prompt = prompt("image-analysis") {
        user {
            text("What do you see in this image?")
            image(Path("/path/to/image.png"))
        }
    },
    model = SiliconFlowModels.GLM4_1V_9B_Thinking,
)

// Document processing with advanced routing
val documentResponse = client.execute(
    prompt = prompt("document-analysis") {
        user {
            text("Summarize this document")
            document(Path("/path/to/document.pdf"))
        }
    },
    model = SiliconFlowModels.Qwen3_8B,
)
```
