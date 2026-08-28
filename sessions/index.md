# LLM sessions and manual history management

This page provides detailed information about LLM sessions, including how to work with read and write sessions, manage conversation history, and make requests to language models.

## Introduction

LLM sessions are a fundamental concept that provides a structured way to interact with language models (LLMs). They manage the conversation history, handle requests to the LLM, and provide a consistent interface for running tools and processing responses.

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

1. **Write Sessions** (`AIAgentLLMWriteSession`): Allow modifying the prompt and tools, making LLM requests, and running tools. Changes made in a write session are persisted back to the LLM context.
1. **Read Sessions** (`AIAgentLLMReadSession`): Provide read-only access to the prompt and tools. They are useful for inspecting the current state without making changes.

The key difference is that write sessions can modify the conversation history, while read sessions cannot.

### Session lifecycle

Sessions have a defined lifecycle:

1. **Creation**: a session is created, e.g., using `llm.writeSession { ... }` or `llm.readSession { ... }`.
1. **Active phase**: the session is active while the lambda block is executing.
1. **Termination**: the session is automatically closed when the lambda block completes.

Sessions implement the `AutoCloseable` interface, ensuring they are properly cleaned up even if an exception occurs.

## Working with LLM sessions

### Creating sessions

Sessions are created using methods of the `AIAgentLLMContext` class:

```
// Creating a write session
llm.writeSession {
    // Session code here
}

// Creating a read session
llm.readSession {
    // Session code here
}
```

```
// Creating a write session
ctx.getLlm().writeSession(session -> {
    // Session code here
    return null;
});

// Creating a read session
ctx.getLlm().readSession(session -> {
    // Session code here
    return null;
});
```

These functions take a lambda block that runs within the context of the session. The session is automatically closed when the block completes.

### Session scope and thread safety

Sessions use a read-write lock to ensure thread safety:

- Multiple read sessions can be active simultaneously.
- Only one write session can be active at a time.
- A write session blocks all other sessions (both read and write).

This ensures that the conversation history is not corrupted by concurrent modifications.

### Accessing session properties

Within a session, you can access the prompt and tools:

```
llm.readSession {
    val messageCount = prompt.messages.size
    val availableTools = tools.map { it.name }
}
```

```
ctx.getLlm().readSession(session -> {
    int messageCount = session.getPrompt().getMessages().size();
    var availableTools = session.getTools().stream().map(tool -> tool.getName()).toList();
    return null;
});
```

In a write session, you can also modify these properties:

```
llm.writeSession {
    // Modify the prompt
    appendPrompt {
        user("New user message")
    }

    // Modify the tools
    tools = newTools
}
```

```
ctx.getLlm().writeSession(session -> {
    // Modify the prompt
    session.appendPrompt(promptBuilder -> {
        promptBuilder.user("New user message");
        return null;
    });

    // Modify the tools
    session.setTools(newTools);
    return null;
});
```

For more information, see the detailed API reference for [AIAgentLLMReadSession](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.session/-a-i-agent-l-l-m-read-session/index.html) and [AIAgentLLMWriteSession](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.session/-a-i-agent-l-l-m-write-session/index.html).

## Making LLM requests

### Basic request methods

The most common methods for making LLM requests are:

1. `requestLLM()`: makes a request to the LLM with the current prompt and tools, returning a response.
1. `requestLLMWithoutTools()`: makes a request to the LLM with the current prompt but without any tools, returning a single response.
1. `requestLLMForceOneTool()`: makes a request to the LLM with the current prompt and tools, forcing the use of one tool.
1. `requestLLMOnlyCallingTools()`: makes a request to the LLM that should be processed by only using tools.

Example:

```
llm.writeSession {
    // Make a request with tools enabled
    val response = requestLLM()

    // Make a request without tools
    val responseWithoutTools = requestLLMWithoutTools()
}
```

