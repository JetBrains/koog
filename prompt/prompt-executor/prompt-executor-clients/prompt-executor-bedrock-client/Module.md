# Module prompt-executor-bedrock-client

A Kotlin Multiplatform client implementation for executing prompts using AWS Bedrock's foundation models with
comprehensive multimodal support, tool calling, and streaming capabilities.

## Overview

This module provides a client implementation for AWS Bedrock, Amazon's fully managed service that offers foundation
models from leading AI companies including Anthropic, Amazon, Meta, Moonshot AI, MiniMax, OpenAI, Google, and Cohere.
The client supports multimodal content (text and images), tool/function calling, structured output, streaming responses,
and comprehensive error handling across multiple model providers via both the InvokeModel and Converse APIs.

## Supported Models

Models are organized by provider. All models support both the InvokeModel and Converse APIs unless noted.
Models marked **Converse-only** require `BedrockClientSettings(apiMethod = BedrockAPIMethod.Converse)`.

### Anthropic Claude Models

All Claude models support text, images, documents, and tool calling.

| Model                    | Context | Tools | Vision | Structured Output | Notes                       |
|--------------------------|---------|-------|--------|-------------------|-----------------------------|
| Claude 4.7 Opus          | 200K    | Yes   | Yes    | Yes               | Latest Opus                 |
| Claude 4.6 Opus          | 200K    | Yes   | Yes    | Yes               | Frontier model              |
| Claude 4.5 Opus          | 200K    | Yes   | Yes    | Yes               | Best for coding and agents  |
| Claude 4.1 Opus          | 200K    | Yes   | Yes    | -                 |                             |
| Claude 4 Opus            | 200K    | Yes   | Yes    | -                 |                             |
| Claude 4.6 Sonnet        | 200K    | Yes   | Yes    | Yes               | Speed and intelligence      |
| Claude 4.5 Sonnet        | 200K    | Yes   | Yes    | Yes               | Enhanced capabilities       |
| Claude 4 Sonnet          | 200K    | Yes   | Yes    | -                 |                             |
| Claude 4.5 Haiku         | 200K    | Yes   | Yes    | Yes               | Fast, cost-effective        |
| Claude 3 Haiku (deprecated) | 200K | Yes   | Yes    | -                 | Use Claude 4.5 Haiku instead |

### Amazon Nova Models

| Model         | Context | Tools | Vision | Notes                          |
|---------------|---------|-------|--------|--------------------------------|
| Nova Micro    | 128K    | Yes   | -      | Ultra-fast, low-cost           |
| Nova Lite     | 300K    | Yes   | -      | Balanced performance and cost  |
| Nova Pro      | 300K    | Yes   | -      | Complex reasoning              |
| Nova Premier  | 1M      | Yes   | -      | Most capable Amazon model      |

### Meta Llama Models

| Model                   | Context | Tools | Vision | Notes                    |
|-------------------------|---------|-------|--------|--------------------------|
| Llama 3.3 70B Instruct  | 128K    | Yes   | -      | Latest Llama 3.x         |
| Llama 3.2 90B Instruct  | 128K    | Yes   | Yes    | Multimodal               |
| Llama 3.2 11B Instruct  | 128K    | Yes   | Yes    | Multimodal               |
| Llama 3.2 3B Instruct   | 128K    | -     | -      | Compact                  |
| Llama 3.2 1B Instruct   | 128K    | -     | -      | Edge deployment          |
| Llama 3.1 405B Instruct | 128K    | -     | -      | Largest Llama            |
| Llama 3.1 70B Instruct  | 128K    | -     | -      |                          |
| Llama 3.1 8B Instruct   | 128K    | -     | -      | Efficient                |
| Llama 3.0 70B Instruct  | 8K      | -     | -      |                          |
| Llama 3.0 8B Instruct   | 8K      | -     | -      |                          |

### Moonshot Kimi Models (Converse-only)

