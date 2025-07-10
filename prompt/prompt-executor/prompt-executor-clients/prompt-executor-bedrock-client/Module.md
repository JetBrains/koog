# Module prompt-executor-bedrock-client

A Kotlin Multiplatform client implementation for executing prompts using AWS Bedrock's foundation models with
comprehensive multimodal support, tool calling, and streaming capabilities.

## Overview

This module provides a client implementation for AWS Bedrock, Amazon's fully managed service that offers foundation
models from leading AI companies including Anthropic, AI21 Labs, Cohere, Meta, Mistral, and Amazon. The client supports
multimodal content (text and images), tool/function calling, streaming responses, and comprehensive error handling
across multiple model providers.

## Supported Models

### Anthropic Claude Models

| Model           | Speed     | Input Support       | Output Support | Context | Pricing            | Notes                          |
|-----------------|-----------|---------------------|----------------|---------|--------------------|--------------------------------|
| Claude 3 Opus   | Medium    | Text, Images, Tools | Text, Tools    | 200K    | $15/$75 per 1M     | Most capable, best for complex |
| Claude 3 Sonnet | Fast      | Text, Images, Tools | Text, Tools    | 200K    | $3/$15 per 1M      | Balanced performance and cost  |
| Claude 3 Haiku  | Very Fast | Text, Images, Tools | Text, Tools    | 200K    | $0.25/$1.25 per 1M | Fast and efficient             |
| Claude 2.1      | Medium    | Text                | Text           | 200K    | $8/$24 per 1M      | Previous gen, extended context |
| Claude 2.0      | Medium    | Text                | Text           | 100K    | $8/$24 per 1M      | Previous generation            |
| Claude Instant  | Very Fast | Text                | Text           | 100K    | $0.8/$2.4 per 1M   | Fast, cost-effective           |

### Amazon Titan Models

| Model              | Speed     | Input Support | Output Support | Pricing           | Notes                     |
|--------------------|-----------|---------------|----------------|-------------------|---------------------------|
| Titan Text Express | Fast      | Text          | Text           | $0.2/$0.6 per 1M  | Cost-effective, general   |
| Titan Text Lite    | Very Fast | Text          | Text           | $0.15/$0.2 per 1M | Lightweight, simple tasks |
| Titan Text Premier | Medium    | Text          | Text           | $0.5/$1.5 per 1M  | Advanced reasoning        |

### AI21 Labs Jurassic Models

| Model            | Speed  | Input Support | Output Support | Pricing            | Notes                  |
|------------------|--------|---------------|----------------|--------------------|------------------------|
| Jurassic-2 Ultra | Medium | Text          | Text           | $15/$15 per 1M     | Most powerful, complex |
| Jurassic-2 Mid   | Fast   | Text          | Text           | $12.5/$12.5 per 1M | Balanced performance   |

### Cohere Models

| Model         | Speed  | Input Support | Output Support | Pricing          | Notes                 |
|---------------|--------|---------------|----------------|------------------|-----------------------|
| Command       | Medium | Text          | Text           | $0.75/$2 per 1M  | Instruction following |
| Command Light | Fast   | Text          | Text           | $0.3/$0.6 per 1M | Lightweight version   |

### Meta Llama Models

| Model                | Speed  | Input Support | Output Support | Pricing            | Notes                   |
|----------------------|--------|---------------|----------------|--------------------|-------------------------|
| Llama 3 70B Instruct | Medium | Text          | Text           | $2.65/$3.5 per 1M  | Latest arch, 70B params |
| Llama 3 8B Instruct  | Fast   | Text          | Text           | $0.3/$0.6 per 1M   | Latest arch, 8B params  |
| Llama 2 70B Chat     | Medium | Text          | Text           | $1.95/$2.56 per 1M | Dialogue optimized, 70B |
| Llama 2 13B Chat     | Fast   | Text          | Text           | $0.75/$1 per 1M    | Dialogue optimized, 13B |

### Mistral Models

| Model         | Speed  | Input Support | Output Support | Pricing           | Notes                       |
|---------------|--------|---------------|----------------|-------------------|-----------------------------|
| Mistral Large | Medium | Text, Tools   | Text, Tools    | $4/$12 per 1M     | Most capable, multilingual  |
| Mixtral 8x7B  | Medium | Text          | Text           | $0.45/$0.7 per 1M | Mixture of experts          |
| Mistral 7B    | Fast   | Text          | Text           | $0.15/$0.2 per 1M | Efficient, 7B params        |
| Mistral Small | Fast   | Text          | Text           | $1/$3 per 1M      | Lightweight, cost-effective |

