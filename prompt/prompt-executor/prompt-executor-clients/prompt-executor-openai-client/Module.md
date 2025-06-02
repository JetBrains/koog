# Module prompt-executor-openai-client

A client implementation for executing prompts using OpenAI's GPT models with support for images and audio.

### Overview

This module provides a client implementation for the OpenAI API, allowing you to execute prompts using GPT models. It
handles authentication, request formatting, response parsing, and multimodal content encoding specific to OpenAI's API
requirements.

### Supported Models

#### Reasoning Models

| Model       | Speed   | Context | Input Support       | Output Support | Pricing (per 1M tokens) |
|-------------|---------|---------|---------------------|----------------|-------------------------|
| GPT-4o Mini | Medium  | 128K    | Text, Images, Tools | Text, Tools    | $1.1-$4.4               |
| o3-mini     | Medium  | 200K    | Text, Tools         | Text, Tools    | $1.1-$4.4               |
| o1-mini     | Slow    | 128K    | Text                | Text           | $1.1-$4.4               |
| o3          | Slowest | 200K    | Text, Images, Tools | Text, Tools    | $10-$40                 |
| o1          | Slowest | 200K    | Text, Images, Tools | Text, Tools    | $15-$60                 |

#### Chat Models

| Model   | Speed  | Context | Input Support       | Output Support | Pricing (per 1M tokens) |
|---------|--------|---------|---------------------|----------------|-------------------------|
| GPT-4o  | Medium | 128K    | Text, Images, Tools | Text, Tools    | $2.5-$10                |
| GPT-4.1 | Medium | 1M      | Text, Images, Tools | Text, Tools    | $2-$8                   |

#### Audio Models

| Model             | Speed  | Context | Input Support      | Output Support     | Pricing (per 1M tokens) |
|-------------------|--------|---------|--------------------|--------------------|-------------------------|
| GPT-4o Mini Audio | Fast   | 128K    | Text, Audio, Tools | Text, Audio, Tools | $0.15-$0.6/$10-$20      |
| GPT-4o Audio      | Medium | 128K    | Text, Audio, Tools | Text, Audio, Tools | $2.5-$10/$40-$80        |

#### Cost-Optimized Models

| Model        | Speed     | Context | Input Support       | Output Support | Pricing (per 1M tokens) |
|--------------|-----------|---------|---------------------|----------------|-------------------------|
| o4-mini      | Medium    | 200K    | Text, Images, Tools | Text, Tools    | $1.1-$4.4               |
| GPT-4.1-nano | Very fast | 1M      | Text, Images, Tools | Text, Tools    | $0.1-$0.4               |
| GPT-4.1-mini | Fast      | 1M      | Text, Images, Tools | Text, Tools    | $0.4-$1.6               |

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

    // Text-only example
    val response = client.execute(
        prompt = prompt {
            system("You are helpful assistant")
            user("What time is it now?")
        },
        model = OpenAIModels.Chat.GPT4o
    )

    println(response)
}
```

### Multimodal Examples

```kotlin
// Image analysis
val imageResponse = client.execute(
    prompt = prompt {
        user {
            text("What do you see in this image?")
            image("/path/to/image.jpg")
        }
    },
    model = OpenAIModels.Chat.GPT4o
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
    model = OpenAIModels.Audio.GPT4oAudio
)

// PDF document processing (requires vision models)
val pdfResponse = client.execute(
    prompt = prompt {
        user {
            text("Summarize this PDF document")
            document("/path/to/document.pdf")
        }
    },
    model = OpenAIModels.Chat.GPT4o
)

// Embedding example
val embedding = client.embed(
    text = "This is a sample text for embedding",
    model = OpenAIModels.Embeddings.TextEmbeddingAda3Small
)

// Mixed content (image + PDF)
val mixedResponse = client.execute(
    prompt = prompt {
        user {
            text("Compare this image with the PDF:")
            image("/path/to/chart.png")
            document("/path/to/report.pdf")
            text("What insights can you provide?")
        }
    },
    model = OpenAIModels.Chat.GPT4o
)
```
