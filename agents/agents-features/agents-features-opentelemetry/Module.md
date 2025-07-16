# Module agents-features-opentelemetry

Provides [OpenTelemetry](https://opentelemetry.io) integration for monitoring and tracing AI agents in the Koog framework.

### Overview

The agents-features-opentelemetry module enables observability for AI agents by integrating with OpenTelemetry, allowing you to:

- Monitor agent performance and behavior
- Debug issues in complex agent workflows
- Visualize the execution flow of your agents
- Track LLM calls and tool usage
- Analyze agent behavior patterns

Key features include:
- Automatic span creation for agent events (agent execution, node execution, LLM calls, tool calls)
- Support for various exporters (OTLP, Logging)
- Customizable sampling strategies
- Resource attributes following [OpenTelemetry Semantic Convention for GenAI](https://opentelemetry.io/docs/specs/semconv/gen-ai/)
- Integration with observability systems like Jaeger

### Using in your project

To use the OpenTelemetry feature in your project, add the following dependency:

```kotlin
dependencies {
    implementation("ai.koog.agents:agents-features-opentelemetry:$version")
}
```

Then, install the OpenTelemetry feature when creating your agent:

```kotlin
val agent = AIAgent(
    executor = simpleOpenAIExecutor(apiKey),
    llmModel = OpenAIModels.Chat.GPT4o,
    systemPrompt = "You are a helpful assistant.",
    installFeatures = {
        install(OpenTelemetry) {
            setServiceInfo("my-agent-service", "1.0.0")   // Set your service configuration
            addSpanExporter(LoggingSpanExporter.create()) // Add Logging exporter
        }
    }
)
```

### Using in tests

For testing agents with OpenTelemetry, you can use a mock span exporter:

```kotlin
// Create a mock span exporter for testing
val mockSpanExporter = MockSpanExporter()

// Create a test agent with OpenTelemetry
val testAgent = AIAgent(
    // other test configuration
) {
    install(OpenTelemetry) {
        setServiceInfo("test-agent", "1.0.0")
        addSpanExporter(mockSpanExporter)
    }
    
    // Enable testing mode
    withTesting()
}
```

### Example of usage

Here's an example of using OpenTelemetry with an OTLP exporter for Jaeger integration:

```kotlin
val agent = AIAgent(
    executor = simpleOpenAIExecutor(apiKey),
    llmModel = OpenAIModels.Chat.GPT4o,
    systemPrompt = "You are a helpful assistant.",
    installFeatures = {
        install(OpenTelemetry) {
            // Configure service info
            setServiceInfo("my-otlp-agent", "1.0.0")
            
            // Add OTLP exporter for Jaeger
            addSpanExporter(
                OtlpGrpcSpanExporter.builder()
                    .setEndpoint("http://localhost:4317")
                    .build()
            )
            
            // Add resource attributes
            addResourceAttributes(mapOf(
                AttributeKey.stringKey("deployment.environment") to "production"
            ))
        }
    }
)

// Run the agent
val result = agent.run("Tell me about OpenTelemetry")
println(result)

// Wait for telemetry data to be exported
delay(5.seconds)
```

This example demonstrates configuring an agent with OpenTelemetry, running it, and ensuring that telemetry data is exported before the application terminates.