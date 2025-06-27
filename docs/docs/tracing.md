# Tracing

This page includes details about the Tracing feature, which provides comprehensive tracing capabilities for AI agents.

## Feature overview

The Tracing feature is a powerful monitoring and debugging tool that captures detailed information about agent runs,
including:

- Agent creation and initialization
- Strategy execution
- LLM calls
- Tool invocations
- Node execution within the agent graph

This feature operates by intercepting key events in the agent pipeline and forwarding them to configurable message
processors. These processors can output the trace information to various destinations such as log files or the
filesystem, enabling developers to gain insights into agent behavior and troubleshoot issues effectively.

### Event flow

1. The Tracing feature intercepts events in the agent pipeline.
2. Events are filtered based on the configured message filter.
3. Filtered events are passed to registered message processors.
4. Message processors format and output the events to their respective destinations.

## Configuration and initialization

### Basic setup

To use the Tracing feature, you need to:

1. Have one or more message processors (you can use the existing ones or create your own).
2. Install `Tracing` in your agent.
3. Configure the message filter (optional).
4. Add the message processors to the feature.

```kotlin
// Defining a logger/file that will be used as a destination of trace messages 
val logger = LoggerFactory.create("my.trace.logger")
val fs = JVMFileSystemProvider.ReadWrite
val path = Paths.get("/path/to/trace.log")

// Creating an agent
val agent = AIAgent(...) {
    install(Tracing) {
        // Configure message processors to handle trace events
        addMessageProcessor(TraceFeatureMessageLogWriter(logger))
        addMessageProcessor(TraceFeatureMessageFileWriter(outputPath, fileSystem::sink))

        // Optionally filter messages
        messageFilter = { message -> 
            // Only trace LLM calls and tool calls
            message is LLMCallStartEvent || message is ToolCallEvent 
        }
    }
}
```

### Message filtering

You can process all existing events or select some of them based on specific criteria.
The message filter lets you control which events are processed. This is useful for focusing on specific aspects of
agent runs:

```kotlin
// Filter for LLM-related events only
messageFilter = { message ->
    message is LLMCallStartEvent ||
            message is LLMCallEndEvent ||
            message is LLMCallWithToolsStartEvent ||
            message is LLMCallWithToolsEndEvent
}

// Filter for tool-related events only
messageFilter = { message ->
    message is ToolCallsEvent ||
            message is ToolCallResultEvent ||
            message is ToolValidationErrorEvent ||
            message is ToolCallFailureEvent
}

// Filter for node execution events only
messageFilter = { message ->
    message is AIAgentNodeExecutionStartEvent || message is AIAgentNodeExecutionEndEvent
}
```

### Large trace volumes

For agents with complex strategies or long-running executions, the volume of trace events can be substantial. Consider using the following methods to manage the volume of events:

- Use specific message filters to reduce the number of events.
- Implement custom message processors with buffering or sampling.
- Use file rotation for log files to prevent them from growing too large.

### Dependency graph

The Tracing feature has the following dependencies:

```
Tracing
├── AIAgentPipeline (for intercepting events)
├── TraceFeatureConfig
│   └── FeatureConfig
├── Message Processors
│   ├── TraceFeatureMessageLogWriter
│   │   └── FeatureMessageLogWriter
│   ├── TraceFeatureMessageFileWriter
│   │   └── FeatureMessageFileWriter
│   └── TraceFeatureMessageRemoteWriter
│       └── FeatureMessageRemoteWriter
└── Event Types (from ai.koog.agents.core.feature.model)
    ├── AIAgentStartedEvent
    ├── AIAgentFinishedEvent
    ├── AIAgentRunErrorEvent
    ├── AIAgentStrategyStartEvent
    ├── AIAgentStrategyFinishedEvent
    ├── AIAgentNodeExecutionStartEvent
    ├── AIAgentNodeExecutionEndEvent
    ├── LLMCallStartEvent
    ├── LLMCallWithToolsStartEvent
    ├── LLMCallEndEvent
    ├── LLMCallWithToolsEndEvent
    ├── ToolCallEvent
    ├── ToolValidationErrorEvent
    ├── ToolCallFailureEvent
    └── ToolCallResultEvent
```

## Examples and quickstarts

### Basic tracing to logger

```kotlin
// Create a logger
val logger = LoggerFactory.create("my.agent.trace")

// Create an agent with tracing
val agent = AIAgent(...) {
    install(Tracing) {
        addMessageProcessor(TraceFeatureMessageLogWriter(logger))
    }
}

// Run the agent
agent.run("Hello, agent!")
```

