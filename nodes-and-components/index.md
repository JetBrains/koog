# Predefined nodes and components

Nodes are the fundamental building blocks of agent workflows in the Koog framework. Each node represents a specific operation or transformation in the workflow, and they can be connected using edges to define the flow of execution.

In general, nodes let you encapsulate complex logic into reusable components that can be easily integrated into different agent workflows. This guide will walk you through the existing nodes that can be used in your agent strategies.

Each node is essentially a function (Kotlin) or action (Java) that takes an input of a specific type and returns an output of a specific type.

```
graph LR
    in:::hidden
    out:::hidden

    subgraph node ["Node"]
        execute(Do stuff)
    end

    in --Input--> execute --Output--> out

    classDef hidden display: none;
```

Here is how you can define a node that expects a string as input and returns the length of the string (an integer) as output:

```
val nodeLength by node<String, Int> { input ->
    input.length
}
```

```
var nodeLength = AIAgentNode.builder("nodeLength")
    .withInput(String.class)
    .withOutput(Integer.class)
    .withAction((input, ctx) -> input.length())
    .build();
```

For more information, see [node()](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.builder/node.html) (Kotlin) or [AIAgentNode.builder()](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.entity/-a-i-agent-node/-companion/builder.html) for Java.

## Utility nodes

### Pass-through node

A simple pass-through node that does nothing and returns the input as output. For details, see [nodeDoNothing](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-do-nothing.html) (Kotlin) or [AIAgentNode.doNothing()](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.entity/-a-i-agent-node/-companion/do-nothing.html) (Java).

```
graph LR
    in:::hidden
    out:::hidden

    subgraph node ["Pass-through node"]
        execute(Do nothing)
    end

    in ---|T| execute --T--> out

    classDef hidden display: none;
```

You can use this node for the following purposes:

- Create a placeholder node in your graph.
- Create a connection point without modifying the data.

Here is an example:

```
val passthrough by nodeDoNothing<String>("passthrough")

edge(nodeStart forwardTo passthrough)
edge(passthrough forwardTo nodeFinish)
```

```
var passthrough = AIAgentNode.builder("passthrough")
    .withInput(String.class)
    .withOutput(String.class)
    .withAction((input, ctx) -> input)
    .build();

strategy.edge(strategy.nodeStart, passthrough);
strategy.edge(passthrough, strategy.nodeFinish);
```

## LLM nodes

### Prompt preparation node

**A node that adds messages to the LLM prompt using the provided prompt builder. This is useful for modifying the conversation context before making an actual LLM request.** For details, see [nodeAppendPrompt](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-append-prompt.html) (Kotlin) or [AIAgentNode.appendPrompt()](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.entity/-a-i-agent-node-builder-with-input/append-prompt.html) (Java).

```
graph LR
    in:::hidden
    out:::hidden

    subgraph node ["Prompt preparation node"]
        execute(Append prompt)
    end

    in ---|T| execute --T--> out

    classDef hidden display: none;
```

You can use this node for the following purposes:

- Add system instructions to the prompt.
- Insert user messages into the conversation.
- Prepare the context for subsequent LLM requests.

Here is an example:

```
val firstNode by node<Input, Output> {
    // Transform input to output
}

val secondNode by node<Output, Output> {
    // Transform output to output
}

// Node will get the value of type Output as input from the previous node and path through it to the next node
val setupContext by nodeAppendPrompt<Output>("setupContext") {
    system("You are a helpful assistant specialized in Kotlin programming.")
    user("I need help with Kotlin coroutines.")
}

edge(firstNode forwardTo setupContext)
edge(setupContext forwardTo secondNode)
```

