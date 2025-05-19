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

The `FeatureMessageRemoteWriter` takes an optional `ServerConnectionConfig` parameter that specifies the host and port for the remote server. If not provided, it uses a default configuration with port 8080.

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

2. **Context Stage Feature Interception**: Customize how features are provided to stage contexts
   ```kotlin
   pipeline.interceptContextStageFeature(MyFeature) { stageContext ->
       // Inspect stage context and return a feature instance
       MyFeature(customizedForStage = stageContext.stageName)
   }
   ```

3. **Before Agent Started Interception**: Modify or enhance the agent during creation
   ```kotlin
   pipeline.interceptBeforeAgentStarted(this, feature) {
       readStages { stages ->
           // Inspect agent stages
       }
   }
   ```

4. **Strategy Started Interception**: Execute code when a strategy starts
   ```kotlin
   pipeline.interceptStrategyStarted(this, feature) {
       readStages { stages ->
           // Inspect agent stages when strategy starts
       }
   }
   ```

5. **Before Node Execution**: Execute code before a node runs
   ```kotlin
   pipeline.interceptBeforeNode(this, feature) { node, context, input ->
       logger.info("Node ${node.name} is about to execute with input: $input")
   }
   ```

6. **After Node Execution**: Execute code after a node completes
   ```kotlin
   pipeline.interceptAfterNode(this, feature) { node, context, input, output ->
       logger.info("Node ${node.name} executed with input: $input and produced output: $output")
   }
   ```

7. **Before LLM Call**: Execute code before a call to the language model
   ```kotlin
   pipeline.interceptBeforeLLMCall(this, feature) { prompt ->
       logger.info("About to make LLM call with prompt: ${prompt.messages.last().content}")
   }
   ```

8. **Before LLM Call With Tools**: Execute code before a call to the language model with tools
   ```kotlin
   pipeline.interceptBeforeLLMCallWithTools(this, feature) { prompt, tools ->
       logger.info("About to make LLM call with ${tools.size} tools")
   }
   ```

9. **After LLM Call**: Execute code after a call to the language model
   ```kotlin
   pipeline.interceptAfterLLMCall(this, feature) { response ->
       logger.info("Received LLM response: $response")
   }
   ```

10. **After LLM Call With Tools**: Execute code after a call to the language model with tools
    ```kotlin
    pipeline.interceptAfterLLMCallWithTools(this, feature) { response ->
        logger.info("Received structured LLM response with role: ${response.role}")
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

            // Intercept agent started
            pipeline.interceptBeforeAgentStarted(this, logging) {
                readStages { stages ->
                    stages.forEach { stage ->
                        feature.logger.info("Stage ${stage.name} has ${stage.start.edges.size} edges")
                    }
                }
            }

            // Intercept strategy started
            pipeline.interceptStrategyStarted(this, logging) {
                readStages { stages ->
                    stages.forEach { stage ->
                        feature.logger.info("Strategy started with stage ${stage.name}")
                    }
                }
            }

            // Intercept before node execution
            pipeline.interceptBeforeNode(this, logging) { node, context, input ->
                logger.info("Node ${node.name} received input: $input")
            }

            // Intercept after node execution
            pipeline.interceptAfterNode(this, logging) { node, context, input, output ->
                logger.info("Node ${node.name} with input: $input produced output: $output")
            }

            // Intercept LLM calls
            pipeline.interceptBeforeLLMCall(this, logging) { prompt ->
                logger.info("Making LLM call with prompt: ${prompt.messages.lastOrNull()?.content?.take(100)}...")
            }

            pipeline.interceptAfterLLMCall(this, logging) { response ->
                logger.info("Received LLM response: ${response.take(100)}...")
            }

            // Intercept LLM calls with tools
            pipeline.interceptBeforeLLMCallWithTools(this, logging) { prompt, tools ->
                logger.info("Making LLM call with ${tools.size} tools")
                tools.forEach { tool ->
                    logger.info("Tool available: ${tool.name}")
                }
            }

            pipeline.interceptAfterLLMCallWithTools(this, logging) { response ->
                logger.info("Received structured LLM response with role: ${response.role}")
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
