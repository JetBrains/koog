# Module prompt-executor-openai-client

A client implementation for executing prompts using OpenAI's GPT models with support for images, audio, and custom
parameters. Includes support for both Chat Completions and Responses APIs.

### Overview

This module provides a client implementation for the OpenAI API, allowing you to execute prompts using GPT models. It
handles authentication, request formatting, response parsing, and multimodal content encoding specific to OpenAI's API
requirements.

### Supported Models

#### Reasoning Models

| Model   | Speed   | Context | Input Support       | Output Support | Pricing (per 1M tokens) | APIs Support    |
|---------|---------|---------|---------------------|----------------|-------------------------|-----------------|
| o4-mini | Medium  | 200K    | Text, Images, Tools | Text, Tools    | $1.1-$4.4               | Chat, Responses |
| o3-mini | Medium  | 200K    | Text, Tools         | Text, Tools    | $1.1-$4.4               | Chat, Responses |
| o1-mini | Slow    | 128K    | Text                | Text           | $1.1-$4.4               | Chat            |
| o3      | Slowest | 200K    | Text, Images, Tools | Text, Tools    | $10-$40                 | Chat, Responses |
| o1      | Slowest | 200K    | Text, Images, Tools | Text, Tools    | $15-$60                 | Chat, Responses |

#### Chat Models

| Model      | Speed     | Context | Input Support           | Output Support | Pricing (per 1M tokens) | APIs Support    |
|------------|-----------|---------|-------------------------|----------------|-------------------------|-----------------|
| GPT-4o     | Medium    | 128K    | Text, Images, Tools     | Text, Tools    | $2.5-$10                | Chat, Responses |
| GPT-4.1    | Medium    | 1M      | Text, Images, Tools     | Text, Tools    | $2-$8                   | Chat, Responses |
| GPT-5      | Medium    | 400K    | Text, Images, Documents | Text, Tools    | $1.25-$10               | Chat, Responses |
| GPT-5 Mini | Fast      | 400K    | Text, Images, Documents | Text, Tools    | $0.25-$2                | Chat, Responses |
| GPT-5 Nano | Very fast | 400K    | Text, Images, Documents | Text, Tools    | $0.05-$0.4              | Chat, Responses |

#### Audio Models

| Model             | Speed  | Context | Input Support      | Output Support     | Pricing (per 1M tokens) |
|-------------------|--------|---------|--------------------|--------------------|-------------------------|
| GPT Audio         | Medium | 128K    | Text, Audio, Tools | Text, Audio, Tools | $2.5-$10                |
| GPT-4o Mini Audio | Fast   | 128K    | Text, Audio, Tools | Text, Audio, Tools | $0.15-$0.6/$10-$20      |
| GPT-4o Audio      | Medium | 128K    | Text, Audio, Tools | Text, Audio, Tools | $2.5-$10/$40-$80        |

#### Cost-Optimized Models

| Model        | Speed     | Context | Input Support       | Output Support | Pricing (per 1M tokens) | APIs Support    |
|--------------|-----------|---------|---------------------|----------------|-------------------------|-----------------|
| o4-mini      | Medium    | 200K    | Text, Images, Tools | Text, Tools    | $1.1-$4.4               | Chat, Responses |
| GPT-4o Mini  | Medium    | 128K    | Text, Images, Tools | Text, Tools    | $0.15-$0.6              | Chat, Responses |
| GPT-4.1-nano | Very fast | 1M      | Text, Images, Tools | Text, Tools    | $0.1-$0.4               | Chat, Responses |
| GPT-4.1-mini | Fast      | 1M      | Text, Images, Tools | Text, Tools    | $0.4-$1.6               | Chat, Responses |
| o3-mini      | Medium    | 200K    | Text, Tools         | Text, Tools    | $1.1-$4.4               | Chat, Responses |

#### Embedding Models

| Model                  | Speed  | Dimensions | Input Support | Pricing (per 1M tokens) |
|------------------------|--------|------------|---------------|-------------------------|
| text-embedding-3-small | Medium | 1536       | Text          | $0.02                   |
| text-embedding-3-large | Slow   | 3072       | Text          | $0.13                   |
| text-embedding-ada-002 | Slow   | 1536       | Text          | $0.1                    |

### Media Content Support

| Content Type | Supported Formats    | Max Size | Notes                               |
|--------------|----------------------|----------|-------------------------------------|
| Images       | PNG, JPEG, WebP, GIF | 20MB     | Base64 encoded or URL               |
| Audio        | WAV, MP3             | 25MB     | Base64 encoded only (audio models)  |
| Documents    | PDF                  | 20MB     | Base64 encoded only (vision models) |
| Video        | ❌ Not supported      | -        | -                                   |

**Important Details:**

