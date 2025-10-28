# LLM sessions and manual history management

This page provides detailed information about LLM sessions, including how to work with read
and write sessions, manage conversation history, and make requests to language models.

## Introduction

LLM sessions are a fundamental concept that provides a structured way to interact with language models (LLMs). 
They manage the conversation history, handle requests to the LLM, and provide a consistent
interface for running tools and processing responses.

## Understanding LLM sessions

An LLM session represents a context for interacting with a language model. It encapsulates:

- The conversation history (prompt)
- Available tools
- Methods for making requests to the LLM
- Methods for updating the conversation history
- Methods for running tools

Sessions are managed by the `AIAgentLLMContext` class, which provides methods for creating read and write sessions.

### Session types

The Koog framework provides two types of sessions:

1. **Write Sessions** (`AIAgentLLMWriteSession`): Allow modifying the prompt and tools, making LLM requests, and
   running tools. Changes made in a write session are persisted back to the LLM context.

2. **Read Sessions** (`AIAgentLLMReadSession`): Provide read-only access to the prompt and tools. They are useful for
   inspecting the current state without making changes.

The key difference is that write sessions can modify the conversation history, while read sessions cannot.

### Session lifecycle

Sessions have a defined lifecycle:

1. **Creation**: a session is created using `llm.writeSession { ... }` or `llm.readSession { ... }`.
2. **Active phase**: the session is active while the lambda block is executing.
3. **Termination**: the session is automatically closed when the lambda block completes.

Sessions implement the `AutoCloseable` interface, ensuring they are properly cleaned up even if an exception occurs.

## Working with LLM sessions

### Creating sessions

Sessions are created using extension functions on the `AIAgentLLMContext` class:

<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.strategy


val strategy = strategy<Unit, Unit>("strategy-name") {
    val node by node<Unit, Unit> {
-->
<!--- SUFFIX
   }
}
-->
```kotlin
// Creating a write session
llm.writeSession {
    // Session code here
}

// Creating a read session
llm.readSession {
    // Session code here
}
```
<!--- KNIT example-sessions-01.kt -->

These functions take a lambda block that runs within the context of the session. The session is automatically closed
when the block completes.

### Session scope and thread safety

Sessions use a read-write lock to ensure thread safety:

- Multiple read sessions can be active simultaneously.
- Only one write session can be active at a time.
- A write session blocks all other sessions (both read and write).

This ensures that the conversation history is not corrupted by concurrent modifications.

### Accessing session properties

Within a session, you can access the prompt and tools:

<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.strategy


val strategy = strategy<Unit, Unit>("strategy-name") {
    val node by node<Unit, Unit> {
-->
<!--- SUFFIX
   }
}
-->
```kotlin
llm.readSession {
    val messageCount = prompt.messages.size
    val availableTools = tools.map { it.name }
}
```
<!--- KNIT example-sessions-02.kt -->

In a write session, you can also modify these properties:

<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolDescriptor

val newTools = listOf<ToolDescriptor>()

val strategy = strategy<Unit, Unit>("strategy-name") {
    val node by node<Unit, Unit> {
-->
<!--- SUFFIX
   }
}
-->
```kotlin
llm.writeSession {
    // Modify the prompt
    appendPrompt {
        user("New user message")
    }

    // Modify the tools
    tools = newTools
}
```
<!--- KNIT example-sessions-03.kt -->

For more information, see the detailed API reference for [AIAgentLLMReadSession](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.session/-a-i-agent-l-l-m-read-session/index.html) and [AIAgentLLMWriteSession](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.session/-a-i-agent-l-l-m-write-session/index.html).

## Making LLM requests

### Basic request methods

The most common methods for making LLM requests are:

1. `requestLLM()`: makes a request to the LLM with the current prompt and tools, returning a single response.

2. `requestLLMMultiple()`: makes a request to the LLM with the current prompt and tools, returning multiple
   responses.

3. `requestLLMWithoutTools()`: makes a request to the LLM with the current prompt but without any tools, returning a
   single response.

4. `requestLLMForceOneTool`: makes a request to the LLM with the current prompt and tools, forcing the use of one tool.

5. `requestLLMOnlyCallingTools`: makes a request to the LLM that should be processed by only using tools.

Example:

<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.strategy


val strategy = strategy<Unit, Unit>("strategy-name") {
    val node by node<Unit, Unit> {
-->
<!--- SUFFIX
   }
}
-->
```kotlin
llm.writeSession {
    // Make a request with tools enabled
    val response = requestLLM()

    // Make a request without tools
    val responseWithoutTools = requestLLMWithoutTools()

    // Make a request that returns multiple responses
    val responses = requestLLMMultiple()
}
```
<!--- KNIT example-sessions-04.kt -->

