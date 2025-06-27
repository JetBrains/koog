# Predefined nodes and components

Nodes are the fundamental building blocks of agent workflows in the Koog framework.
Each node represents a specific operation or transformation in the workflow, and they can be connected using edges to define the flow of execution.

In general, they let you encapsulate complex logic into reusable components that can be easily integrated into
different agent workflows. This guide will walk you through the existing nodes that can be used in your agent
strategies.

For more detailed reference documentation, see [API reference](https://api.koog.ai/index.html).

## Utility nodes

### nodeDoNothing

A simple pass-through node that does nothing and returns the input as output. For details, see [API reference](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-do-nothing.html).

You can use this node for the following purposes:

- Create a placeholder node in your graph.
- Create a connection point without modifying the data.

Here is an example:

```kotlin
val passthrough by nodeDoNothing<String>("passthrough")

edge(someNode forwardTo passthrough)
edge(passthrough forwardTo anotherNode)
```

## LLM nodes

### nodeUpdatePrompt

A node that adds messages to the LLM prompt using the provided prompt builder.
This is useful for modifying the conversation context before making an actual LLM request. For details, see [API reference](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-update-prompt.html).

You can use this node for the following purposes:

- Add system instructions to the prompt.
- Insert user messages into the conversation.
- Prepare the context for subsequent LLM requests.

Here is an example:

```kotlin
val setupContext by nodeUpdatePrompt("setupContext") {
    system("You are a helpful assistant specialized in Kotlin programming.")
    user("I need help with Kotlin coroutines.")
}
```

### nodeLLMSendMessageOnlyCallingTools

A node that appends a user message to the LLM prompt and gets a response where the LLM can only call tools. For details, see [API reference](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-l-l-m-send-message-only-calling-tools.html).

### nodeLLMSendMessageForceOneTool

A node that that appends a user message to the LLM prompt and forces the LLM to use a specific tool. For details, see [API reference](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-l-l-m-send-message-force-one-tool.html).

### nodeLLMRequest

A node that appends a user message to the LLM prompt and gets a response with optional tool usage. The node configuration determines whether
tool calls are allowed during the processing of the message. For details, see [API reference](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-l-l-m-request.html).

You can use this node for the following purposes:

- Generate LLM response for the current prompt, controlling if the LLM is allowed to generate tool calls.

Here is an example:

```kotlin
val processQuery by nodeLLMRequest("processQuery", allowToolCalls = true)
edge(someNode forwardTo processQuery)
```

### nodeLLMRequestStructured

A node that appends a user message to the LLM prompt and requests structured data from the LLM with error correction capabilities. For details, see [API reference](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-l-l-m-request-structured.html).

### nodeLLMRequestStreaming

A node that appends a user message to the LLM prompt and streams LLM response with or without stream data transformation. For details, see [API reference](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-l-l-m-request-streaming.html).

### nodeLLMRequestMultiple

A node that appends a user message to the LLM prompt and gets multiple LLM responses with tool calls enabled. For details, see [API reference](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-l-l-m-request-multiple.html).

You can use this node for the following purposes:

- Handle complex queries that require multiple tool calls.
- Generate multiple tool calls.
- Implement a workflow that requires multiple parallel actions.

Here is an example:

```kotlin
val processComplexQuery by nodeLLMRequestMultiple("processComplexQuery")
edge(someNode forwardTo processComplexQuery)
```

### nodeLLMCompressHistory

A node that compresses the current LLM prompt (message history) into a summary, replacing messages with a concise summary (TL;DR). For details, see [API reference](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-l-l-m-compress-history.html).
This is useful for managing long conversations by compressing the history to reduce token usage.

To learn more about history compression, see [History compression](history-compression.md).

You can use this node for the following purposes:

- Manage long conversations to reduce token usage.
- Summarize conversation history to maintain context.
- Implement memory management in long-running agents.

Here is an example:

```kotlin
val compressHistory by nodeLLMCompressHistory<String>(
    "compressHistory",
    strategy = HistoryCompressionStrategy.FromLastNMessages(10),
    preserveMemory = true
)
edge(someNode forwardTo compressHistory)
```

## Tool nodes

### nodeExecuteTool

A node that executes a single tool call and returns its result. This node is used to handle tool calls made by the LLM. For details, see [API reference](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-execute-tool.html).

You can use this node for the following purposes:

- Execute tools requested by the LLM.
- Handle specific actions in response to LLM decisions.
- Integrate external functionality into the agent workflow.

Here is an example:

```kotlin
val executeToolCall by nodeExecuteTool("executeToolCall")
edge(llmNode forwardTo executeToolCall onToolCall { true })
```

### nodeLLMSendToolResult

A node that adds a tool result to the prompt and requests an LLM response. For details, see [API reference](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-l-l-m-send-tool-result.html).

You can use this node for the following purposes:

- Process the results of tool executions.
- Generate responses based on tool outputs.
- Continue a conversation after tool execution.

Here is an example:

```kotlin
val processToolResult by nodeLLMSendToolResult("processToolResult")
edge(executeToolCall forwardTo processToolResult)
```

### nodeExecuteMultipleTools

A node that executes multiple tool calls. These calls can optionally be executed in parallel. For details, see [API reference](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-execute-multiple-tools.html).

You can use this node for the following purposes:

- Execute multiple tools in parallel.
- Handle complex workflows that require multiple tool executions.
- Optimize performance by batching tool calls.

Here is an example:

```kotlin
val executeMultipleTools by nodeExecuteMultipleTools("executeMultipleTools")
edge(llmNode forwardTo executeMultipleTools)
```

### nodeLLMSendMultipleToolResults

A node that adds multiple tool results to the prompt and gets multiple LLM responses. For details, see [API reference](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-l-l-m-send-multiple-tool-results.html).

You can use this node for the following purposes:

- Process the results of multiple tool executions.
- Generate multiple tool calls.
- Implement complex workflows with multiple parallel actions.

Here is an example:

```kotlin
val processMultipleToolResults by nodeLLMSendMultipleToolResults("processMultipleToolResults")
edge(executeMultipleTools forwardTo processMultipleToolResults)
```

## Predefined subgraphs

The framework provides predefined subgraphs that encapsulate commonly used patterns and workflows. These subgraphs simplify the development of complex agent strategies by handling the creation of base nodes and edges automatically.

By using the predefined subgraphs, you can implement various popular pipelines. Here is an example:

1. Prepare the data.
2. Run the task.
3. Validate the task results. If the results are incorrect, return to step 2 with a feedback message to make adjustments.

### subgraphWithTask

A subgraph that performs a specific task using provided tools and returns a structured result. This subgraph is designed to handle self-contained tasks within a larger workflow. For details, see [API reference](https://api.koog.ai/agents/agents-ext/ai.koog.agents.ext.agent/subgraph-with-task.html).

You can use this subgraph for the following purposes:

- Create special components that handle specific tasks within a larger workflow.
- Encapsulate complex logic with clear input and output interfaces.
- Configure task-specific tools, models, and prompts.
- Manage conversation history with automatic compression.
- Develop structured agent workflows and task execution pipelines.
- Generate structured results from LLM task execution.

You can provide a task to the subgraph as text, configure the LLM if needed, and provide the necessary tools, and the subgraph will process and solve the task. Here is an example:

```kotlin
val processQuery by subgraphWithTask<String>(
    tools = listOf(searchTool, calculatorTool, weatherTool),
    model = OpenAIModels.Chat.GPT4o,
    shouldTLDRHistory = true
) { userQuery ->
    """
    You are a helpful assistant that can answer questions about various topics.
    Please help with the following query:
    $userQuery
    """
}
```

### subgraphWithVerification

A special version of `subgraphWithTask` that verifies whether a task was performed correctly and provides details about any issues encountered. This subgraph is useful for workflows that require validation or quality checks. For details, see [API reference](https://api.koog.ai/agents/agents-ext/ai.koog.agents.ext.agent/subgraph-with-verification.html).

You can use this subgraph for the following purposes:

- Verify the correctness of task execution.
- Implement quality control processes in your workflows.
- Create self-validating components.
- Generate structured verification results with success/failure status and detailed feedback.

The subgraph ensures that the LLM calls a verification tool at the end of the workflow to check whether the task was successfully completed. It guarantees this verification is performed as the final step and returns a `VerifiedSubgraphResult` that indicates whether a task was completed successfully and provides detailed feedback. 
Here is an example:

```kotlin
val verifyCode by subgraphWithVerification(
    tools = listOf(runTestsTool, analyzeTool, readFileTool),
    model = AnthropicModels.Sonnet_3_7
) { codeToVerify ->
    """
    You are a code reviewer. Please verify that the following code meets all requirements:
    1. It compiles without errors
    2. All tests pass
    3. It follows the project's coding standards

    Code to verify:
    $codeToVerify
    """
}
```

## Predefined strategies and common strategy patterns

The framework provides predefined strategies that combine various nodes.
The nodes are connected using edges to define the flow of operations, with conditions that specify when to follow each edge.

You can integrate these strategies into your agent workflows if needed.

### Single run strategy

A single run strategy is designed for non-interactive use cases where the agent processes input once and
returns a result.

You can use this strategy when you need to run straightforward processes that do not require complex logic.

```kotlin
public fun singleRunStrategy(): AIAgentStrategy = strategy("single_run") {
    val nodeCallLLM by nodeLLMRequest("sendInput")
    val nodeExecuteTool by nodeExecuteTool("nodeExecuteTool")
    val nodeSendToolResult by nodeLLMSendToolResult("nodeSendToolResult")

    edge(nodeStart forwardTo nodeCallLLM)
    edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })
    edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeExecuteTool forwardTo nodeSendToolResult)
    edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
}
```

### Tool-based strategy

A tool-based strategy is designed for workflows that heavily rely on tools to perform specific operations.
It typically executes tools based on the LLM decisions and processes the results.

```kotlin
fun toolBasedStrategy(name: String, toolRegistry: ToolRegistry): AIAgentStrategy {
    return strategy(name) {
        val nodeSendInput by nodeLLMRequest()
        val nodeExecuteTool by nodeExecuteTool()
        val nodeSendToolResult by nodeLLMSendToolResult()

        // Define the flow of the agent
        edge(nodeStart forwardTo nodeSendInput)

        // If the LLM responds with a message, finish
        edge(
            (nodeSendInput forwardTo nodeFinish)
                    onAssistantMessage { true }
        )

        // If the LLM calls a tool, execute it
        edge(
            (nodeSendInput forwardTo nodeExecuteTool)
                    onToolCall { true }
        )

        // Send the tool result back to the LLM
        edge(nodeExecuteTool forwardTo nodeSendToolResult)

        // If the LLM calls another tool, execute it
        edge(
            (nodeSendToolResult forwardTo nodeExecuteTool)
                    onToolCall { true }
        )

        // If the LLM responds with a message, finish
        edge(
            (nodeSendToolResult forwardTo nodeFinish)
                    onAssistantMessage { true }
        )
    }
}
```

### Streaming data strategy

A streaming data strategy is designed for processing streaming data from the LLM. It typically requests
streaming data, processes it, and potentially calls tools with the processed data.


```kotlin
fun streamingDataStrategy(): AIAgentStrategy = strategy("streaming-data") {
    val processStreamingData by node<Unit, String> { _ ->
        val books = mutableListOf<Book>()
        val mdDefinition = markdownBookDefinition()

        llm.writeSession {
            val markdownStream = requestLLMStreaming(mdDefinition)
            parseMarkdownStreamToBooks(markdownStream).collect { book ->
                books.add(book)
                println("Parsed Book: ${book.bookName} by ${book.author}")
            }
        }

        formatOutput(books)
    }

    edge(nodeStart forwardTo processStreamingData)
    edge(processStreamingData forwardTo nodeFinish)
}
```