```
ctx.getLlm().writeSession(session -> {
    // Make a request with tools enabled
    var response = session.requestLLM();

    // Make a request without tools
    var responseWithoutTools = session.requestLLMWithoutTools();

    return null;
});
```

### How requests work

LLM requests are made when you explicitly call one of the request methods. The key points to understand are:

1. **Explicit invocation**: requests only happen when you call methods like `requestLLM()`, `requestLLMWithoutTools()` and so on.
1. **Immediate execution**: when you call a request method, the request is made immediately, and the method blocks until a response is received.
1. **Automatic history update**: in a write session, the response is automatically added to the conversation history.

### Request methods with tools

When making requests with tools enabled, the LLM may respond with a tool call instead of a text response. The request methods handle this transparently:

```
llm.writeSession {
    val response = requestLLM()

    // The response might contain tool calls and/or text
    val toolCalls = response.parts.filterIsInstance<MessagePart.Tool.Call>()
    if (toolCalls.isNotEmpty()) {
        // Handle tool calls
    } else {
        // Handle text response
    }
}
```

```
ctx.getLlm().writeSession(session -> {
    var response = session.requestLLM();

    // The response parts might contain a tool call or text content
    boolean hasToolCall = response.getParts().stream()
        .anyMatch(p -> p instanceof MessagePart.Tool.Call);
    if (hasToolCall) {
        // Handle tool call
    } else {
        // Handle text response
    }
    return null;
});
```

In practice, you typically do not need to check the response type manually, as the agent graph handles this routing automatically.

### Structured and streaming requests

For more advanced use cases, methods for structured and streaming requests are provided:

1. `requestLLMStructured()`: requests the LLM to provide a response in a specific structured format.
1. `requestLLMStructuredOneShot()`: similar to `requestLLMStructured()` but without retries or corrections.
1. `requestLLMStreaming()`: makes a streaming request to the LLM, returning a flow of response chunks. You can learn more about streaming on the [Streaming API](../streaming-api/) page.

Example:

```
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

```
ctx.getLlm().writeSession(session -> {
    // Make a non-tool request
    var responseWithoutTools = session.requestLLMWithoutTools();

    // Make a streaming request
    var responseStream = session.requestLLMStreaming();
    // Process chunks from Flow.Publisher<StreamFrame>
    return null;
});
```

## Managing conversation history

### Updating the prompt

In a write session, you can add messages to the prompt (conversation history) using the `appendPrompt` method:

```
llm.writeSession {
    appendPrompt {
        // Add a system message
        system("You are a helpful assistant.")

        // Add a user message
        user("Hello, can you help me with a coding question?")

        // Add an assistant message
        assistant("Of course! What's your question?")

        // Add a tool result
        toolResult(myToolResult)
    }
}
```

```
ctx.getLlm().writeSession(session -> {
    session.appendPrompt(promptBuilder -> {
        // Add a system message
        promptBuilder.system("You are a helpful assistant.");

        // Add a user message
        promptBuilder.user("Hello, can you help me with a coding question?");

        // Add an assistant message
        promptBuilder.assistant("Of course! What's your question?");

        // Add follow-up context after tool execution
        promptBuilder.assistant("Tool execution completed successfully.");
        return null;
    });
    return null;
});
```

You can also completely rewrite the prompt by assigning a new `Prompt` object to the `prompt` property:

```
llm.writeSession {
    // Create a new prompt based on the old one
    prompt = prompt.copy(messages = filteredMessages)
}
```

```
ctx.getLlm().writeSession(session -> {
    var oldPrompt = session.getPrompt();
    // Rebuild and replace the prompt (manual rewrite approach in Java)
    session.setPrompt(
        Prompt.builder(oldPrompt.getId())
            .user("Retained summary of previous conversation")
            .build()
    );
    return null;
});
```

### Automatic history update on response

When you make an LLM request in a write session, the response is automatically added to the conversation history:

```
llm.writeSession {
    // Add a user message
    appendPrompt {
        user("What's the capital of France?")
    }

    // Make a request - the response is automatically added to the history
    val response = requestLLM()

    // The prompt now includes both the user message and the model's response
}
```

```
ctx.getLlm().writeSession(session -> {
    // Add a user message
    session.appendPrompt(promptBuilder -> {
        promptBuilder.user("What's the capital of France?");
        return null;
    });

    // Make a request - the response is automatically added to the history
    var response = session.requestLLM();

    // The prompt now includes both the user message and the model's response
    return null;
});
```

This automatic history update is the key feature of write sessions, ensuring that the conversation flows naturally.

### History compression

For long-running conversations, the history can grow large and consume a lot of tokens. The platform provides methods for compressing history:

```
llm.writeSession {
    // Compress the history using a TLDR approach
    replaceHistoryWithTLDR(HistoryCompressionStrategy.WholeHistory, preserveMemory = true)
}
```

```
// Use the dedicated Java node for history compression.
var compressHistory = AIAgentNode.llmCompressHistory("compressHistory");
```

You can also use the `nodeLLMCompressHistory` node in a strategy graph to compress history at specific points.

For more information about history compression and compression strategies, see [History compression](../history-compression/).

## Running tools in sessions

### Calling tools

Write sessions provide several methods for calling tools:

1. `callTool(tool, args)`: calls a tool by reference.
1. `callTool(toolName, args)`: calls a tool by name.
1. `callTool(toolClass, args)`: calls a tool by class.
1. `callToolRaw(toolName, args)`: calls a tool by name and returns the raw string result.

Example:

```
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