### How requests work

LLM requests are made when you explicitly call one of the request methods. The key points to understand are:

1. **Explicit invocation**: requests only happen when you call methods like `requestLLM()`, `requestLLMWithoutTools()` and so on.
2. **Immediate execution**: when you call a request method, the request is made immediately, and the method blocks
until a response is received.
3. **Automatic history update**: in a write session, the response is automatically added to the conversation history.
4. **No implicit requests**: the system does not make implicit requests; you need to explicitly call a request method.

### Request methods with tools

When making requests with tools enabled, the LLM may respond with a tool call instead of a text response. The request
methods handle this transparently:

<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.prompt.message.Message

val strategy = strategy<Unit, Unit>("strategy-name") {
    val node by node<Unit, Unit> {
-->
<!--- SUFFIX
   }
}
-->
```kotlin
llm.writeSession {
    val response = requestLLM()

    // The response might be a tool call or a text response
    if (response is Message.Tool.Call) {
        // Handle tool call
    } else {
        // Handle text response
    }
}
```
<!--- KNIT example-sessions-05.kt -->

In practice, you typically do not need to check the response type manually, as the agent graph handles this routing
automatically.

### Structured and streaming requests

For more advanced use cases, the platform provides methods for structured and streaming requests:

1. `requestLLMStructured()`: requests the LLM to provide a response in a specific structured format.

2. `requestLLMStructuredOneShot()`: similar to `requestLLMStructured()` but without retries or corrections.

3. `requestLLMStreaming()`: makes a streaming request to the LLM, returning a flow of response chunks.

Example:

<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.example.exampleParallelNodeExecution07.JokeRating

val strategy = strategy<Unit, Unit>("strategy-name") {
    val node by node<Unit, Unit> {
-->
<!--- SUFFIX
   }
}
-->
```kotlin
llm.writeSession {
    // Make a structured request
    val structuredResponse = requestLLMStructured<JokeRating>()

    // Make a streaming request
    val responseStream = requestLLMStreaming()
    responseStream.collect { chunk ->
        // Process each chunk as it arrives
    }
}
```
<!--- KNIT example-sessions-06.kt -->

## Managing conversation history

### Updating the prompt

In a write session, you can add messages to the prompt (conversation history) using the `appendPrompt` method:

<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import kotlinx.datetime.Clock

val myToolResult = Message.Tool.Result(
    id = "",
    tool = "",
    content = "",
    metaInfo = RequestMetaInfo(Clock.System.now())
)

val strategy = strategy<Unit, Unit>("strategy-name") {
    val node by node<Unit, Unit> {
-->
<!--- SUFFIX
   }
}
-->
```kotlin
llm.writeSession {
    appendPrompt {
        // Add a system message
        system("You are a helpful assistant.")

        // Add a user message
        user("Hello, can you help me with a coding question?")

        // Add an assistant message
        assistant("Of course! What's your question?")

        // Add a tool result
        tool {
            result(myToolResult)
        }
    }
}
```
<!--- KNIT example-sessions-07.kt -->

You can also completely rewrite the prompt using the `rewritePrompt` method:

<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.prompt.message.Message

val filteredMessages = emptyList<Message>()

val strategy = strategy<Unit, Unit>("strategy-name") {
    val node by node<Unit, Unit> {
-->
<!--- SUFFIX
   }
}
-->
```kotlin
llm.writeSession {
    rewritePrompt { oldPrompt ->
        // Create a new prompt based on the old one
        oldPrompt.copy(messages = filteredMessages)
    }
}
```
<!--- KNIT example-sessions-08.kt -->

### Automatic history update on response

When you make an LLM request in a write session, the response is automatically added to the conversation history:

<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.strategy


val strategy = strategy<Unit, Unit>("strategy-name") {
    val node by node<Unit, Unit> {
-->
<!--- SUFFIX
   }
}
-->
```kotlin
llm.writeSession {
    // Add a user message
    appendPrompt {
        user("What's the capital of France?")
    }

    // Make a request – the response is automatically added to the history
    val response = requestLLM()

    // The prompt now includes both the user message and the model's response
}
```
<!--- KNIT example-sessions-09.kt -->

This automatic history update is the key feature of write sessions, ensuring that the conversation flows naturally.

### History compression

For long-running conversations, the history can grow large and consume a lot of tokens. The platform provides methods
for compressing history:

<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.dsl.extension.replaceHistoryWithTLDR