```
var firstNode = AIAgentNode.builder()
    .withInput(Input.class)
    .withOutput(Output.class)
    .withAction((input, ctx) -> {
        // Transform input to output
        return input;
    })
    .build();

var secondNode = AIAgentNode.builder()
    .withInput(Output.class)
    .withOutput(Output.class)
    .withAction((output, ctx) -> {
        // Transform output to output
        return output;
    })
    .build();

var setupContext = AIAgentNode.builder()
    .withInput(Output.class)
    .appendPrompt(prompt -> {
        prompt.system("You are a helpful assistant specialized in Kotlin programming.");
        prompt.user("I need help with Kotlin coroutines.");
    });

strategy.edge(firstNode, setupContext);
strategy.edge(setupContext, secondNode);
```

### Tool-only node

A node that appends a user message to the LLM prompt and gets a response where the LLM can only call tools. For details, see [nodeLLMSendMessageOnlyCallingTools](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-l-l-m-send-message-only-calling-tools.html) (Kotlin) or [AIAgentNode.llmSendMessageOnlyCallingTools()](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.entity/-a-i-agent-node/-companion/llm-send-message-only-calling-tools.html) (Java).

```
graph LR
    in:::hidden
    out:::hidden

    subgraph node ["Tool-only node"]
        execute(Request LLM expecting only tool calls)
    end

    in --String--> execute --Message.Response--> out

    classDef hidden display: none;
```

### Forced single tool use node

A node that that appends a user message to the LLM prompt and forces the LLM to use a specific tool. For details, see [nodeLLMSendMessageForceOneTool](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-l-l-m-send-message-force-one-tool.html) (Kotlin) or [AIAgentNode.llmSendMessageForceOneTool()](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.entity/-a-i-agent-node/-companion/llm-send-message-force-one-tool.html) (Java).

```
graph LR
    in:::hidden
    out:::hidden

    subgraph node ["Forced single tool use node"]
        execute(Request LLM expecting a specific tool call)
    end

    in --String--> execute --Message.Response--> out

    classDef hidden display: none;
```

### LLM request node

A node that appends a user message to the LLM prompt and gets a response with optional tool usage. The node configuration determines whether tool calls are allowed during the processing of the message. For details, see [nodeLLMRequest](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-l-l-m-request.html) (Kotlin) or [AIAgentNode.llmRequest()](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.entity/-a-i-agent-node/-companion/llm-request.html) (Java).

```
graph LR
    in:::hidden
    out:::hidden

    subgraph node ["LLM request node"]
        execute(Request LLM)
    end

    in --String--> execute --Message.Response--> out

    classDef hidden display: none;
```

You can use this node for the following purposes:

- Generate LLM response for the current prompt, controlling if the LLM is allowed to generate tool calls.

Here is an example:

```
val requestLLM by nodeLLMRequest("requestLLM")
edge(getUserQuestion forwardTo requestLLM)
```

```
var requestLLM = AIAgentNode.llmRequest("requestLLM");

strategy.edge(AIAgentEdge.builder()
    .from(getUserQuestion)
    .to(requestLLM)
    .build());
```

### LLM request node with structured response

A node that appends a user message to the LLM prompt and requests structured data from the LLM with error correction capabilities. For details, see [nodeLLMRequestStructured](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-l-l-m-request-structured.html) (Kotlin) or [AIAgentNode.llmRequestStructured()](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.entity/-a-i-agent-node/-companion/llm-request-structured.html) (Java).

```
graph LR
    in:::hidden
    out:::hidden

    subgraph node ["LLM request node, structured response"]
        execute(Request LLM structured)
    end

    in --String--> execute -- "Result&lt;StructuredResponse&gt;" --> out

    classDef hidden display: none;
```

### LLM request node with streaming response

A node that appends a user message to the LLM prompt and streams LLM response with or without stream data transformation. For details, see [nodeLLMRequestStreaming](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-l-l-m-request-streaming.html) (Kotlin) or [AIAgentNode.llmRequestStreaming()](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.entity/-a-i-agent-node/-companion/llm-request-streaming.html) (Java).

```
graph LR
    in:::hidden
    out:::hidden

    subgraph node ["LLM request node, streaming response"]
        execute(Request LLM streaming)
    end

    in --String--> execute --Flow--> out

    classDef hidden display: none;
```