| Model            | Context | Tools | Vision | Notes                                      |
|------------------|---------|-------|--------|--------------------------------------------|
| Kimi K2.5        | 256K    | Yes   | Yes    | Multimodal, dual-mode reasoning             |
| Kimi K2 Thinking | 256K    | Yes   | -      | Deep reasoning with chain-of-thought traces |

### MiniMax Models (Converse-only)

| Model      | Context | Tools | Vision | Notes                                  |
|------------|---------|-------|--------|----------------------------------------|
| MiniMax M2.5 | 1M    | Yes   | -      | Agent-native, token-efficient reasoning |

### OpenAI GPT-OSS Models (Converse-only)

| Model          | Context | Tools | Vision | Structured Output | Notes                        |
|----------------|---------|-------|--------|-------------------|------------------------------|
| GPT-OSS 120B   | 128K    | Yes   | -      | Yes               | MoE, 5.1B active per token   |
| GPT-OSS 20B    | 128K    | Yes   | -      | Yes               | MoE, 3.6B active per token   |

### Google Gemma 3 Models (Converse-only)

| Model            | Context | Tools | Vision | Structured Output | Notes                        |
|------------------|---------|-------|--------|-------------------|------------------------------|
| Gemma 3 27B IT   | 128K    | Yes   | Yes    | Yes               | Largest, multimodal          |
| Gemma 3 12B IT   | 128K    | Yes   | Yes    | Yes               | Multimodal, structured output |
| Gemma 3 4B IT    | 128K    | Yes   | Yes    | -                 | Compact, edge deployment     |

### Embedding Models

| Model                      | Context | Dimensions | Notes                            |
|----------------------------|---------|------------|----------------------------------|
| Cohere Embed v4            | 512     | 1536       | Multimodal, requires inference profile |
| Cohere Embed English v3    | 8K      | 1024       | English text                     |
| Cohere Embed Multilingual v3 | 8K    | 1024       | 100+ languages                   |
| Amazon Titan Embed Text v2 | 8K      | 1024       | Amazon native                    |
| Amazon Titan Embed Text v1 | 8K      | 1024       | Amazon native                    |

## Media Content Support

| Content Type | Supported Models                                          | Formats              | Max Size | Notes                   |
|--------------|-----------------------------------------------------------|----------------------|----------|-------------------------|
| Images       | Claude (all), Kimi K2.5, Gemma 3 (all), Llama 3.2 11B/90B | PNG, JPEG, WebP, GIF | 5MB     | Base64 or URL supported |
| Documents    | Claude (all), Gemma 3 12B/27B                             | PDF                  | -        | Via Converse API        |
| Audio        | Not supported                                              | -                    | -        |                         |
| Video        | Not supported                                              | -                    | -        |                         |

**Important Notes:**

- **Converse-only models**: Kimi, MiniMax, GPT-OSS, and Gemma 3 models require `BedrockAPIMethod.Converse`
- **Inference profiles**: Cohere Embed v4 requires an inference profile prefix (default: `us.`)
- **Third-party models**: Kimi, MiniMax, GPT-OSS, and Gemma 3 do not use inference profile prefixes
- **Tool calling**: Supported by all models except some older Llama variants
- **Streaming**: All models support token streaming via both InvokeModel and Converse APIs

## Features

- **Multi-Model Support**: Access to 30+ models from 8 providers through a single API
- **Dual API Support**: Both InvokeModel (provider-specific) and Converse (unified) APIs
- **Tool/Function Calling**: Supported across Claude, Nova, Llama 3.3, Kimi, MiniMax, GPT-OSS, and Gemma 3 models
- **Structured Output**: JSON Schema-constrained output for GPT-OSS and Gemma 3 12B/27B models
- **Streaming Responses**: Real-time token streaming for all supported models
- **Multimodal Input**: Image support for Claude, Kimi K2.5, Gemma 3, and Llama 3.2 11B/90B models
- **Embeddings**: Text embedding via Titan, Cohere v3, and Cohere v4 models
- **Prompt Caching**: Cache control support for reduced latency and cost
- **Kotlin Multiplatform**: Works on JVM and Android (JS/Native not supported due to AWS SDK limitations)
- **Comprehensive Error Handling**: Model-specific error messages and validation
- **Token Usage Tracking**: Detailed token consumption metrics including cache hit/miss
- **Region Support**: Available across multiple AWS regions with inference profile routing

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
        model = BedrockModels.AnthropicClaude4Sonnet
    )

    println(response.first().content)
}
```

## Advanced Examples

### Multimodal Input (Images)

```kotlin
// Image analysis with Claude
val imageResponse = client.execute(
    prompt = prompt {
        user {
            text("What do you see in this image?")
            image("/path/to/image.jpg")
        }
    },
    model = BedrockModels.AnthropicClaude46Opus
)