val strategy = strategy<Unit, Unit>("strategy-name") {
    val node by node<Unit, Unit> {
-->
<!--- SUFFIX
   }
}
-->
```kotlin
llm.writeSession {
    // Compress the history using a TLDR approach
    replaceHistoryWithTLDR(HistoryCompressionStrategy.WholeHistory, preserveMemory = true)
}
```
<!--- KNIT example-sessions-10.kt -->

You can also use the `nodeLLMCompressHistory` node in a strategy graph to compress history at specific points.

For more information about history compression and compression strategies, see [History compression](history-compression.md).

## Running tools in sessions

### Calling tools

Write sessions provide several methods for calling tools:

1. `callTool(tool, args)`: calls a tool by reference.

2. `callTool(toolName, args)`: calls a tool by name.

3. `callTool(toolClass, args)`: calls a tool by class.

4. `callToolRaw(toolName, args)`: calls a tool by name and returns the raw string result.

Example:

<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.ext.tool.AskUser

val myTool = AskUser
val myArgs = AskUser.Args("this is a string")

typealias MyTool = AskUser


val strategy = strategy<Unit, Unit>("strategy-name") {
    val node by node<Unit, Unit> {
-->
<!--- SUFFIX
   }
}
-->
```kotlin
llm.writeSession {
    // Call a tool by reference
    val result = callTool(myTool, myArgs)

    // Call a tool by name
    val result2 = callTool("myToolName", myArgs)

    // Call a tool by class
    val result3 = callTool(MyTool::class, myArgs)

    // Call a tool and get the raw result
    val rawResult = callToolRaw("myToolName", myArgs)
}
```
<!--- KNIT example-sessions-11.kt -->

### Parallel tool runs

To run multiple tools in parallel, write sessions provide extension functions on `Flow`:

<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.ext.tool.AskUser
import kotlinx.coroutines.flow.flow

typealias MyTool = AskUser

val data = ""
fun parseDataToArgs(data: String) = flow { emit(AskUser.Args(data)) }

val strategy = strategy<Unit, Unit>("strategy-name") {
    val node by node<Unit, Unit> {
-->
<!--- SUFFIX
   }
}
-->
```kotlin
llm.writeSession {
    // Run tools in parallel
    parseDataToArgs(data).toParallelToolCalls(MyTool::class).collect { result ->
        // Process each result
    }

    // Run tools in parallel and get raw results
    parseDataToArgs(data).toParallelToolCallsRaw(MyTool::class).collect { rawResult ->
        // Process each raw result
    }
}
```
<!--- KNIT example-sessions-12.kt -->

This is useful for processing large amounts of data efficiently.

## Best practices

When working with LLM sessions, follow these best practices:

1. **Use the right session type**: Use write sessions when you need to modify the conversation history and read
   sessions when you only need to read it.

2. **Keep sessions short**: Sessions should be focused on a specific task and closed as soon as possible to release
   resources.

3. **Handle exceptions**: Make sure to handle exceptions within sessions to prevent resource leaks.

4. **Manage history size**: For long-running conversations, use history compression to reduce token usage.

5. **Prefer high-Level abstractions**: When possible, use the node-based API. For example, `nodeLLMRequest` instead of directly working with sessions.

6. **Be mindful of thread safety**: Remember that write sessions block other sessions, so keep write operations as short
   as possible.

7. **Use structured requests for complex data**: When you need the LLM to return structured data, use
   `requestLLMStructured` instead of parsing free-form text.

8. **Use streaming for long responses**: For long responses, use `requestLLMStreaming` to process the response as it
   arrives.

## Troubleshooting

### Session already closed

If you see an error such as `Cannot use session after it was closed`, you are trying to use a session after its lambda 
block has completed. Make sure all session operations are performed within the session block.

### History too large

If your history becomes too large and consumes too many tokens, use history compression techniques:

<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.dsl.extension.replaceHistoryWithTLDR

val strategy = strategy<Unit, Unit>("strategy-name") {
    val node by node<Unit, Unit> {
-->
<!--- SUFFIX
   }
}
-->
```kotlin
llm.writeSession {
    replaceHistoryWithTLDR(HistoryCompressionStrategy.FromLastNMessages(10), preserveMemory = true)
}
```
<!--- KNIT example-sessions-13.kt -->

For more information, see [History compression](history-compression.md)

### Tool not found

If you see errors about tools not being found, check that:

- The tool is correctly registered in the tool registry.
- You are using the correct tool name or class.

## API documentation

For more information, see the full [AIAgentLLMSession](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.session/-a-i-agent-l-l-m-session/index.html) and [AIAgentLLMContext](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.context/-a-i-agent-l-l-m-context/index.html) reference.