## Error handling and edge cases

### No message processors

If no message processors are added to the Tracing feature, a warning will be logged:

```
Tracing Feature. No feature out stream providers are defined. Trace streaming has no target.
```

The feature will still intercept events, but they will not be processed or output anywhere.

### Resource management

Message processors may hold resources (like file handles) that need to be properly released. Use the `use` extension
function to ensure proper cleanup:

```kotlin
TraceFeatureMessageFileWriter(fs, path).use { writer ->
    // Use the writer
    install(Tracing) {
        addMessageProcessor(writer)
    }

    // Run the agent
    agent.run(input)

    // Writer will be automatically closed when the block exits
}
```

### Tracing specific events to file

```kotlin
// Create a file writer
val fs = JVMFileSystemProvider.ReadWrite
val path = Paths.get("/path/to/llm-calls.log")
val writer = TraceFeatureMessageFileWriter(fs, path)

// Create an agent with filtered tracing
val agent = AIAgent(...) {
    install(Tracing) {
        // Only trace LLM calls
        messageFilter = { message ->
            message is LLMCallWithToolsStartEvent || message is LLMCallWithToolsEndEvent
        }
        addMessageProcessor(writer)
    }
}

// Run the agent
agent.run("Generate a story about a robot.")
```

### Tracing specific events to remote endpoint

```kotlin
// Create a file writer
val port = 8080
val serverConfig = ServerConnectionConfig(port = port)
val writer = TraceFeatureMessageRemoteWriter(connectionConfig = serverConfig)

// Create an agent with filtered tracing
val agent = AIAgent(...) {
    install(Tracing) {
        // Only trace LLM calls
        messageFilter = { message ->
            message is LLMCallWithToolsStartEvent || message is LLMCallWithToolsEndEvent
        }
        addMessageProcessor(writer)
    }
}

// Run the agent
agent.run("Generate a story about a robot.")
```

## API documentation

The Tracing feature follows a modular architecture with these key components:

1. [Tracing](https://api.koog.ai/agents/agents-features/agents-features-trace/ai.koog.agents.local.features.tracing.feature/-tracing/index.html): the main feature class that intercepts events in the agent pipeline.
2. [TraceFeatureConfig](https://api.koog.ai/agents/agents-features/agents-features-trace/ai.koog.agents.local.features.tracing.feature/-trace-feature-config/index.html): configuration class for customizing feature behavior.
3. Message Processors: components that process and output trace events:
    - [TraceFeatureMessageLogWriter](https://api.koog.ai/agents/agents-features/agents-features-trace/ai.koog.agents.local.features.tracing.writer/-trace-feature-message-log-writer/index.html): writes trace events to a logger.
    - [TraceFeatureMessageFileWriter](https://api.koog.ai/agents/agents-features/agents-features-trace/ai.koog.agents.local.features.tracing.writer/-trace-feature-message-file-writer/index.html): writes trace events to a file.
    - [TraceFeatureMessageRemoteWriter](https://api.koog.ai/agents/agents-features/agents-features-trace/ai.koog.agents.local.features.tracing.writer/-trace-feature-message-remote-writer/index.html): sends trace events to a remote server.

## FAQ and troubleshooting

The following section includes commonly asked questions and answers related to the Tracing feature. 

### How do I trace only specific parts of my agent's execution?

Use the `messageFilter` property to filter events. For example, to trace only node execution:

```kotlin
install(Tracing) {
    messageFilter = { message ->
        message is AIAgentNodeExecutionStartEvent || message is AIAgentNodeExecutionEndEvent
    }
    addMessageProcessor(writer)
}
```

### Can I use multiple message processors?

Yes, you can add multiple message processors to trace to different destinations simultaneously:

```kotlin
install(Tracing) {
    addMessageProcessor(TraceFeatureMessageLogWriter(logger))
    addMessageProcessor(TraceFeatureMessageFileWriter(fs, path))
    addMessageProcessor(TraceFeatureMessageRemoteWriter(connectionConfig))
}
```

### How can I create a custom message processor?

Implement the `FeatureMessageProcessor` interface:

```kotlin
class CustomTraceProcessor : FeatureMessageProcessor {
    override suspend fun onMessage(message: FeatureMessage) {
        // Custom processing logic
        when (message) {
            is AIAgentNodeExecutionStartEvent -> {
                // Process node start event
            }
            is LLMCallWithToolsEndEvent -> {
                // Process LLM call end event
            }
            // Handle other event types
        }
    }
}

// Use your custom processor
install(Tracing) {
    addMessageProcessor(CustomTraceProcessor())
}
```