*Pricing shown as Input/Output per 1M tokens

## Media Content Support

| Content Type | Supported Models        | Formats              | Max Size | Notes                     |
|--------------|-------------------------|----------------------|----------|---------------------------|
| Images       | Claude 3 (all variants) | PNG, JPEG, WebP, GIF | 5MB      | Base64 or URL supported   |
| Audio        | ❌ Not supported         | -                    | -        | No audio models available |
| Documents    | ❌ Not supported         | -                    | -        | Extract text first        |
| Video        | ❌ Not supported         | -                    | -        | -                         |

**Important Notes:**

- **Images**: Only Claude 3 models support image input
- **Size limits**: AWS Bedrock enforces a 5MB limit for image data
- **Tools**: Only Claude 3 models and Mistral Large support function calling
- **Streaming**: All models support token streaming

## Features

- **Multi-Model Support**: Access to models from multiple providers through a single API
- **Tool/Function Calling**: Supported for Claude 3 and Mistral Large models
- **Streaming Responses**: Real-time token streaming for all supported models
- **Multimodal Input**: Image support for Claude 3 models
- **Kotlin Multiplatform**: Works on JVM and Android (JS/Native not supported due to AWS SDK limitations)
- **Comprehensive Error Handling**: Model-specific error messages and validation
- **Token Usage Tracking**: Detailed token consumption metrics
- **Region Support**: Available across multiple AWS regions

## Installation

Add the dependency to your project:

```kotlin
dependencies {
    implementation("ai.koog.prompt:prompt-executor-bedrock-client:$version")
}
```

## Configuration

Configure the client with your AWS credentials:

```kotlin
val bedrockClient = createBedrockLLMClient(
    awsAccessKeyId = "your-access-key",
    awsSecretAccessKey = "your-secret-key",
    settings = BedrockClientSettings(
        region = "us-east-1",
        endpointUrl = null, // Optional custom endpoint
        maxRetries = 3,
        enableLogging = false,
        timeoutConfig = ConnectionTimeoutConfig(
            requestTimeoutMillis = 60_000,
            connectTimeoutMillis = 5_000,
            socketTimeoutMillis = 60_000
        )
    )
)
```

## Basic Usage

```kotlin
suspend fun main() {
    val client = createBedrockLLMClient(
        awsAccessKeyId = ApiKeyService.awsAccessKey,
        awsSecretAccessKey = ApiKeyService.awsSecretAccessKey,
        settings = BedrockClientSettings(region = "us-west-2")
    )

    // Simple text generation
    val response = client.execute(
        prompt = prompt {
            system("You are a helpful assistant")
            user("What is the capital of France?")
        },
        model = BedrockModels.AnthropicClaude3Sonnet
    )

    println(response.first().content)
}
```

## Advanced Examples

### Multimodal Input (Images)

```kotlin
// Image analysis with Claude 3
val imageResponse = client.execute(
    prompt = prompt {
        user {
            text("What do you see in this image?")
            image("/path/to/image.jpg")
        }
    },
    model = BedrockModels.AnthropicClaude3Opus
)

// Multiple images with Claude 3
val multiImageResponse = client.execute(
    prompt = prompt {
        user {
            text("Compare these images")
            image("/path/to/image1.jpg")
            image("/path/to/image2.jpg")
        }
    },
    model = BedrockModels.AnthropicClaude3Sonnet
)
```

### Tool/Function Calling

```kotlin
// Define a tool
val weatherTool = ToolDescriptor(
    name = "get_weather",
    description = "Get the weather for a location",
    requiredParameters = listOf(
        ToolParameterDescriptor(
            name = "location",
            description = "The city and state",
            type = ToolParameterType.String
        )
    )
)

// Use with Claude 3 or Mistral Large
val toolResponse = client.execute(
    prompt = prompt {
        user("What's the weather in New York?")
    },
    model = BedrockModels.AnthropicClaude3Sonnet,
    tools = listOf(weatherTool)
)

// Handle tool calls in response
toolResponse.forEach { message ->
    when (message) {
        is Message.Tool.Call -> {
            println("Tool called: ${message.tool}")
            println("Arguments: ${message.content}")
        }
        is Message.Assistant -> {
            println("Assistant: ${message.content}")
        }
    }
}
```

