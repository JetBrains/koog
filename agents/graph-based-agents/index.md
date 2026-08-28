# Graph-based agents

With graph-based agents, you model the behavior as an explicit state machine: nodes of a graph strategy represent actions (LLM calls, tool execution) and edges represent data flow between nodes.

The main advantages of graph-based agents are:

- Easy to visualize
- State persistence
- Composable architecture

Prerequisites

Ensure your environment and project meet the following requirements:

- JDK 17+
- Kotlin 2.2.0+
- Gradle 8.0+ or Maven 3.8+

Add the [Koog package](https://central.sonatype.com/artifact/ai.koog/koog-agents/) as a dependency:

build.gradle.kts

```
dependencies {
    // Stable
    implementation("ai.koog:koog-agents:1.2.0")

    // Beta
    implementation("ai.koog:koog-agents-additions:1.2.0-beta")
}
```

build.gradle

```
dependencies {
    // Stable
    implementation 'ai.koog:koog-agents:1.2.0'

    // Beta
    implementation 'ai.koog:koog-agents-additions:1.2.0-beta'
}
```

pom.xml

```
<dependency>
    <!-- Stable -->
    <dependency>
        <groupId>ai.koog</groupId>
        <artifactId>koog-agents-jvm</artifactId>
        <version>1.2.0</version>
    </dependency>

    <!-- Beta -->
    <dependency>
        <groupId>ai.koog</groupId>
        <artifactId>koog-agents-additions-jvm</artifactId>
        <version>1.2.0-beta</version>
    </dependency>
</dependency>
```

Get an API key from an LLM provider or run a local LLM via Ollama. For more information, see [Quickstart](../../quickstart/).

Examples on this page assume that you are running Llama 3.2 locally via Ollama.

This page describes how to re-create the strategy graph used by [basic agents](../basic-agents/). It sends a request to an LLM and then either outputs the response (if the LLM responded with an assistant message) or executes a tool (if the LLM requested a tool call). In case of a tool call, the agent sends the tool result to the LLM and then either outputs the response or executes a tool.

Here is an illustration of the strategy graph:

```
---
config:
  flowchart:
    defaultRenderer: "elk"
---
graph TB
    subgraph nodeStart
        Input
    end

    subgraph nodeFinish
        Output
    end

    subgraph nodeSendInput
        llmRequest(Request LLM)
    end

    subgraph nodeExecuteTool
        executeTool(Execute tool call)
    end

    subgraph nodeSendToolResult
        sendToolResult(Request LLM)
    end

    Input --String--> llmRequest
    llmRequest --Message.Assistant--> onToolCalls{{onToolCalls}}
    llmRequest --Message.Assistant--> onTextMessage{{onTextMessage}}
    onTextMessage --String--> Output
    onToolCalls --ToolCalls--> executeTool --ReceivedToolResults--> sendToolResult
    sendToolResult --Message.Assistant--> onToolCalls
    sendToolResult --Message.Assistant--> onTextMessage
```

## Build a strategy graph

In Koog, you implement a strategy using [`AIAgentGraphStrategyBuilder`](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.builder/-a-i-agent-graph-strategy-builder/index.html). Just like every node has an input and output type, the strategy as a whole also defines some input and output type. This example assumes that the input and output types are strings, which means the agent implementing this strategy will expect a string and return a string.

To create a strategy, use the [`strategy()`](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.builder/strategy.html) function with two generics as the input and output types, provide a unique identifier for the strategy, and define the nodes and edges.

```
val calculatorAgentStrategy = strategy<String, String>("Simple calculator") {
    val nodeSendInput by nodeLLMRequest()
    val nodeExecuteTool by nodeExecuteTools()
    val nodeSendToolResult by nodeLLMSendToolResults()

    edge(nodeStart forwardTo nodeSendInput)
    edge(nodeSendInput forwardTo nodeFinish onTextMessage { true })
    edge(nodeSendInput forwardTo nodeExecuteTool onToolCalls { true })
    edge(nodeExecuteTool forwardTo nodeSendToolResult)
    edge(nodeSendToolResult forwardTo nodeFinish onTextMessage { true })
    edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCalls { true })
}
```

```
var calculatorAgentStrategy = AIAgentGraphStrategy.builder("Simple calculator")
    .withInput(String.class)
    .withOutput(String.class);

var nodeSendInput = AIAgentNode.llmRequest("nodeSendInput");
var nodeExecuteTool = AIAgentNode.executeTools("nodeExecuteTool");
var nodeSendToolResult = AIAgentNode.llmSendToolResults("nodeSendToolResult");

calculatorAgentStrategy.edge(AIAgentEdge.builder()
    .from(calculatorAgentStrategy.nodeStart)
    .to(nodeSendInput)
    .build());
calculatorAgentStrategy.edge(AIAgentEdge.builder()
    .from(nodeSendInput)
    .to(calculatorAgentStrategy.nodeFinish)
    .onTextMessage()
    .build());
calculatorAgentStrategy.edge(AIAgentEdge.builder()
    .from(nodeSendInput)
    .to(nodeExecuteTool)
    .onToolCalls()
    .build());
calculatorAgentStrategy.edge(nodeExecuteTool, nodeSendToolResult);
calculatorAgentStrategy.edge(AIAgentEdge.builder()
    .from(nodeSendToolResult)
    .to(calculatorAgentStrategy.nodeFinish)
    .onTextMessage()
    .build());
calculatorAgentStrategy.edge(AIAgentEdge.builder()
    .from(nodeSendToolResult)
    .to(nodeExecuteTool)
    .onToolCalls()
    .build());
```

This example uses only [predefined nodes](../../nodes-and-components/), but you can also create [custom nodes](../../custom-nodes/).

Every strategy graph must have a path from `nodeStart` to `nodeFinish` connected by [edges](../../custom-strategy-graphs/#edges). Edges can have conditions to determine when to follow a particular edge. Edges can also transform the output of the previous node before passing it to the next one. This is necessary to connect nodes that have non-matching output and input types.

In the previous example, `onToolCalls { true }` means that the edge will follow only if the previous node returned an assistant message containing at least one tool call (`MessagePart.Tool.Call`).

When using `onTextMessage { true }`, the edge will follow only if the previous node returned an assistant message containing text parts (`MessagePart.Text`). This function also extracts and joins the text content of those parts, effectively transforming `Message.Assistant` to `String`, because `nodeFinish` expects a string.

Tip

Instead of `onTextMessage { true }`, you can do the following:

```
onMessageParts(MessagePart.Text::class) transformed { it.joinToString("\n") { part -> part.text } }
```

Or:

```
onCondition { it is Message.Assistant } transformed { (it as Message.Assistant).parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { part -> part.text } }
```

## Create and run the agent

Let's create an agent instance with this strategy and run it:

```
val calculatorAgentStrategy = strategy<String, String>("Simple calculator") {
    val nodeSendInput by nodeLLMRequest()
    val nodeExecuteTool by nodeExecuteTools()
    val nodeSendToolResult by nodeLLMSendToolResults()

    edge(nodeStart forwardTo nodeSendInput)
    edge(nodeSendInput forwardTo nodeFinish onTextMessage { true })
    edge(nodeSendInput forwardTo nodeExecuteTool onToolCalls { true })
    edge(nodeExecuteTool forwardTo nodeSendToolResult)
    edge(nodeSendToolResult forwardTo nodeFinish onTextMessage { true })
    edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCalls { true })
}

val mathAgent = AIAgent(
    promptExecutor = simpleOllamaAIExecutor(),
    llmModel = OllamaModels.Meta.LLAMA_3_2,
    strategy = calculatorAgentStrategy
)

fun main() = runBlocking {
    val result = mathAgent.run("Multiply 3 by 4, then multiply the result by 5, then add 10, then add 123.")
    println(result)
}
```

```
var calculatorAgentStrategy = AIAgentGraphStrategy.builder("Simple calculator")
    .withInput(String.class)
    .withOutput(String.class);

var nodeSendInput = AIAgentNode.llmRequest("nodeSendInput");
var nodeExecuteTool = AIAgentNode.executeTools("nodeExecuteTool");
var nodeSendToolResult = AIAgentNode.llmSendToolResults("nodeSendToolResult");

calculatorAgentStrategy.edge(AIAgentEdge.builder()
    .from(calculatorAgentStrategy.nodeStart)
    .to(nodeSendInput)
    .build());
calculatorAgentStrategy.edge(AIAgentEdge.builder()
    .from(nodeSendInput)
    .to(calculatorAgentStrategy.nodeFinish)
    .onTextMessage()
    .build());
calculatorAgentStrategy.edge(AIAgentEdge.builder()
    .from(nodeSendInput)
    .to(nodeExecuteTool)
    .onToolCalls()
    .build());
calculatorAgentStrategy.edge(nodeExecuteTool, nodeSendToolResult);
calculatorAgentStrategy.edge(AIAgentEdge.builder()
    .from(nodeSendToolResult)
    .to(calculatorAgentStrategy.nodeFinish)
    .onTextMessage()
    .build());
calculatorAgentStrategy.edge(AIAgentEdge.builder()
    .from(nodeSendToolResult)
    .to(nodeExecuteTool)
    .onToolCalls()
    .build());

var promptExecutor = PromptExecutor.builder()
    .ollama("http://localhost:11434")
    .build();

AIAgent<String, String> mathAgent = AIAgent.builder()
    .promptExecutor(promptExecutor)
    .llmModel(OllamaModels.Meta.LLAMA_3_2)
    .graphStrategy(calculatorAgentStrategy.build())
    .build();

    String result = mathAgent.run("Multiply 3 by 4, then multiply the result by 5, then add 10, then add 123.", null);
    System.out.println(result);
```

When you run this agent, it will respond with something like this:

```
To calculate this, I'll follow the order of operations:

1. Multiply 3 by 4: 3 * 4 = 12
2. Multiply the result by 5: 12 * 5 = 60
3. Add 10: 60 + 10 = 70
4. Add 123: 70 + 123 = 193

The final answer is 193.
```

However, since this agent doesn't have any tools, the LLM never returns a tool call and simply generates the whole answer. This is what effectively happens:

```
---
config:
  flowchart:
    defaultRenderer: "elk"
---
graph LR
    subgraph nodeStart
        Input
    end

    subgraph nodeFinish
        Output
    end

    subgraph nodeSendInput
        llmRequest(Request LLM)
    end

    Input --String--> llmRequest --Message.Assistant--> onTextMessage{{onTextMessage}} --String--> Output
```

Even though it is correct in this case, the answer will depend on the arithmetic abilities of the underlying LLM. To make sure the calculations are correct, we should provide the agent with math tools. Then the LLM will be able to decide to call tools that perform the calculations deterministically.

## Add tools

Define [tools](../../tools/) for performing math operations and add them to a [ToolRegistry](https://api.koog.ai/agents/agents-tools/ai.koog.agents.core.tools/-tool-registry/index.html):

```
@LLMDescription("Tools for performing math operations")
class MathTools : ToolSet {
    @Tool
    @LLMDescription("Adds two numbers and returns the result")
    fun add(a: Int, b: Int): Int {
        // This is not necessary, but it helps to see the tool call in the console output
        println("Adding $a and $b...")
        return a + b
    }
    @Tool
    @LLMDescription("Multiplies two numbers and returns the result")
    fun multiply(a: Int, b: Int): Int {
        // This is not necessary, but it helps to see the tool call in the console output
        println("Multiplying $a and $b...")
        return a * b
    }
}

val toolRegistry = ToolRegistry {
    tools(MathTools())
}
```

```
@LLMDescription("Tools for performing math operations")
public static class MathTools implements ToolSet {
    @Tool
    @LLMDescription("Adds two numbers and returns the result")
    public int add(int a, int b) {
        // This is not necessary, but it helps to see the tool call in the console output
        System.out.println("Adding " + a + " and " + b + "...");
        return a + b;
    }

    @Tool
    @LLMDescription("Multiplies two numbers and returns the result")
    public int multiply(int a, int b) {
        // This is not necessary, but it helps to see the tool call in the console output
        System.out.println("Multiplying " + a + " and " + b + "...");
        return a * b;
    }
}
public static void main(String[] args) {
    ToolRegistry toolRegistry = ToolRegistry.builder()
        .tools(new MathTools())
        .build();
}
```

Add the tool registry to the agent configuration:

```
val mathAgent = AIAgent(
    promptExecutor = simpleOllamaAIExecutor(),
    llmModel = OllamaModels.Meta.LLAMA_3_2,
    strategy = calculatorAgentStrategy,
    toolRegistry = toolRegistry
)

fun main() = runBlocking {
    val result = mathAgent.run("Multiply 3 by 4, then multiply the result by 5, then add 10, then add 123.")
    println(result)
}
```

```
AIAgent<String, String> mathAgent = AIAgent.builder()
    .promptExecutor(promptExecutor)
    .llmModel(OllamaModels.Meta.LLAMA_3_2)
    .graphStrategy(calculatorAgentStrategy.build())
    .toolRegistry(toolRegistry)
    .build();

String result = mathAgent.run("Multiply 3 by 4, then multiply the result by 5, then add 10, then add 123.", null);
System.out.println(result);
```

When you run the agent now, it will respond with something like this:

```
Multiplying 3 and 4...
The output from the first operation was multiplied by 5:
5 * 12 = 60

Then, 10 was added to the result:
60 + 10 = 70

Finally, 123 was added to the result:
70 + 123 = 193
```

According to this output, the agent correctly performed the calculations, but it only called the `multiply` tool once instead of calling the corresponding tool for every operation. We can help the agent by describing its role and providing instructions for using appropriate tools in the system prompt.

## Provide a system prompt

A [system prompt](../../prompts/prompt-creation/#system-message) defines the agent's role and instructions for performing tasks. In our example, it is important to describe how the agent should process complex multistep calculations:

```
val mathAgent = AIAgent(
    promptExecutor = simpleOllamaAIExecutor(),
    llmModel = OllamaModels.Meta.LLAMA_3_2,
    systemPrompt = """
                You are a simple calculator assistant.
                You can add and multiply two numbers using the 'add' and 'multiply' tools.
                When the user provides input, extract the numbers and operations they requested.
                Use the appropriate tool for the first operation, then the next one, and so on, until you calculate the result.
                Always respond with a clear, friendly message showing the calculation and result.
                """.trimIndent(),
    toolRegistry = toolRegistry,
    strategy = calculatorAgentStrategy
)

fun main() = runBlocking {
    val result = mathAgent.run("Multiply 3 by 4, then multiply the result by 5, then add 10, then add 123.")
    println(result)
}
```

```
AIAgent<String, String> mathAgent = AIAgent.builder()
    .promptExecutor(promptExecutor)
    .llmModel(OllamaModels.Meta.LLAMA_3_2)
    .systemPrompt("You are a simple calculator assistant. You can add and multiply two numbers using the 'add' and 'multiply' tools. When the user provides input, extract the numbers and operations they requested. Use the appropriate tool for the first operation, then the next one, and so on, until you calculate the result. Always respond with a clear, friendly message showing the calculation and result.")
    .graphStrategy(calculatorAgentStrategy.build())
    .toolRegistry(toolRegistry)
    .build();

String result = mathAgent.run("Multiply 3 by 4, then multiply the result by 5, then add 10, then add 123.", null);
System.out.println(result);
```

When you run the agent now, it will respond with something like this:

```
Multiplying 3 and 4...
Multiplying 12 and 5...
Adding 60 and 10...
Adding 70 and 123...
The final result is: 193
```

As you can see, the agent now correctly calls the appropriate tool for each operation, ensuring that it performs the calculations deterministically instead of risking a hallucinated result.

## Next steps

- Compare to [functional agents](../functional-agents/) and [planner agents](../planner-agents/)
- Enhance your agent by [installing features](../../features/)
- Improve the predictability and reliability with [structured output](../../structured-output/)