### LLM request node with multiple responses

A node that appends a user message to the LLM prompt and gets multiple LLM responses with tool calls enabled. For details, see [nodeLLMRequest](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-l-l-m-request.html) (Kotlin) or [AIAgentNode.llmRequest()](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.entity/-a-i-agent-node/-companion/llm-request.html) (Java).

```
graph LR
    in:::hidden
    out:::hidden

    subgraph node ["LLM request node, multiple responses"]
        execute(Request LLM expecting multiple responses)
    end

    in --String--> execute -- "List&lt;Message.Response&gt;" --> out

    classDef hidden display: none;
```

You can use this node for the following purposes:

- Handle complex queries that require multiple tool calls.
- Generate multiple tool calls.
- Implement a workflow that requires multiple parallel actions.

Here is an example:

```
val requestLLMMultipleTools by nodeLLMRequest()
edge(getComplexUserQuestion forwardTo requestLLMMultipleTools)
```

```
var requestLLMMultipleTools = AIAgentNode.llmRequest("requestLLMMultipleTools");

strategy.edge(AIAgentEdge.builder()
    .from(getComplexUserQuestion)
    .to(requestLLMMultipleTools)
    .build());
```

### History compression node

A node that compresses the current LLM prompt (message history) into a summary, replacing messages with a concise summary (TL;DR). This is useful for managing long conversations by compressing the history to reduce token usage. For details, see [nodeLLMCompressHistory](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-l-l-m-compress-history.html) (Kotlin) or [AIAgentNode.llmCompressHistory()](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.entity/-a-i-agent-node/-companion/llm-compress-history.html) (Java).

```
graph LR
    in:::hidden
    out:::hidden

    subgraph node ["History compression node"]
        execute(Compress current prompt)
    end

    in ---|T| execute --T--> out

    classDef hidden display: none;
```

To learn more about history compression, see [History compression](../history-compression/).

You can use this node for the following purposes:

- Manage long conversations to reduce token usage.
- Summarize conversation history to maintain context.
- Implement memory management in long-running agents.

Here is an example:

```
val compressHistory by nodeLLMCompressHistory<String>(
    "compressHistory",
    strategy = HistoryCompressionStrategy.FromLastNMessages(10),
    preserveMemory = true
)
edge(generateHugeHistory forwardTo compressHistory)
```

```
var compressHistory = AIAgentNode.llmCompressHistory("compressHistory")
    .withInput(String.class)
    .build();

strategy.edge(generateHugeHistory, compressHistory);
```

## Tool nodes

### Tool execution node

A node that executes a single tool call and returns its result. This node is used to handle tool calls made by the LLM. For details, see [nodeExecuteTool](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-execute-tool.html) (Kotlin) or [AIAgentNode.executeTool()](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.entity/-a-i-agent-node/-companion/execute-tool.html) (Java).

```
graph LR
    in:::hidden
    out:::hidden

    subgraph node ["Tool execution node"]
        execute(Execute tool call)
    end

    in --MessagePart.Tool.Call--> execute --ReceivedToolResult--> out

    classDef hidden display: none;
```

You can use this node for the following purposes:

- Execute tools requested by the LLM.
- Handle specific actions in response to LLM decisions.
- Integrate external functionality into the agent workflow.

Here is an example:

```
val requestLLM by nodeLLMRequest()
val executeTool by nodeExecuteTools()
edge(requestLLM forwardTo executeTool onToolCalls { true })
```

```
var requestLLM = AIAgentNode.llmRequest("requestLLM");
var executeTool = AIAgentNode.executeTools("executeTool");

strategy.edge(AIAgentEdge.builder()
    .from(requestLLM)
    .to(executeTool)
    .onToolCalls()
    .build());
```

### Tool result follow-up node

