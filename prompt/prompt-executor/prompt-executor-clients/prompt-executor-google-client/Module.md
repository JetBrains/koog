# Module prompt-executor-google-client

A client implementation for executing prompts using Google Gemini models with comprehensive multimodal support.

### Overview

This module provides a client implementation for the Google Gemini API, allowing you to execute prompts using Gemini
models. It handles authentication, request formatting, response parsing, and multimodal content encoding specific to
Google's API requirements. This client offers the most comprehensive multimodal support among all providers.

### Supported Models

| Name                       | Speed     | Context | Input Support                    | Output Support | Pricing (per 1M tokens)      |
|----------------------------|-----------|---------|----------------------------------|----------------|------------------------------|
| Gemini 2.0 Flash           | Fast      | 1M      | Audio, Image, Video, Text, Tools | Text, Tools    | $0.10-$0.70 / $0.40          |
| Gemini 2.0 Flash-001       | Fast      | 1M      | Audio, Image, Video, Text, Tools | Text, Tools    | $0.10-$0.70 / $0.40          |
| Gemini 2.0 Flash-Lite      | Very fast | 1M      | Audio, Image, Video, Text        | Text           | $0.075 / $0.30               |
| Gemini 1.5 Pro             | Medium    | 1M      | Audio, Image, Video, Text, Tools | Text, Tools    | $1.25-$2.50 / $5.00-$10.00   |
| Gemini 1.5 Pro Latest      | Medium    | 1M      | Audio, Image, Video, Text, Tools | Text, Tools    | $1.25-$2.50 / $5.00-$10.00   |
| Gemini 1.5 Pro-001         | Medium    | 1M      | Audio, Image, Video, Text, Tools | Text, Tools    | $1.25-$2.50 / $5.00-$10.00   |
| Gemini 1.5 Pro-002         | Medium    | 1M      | Audio, Image, Video, Text, Tools | Text, Tools    | $1.25-$2.50 / $5.00-$10.00   |
| Gemini 1.5 Flash           | Fast      | 1M      | Audio, Image, Video, Text, Tools | Text, Tools    | $0.075-$0.15 / $0.30-$0.60   |
| Gemini 1.5 Flash Latest    | Fast      | 1M      | Audio, Image, Video, Text        | Text           | $0.075-$0.15 / $0.30-$0.60   |
| Gemini 1.5 Flash-001       | Fast      | 1M      | Audio, Image, Video, Text        | Text           | $0.075-$0.15 / $0.30-$0.60   |
| Gemini 1.5 Flash-002       | Fast      | 1M      | Audio, Image, Video, Text        | Text           | $0.075-$0.15 / $0.30-$0.60   |
| Gemini 1.5 Flash 8B        | Very fast | 1M      | Audio, Image, Video, Text        | Text           | $0.0375-$0.075 / $0.15-$0.30 |
| Gemini 1.5 Flash 8B Latest | Very fast | 1M      | Audio, Image, Video, Text        | Text           | $0.0375-$0.075 / $0.15-$0.30 |
| Gemini 2.5 Pro Preview     | Slow      | 1M      | Audio, Image, Video, Text, Tools | Text           | $1.25-$2.50 / $10.00-$15.00  |
| Gemini 2.5 Flash Preview   | Medium    | 1M      | Audio, Image, Video, Text        | Text           | $0.15-$1.00 / $0.60-$3.50    |

### Media Content Support

| Content Type | Supported Formats              | Max Size | Notes                                      |
|--------------|--------------------------------|----------|--------------------------------------------|
| Images       | PNG, JPEG, WebP, HEIC, HEIF    | 20MB     | Base64 encoded only (no URLs)              |
| Audio        | WAV, MP3, AIFF, AAC, OGG, FLAC | 20MB     | Base64 encoded, for transcription/analysis |
| Video        | All formats via MIME type      | 20MB     | Base64 encoded, video analysis             |
| Documents    | All formats via MIME type      | 20MB     | Base64 encoded, content passed directly    |

**Important Limitations:**

- **No URL support**: All media must be provided as base64-encoded data
- **Documents**: Passed directly to model (no text extraction)
- **File validation**: Format checked via MIME type detection

### Using in your project

Add the dependency to your project:

```kotlin
dependencies {
    implementation("ai.koog.prompt:prompt-executor-google-client:$version")
}
```

Configure the client with your API key:

```kotlin
val googleClient = GoogleLLMClient(
    apiKey = "your-google-api-key",
)
```

### Example of usage

```kotlin
suspend fun main() {
    val client = GoogleLLMClient(
        apiKey = System.getenv("GEMINI_API_KEY"),
    )

    // Text-only example
    val response = client.execute(
        prompt = prompt {
            system("You are helpful assistant")
            user("What time is it now?")
        },
        model = GoogleModels.Gemini2_0Flash
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
    model = GoogleModels.Gemini2_0Flash
)

// Video analysis
val videoData = File("/path/to/video.mp4").readBytes()
val videoResponse = client.execute(
    prompt = prompt {
        user {
            text("Describe what happens in this video")
            video(videoData, "mp4")
        }
    },
    model = GoogleModels.Gemini1_5Pro
)

// Audio transcription
val audioData = File("/path/to/audio.wav").readBytes()
val audioResponse = client.execute(
    prompt = prompt {
        user {
            text("Transcribe and analyze this audio")
            audio(audioData, "wav")
        }
    },
    model = GoogleModels.Gemini1_5Pro
)

// Document processing
val documentResponse = client.execute(
    prompt = prompt {
        user {
            text("Summarize this document")
            document("/path/to/document.pdf")
        }
    },
    model = GoogleModels.Gemini2_0Flash
)

// All media types combined
val comprehensiveResponse = client.execute(
    prompt = prompt {
        user {
            text("Analyze all this content and find connections:")
            image("/path/to/chart.png")
            video(videoData, "mp4")
            audio(audioData, "wav")
            document("/path/to/report.pdf")
            text("What insights can you provide?")
        }
    },
    model = GoogleModels.Gemini1_5Pro
)
```