- **Images**: Both URL and base64 supported
- **Audio**: Only WAV and MP3 formats, base64 only
- **PDF Documents**: Only PDF format, requires vision capability
- **Model Requirements**: Audio needs Audio capability, PDF needs Vision.Image capability

### Model-Specific Parameters Support

#### OpenAI Chat Parameters

The client supports OpenAI-specific parameters through `OpenAIChatParams` class:

```kotlin
val chatParams = OpenAIChatParams(
    temperature = 0.7,
    maxTokens = 1000,
    frequencyPenalty = 0.5,
    presencePenalty = 0.5,
    topP = 0.9,
    stop = listOf("\\n", "END"),
    logprobs = true,
    topLogprobs = 5,
    reasoningEffort = ReasoningEffort.MEDIUM,
    parallelToolCalls = true,
    audio = OpenAIAudioConfig(voice = "alloy", format = "mp3"),
    webSearchOptions = OpenAIWebSearchOptions(enabled = true)
)
```

#### OpenAI Responses API Parameters

For the Responses API, use `OpenAIResponsesParams`:

```kotlin
val responsesParams = OpenAIResponsesParams(
    temperature = 0.7,
    maxTokens = 1000,
    background = true,
    include = listOf("sources", "citations"),
    maxToolCalls = 10,
    reasoning = ReasoningConfig(effort = ReasoningEffort.HIGH),
    truncation = Truncation(type = "auto")
)
```

### API Endpoints Support

The client now supports both OpenAI API endpoints:

- **Chat Completions API**: Traditional chat completions with streaming support
- **Responses API**: Enhanced API with background processing, built-in tools, and structured outputs

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

    // Text-only example with Chat API
    val response = client.execute(
        prompt = prompt {
            system("You are helpful assistant")
            user("What time is it now?")
        },
        model = OpenAIModels.Chat.GPT5,
        params = OpenAIChatParams(
            temperature = 0.7,
            reasoningEffort = ReasoningEffort.MEDIUM
        )
    )

    // Using Responses API
    val responsesResponse = client.execute(
        prompt = prompt {
            system("You are helpful assistant")
            user("Research the latest developments in AI")
        },
        model = OpenAIModels.Chat.GPT5,
        params = OpenAIResponsesParams(
            background = true,
            include = listOf("sources", "citations"),
            maxToolCalls = 5
        )
    )

    println(response)
}
```

### Multimodal Examples

```kotlin
// Image analysis with GPT-5
val imageResponse = client.execute(
    prompt = prompt {
        user {
            text("What do you see in this image?")
            image("/path/to/image.jpg")
        }
    },
    model = OpenAIModels.Chat.GPT5,
    params = OpenAIChatParams(
        temperature = 0.3,
        reasoningEffort = ReasoningEffort.HIGH
    )
)

// Audio transcription (requires audio models)
val audioData = File("/path/to/audio.wav").readBytes()
val transcriptionResponse = client.execute(
    prompt = prompt {
        user {
            text("Transcribe this audio")
            audio(audioData, "wav")
        }
    },
    model = OpenAIModels.Audio.GPT4oAudio,
    params = OpenAIChatParams(
        audio = OpenAIAudioConfig(voice = "alloy", format = "mp3")
    )
)

// PDF document processing with Responses API
val pdfResponse = client.execute(
    prompt = prompt {
        user {
            text("Summarize this PDF document with citations")
            document("/path/to/document.pdf")
        }
    },
    model = OpenAIModels.Chat.GPT5,
    params = OpenAIResponsesParams(
        include = listOf("sources", "citations"),
        reasoning = ReasoningConfig(effort = ReasoningEffort.MEDIUM)
    )
)

// Embedding example
val embedding = client.embed(
    text = "This is a sample text for embedding",
    model = OpenAIModels.Embeddings.TextEmbedding3Small
)

// Mixed content with custom parameters
val mixedResponse = client.execute(
    prompt = prompt {
        user {
            text("Compare this image with the PDF:")
            image("/path/to/chart.png")
            document("/path/to/report.pdf")
            text("What insights can you provide?")
        }
    },
    model = OpenAIModels.Chat.GPT5,
    params = OpenAIChatParams(
        temperature = 0.5,
        maxTokens = 4000,
        reasoningEffort = ReasoningEffort.HIGH,
        parallelToolCalls = true
    )
)

// Background processing with Responses API
val backgroundResponse = client.execute(
    prompt = prompt {
        user("Research and analyze market trends for renewable energy")
    },
    model = OpenAIModels.Chat.GPT5,
    params = OpenAIResponsesParams(
        background = true,
        include = listOf("sources", "citations", "steps"),
        maxToolCalls = 20,
        reasoning = ReasoningConfig(effort = ReasoningEffort.HIGH)
    )
)
```