A node that adds a tool result to the prompt and requests an LLM response. For details, see [nodeLLMSendToolResult](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-l-l-m-send-tool-result.html) (Kotlin) or [AIAgentNode.llmSendToolResult()](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.entity/-a-i-agent-node/-companion/llm-send-tool-result.html) (Java).

```
graph LR
    in:::hidden
    out:::hidden

    subgraph node ["Tool result follow-up node"]
        execute(Request LLM)
    end

    in --ReceivedToolResult--> execute --Message.Response--> out

    classDef hidden display: none;
```

You can use this node for the following purposes:

- Process the results of tool executions.
- Generate responses based on tool outputs.
- Continue a conversation after tool execution.

Here is an example:

```
val executeTool by nodeExecuteTools()
val sendToolResultToLLM by nodeLLMSendToolResults()
edge(executeTool forwardTo sendToolResultToLLM)
```

```
var executeTool = AIAgentNode.executeTools("executeTool");
var sendToolResultToLLM = AIAgentNode.llmSendToolResults("sendToolResultToLLM");

strategy.edge(executeTool, sendToolResultToLLM);
```

### Multi-tool execution node

A node that executes multiple tool calls. These calls can optionally be executed in parallel. For details, see [nodeExecuteMultipleTools](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-execute-multiple-tools.html) (Kotlin) or [AIAgentNode.executeMultipleTools()](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.entity/-a-i-agent-node/-companion/execute-multiple-tools.html) (Java).

```
graph LR
    in:::hidden
    out:::hidden

    subgraph node ["Multi-tool execution node"]
        execute(Execute multiple tool calls)
    end

    in -- "List&lt;MessagePart.Tool.Call&gt;" --> execute -- "List&lt;ReceivedToolResult&gt;" --> out

    classDef hidden display: none;
```

You can use this node for the following purposes:

- Execute multiple tools in parallel.
- Handle complex workflows that require multiple tool executions.
- Optimize performance by batching tool calls.

Here is an example:

```
val requestLLMMultipleTools by nodeLLMRequest()
val executeMultipleTools by nodeExecuteTools(parallel = true)
edge(requestLLMMultipleTools forwardTo executeMultipleTools onToolCalls { true })
```

```
var requestLLMMultipleTools = AIAgentNode.llmRequest("requestLLMMultipleTools");
var executeMultipleTools = AIAgentNode.executeTools("executeMultipleTools");

// Route tool calls from the assistant response to the tool-execution node
strategy.edge(AIAgentEdge.builder()
    .from(requestLLMMultipleTools)
    .to(executeMultipleTools)
    .onToolCalls()
    .build());
```

### Multiple tool result follow-up node

A node that adds multiple tool results to the prompt and gets multiple LLM responses. For details, see [nodeLLMSendMultipleToolResults](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-l-l-m-send-multiple-tool-results.html) (Kotlin) or [AIAgentNode.llmSendMultipleToolResults()](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.entity/-a-i-agent-node/-companion/llm-send-multiple-tool-results.html) (Java).

```
graph LR
    in:::hidden
    out:::hidden

    subgraph node ["Multiple tool result follow-up node"]
        execute(Request LLM expecting multiple responses)
    end

    in -- "List&lt;ReceivedToolResult&gt;" --> execute -- "List&lt;Message.Response&gt;" --> out

    classDef hidden display: none;
```

You can use this node for the following purposes:

- Process the results of multiple tool executions.
- Generate multiple tool calls.
- Implement complex workflows with multiple parallel actions.

Here is an example:

```
val executeTools by nodeExecuteTools(parallel = true)
val sendToolResultsToLLM by nodeLLMSendToolResults()
edge(executeTools forwardTo sendToolResultsToLLM)
```

```
var executeTools = AIAgentNode.executeTools("executeTools");
var sendToolResultsToLLM = AIAgentNode.llmSendToolResults("sendToolResultsToLLM");

strategy.edge(executeTools, sendToolResultsToLLM);
```

## Node output transformation