```
// Java uses dedicated tool-execution nodes in graph strategies.
var executeTool = AIAgentNode.executeTools("executeTool");
var sendToolResult = AIAgentNode.llmSendToolResults("sendToolResult");
```

### Parallel tool runs

To run multiple tools in parallel, write sessions provide extension functions on `Flow`:

```
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

```
// Java equivalent for multi-tool execution: use the executeTools node.
var executeMultipleTools = AIAgentNode.executeTools("executeMultipleTools");
```

This is useful for processing large amounts of data efficiently.

## Best practices

When working with LLM sessions, follow these best practices:

1. **Use the right session type**: Use write sessions when you need to modify the conversation history and read sessions when you only need to read it.
1. **Keep sessions short**: Sessions should be focused on a specific task and closed as soon as possible to release resources.
1. **Handle exceptions**: Make sure to handle exceptions within sessions to prevent resource leaks.
1. **Manage history size**: For long-running conversations, use history compression to reduce token usage.
1. **Prefer high-Level abstractions**: When possible, use the node-based API. For example, `nodeLLMRequest` instead of directly working with sessions.
1. **Be mindful of thread safety**: Remember that write sessions block other sessions, so keep write operations as short as possible.
1. **Use structured requests for complex data**: When you need the LLM to return structured data, use `requestLLMStructured` instead of parsing free-form text.
1. **Use streaming for long responses**: For long responses, use `requestLLMStreaming` to process the response as it arrives.

## Troubleshooting

### Session already closed

If you see an error such as `Cannot use session after it was closed`, you are trying to use a session after its lambda block has completed. Make sure all session operations are performed within the session block.

### History too large

If your history becomes too large and consumes too many tokens, use history compression techniques:

```
llm.writeSession {
    replaceHistoryWithTLDR(HistoryCompressionStrategy.FromLastNMessages(10), preserveMemory = true)
}
```

```
// Compress recent history with the Java compression node.
var compressHistory = AIAgentNode.llmCompressHistory("compressHistory");
```

For more information, see [History compression](../history-compression/)

### Tool not found

If you see errors about tools not being found, check that:

- The tool is correctly registered in the tool registry.
- You are using the correct tool name or class.

## API documentation

For more information, see the full [AIAgentLLMSession](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.session/-a-i-agent-l-l-m-session/index.html) and [AIAgentLLMContext](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.context/-a-i-agent-l-l-m-context/index.html) reference.