### Streaming Responses

```kotlin
// Stream tokens as they're generated
val stream = client.executeStreaming(
    prompt = prompt {
        user("Write a haiku about clouds")
    },
    model = BedrockModels.AnthropicClaude3Haiku
)

stream.collect { token ->
    print(token) // Print each token as it arrives
}
```

### Model-Specific Features

```kotlin
// Use extended context with Claude 2.1
val longContextResponse = client.execute(
    prompt = prompt {
        system("Analyze this document")
        user(veryLongDocument) // Up to 200K tokens
    },
    model = BedrockModels.AnthropicClaude21
)

// Use Mixtral's mixture of experts
val mixtralResponse = client.execute(
    prompt = prompt {
        user("Explain quantum computing")
    },
    model = BedrockModels.MistralMixtral8x7BInstruct
)

// Use Mistral Large with tools
val mistralToolResponse = client.execute(
    prompt = prompt {
        user("Search for recent AI news")
    },
    model = BedrockModels.MistralLarge,
    tools = listOf(searchTool)
)
```

## Testing

For testing, you can use a mock implementation:

```kotlin
val mockBedrockClient = MockBedrockClient(
    responses = listOf("Mocked response 1", "Mocked response 2")
)
```

## Platform Support

- ✅ **JVM**: Full support with all features
- ✅ **Android**: Full support (requires Java 8+ compatibility)
- ❌ **JavaScript**: Not supported (AWS SDK limitation)
- ❌ **Native**: Not supported (AWS SDK limitation)

For non-JVM platforms, consider using a server-side proxy or the OpenRouter client as an alternative.

## Region Availability

Not all models are available in all AWS regions. Check
the [AWS documentation](https://docs.aws.amazon.com/bedrock/latest/userguide/models-regions.html) for current
availability.

Common regions with good model coverage:

- `us-east-1` (N. Virginia) - Widest model selection
- `us-west-2` (Oregon) - Most models available
- `eu-west-1` (Ireland) - European deployment
- `ap-northeast-1` (Tokyo) - Asia Pacific deployment

## Security Best Practices

1. **Never hardcode credentials**: Use environment variables or AWS IAM roles
2. **Use IAM policies**: Restrict access to specific models and actions
3. **Enable CloudTrail**: Monitor API usage for security and cost tracking
4. **Rotate credentials**: Regularly update access keys
5. **Use VPC endpoints**: For enhanced security in production environments

## Error Handling

The client provides detailed error messages for common issues:

```kotlin
try {
    val response = client.execute(prompt, model)
} catch (e: IllegalArgumentException) {
    // Model doesn't support requested features (e.g., tools, vision)
    logger.error("Feature not supported: ${e.message}")
} catch (e: Exception) {
    // AWS API errors, network issues, rate limits, etc.
    logger.error("API error: ${e.message}")
}
```

## Cost Considerations

AWS Bedrock charges per token for model usage. Monitor your usage and set up billing alerts:

- **Claude 3/4 models**: Higher cost, superior performance and capabilities
- **Titan models**: Cost-effective for general-purpose tasks
- **Llama models**: Good balance of cost and performance
- **Mistral models**: Competitive pricing with tool support (Large model)

## Limitations

1. **Model Availability**: Not all models are available in all AWS regions
2. **Rate Limits**: AWS imposes rate limits per model and region
3. **Context Windows**: Vary by model (see tables above)
4. **Feature Support**: Not all models support all features (tools, vision, etc.)
5. **Platform Support**: Limited to JVM and Android due to AWS SDK constraints
6. **Media Support**: Only images supported, no audio/video/documents

## Performance Tips

1. **Choose the right model**: Balance cost, speed, and capability needs
2. **Use streaming**: For better user experience with long responses
3. **Optimize prompts**: Shorter prompts reduce costs and latency
4. **Batch requests**: When possible, combine multiple tasks
5. **Monitor usage**: Track token consumption and costs

## Contributing

See the main project README for contribution guidelines.

## License

This module is part of the Koog project and follows the same license terms.