The framework provides the `transform` extension function in Kotlin that allows you to create transformed versions of nodes that apply transformations to their output. In Java, you achieve the same result by creating intermediate nodes with explicit transformations. This is useful when you need to convert the output of a node to a different type or format while preserving the original node's functionality.

```
graph LR
    in:::hidden
    out:::hidden

    subgraph nodeWithTransform [transformed node]
        subgraph node ["node"]
            execute(Do stuff)
        end
        transform
    end

    in --Input--> execute --> transform --Output--> out

    classDef hidden display: none;
```

### Node transformation

In Kotlin, the [transform()](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.builder/-a-i-agent-node-delegate/transform.html) function creates a new `AIAgentNodeDelegate` that wraps the original node and applies a transformation function to its output. In Java, you need to manually compose nodes with transformation logic using `AIAgentNode.builder()` and explicit type parameters.

```
inline fun <reified T> AIAgentNodeDelegate<Input, Output>.transform(
    noinline transformation: suspend (Output) -> T
): AIAgentNodeDelegate<Input, T>
```

```
// In Java, you need to manually compose nodes
// with transformation logic using AIAgentNode.builder() and explicit type parameters.
// See the examples below for the Java approach to node transformations.
```

#### Custom node transformation

Transform the output of a custom node to a different data type:

```
val textNode by nodeDoNothing<String>("textNode").transform<Int> { text ->
    text.split(" ").filter { it.isNotBlank() }.size
}

edge(nodeStart forwardTo textNode)
edge(textNode forwardTo nodeFinish)
```

```
var textNode = AIAgentNode.builder("textNode")
    .withInput(String.class)
    .withOutput(Integer.class)
    .withAction((text, ctx) -> {
        String[] words = text.split(" ");
        int count = 0;
        for (String word : words) {
            if (!word.isBlank()) {
                count++;
            }
        }
        return count;
    })
    .build();

strategy.edge(strategy.nodeStart, textNode);
strategy.edge(textNode, strategy.nodeFinish);
```

#### Built-in node transformation

Transform the output of built-in nodes like `nodeLLMRequest` (Kotlin) or `AIAgentNode.llmRequest()` (Java):

```
val lengthNode by nodeLLMRequest("llmRequest").transform<Int> { assistantMessage ->
    assistantMessage.parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text }.length
}

edge(nodeStart forwardTo lengthNode)
edge(lengthNode forwardTo nodeFinish)
```

```
var llmRequest = AIAgentNode.llmRequest("llmRequest");
var lengthNode = AIAgentNode.builder("lengthNode")
    .withInput(Message.Assistant.class)
    .withOutput(Integer.class)
    .withAction((assistantMessage, ctx) -> {
        String text = assistantMessage.getParts().stream()
            .filter(p -> p instanceof MessagePart.Text)
            .map(p -> ((MessagePart.Text) p).getText())
            .collect(Collectors.joining());
        return text.length();
    })
    .build();

strategy.edge(AIAgentEdge.builder()
    .from(strategy.nodeStart)
    .to(llmRequest)
    .build());
strategy.edge(llmRequest, lengthNode);
strategy.edge(lengthNode, strategy.nodeFinish);
```

## Predefined subgraphs

The framework provides predefined subgraphs that encapsulate commonly used patterns and workflows. These subgraphs simplify the development of complex agent strategies by handling the creation of base nodes and edges automatically. The API is consistent between Kotlin and Java, with Kotlin using DSL functions and Java using builder methods.

By using the predefined subgraphs, you can implement various popular pipelines. Here is an example:

1. Prepare the data.
1. Run the task.
1. Validate the task results. If the results are incorrect, return to step 2 with a feedback message to make adjustments.

### Task execution subgraph

