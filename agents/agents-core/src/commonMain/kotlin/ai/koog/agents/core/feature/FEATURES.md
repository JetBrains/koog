# AIAgent Features

This document describes how to use and implement custom features for AIAgent.

## Table of Contents

- [Introduction](#introduction)
- [Installing Features](#installing-features)
    - [Using FeatureMessageProcessor](#using-featuremessageprocessor)
- [Message Processors](#message-processors)
    - [Using FeatureMessageLogWriter](#using-featuremessagelogwriter)
    - [Using FeatureMessageFileWriter](#using-featuremessagefilewriter)
    - [Using FeatureMessageRemoteWriter](#using-featuremessageremotewriter)
- [Configuring Features](#configuring-features)
- [Implementing Custom Features](#implementing-custom-features)
    - [Basic Feature Structure](#basic-feature-structure)
    - [Pipeline Interceptors](#pipeline-interceptors)
    - [Advanced Interceptors](#advanced-interceptors)
- [Available Features](#available-features)
    - [AgentMemory](#agentmemory)

## Introduction

AIAgent features provide a way to extend and enhance the functionality of AI agents. Features can:

- Add new capabilities to agents
- Intercept and modify agent behavior
- Provide access to external systems and resources
- Log and monitor agent execution

Features are designed to be modular and composable, allowing you to mix and match them according to your needs.

## Installing Features

Features are installed when creating a AIAgent instance using the `install` method in the agent constructor:

```kotlin
val agent = AIAgent(
    localEngine = localEngine,
    toolRegistry = toolRegistry,
    strategy = strategy,
    agentConfig = agentConfig
) {
    // Install features here
    install(MyFeature) {
        // Configure the feature
        someProperty = "value"
    }

    install(AnotherFeature) {
        // Configure another feature
        anotherProperty = 42
    }

    // Install a feature with FeatureMessageProcessor
    install(TraceFeature) {
        // Configure the feature
        someProperty = "value"
        // Add message processor
        addMessageProcessor(myFeatureMessageProcessor)
    }
}
```

## Filtering agent events with setEventFilter

In addition to per-processor message filtering, you can filter which agent events a feature will handle using FeatureConfig.setEventFilter. This filter works for any feature and is evaluated before events are passed to any FeatureMessageProcessor.

Key points:
- The predicate receives an EventHandlerContext and must return true to let the event be handled; false will skip it.
- EventHandlerContext exposes eventType and has useful subtypes you can match on (e.g., LLMEventHandlerContext, NodeEventHandlerContext, ToolEventHandlerContext, StrategyEventHandlerContext).
- If you do not set a filter, all events are allowed (default behavior).
- You can change the filter at runtime by calling setEventFilter again; the new predicate is applied to subsequent events.
- This event-level filter composes with per-processor setMessageFilter. Both must allow the item for it to be processed and emitted.

Example: allow only LLM call start/end events for a feature
```kotlin
install(TraceFeature) {
    setEventFilter { context ->
        context.eventType is AgentEventType.BeforeLLMCall ||
            context.eventType is AgentEventType.AfterLLMCall
    }
}
```

Equivalent using context type checks
```kotlin

install(TraceFeature) {
    setEventFilter { context -> context is LLMEventHandlerContext }
}
```

Example: filter node-related events by node name
```kotlin

install(MyFeature) {
    setEventFilter { context ->
        when (context) {
            is NodeBeforeExecuteContext -> context.node.name == "Summarize"
            is NodeAfterExecuteContext -> context.node.name == "Summarize"
            is NodeExecutionErrorContext -> context.node.name == "Summarize"
            else -> true // allow all other event types
        }
    }
}
```

Example: combine setEventFilter with per-processor setMessageFilter
```kotlin
val logWriter = MyFeatureMessageLogWriter(targetLogger = KotlinLogger.logger("my.feature.logger")).apply {
    initialize()
    setMessageFilter { message ->
        // Only log AfterLLMCall messages
        message is AfterLLMCallEvent
    }
}

install(TraceFeature) {
    // Only allow LLM events for this feature instance
    setEventFilter { context -> 
        context.eventType is AgentEventType.BeforeLLMCall || 
            context.eventType is AgentEventType.AfterLLMCall 
    }

    // Then add a processor with its own, more granular, message filter
    addMessageProcessor(logWriter)
}
```

### Using FeatureMessageProcessor

You can provide a list of `FeatureMessageProcessor` implementations when configuring a feature. These processors can be accessed by the feature configuration. A configuration class should inherit from `FeatureConfig` class to get access to the `messageProcessor` property:
```kotlin
class MyFeatureConfig() : FeatureConfig() { }
```

To install a feature message processor, you can use the `addMessageProcessor()` method on a feature configuration step:
```kotlin
// Create a FeatureMessageProcessor implementation
val myFeatureMessageProcessor = object : FeatureMessageProcessor {
    override suspend fun processMessage(message: FeatureMessage) {
        // Handle feature messages
        println("Received message: $message")
    }
}

// Install a feature with the FeatureMessageProcessor
install(TraceFeature) {
    // Configure the feature
    addMessageProcessor(myFeatureMessageProcessor)
}
```

The `FeatureMessageProcessor` class contains methods for initialization of a concrete processor instance and properly closing it when finished.

#### Filtering messages with setMessageFilter

Every `FeatureMessageProcessor` now supports message filtering via `setMessageFilter`. By default, all messages are processed. You can supply a predicate to process only specific messages. The filter is evaluated for each incoming `FeatureMessage` before it is passed to the processor.

Example: only process LLM call start/end events
```kotlin
myFeatureMessageProcessor.setMessageFilter { message ->
    message is BeforeLLMCallEvent ||
    message is AfterLLMCallEvent
}
```

You can use the same approach with any concrete processor implementation (e.g., `FeatureMessageLogWriter`, `FeatureMessageFileWriter`, or `FeatureMessageRemoteWriter`):
```kotlin
val logWriter = MyFeatureMessageLogWriter(targetLogger = KotlinLogger.logger("my.feature.logger"))
logWriter.initialize()
logWriter.setMessageFilter { message -> 
    message is AfterLLMCallEvent && message.content.contains("keyword")
}

install(MyFeature) {
    addMessageProcessor(logWriter)
}
```

Notes:
- If you do not set a filter, all messages are processed (default behavior).
- You can change the filter at runtime by calling `setMessageFilter` again; the new predicate will be applied to subsequent messages.

### Using FeatureMessageFileWriter

For more advanced message processing, you can use `FeatureMessageFileWriter` to write feature messages to files. This abstract class provides functionality to convert and write feature messages to a target file using a specified file system provider.

```kotlin
// Create a custom implementation of FeatureMessageFileWriter
class MyFeatureMessageFileWriter<Path>(
    fs: FileSystemProvider.ReadWrite<Path>,
    root: Path,
    append: Boolean = false
) : FeatureMessageFileWriter<Path>(fs, root, append) {
    // Implement the required method to convert a FeatureMessage to a string
    override fun FeatureMessage.toFileString(): String {
        return "Custom format: ${this.messageType.value} - ${this.toString()}"
    }
}

// Create an instance and use it with a feature
val fileWriter = MyFeatureMessageFileWriter(
    sinkOpener = JVMFileSystemProvider.ReadWrite::sink,
    targetPath = Path("/path/to/logs/main.log"),
)

// Initialize the writer before use
fileWriter.initialize()

// Use the writer with a feature
install(TraceFeature) {
    // Add the file writer as a message processor
    addMessageProcessor(fileWriter)
}
```

The `FeatureMessageFileWriter` takes the following parameters:
- `fs`: The file system provider used to interact with the file system
- `root`: The directory root or file path where messages will be written
- `append`: Whether to append to an existing file or overwrite it (defaults to `false`)

If `root` is a directory, a new file will be created with a timestamp-based name. If `root` is an existing file, messages will be written to that file.

You must implement the abstract method `FeatureMessage.toFileString()` to define how feature messages are converted to strings for file output.

The writer handles thread safety, file path resolution, and proper resource management. Remember to call `initialize()` before using the writer and `close()` when you're done with it.

## Message Processors

The AIAgent features framework provides several message processor implementations that can be used to process feature messages in different ways. These processors can be added to a feature configuration using the `addMessageProcessor` method from the `FeatureConfig` class.

### Using FeatureMessageLogWriter

The `FeatureMessageLogWriter` is a message processor that logs feature messages to a provided logger instance. It's useful for debugging and monitoring feature activity.

```kotlin
// Create a custom implementation of FeatureMessageLogWriter
class MyFeatureMessageLogWriter(
    logger: KLogger
) : FeatureMessageLogWriter(logger) {
    // Implement the required method to convert a FeatureMessage to a string
    override fun FeatureMessage.toLoggerMessage(): String {
        return "Custom log format: ${this.messageType.value} - ${this.toString()}"
    }
}

// Create an instance and use it with a feature
val logWriter = MyFeatureMessageLogWriter(
    targetLogger = KotlinLogger.logger("my.feature.logger")
)

// Initialize the writer before use
logWriter.initialize()

// Use the writer with a feature
install(MyFeature) {
    // Add the log writer as a message processor
    addMessageProcessor(logWriter)
}
```

### Using FeatureMessageFileWriter

The `FeatureMessageFileWriter` is a message processor that writes feature messages to a file using a specified file system provider. It's useful for persistent logging and data collection.

```kotlin
// Create a custom implementation of FeatureMessageFileWriter
class MyFeatureMessageFileWriter<Path>(
    fs: FileSystemProvider.ReadWrite<Path>,
    root: Path,
    append: Boolean = false
) : FeatureMessageFileWriter<Path>(fs, root, append) {
    // Implement the required method to convert a FeatureMessage to a string
    override fun FeatureMessage.toFileString(): String {
        return "Custom format: ${this.messageType.value} - ${this.toString()}"
    }
}

// Create an instance and use it with a feature
val fileWriter = MyFeatureMessageFileWriter(
    sinkOpener = JVMFileSystemProvider.ReadWrite::sink,
    targetPath = Path("/path/to/logs/main.log"),
)

// Initialize the writer before use
fileWriter.initialize()

// Use the writer with a feature
install(MyFeature) {
    // Add the file writer as a message processor
    addMessageProcessor(fileWriter)
}
```

### Using FeatureMessageRemoteWriter

The `FeatureMessageRemoteWriter` is a message processor that facilitates writing feature messages to a remote server. It's useful for distributed systems and remote monitoring.

```kotlin
// Create a custom implementation of FeatureMessageRemoteWriter
class MyFeatureMessageRemoteWriter(
    connectionConfig: ServerConnectionConfig? = null
) : FeatureMessageRemoteWriter(connectionConfig) {
    // You can override methods to customize behavior if needed
}

// Create an instance with custom server configuration
val remoteWriter = MyFeatureMessageRemoteWriter(
    connectionConfig = ServerConnectionConfig(
        host = "localhost",
        port = 9090
    )
)

// Initialize the writer before use
remoteWriter.initialize()

// Use the writer with a feature
install(MyFeature) {
    // Add the remote writer as a message processor
    addMessageProcessor(remoteWriter)
}
```

The `FeatureMessageRemoteWriter` takes an optional `ServerConnectionConfig` parameter that specifies the host and port for the remote server. If not provided, it uses a default configuration with port 50881.

## Configuring Features

Each feature has its own configuration options that can be set in the installation block. The configuration options are defined by the feature's `Config` class.

```kotlin
install(MyFeature) {
    // 'this' is the Config instance
    someProperty = "value"
    anotherProperty = 42

    // You can also use conditional configuration
    if (someCondition) {
        optionalProperty = "optional value"
    }
}
```

## Implementing Custom Features

### Basic Feature Structure

To implement a custom feature, you need to:

1. Create a feature class
2. Define a configuration class
3. Create a companion object that implements `AIAgentFeature`
4. Implement the required methods

Here's a basic example:

```kotlin
class MyFeature(val someProperty: String) {
    // Configuration class
    class Config {
        var someProperty: String = "default"
    }

    // Feature definition
    companion object Feature : AIAgentFeature<Config, MyFeature> {
        // Unique key for the feature
        override val key = createStorageKey<MyFeature>("my-feature")

        // Create default configuration
        override fun createInitialConfig(): Config = Config()

        // Install the feature
        override fun install(config: Config, pipeline: AIAgentPipeline) {
            // Create feature instance
            val feature = MyFeature(config.someProperty)

            // Make the feature available in stage contexts
            pipeline.installToStageContext(this) { context ->
                feature
            }
        }
    }
}
```

### Pipeline Interceptors

Features can intercept various points in the agent execution pipeline:

1. **Stage Context Installation**: Make the feature available in stage contexts
   ```kotlin
   pipeline.installToStageContext(this) { context ->
       MyFeature(config.someProperty)
   }
   ```

2. **Context Agent Feature Interception**: Customize how features are provided to agent contexts
   ```kotlin
   pipeline.interceptContextAgentFeature(MyFeature) { agentContext ->
       // Inspect agent context and return a feature instance
       MyFeature(customizedForStage = agentContext.stageName)
   }
   ```

3. **Agent Starting Interception**: Execute code when an agent starts
   ```kotlin
   val interceptContext = InterceptContext(this, feature)
   pipeline.interceptAgentStarting(interceptContext) { event ->
       // Access agent, runId, context, or feature
       // event.agent, event.runId, event.context, event.feature
   }
   ```

4. **Strategy Starting Interception**: Execute code when a strategy starts
   ```kotlin
   pipeline.interceptStrategyStarting(interceptContext) { event ->
       // Inspect agent or context when strategy starts
   }
   ```

5. **Before Node Execution**: Execute code before a node runs
   ```kotlin
   pipeline.interceptNodeExecutionStarting(interceptContext) { event ->
       logger.info("Node ${event.node.name} is about to execute with input: ${event.input}")
   }
   ```

6. **After Node Execution**: Execute code after a node completes
   ```kotlin
   pipeline.interceptNodeExecutionCompleted(interceptContext) { event ->
       logger.info("Node ${event.node.name} executed with input: ${event.input} and produced output: ${event.output}")
   }
   ```

7. **LLM Call Starting**: Execute code before a call to the language model
   ```kotlin
   pipeline.interceptLLMCallStarting(interceptContext) { eventContext ->
       logger.info("About to make LLM call with prompt: ${eventContext.prompt.messages.last().content}")
   }
   ```

8. **LLM Call Starting (with tools)**: Tools are available via the event context
   ```kotlin
   pipeline.interceptLLMCallStarting(interceptContext) { eventContext ->
       logger.info("About to make LLM call with ${eventContext.tools.size} tools")
   }
   ```

9. **LLM Call Completed**: Execute code after a call to the language model
   ```kotlin
   pipeline.interceptLLMCallCompleted(interceptContext) { eventContext ->
       logger.info("Received LLM responses: ${eventContext.responses}")
   }
   ```

10. **LLM Call Completed (with tools)**: Access responses and tools via the event context
    ```kotlin
    pipeline.interceptLLMCallCompleted(interceptContext) { eventContext ->
        logger.info("Received ${eventContext.responses.size} responses (tools used: ${eventContext.tools.size})")
    }
    ```

### Advanced Interceptors

For more advanced use cases, you can combine multiple interceptors to create complex features. Here's an example of a logging feature:

```kotlin
class LoggingFeature(val logger: Logger) {
    class Config {
        var loggerName: String = "agent-logs"
    }

    companion object Feature: AIAgentFeature<LoggingFeature.Config, LoggingFeature> {
        override val key: AIAgentStorageKey<LoggingFeature> = createStorageKey("logging-feature")

        override fun createInitialConfig(): Config = Config()

        override fun install(
            config: Config,
            pipeline: AIAgentPipeline
        ) {
            val logging = LoggingFeature(LoggerFactory.getLogger(config.loggerName))

            val interceptContext = InterceptContext(this, logging)

            // Intercept agent starting
            pipeline.interceptAgentStarting(interceptContext) { event ->
                event.feature.logger.info("Agent starting: runId=${event.runId}")
            }

            // Intercept strategy starting
            pipeline.interceptStrategyStarting(interceptContext) { event ->
                event.feature.logger.info("Strategy starting")
            }

            // Intercept before node execution
            pipeline.interceptNodeExecutionStarting(interceptContext) { eventContext ->
                logger.info("Node ${eventContext.node.name} received input: ${eventContext.input}")
            }

            // Intercept after node execution
            pipeline.interceptNodeExecutionCompleted(interceptContext) { eventContext ->
                logger.info("Node ${eventContext.node.name} with input: ${eventContext.input} produced output: ${eventContext.output}")
            }

            // Intercept LLM calls
            pipeline.interceptLLMCallStarting(interceptContext) { eventContext ->
                logger.info("Making LLM call with prompt: ${eventContext.prompt.messages.lastOrNull()?.content?.take(100)}...")
            }

            pipeline.interceptLLMCallCompleted(interceptContext) { eventContext ->
                logger.info("Received LLM responses: ${eventContext.responses}")
            }

            // Intercept LLM calls with tools (available via eventContext.tools)
            pipeline.interceptLLMCallStarting(interceptContext) { eventContext ->
                logger.info("Making LLM call with ${eventContext.tools.size} tools")
                eventContext.tools.forEach { tool ->
                    logger.info("Tool available: ${tool.name}")
                }
            }

            pipeline.interceptLLMCallCompleted(interceptContext) { eventContext ->
                logger.info("Received ${eventContext.responses.size} response(s)")
            }
        }
    }
}
```

## Available Features

### AgentMemory

The AgentMemory provides persistent memory capabilities for agents. It allows agents to store and retrieve information across runs.

> **Note**: AgentMemory is in a separate module and requires a separate dependency. It's defined in the `agents-features/agents-features-memory` module.

Installation:

```kotlin
install(AgentMemory) {
    memoryProvider = LocalFileMemoryProvider(
        config = LocalMemoryConfig("my-agent-memory"),
        storage = EncryptedStorage(
            fs = JVMFileSystemProvider.ReadWrite,
            encryption = Aes256GCMEncryptor(secretKey)
        ),
        fs = JVMFileSystemProvider.ReadWrite,
        root = Path("path/to/memory/root")
    )

    featureName = "my-feature"
    productName = "my-product"
    organizationName = "my-organization"
}
```

Usage:

```kotlin
// In a node implementation
context.withMemory {
    // Save facts to memory
    saveFactsFromHistory(
        concept = myConcept,
        subject = MemorySubject.Project,
        scope = MemoryScopeType.PRODUCT
    )

    // Load facts from memory
    loadFactsToAgent(
        concept = myConcept,
        scopes = listOf(MemoryScopeType.PRODUCT),
        subjects = listOf(MemorySubject.Project)
    )
}
```

For more details on using AgentMemory, see the examples in the `examples` module.