// Multiple images with Claude
val multiImageResponse = client.execute(
    prompt = prompt {
        user {
            text("Compare these images")
            image("/path/to/image1.jpg")
            image("/path/to/image2.jpg")
        }
    },
    model = BedrockModels.AnthropicClaude4Sonnet
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

// Use with any tool-capable model
val toolResponse = client.execute(
    prompt = prompt {
        user("What's the weather in New York?")
    },
    model = BedrockModels.AnthropicClaude4Sonnet,
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
    model = BedrockModels.AnthropicClaude4_5Haiku
)

stream.collect { token ->
    print(token) // Print each token as it arrives
}
```

### Converse-Only Models

Kimi, MiniMax, GPT-OSS, and Gemma 3 models require the Converse API:

```kotlin
val converseClient = createBedrockLLMClient(
    awsAccessKeyId = ApiKeyService.awsAccessKey,
    awsSecretAccessKey = ApiKeyService.awsSecretAccessKey,
    settings = BedrockClientSettings(
        region = "us-east-1",
        apiMethod = BedrockAPIMethod.Converse // Required for these models
    )
)

// MiniMax M2.5 with 1M context
val minimaxResponse = converseClient.execute(
    prompt = prompt {
        user(veryLongDocument) // Up to 1M tokens
    },
    model = BedrockModels.MiniMaxM2_5
)

// GPT-OSS with structured output
val gptOssResponse = converseClient.execute(
    prompt = prompt {
        user("List the top 3 programming languages")
    },
    model = BedrockModels.OpenAIGptOss120B
)

// Gemma 3 with image input
val gemmaResponse = converseClient.execute(
    prompt = prompt {
        user {
            text("Describe this image")
            image("/path/to/image.jpg")
        }
    },
    model = BedrockModels.GoogleGemma3_27BIt
)

// Kimi K2 Thinking with deep reasoning traces
val kimiResponse = converseClient.execute(
    prompt = prompt {
        user("Solve this complex problem step by step: What are the environmental impacts of quantum computing?")
    },
    model = BedrockModels.MoonshotKimiK2Thinking
)

kimiResponse.forEach { message ->
    when (message) {
        is Message.Reasoning -> {
            // Access the model's chain-of-thought reasoning
            println("Reasoning trace: ${message.content}")
        }
        is Message.Assistant -> {
            println("Final answer: ${message.content}")
        }
    }
}
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

- **Claude 4.x models**: Higher cost, superior performance and capabilities
- **Amazon Nova models**: Cost-effective for general-purpose tasks
- **Meta Llama models**: Good balance of cost and performance
- **Gemma 3 / GPT-OSS models**: Competitive pricing, open-weight models
- **MiniMax M2.5 / Kimi K2.5**: Cost-efficient for agentic workloads

## Limitations

1. **Model Availability**: Not all models are available in all AWS regions
2. **Rate Limits**: AWS imposes rate limits per model and region
3. **Context Windows**: Vary by model (see tables above)
4. **Feature Support**: Not all models support all features (tools, vision, structured output, etc.)
5. **Platform Support**: Limited to JVM and Android due to AWS SDK constraints
6. **Converse-only Models**: Kimi, MiniMax, GPT-OSS, and Gemma 3 models only work with the Converse API
7. **Embedding Limitations**: Cohere Embed v4 multimodal and configurable dimensions not yet supported by this client

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