A subgraph that performs a specific task using provided tools and returns a structured result. It supports multi-response LLM interactions (the assistant may produce several responses interleaved with tool calls) and lets you control how tool calls are executed. In Kotlin, use [subgraphWithTask()](https://api.koog.ai/agents/agents-core/ai.koog.agents.ext.agent/subgraph-with-task.html), and in Java, use [AIAgentSubgraph.builder().withTask()](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.entity/-typed-a-i-agent-subgraph-builder/with-task.html).

You can use this subgraph for the following purposes:

- Create special components that handle specific tasks within a larger workflow.
- Encapsulate complex logic with clear input and output interfaces.
- Configure task-specific tools, models, and prompts.
- Manage conversation history with automatic compression.
- Develop structured agent workflows and task execution pipelines.
- Generate structured results from LLM task execution, including flows with multiple assistant responses and tool invocations.

The API allows you to fine‑tune execution with optional parameters:

- [runMode](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.entity/-subgraph-with-task-builder/run-mode.html): controls how tool calls are executed during the task (sequential by default). Use this to switch between different tool execution strategies when supported by the underlying model/executor.
- [assistantResponseRepeatMax](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.entity/-subgraph-with-task-builder/assistant-response-repeat-max.html): limits how many assistant responses are allowed before concluding the task cannot be completed (defaults to a safe internal limit if not provided).

You can provide a task to the subgraph as text, configure the LLM if needed, and provide the necessary tools, and the subgraph will process and solve the task. Here is an example:

```
val processQuery by subgraphWithTask<String, String>(
    tools = listOf(searchTool, calculatorTool, weatherTool),
    llmModel = OpenAIModels.Chat.GPT4o,
    parallelTools = false,
    assistantResponseRepeatMax = 3,
) { userQuery ->
    """
    You are a helpful assistant that can answer questions about various topics.
    Please help with the following query:
    $userQuery
    """
}
```

```
var processQuery = AIAgentSubgraph.builder("processQuery")
    .limitedTools(List.of(searchTool, calculatorTool, weatherTool))
    .withInput(String.class)
    .withOutput(String.class)
    .withTask(userQuery ->
        "You are a helpful assistant that can answer questions about various topics.\n" +
        "Please help with the following query:\n" +
        userQuery)
    .parallelTools(false)
    .assistantResponseRepeatMax(3)
    .build();
```

### Task execution subgraph with verification

A special version of `subgraphWithTask` that verifies whether a task was performed correctly and provides details about any issues encountered. This subgraph is useful for workflows that require validation or quality checks. In Kotlin, use [subgraphWithVerification()](https://api.koog.ai/agents/agents-core/ai.koog.agents.ext.agent/subgraph-with-verification.html), and in Java, use `AIAgentSubgraph.builder().withVerification()`.

You can use this subgraph for the following purposes:

- Verify the correctness of task execution.
- Implement quality control processes in your workflows.
- Create self-validating components.
- Generate structured verification results with success/failure status and detailed feedback.

The subgraph ensures that the LLM calls a verification tool at the end of the workflow to check whether the task was successfully completed. It guarantees this verification is performed as the final step and returns a [CriticResult](https://api.koog.ai/agents/agents-core/ai.koog.agents.ext.agent/-critic-result/index.html) that indicates whether a task was completed successfully and provides detailed feedback. Here is an example:

```
val verifyCode by subgraphWithVerification<String>(
    tools = listOf(runTestsTool, analyzeTool, readFileTool),
    llmModel = AnthropicModels.Opus_4_6,
    parallelTools = false,
    assistantResponseRepeatMax = 3,
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

```
var verifyCode = AIAgentSubgraph.builder("verifyCode")
    .limitedTools(List.of(runTestsTool, analyzeTool, readFileTool))
    .withInput(String.class)
    .withVerification(codeToVerify ->
        "You are a code reviewer. Please verify that the following code meets all requirements:\n" +
        "1. It compiles without errors\n" +
        "2. All tests pass\n" +
        "3. It follows the project's coding standards\n\n" +
        "Code to verify:\n" +
        codeToVerify)
    .parallelTools(false)
    .assistantResponseRepeatMax(3)
    .build();
```

## Predefined strategies and common strategy patterns

Koog provides predefined strategies that combine various nodes. The nodes are connected using edges to define the flow of operations, with conditions that specify when to follow each edge.

You can integrate these strategies into your agent workflows if needed.

### Single run strategy

A single run strategy is designed for non-interactive use cases where the agent processes input once and returns a result.

You can use this strategy when you need to run straightforward processes that do not require complex logic.

```
public fun singleRunStrategy(): AIAgentGraphStrategy<String, String> = strategy("single_run") {
    val nodeCallLLM by nodeLLMRequest("sendInput")
    val nodeExecuteTool by nodeExecuteTools("nodeExecuteTool")
    val nodeSendToolResult by nodeLLMSendToolResults("nodeSendToolResult")

    edge(nodeStart forwardTo nodeCallLLM)
    edge(nodeCallLLM forwardTo nodeExecuteTool onToolCalls { true })
    edge(nodeCallLLM forwardTo nodeFinish onTextMessage { true })
    edge(nodeExecuteTool forwardTo nodeSendToolResult)
    edge(nodeSendToolResult forwardTo nodeFinish onTextMessage { true })
    edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCalls { true })
}
```

```
public static AIAgentGraphStrategy<String, String> singleRunStrategy() {
    var strategy = AIAgentGraphStrategy.builder("single_run")
        .withInput(String.class)
        .withOutput(String.class);

    var nodeCallLLM = AIAgentNode.llmRequest("sendInput");
    var nodeExecuteTool = AIAgentNode.executeTools("nodeExecuteTool");
    var nodeSendToolResult = AIAgentNode.llmSendToolResults("nodeSendToolResult");

    strategy.edge(AIAgentEdge.builder()
        .from(strategy.nodeStart)
        .to(nodeCallLLM)
        .build());

    strategy.edge(AIAgentEdge.builder()
        .from(nodeCallLLM)
        .to(nodeExecuteTool)
        .onToolCalls()
        .build());

    strategy.edge(AIAgentEdge.builder()
        .from(nodeCallLLM)
        .to(strategy.nodeFinish)
        .onTextMessage()
        .build());

    strategy.edge(nodeExecuteTool, nodeSendToolResult);

    strategy.edge(AIAgentEdge.builder()
        .from(nodeSendToolResult)
        .to(strategy.nodeFinish)
        .onTextMessage()
        .build());

    strategy.edge(AIAgentEdge.builder()
        .from(nodeSendToolResult)
        .to(nodeExecuteTool)
        .onToolCalls()
        .build());

    return strategy.build();
}
```

### Tool-based strategy

A tool-based strategy is designed for workflows that heavily rely on tools to perform specific operations. It typically executes tools based on the LLM decisions and processes the results.

```
fun toolBasedStrategy(name: String, toolRegistry: ToolRegistry): AIAgentGraphStrategy<String, String> {
    return strategy(name) {
        val nodeSendInput by nodeLLMRequest()
        val nodeExecuteTool by nodeExecuteTools()
        val nodeSendToolResult by nodeLLMSendToolResults()

        // Define the flow of the agent
        edge(nodeStart forwardTo nodeSendInput)

        // If the LLM responds with a message, finish
        edge(
            (nodeSendInput forwardTo nodeFinish)
                    onTextMessage { true }
        )

        // If the LLM calls a tool, execute it
        edge(
            (nodeSendInput forwardTo nodeExecuteTool)
                    onToolCalls { true }
        )

        // Send the tool result back to the LLM
        edge(nodeExecuteTool forwardTo nodeSendToolResult)

        // If the LLM calls another tool, execute it
        edge(
            (nodeSendToolResult forwardTo nodeExecuteTool)
                    onToolCalls { true }
        )

        // If the LLM responds with a message, finish
        edge(
            (nodeSendToolResult forwardTo nodeFinish)
                    onTextMessage { true }
        )
    }
}
```

```
public static AIAgentGraphStrategy<String, String> toolBasedStrategy(String name, ToolRegistry toolRegistry) {
    var strategy = AIAgentGraphStrategy.builder(name)
        .withInput(String.class)
        .withOutput(String.class);

    var nodeSendInput = AIAgentNode.llmRequest("nodeSendInput");
    var nodeExecuteTool = AIAgentNode.executeTools("nodeExecuteTool");
    var nodeSendToolResult = AIAgentNode.llmSendToolResults("nodeSendToolResult");

    // Define the flow of the agent
    strategy.edge(AIAgentEdge.builder()
        .from(strategy.nodeStart)
        .to(nodeSendInput)
        .build());

    // If the LLM responds with a message, finish
    strategy.edge(AIAgentEdge.builder()
        .from(nodeSendInput)
        .to(strategy.nodeFinish)
        .onTextMessage()
        .build());

    // If the LLM calls a tool, execute it
    strategy.edge(AIAgentEdge.builder()
        .from(nodeSendInput)
        .to(nodeExecuteTool)
        .onToolCalls(call -> true)
        .build());

    // Send the tool result back to the LLM
    strategy.edge(nodeExecuteTool, nodeSendToolResult);

    // If the LLM calls another tool, execute it
    strategy.edge(AIAgentEdge.builder()
        .from(nodeSendToolResult)
        .to(nodeExecuteTool)
        .onToolCalls()
        .build());

    // If the LLM responds with a message, finish
    strategy.edge(AIAgentEdge.builder()
        .from(nodeSendToolResult)
        .to(strategy.nodeFinish)
        .onTextMessage()
        .build());

    return strategy.build();
}
```

### Streaming data strategy

A streaming data strategy is designed for processing streaming data from the LLM. It typically requests streaming data, processes it, and potentially calls tools with the processed data.

```
val agentStrategy = strategy<String, List<Book>>("library-assistant") {
    // Describe the node containing the output stream parsing
    val getMdOutput by node<String, List<Book>> { booksDescription ->
        val books = mutableListOf<Book>()
        val mdDefinition = markdownBookDefinition()

        llm.writeSession {
            appendPrompt { user(booksDescription) }
            // Initiate the response stream in the form of the definition `mdDefinition`
            val markdownStream = requestLLMStreaming(mdDefinition)
            // Call the parser with the result of the response stream and perform actions with the result
            parseMarkdownStreamToBooks(markdownStream).collect { book ->
                books.add(book)
                println("Parsed Book: ${book.title} by ${book.author}")
            }
        }

        books
    }
    // Describe the agent's graph making sure the node is accessible
    edge(nodeStart forwardTo getMdOutput)
    edge(getMdOutput forwardTo nodeFinish)
}
```

```
var strategy = AIAgentGraphStrategy.builder()
    .withInput(String.class)
    .withOutput(List.class);

var getMdOutput = AIAgentNode.builder()
    .withInput(String.class)
    .<List<Book>>withOutput(TypeToken.of(new TypeCapture<List<Book>>() {}))
    .withAction((booksDescription, ctx) -> {
        var books = new ArrayList<Book>();
        StructureDefinition mdDefinition = markdownBookDefinition();

        ctx.getLlm().writeSession(session -> {
            session.appendPrompt(prompt -> {
                prompt.user(booksDescription);
            });

            // Initiate the response stream in the form of the definition `mdDefinition`
            var markdownStream = session.requestLLMStreaming(mdDefinition);
            // Call the parser with the result of the response stream and perform actions with the result
            parseMarkdownStreamToBooks(markdownStream).subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                }

                @Override
                public void onNext(Book book) {
                    books.add(book);
                    System.out.println("Parsed Book: " + book.getTitle() + " by " + book.getAuthor());
                }

                @Override
                public void onError(Throwable throwable) {
                }

                @Override
                public void onComplete() {
                }
            });

            return null;
        });

        return books;
    })
    .build();

strategy.edge(strategy.nodeStart, getMdOutput);
strategy.edge(getMdOutput, strategy.nodeFinish);
```
