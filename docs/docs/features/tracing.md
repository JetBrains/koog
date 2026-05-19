# Tracing

This page includes details about the Tracing feature, which provides comprehensive tracing capabilities for AI agents.

## Feature overview

The Tracing feature is a powerful monitoring and debugging tool that captures detailed information about agent runs,
including:

- Strategy execution
- LLM calls
- LLM streaming (start, frames, completion, errors)
- Tool calls
- Node execution within the agent graph

This feature operates by intercepting key events in the agent pipeline and forwarding them to configurable message
processors. These processors can output the trace information to various destinations such as log files or other types
of files in the filesystem, enabling developers to gain insights into agent behavior and troubleshoot issues effectively.

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

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.core.feature.model.events.LLMCallCompletedEvent
    import ai.koog.agents.core.feature.model.events.ToolCallStartingEvent
    import ai.koog.agents.features.tracing.feature.Tracing
    import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter;
    import ai.koog.agents.features.tracing.writer.TraceFeatureMessageLogWriter
    import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
    import ai.koog.prompt.executor.ollama.client.OllamaModels
    import io.github.oshai.kotlinlogging.KotlinLogging
    import kotlinx.io.buffered
    import kotlinx.io.files.Path
    import kotlinx.io.files.SystemFileSystem
    -->
    ```kotlin
    // Defining a logger/file that will be used as a destination of trace messages 
    val logger = KotlinLogging.logger { }
    val outputPath = Path("/path/to/trace.log")
    
    // Creating an agent
    val agent = AIAgent(
        promptExecutor = simpleOllamaAIExecutor(),
        llmModel = OllamaModels.Meta.LLAMA_3_2,
    ) {
        install(Tracing) {
            // Configure message processors to handle trace events
            addMessageProcessor(TraceFeatureMessageLogWriter(logger))
            addMessageProcessor(TraceFeatureMessageFileWriter.create(outputPath))
        }
    }
    ```
    <!--- KNIT example-tracing-01.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.features.tracing.feature.Tracing;
    import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter;
    import ai.koog.agents.features.tracing.writer.TraceFeatureMessageLogWriter;
    import ai.koog.prompt.executor.model.PromptExecutor;
    import ai.koog.prompt.executor.ollama.client.OllamaModels;
    import org.slf4j.LoggerFactory;
    import java.nio.file.Path;
    public class exampleTracingJava01 {
        public static void main(String[] args) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    // Defining a logger/file that will be used as a destination of trace messages
    var logger = LoggerFactory.getLogger("tracing");
    var outputPath = Path.of("/path/to/trace.log");

    // Creating an agent
    var agent = AIAgent.builder()
        .promptExecutor(PromptExecutor.builder().ollama().build())
        .llmModel(OllamaModels.Meta.LLAMA_3_2)

        .install(Tracing.Feature, config -> {
            // Configure message processors to handle trace events
            config.addMessageProcessor(TraceFeatureMessageLogWriter.create(logger));
            config.addMessageProcessor(TraceFeatureMessageFileWriter.create(outputPath));
        })
        .build();
    ```
    <!--- KNIT exampleTracingJava01.java -->

### Message filtering

You can process all existing events or select some of them based on specific criteria.
The message filter lets you control which events are processed. This is useful for focusing on specific aspects of
agent runs:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.core.feature.model.events.*
    import ai.koog.agents.example.exampleTracing01.outputPath
    import ai.koog.agents.features.tracing.feature.Tracing
    import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter
    import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
    import ai.koog.prompt.executor.ollama.client.OllamaModels
    import kotlinx.io.buffered
    import kotlinx.io.files.Path
    import kotlinx.io.files.SystemFileSystem
    val agent = AIAgent(
        promptExecutor = simpleOllamaAIExecutor(),
        llmModel = OllamaModels.Meta.LLAMA_3_2,
    ) {
        install(Tracing) {
    -->
    <!--- SUFFIX
       }
    }
    -->
    ```kotlin
    
    val fileWriter = TraceFeatureMessageFileWriter(
        outputPath,
        { path: Path -> SystemFileSystem.sink(path).buffered() }
    )
    
    addMessageProcessor(fileWriter)
    
    // Filter for LLM-related events only
    fileWriter.setMessageFilter { message ->
        message is LLMCallStartingEvent || message is LLMCallCompletedEvent
    }
    
    // Filter for tool-related events only
    fileWriter.setMessageFilter { message -> 
        message is ToolCallStartingEvent ||
            message is ToolCallCompletedEvent ||
            message is ToolValidationFailedEvent ||
            message is ToolCallFailedEvent
    }
    
    // Filter for node execution events only
    fileWriter.setMessageFilter { message -> 
        message is NodeExecutionStartingEvent || message is NodeExecutionCompletedEvent
    }
    ```
    <!--- KNIT example-tracing-02.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.core.feature.model.events.*;
    import ai.koog.agents.features.tracing.feature.Tracing;
    import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter;
    import ai.koog.prompt.executor.model.PromptExecutor;
    import ai.koog.prompt.executor.ollama.client.OllamaModels;
    import java.io.IOException;
    import java.io.UncheckedIOException;
    import java.nio.file.Files;
    import java.nio.file.Path;
    public class exampleTracingJava02 {
        public static void main(String[] args) {
            var outputPath = Path.of("/path/to/trace.log");
            var agent = AIAgent.builder()
                .promptExecutor(PromptExecutor.builder().ollama().build())
                .llmModel(OllamaModels.Meta.LLAMA_3_2)
                .install(Tracing.Feature, config -> {
    -->
    <!--- SUFFIX
                })
                .build();
        }
    }
    -->
    ```java
    var fileWriter = TraceFeatureMessageFileWriter.create(
        outputPath,
        path -> { try { return Files.newOutputStream(path); } catch (IOException e) { throw new UncheckedIOException(e); }}
    );

    config.addMessageProcessor(fileWriter);

    // Filter for LLM-related events only
    fileWriter.setMessageFilter(message ->
        message instanceof LLMCallStartingEvent || message instanceof LLMCallCompletedEvent
    );

    // Filter for tool-related events only
    fileWriter.setMessageFilter(message ->
        message instanceof ToolCallStartingEvent ||
            message instanceof ToolCallCompletedEvent ||
            message instanceof ToolValidationFailedEvent ||
            message instanceof ToolCallFailedEvent
    );

    // Filter for node execution events only
    fileWriter.setMessageFilter(message ->
        message instanceof NodeExecutionStartingEvent || message instanceof NodeExecutionCompletedEvent
    );
    ```
    <!--- KNIT exampleTracingJava02.java -->

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
    ├── AgentStartingEvent
    ├── AgentCompletedEvent
    ├── AgentExecutionFailedEvent
    ├── AgentClosingEvent
    ├── GraphStrategyStartingEvent
    ├── FunctionalStrategyStartingEvent
    ├── StrategyCompletedEvent
    ├── NodeExecutionStartingEvent
    ├── NodeExecutionCompletedEvent
    ├── NodeExecutionFailedEvent
    ├── SubgraphExecutionStartingEvent
    ├── SubgraphExecutionCompletedEvent
    ├── SubgraphExecutionFailedEvent
    ├── LLMCallStartingEvent
    ├── LLMCallCompletedEvent
    ├── LLMCallFailedEvent
    ├── LLMStreamingStartingEvent
    ├── LLMStreamingFrameReceivedEvent
    ├── LLMStreamingFailedEvent
    ├── LLMStreamingCompletedEvent
    ├── ToolCallStartingEvent
    ├── ToolValidationFailedEvent
    ├── ToolCallFailedEvent
    └── ToolCallCompletedEvent
```
<!--- KNIT example-tracing-01.txt -->

## Examples and quickstarts

### Basic tracing to logger

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.features.tracing.feature.Tracing
    import ai.koog.agents.features.tracing.writer.TraceFeatureMessageLogWriter
    import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
    import ai.koog.prompt.executor.ollama.client.OllamaModels
    import io.github.oshai.kotlinlogging.KotlinLogging
    import kotlinx.coroutines.runBlocking
    fun main() = runBlocking {
    -->
    <!--- SUFFIX
    }
    -->
    ```kotlin
    // Create a logger
    val logger = KotlinLogging.logger { }

    // Create an agent with tracing
    val agent = AIAgent(
        promptExecutor = simpleOllamaAIExecutor(),
        llmModel = OllamaModels.Meta.LLAMA_3_2,
    ) {
        install(Tracing) {
            addMessageProcessor(TraceFeatureMessageLogWriter(logger))
        }
    }

    // Run the agent
    agent.run("Hello, agent!")
    ```
    <!--- KNIT example-tracing-03.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.features.tracing.feature.Tracing;
    import ai.koog.agents.features.tracing.writer.TraceFeatureMessageLogWriter;
    import ai.koog.prompt.executor.model.PromptExecutor;
    import ai.koog.prompt.executor.ollama.client.OllamaModels;
    import org.slf4j.LoggerFactory;
    public class exampleTracingJava03 {
        public static void main(String[] args) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    // Create a logger
    var logger = LoggerFactory.getLogger("tracing");

    // Create an agent with tracing
    var agent = AIAgent.builder()
        .promptExecutor(PromptExecutor.builder().ollama().build())
        .llmModel(OllamaModels.Meta.LLAMA_3_2)

        .install(Tracing.Feature, config -> {
            config.addMessageProcessor(TraceFeatureMessageLogWriter.create(logger));
        })
        .build();

    // Run the agent
    agent.run("Hello, agent!");
    ```
    <!--- KNIT exampleTracingJava03.java -->


## Error handling and edge cases

### No message processors

If no message processors are added to the Tracing feature, a warning will be logged:

```
Tracing Feature. No feature out stream providers are defined. Trace streaming has no target.
```
<!--- KNIT example-tracing-02.txt -->

The feature will still intercept events, but they will not be processed or output anywhere.

### Resource management

Message processors may hold resources (like file handles) that need to be properly released. Use the `use` extension
function to ensure proper cleanup:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.example.exampleTracing01.outputPath
    import ai.koog.agents.features.tracing.feature.Tracing
    import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter
    import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
    import ai.koog.prompt.executor.ollama.client.OllamaModels
    import kotlinx.coroutines.runBlocking
    import kotlinx.io.buffered
    import kotlinx.io.files.Path
    import kotlinx.io.files.SystemFileSystem
    const val input = "What's the weather like in New York?"
    fun main() {
       runBlocking {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```kotlin
    val writer = TraceFeatureMessageFileWriter(
        outputPath,
        { path: Path -> SystemFileSystem.sink(path).buffered() }
    )

    // Creating an agent
    val agent = AIAgent(
        promptExecutor = simpleOllamaAIExecutor(),
        llmModel = OllamaModels.Meta.LLAMA_3_2,
    ) {
        install(Tracing) {
            addMessageProcessor(writer)
        }
    }

    // Run the agent
    agent.run(input)

    // Writer will be automatically closed when the block exits
    ```
    <!--- KNIT example-tracing-04.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.features.tracing.feature.Tracing;
    import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter;
    import ai.koog.prompt.executor.model.PromptExecutor;
    import ai.koog.prompt.executor.ollama.client.OllamaModels;
    import java.io.IOException;
    import java.io.UncheckedIOException;
    import java.nio.file.Files;
    import java.nio.file.Path;
    public class exampleTracingJava04 {
        public static void main(String[] args) {
            var outputPath = Path.of("/path/to/trace.log");
            String input = "What's the weather like in New York?";
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    var writer = TraceFeatureMessageFileWriter.create(
        outputPath,
        path -> { try { return Files.newOutputStream(path); } catch (IOException e) { throw new UncheckedIOException(e); }}
    );

    // Creating an agent
    var agent = AIAgent.builder()
        .promptExecutor(PromptExecutor.builder().ollama().build())
        .llmModel(OllamaModels.Meta.LLAMA_3_2)

        .install(Tracing.Feature, config -> {
            config.addMessageProcessor(writer);
        })
        .build();

    // Run the agent
    agent.run(input);

    // Writer will be automatically closed when the block exits
    ```
    <!--- KNIT exampleTracingJava04.java -->

### Tracing specific events to file


=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.core.feature.model.events.LLMCallCompletedEvent
    import ai.koog.agents.core.feature.model.events.LLMCallStartingEvent
    import ai.koog.agents.example.exampleTracing01.outputPath
    import ai.koog.agents.features.tracing.feature.Tracing
    import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter
    import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
    import ai.koog.prompt.executor.ollama.client.OllamaModels
    import kotlinx.coroutines.runBlocking
    import kotlinx.io.buffered
    import kotlinx.io.files.Path
    import kotlinx.io.files.SystemFileSystem
    const val input = "What's the weather like in New York?"
    fun main() {
        runBlocking {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```kotlin
    val fileWriter = TraceFeatureMessageFileWriter(
        outputPath,
        { path: Path -> SystemFileSystem.sink(path).buffered() }
    )

    // Creating an agent
    val agent = AIAgent(
        promptExecutor = simpleOllamaAIExecutor(),
        llmModel = OllamaModels.Meta.LLAMA_3_2,
    ) {
        install(Tracing) {
            addMessageProcessor(fileWriter)

            // Only trace LLM calls
            fileWriter.setMessageFilter { message ->
                message is LLMCallStartingEvent || message is LLMCallCompletedEvent
            }
        }
    }
    ```
    <!--- KNIT example-tracing-05.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.core.feature.model.events.LLMCallCompletedEvent;
    import ai.koog.agents.core.feature.model.events.LLMCallStartingEvent;
    import ai.koog.agents.features.tracing.feature.Tracing;
    import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter;
    import ai.koog.prompt.executor.model.PromptExecutor;
    import ai.koog.prompt.executor.ollama.client.OllamaModels;
    import java.io.IOException;
    import java.io.UncheckedIOException;
    import java.nio.file.Files;
    import java.nio.file.Path;
    public class exampleTracingJava05 {
        public static void main(String[] args) {
            var outputPath = Path.of("/path/to/trace.log");
            String input = "What's the weather like in New York?";
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    var fileWriter = TraceFeatureMessageFileWriter.create(
        outputPath,
        path -> { try { return Files.newOutputStream(path); } catch (IOException e) { throw new UncheckedIOException(e); }}
    );

    // Creating an agent
    var agent = AIAgent.builder()
        .promptExecutor(PromptExecutor.builder().ollama().build())
        .llmModel(OllamaModels.Meta.LLAMA_3_2)

        .install(Tracing.Feature, config -> {
            config.addMessageProcessor(fileWriter);

            // Only trace LLM calls
            fileWriter.setMessageFilter(message ->
                message instanceof LLMCallStartingEvent || message instanceof LLMCallCompletedEvent
            );
        })
        .build();
    ```
    <!--- KNIT exampleTracingJava05.java -->

### Tracing specific events to remote endpoint

You use tracing to remote endpoints when you need to send event data via the network. Once initiated, tracing to a
remote endpoint launches a light server at the specified port number and sends events via Kotlin Server-Sent Events 
(SSE).

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.agents.core.feature.remote.server.config.DefaultServerConnectionConfig
    import ai.koog.agents.features.tracing.feature.Tracing
    import ai.koog.agents.features.tracing.writer.TraceFeatureMessageRemoteWriter
    import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
    import ai.koog.prompt.executor.ollama.client.OllamaModels
    import kotlinx.coroutines.runBlocking
    const val input = "What's the weather like in New York?"
    const val port = 4991
    const val host = "localhost"
    fun main() {
       runBlocking {
    -->
    <!--- SUFFIX
       }
    }
    -->
    ```kotlin
    val connectionConfig = DefaultServerConnectionConfig(host = host, port = port)
    val writer = TraceFeatureMessageRemoteWriter(connectionConfig)

    // Creating an agent
    val agent = AIAgent(
        promptExecutor = simpleOllamaAIExecutor(),
        llmModel = OllamaModels.Meta.LLAMA_3_2,
    ) {
        install(Tracing) {
            addMessageProcessor(writer)
        }
    }

    // Run the agent
    agent.run(input)

    // Writer will be automatically closed when the block exits
    ```
    <!--- KNIT example-tracing-06.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent;
    import ai.koog.agents.core.feature.remote.server.config.DefaultServerConnectionConfig;
    import ai.koog.agents.features.tracing.feature.Tracing;
    import ai.koog.agents.features.tracing.writer.TraceFeatureMessageRemoteWriter;
    import ai.koog.prompt.executor.model.PromptExecutor;
    import ai.koog.prompt.executor.ollama.client.OllamaModels;
    import java.io.IOException;
    import java.io.UncheckedIOException;
    public class exampleTracingJava06 {
        public static void main(String[] args) {
            String input = "What's the weather like in New York?";
            int port = 4991;
            String host = "localhost";
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    var connectionConfig = new DefaultServerConnectionConfig(host, port);
    var writer = new TraceFeatureMessageRemoteWriter(connectionConfig);

    // Creating an agent
    var agent = AIAgent.builder()
        .promptExecutor(PromptExecutor.builder().ollama().build())
        .llmModel(OllamaModels.Meta.LLAMA_3_2)

        .install(Tracing.Feature, config -> {
            config.addMessageProcessor(writer);
        })
        .build();

    // Run the agent
    agent.run(input);

    // Writer will be automatically closed when the block exits
    ```
    <!--- KNIT exampleTracingJava06.java -->

On the client side, you can use `FeatureMessageRemoteClient` to receive events and deserialize them.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.feature.model.events.AgentCompletedEvent
    import ai.koog.agents.core.feature.model.events.DefinedFeatureEvent
    import ai.koog.agents.core.feature.remote.client.config.DefaultClientConnectionConfig
    import ai.koog.agents.core.feature.remote.client.FeatureMessageRemoteClient
    import ai.koog.utils.io.use
    import io.ktor.http.*
    import kotlinx.coroutines.*
    import kotlinx.coroutines.flow.consumeAsFlow
    const val input = "What's the weather like in New York?"
    const val port = 4991
    const val host = "localhost"
    fun main() {
       runBlocking {
    -->
    <!--- SUFFIX
       }
    }
    -->
    ```kotlin
    val clientConfig = DefaultClientConnectionConfig(host = host, port = port, protocol = URLProtocol.HTTP)
    val agentEvents = mutableListOf<DefinedFeatureEvent>()
    
    val clientJob = launch {
        FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this).use { client ->
            val collectEventsJob = launch {
                client.receivedMessages.consumeAsFlow().collect { event ->
                    // Collect events from server
                    agentEvents.add(event as DefinedFeatureEvent)
    
                    // Stop collecting events on agent finished
                    if (event is AgentCompletedEvent) {
                        cancel()
                    }
                }
            }
            client.connect()
            collectEventsJob.join()
            client.healthCheck()
        }
    }
    
    listOf(clientJob).joinAll()
    ```
    <!--- KNIT example-tracing-07.kt -->

## API documentation

The Tracing feature follows a modular architecture with these key components:

1. [Tracing](api:agents-features-trace::ai.koog.agents.features.tracing.feature.Tracing): the main feature class that intercepts events in the agent pipeline.
2. [TraceFeatureConfig](api:agents-features-trace::ai.koog.agents.features.tracing.feature.TraceFeatureConfig): configuration class for customizing feature behavior.
3. Message Processors: components that process and output trace events:
    - [TraceFeatureMessageLogWriter](api:agents-features-trace::ai.koog.agents.features.tracing.writer.TraceFeatureMessageLogWriter): writes trace events to a logger.
    - [TraceFeatureMessageFileWriter](api:agents-features-trace::ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter): writes trace events to a file.
    - [TraceFeatureMessageRemoteWriter](api:agents-features-trace::ai.koog.agents.features.tracing.writer.TraceFeatureMessageRemoteWriter): sends trace events to a remote server.
