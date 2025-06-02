# Module prompt-executor-anthropic-client

A client implementation for executing prompts using Anthropic's Claude models with support for images and documents.

### Overview

This module provides a client implementation for the Anthropic API, allowing you to execute prompts using Claude models.
It handles authentication, request formatting, response parsing, and multimodal content encoding specific to Anthropic's
API requirements.

### Supported Models

| Model             | Speed           | Context | Input Support     | Output Support | Pricing (per 1M tokens) |
|-------------------|-----------------|---------|-------------------|----------------|-------------------------| 
| Claude 3 Opus     | Moderately fast | 200K    | Text, Images, PDF | Text, Tools    | $15.00 / $75.00         |
| Claude 3 Haiku    | Fast            | 200K    | Text, Images, PDF | Text, Tools    | $0.25 / $1.25           |
| Claude 3.5 Haiku  | Fastest         | 200K    | Text, Images, PDF | Text, Tools    | $0.8 / $4.0             |
| Claude 3.5 Sonnet | Fast            | 200K    | Text, Images, PDF | Text, Tools    | $3.00 / $15.00          |
| Claude 3.7 Sonnet | Fast            | 200K    | Text, Images, PDF | Text, Tools    | $3.00 / $15.00          |
| Claude Sonnet 4   | Fast            | 200K    | Text, Images, PDF | Text, Tools    | $3.00 / $15.00          |
| Claude Opus 4     | Moderately fast | 200K    | Text, Images, PDF | Text, Tools    | $15.00 / $75.00         |

### Media Content Support

| Content Type | Supported Formats        | Max Size | Notes                                     |
|--------------|--------------------------|----------|-------------------------------------------|
| Images       | PNG, JPEG, WebP, GIF     | 5MB      | Base64 encoded or URL                     |
| Documents    | PDF (URL/local), TXT, MD | 5MB      | PDF passed directly, TXT/MD as plain text |
| Audio        | ❌ Not supported          | -        | -                                         |
| Video        | ❌ Not supported          | -        | -                                         |

**Document Support Details:**

- **PDF**: Supported via URL or local file (base64 encoded)
- **TXT/MD**: Read as plain text content
- **Other formats**: Not supported (HTML, CSV, XML, etc.)

### Using in your project

Add the dependency to your project:

```kotlin
dependencies {
    implementation("ai.koog.prompt:prompt-executor-anthropic-client:$version")
}
```

Configure the client with your API key:

```kotlin
val anthropicClient = AnthropicLLMClient(
    apiKey = "your-anthropic-api-key",
)
```

### Using in tests

For testing, you can use a mock implementation:

```kotlin
val mockAnthropicClient = MockAnthropicClient(
    responses = listOf("Mocked response 1", "Mocked response 2")
)
```

### Example of usage

```kotlin
suspend fun main() {
    val client = AnthropicLLMClient(
        apiKey = System.getenv("ANTHROPIC_API_KEY"),
    )

    // Text-only example
    val response = client.execute(
        prompt = prompt {
            system("You are helpful assistant")
            user("What time is it now?")
        },
        model = AnthropicModels.Sonnet_3_5
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
    model = AnthropicModels.Sonnet_3_5
)

// PDF document processing
val pdfResponse = client.execute(
    prompt = prompt {
        user {
            text("Summarize this PDF document")
            document("/path/to/document.pdf")
        }
    },
    model = AnthropicModels.Sonnet_3_5
)

// PDF from URL
val pdfUrlResponse = client.execute(
    prompt = prompt {
        user {
            text("Analyze this research paper")
            document("https://example.com/paper.pdf")
        }
    },
    model = AnthropicModels.Sonnet_3_5
)

// Text file processing
val textResponse = client.execute(
    prompt = prompt {
        user {
            text("Review this markdown file")
            document("/path/to/readme.md")
        }
    },
    model = AnthropicModels.Sonnet_3_5
)

// Mixed content
val mixedResponse = client.execute(
    prompt = prompt {
        user {
            text("Compare this image with the PDF:")
            image("/path/to/chart.png")
            document("/path/to/report.pdf")
            text("What insights can you provide?")
        }
    },
    model = AnthropicModels.Sonnet_3_5
)
```
