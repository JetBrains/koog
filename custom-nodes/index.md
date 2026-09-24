# Custom node implementation

This page provides detailed instructions on how to implement your own custom nodes in the Koog framework. Custom nodes let you extend the functionality of agent workflows by creating reusable components that perform specific operations.

To learn more about what graph nodes are, their usage, and existing default nodes, see [Graph nodes](../nodes-and-components/).

## Node architecture overview

Before diving into implementation details, it is important to understand the architecture of nodes in the Koog framework. Nodes are the fundamental building blocks of agent workflows, where each node represents a specific operation or transformation in the workflow. You connect nodes using edges, which define the flow of execution between nodes.

Each node has an `execute` method that takes an input and produces an output, which is then passed to the next node in the workflow.

## Implementing a custom node

Custom node implementations range from simple implementations that perform a basic logic on the input data and return an output, to more complex node implementations that accept parameters and maintain state between runs.

### Basic node implementation

The simplest way to implement a custom node in a graph and define your own custom logic would be to use the following pattern:

```
val myNode by node<Input, Output>("node_name") { input ->
    // Processing
    returnValue
}
```

```
var myNode = AIAgentNode.builder("node_name")
    .withInput(Input.class)
    .withOutput(Output.class)
    .withAction((input, ctx) -> {
        // Processing
        return returnValue;
    })
    .build();
```

The code above represents a custom node `myNode` with predefined `Input` and `Output` types, with the optional name string parameter (`node_name`). In Kotlin, you use the `node` DSL function. In Java, you use the `AIAgentNode.builder()` pattern.

In an actual example, here is a simple node that takes a string input and returns the input's length:

```
val myNode by node<String, Int>("node_name") { input ->
    // Processing
    input.length
}
```

```
var myNode = AIAgentNode.builder("node_name")
    .withInput(String.class)
    .withOutput(Integer.class)
    .withAction((input, ctx) -> {
        // Processing
        return input.length();
    })
    .build();
```

Another way to create a custom node is to extract it into a reusable function. In Kotlin, you define an extension function on `AIAgentSubgraphBuilderBase` that calls the `node` function. In Java, you extract the node builder call into a helper method.

```
fun AIAgentSubgraphBuilderBase<*, *>.myCustomNode(
    name: String? = null
): AIAgentNodeDelegate<Input, Output> = node(name) { input ->
    // Custom logic
    input // Return the input as output (pass-through)
}

val myCustomNode by myCustomNode("node_name")
```

```
var myCustomNode = AIAgentNode.builder("node_name")
    .withInput(String.class)
    .withOutput(String.class)
    .withAction((input, ctx) -> {
        // Custom logic
        return input; // Return the input as output (pass-through)
    })
    .build();
```

This creates a pass-through node that performs some custom logic but returns the input as the output without modification.

### Nodes with additional arguments

You can create nodes that accept arguments to customize their behavior:

```
    fun AIAgentSubgraphBuilderBase<*, *>.myNodeWithArguments(
    name: String? = null,
    arg1: String,
    arg2: Int
): AIAgentNodeDelegate<Input, Output> = node(name) { input ->
    // Use arg1 and arg2 in your custom logic
    input // Return the input as the output
}

val myCustomNode by myNodeWithArguments("node_name", arg1 = "value1", arg2 = 42)
```

```
String arg1 = "value1";
int arg2 = 42;

var myCustomNode = AIAgentNode.builder("node_name")
    .withInput(String.class)
    .withOutput(String.class)
    .withAction((input, ctx) -> {
        // Use arg1 and arg2 in your custom logic
        return input; // Return the input as the output
    })
    .build();
```

### Parameterized nodes

You can define nodes with generic input and output type parameters. In Kotlin, you use `inline` functions with `reified` type parameters. In Java, you specify the types explicitly when building the node.

```
inline fun <reified T> AIAgentSubgraphBuilderBase<*, *>.myParameterizedNode(
    name: String? = null,
): AIAgentNodeDelegate<T, T> = node(name) { input ->
    // Do some additional actions
    // Return the input as the output
    input
}

val strategy = strategy<String, String>("strategy_name") {
    val myCustomNode by myParameterizedNode<String>("node_name")
}
```

```
// In Java, specify the types explicitly when building the node
var myCustomNode = AIAgentNode.builder("node_name")
    .withInput(String.class)
    .withOutput(String.class)
    .withAction((input, ctx) -> {
        // Do some additional actions
        // Return the input as the output
        return input;
    })
    .build();
```

### Stateful nodes

If your node needs to maintain state between runs, you can use closure variables. In Kotlin, you declare a variable in the enclosing function. In Java, you use thread-safe wrappers like `AtomicInteger` since lambda captures must be effectively final.

```
fun AIAgentSubgraphBuilderBase<*, *>.myStatefulNode(
    name: String? = null
): AIAgentNodeDelegate<Input, Output> {
    var counter = 0

    return node(name) { input ->
        counter++
        println("Node executed $counter times")
        input
    }
}
```

```
// In Java, use AtomicInteger (or similar) since the lambda captures must be effectively final
AtomicInteger counter = new AtomicInteger(0);

var myStatefulNode = AIAgentNode.builder("node_name")
    .withInput(String.class)
    .withOutput(String.class)
    .withAction((input, ctx) -> {
        int count = counter.incrementAndGet();
        System.out.println("Node executed " + count + " times");
        return input;
    })
    .build();
```

## Node input and output types

Nodes can have different input and output types. In both Kotlin and Java, these are specified as generic type parameters:

```
val stringToIntNode by node<String, Int>("node_name") { input: String ->
    // Processing
    input.toInt() // Convert string to integer
}
```

```
var stringToIntNode = AIAgentNode.builder("node_name")
    .withInput(String.class)
    .withOutput(Integer.class)
    .withAction((input, ctx) -> {
        // Processing
        return Integer.parseInt(input); // Convert string to integer
    })
    .build();
```

Note

The input and output types determine how the node can be connected to other nodes in the workflow. Nodes can only be connected if the output type of the source node is compatible with the input type of the target node.

## Best practices

When implementing custom nodes, follow these best practices:

1. **Keep nodes focused**: each node should perform a single, well-defined operation.
1. **Use descriptive names**: node names should clearly indicate their purpose.
1. **Document parameters**: provide clear documentation for all parameters.
1. **Handle errors gracefully**: implement proper error handling to prevent workflow failures.
1. **Make nodes reusable**: design nodes to be reusable across different workflows.
1. **Use type parameters**: use generic type parameters when appropriate to make nodes more flexible.
1. **Provide default values**: when possible, provide sensible default values for parameters.

## Common patterns

The following sections provide some common patterns for implementing custom nodes.

### Pass-through nodes

Nodes that perform an operation but return the input as the output.

```
val loggingNode by node<String, String>("node_name") { input ->
    println("Processing input: $input")
    input // Return the input as the output
}
```

```
var loggingNode = AIAgentNode.builder("node_name")
    .withInput(String.class)
    .withOutput(String.class)
    .withAction((input, ctx) -> {
        System.out.println("Processing input: " + input);
        return input; // Return the input as the output
    })
    .build();
```

### Transformation nodes

Nodes that transform the input data and produce a modified output.

```
val upperCaseNode by node<String, String>("node_name") { input ->
    println("Processing input: $input")
    input.uppercase() // Transform the input to uppercase
}
```

```
var upperCaseNode = AIAgentNode.builder("node_name")
    .withInput(String.class)
    .withOutput(String.class)
    .withAction((input, ctx) -> {
        System.out.println("Processing input: " + input);
        return input.toUpperCase(); // Transform the input to uppercase
    })
    .build();
```

### LLM interaction nodes

Nodes that interact with the LLM. In Kotlin, you have fine-grained control over the LLM session. In Java, you typically use pre-built factory methods like `AIAgentNode.llmRequest()` that handle prompt construction automatically.

```
val summarizeTextNode by node<String, String>("node_name") { input ->
    llm.writeSession {
        appendPrompt {
            user("Please summarize the following text: $input")
        }

        val response = requestLLMWithoutTools()
        response.parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text }
    }
}
```

```
// In Java, LLM interaction is handled using pre-built factory nodes.
// AIAgentNode.llmRequest() creates a node that sends the input string as a user
// message to the LLM and returns the response. The prompt text is provided as
// the node's input when it is executed in the graph.
var summarizeTextNode = AIAgentNode.llmRequest("node_name");

// To extract the text content from the LLM response, chain a separate node:
var extractContent = AIAgentNode.builder("extract-content")
    .withInput(Message.Assistant.class)
    .withOutput(String.class)
    .withAction((response, ctx) -> response.getParts().stream()
        .filter(p -> p instanceof MessagePart.Text)
        .map(p -> ((MessagePart.Text) p).getText())
        .collect(Collectors.joining()))
    .build();
```

Note

The Kotlin example above shows fine-grained control over the LLM session (custom prompt construction, explicit `requestLLMWithoutTools` call). The Java API provides higher-level factory methods like `AIAgentNode.llmRequest()` that handle prompt construction automatically, where the input string becomes the user message. For advanced prompt customization, compose multiple nodes or use a custom subgraph.

### Tool run node

Custom nodes that execute tools. In Kotlin, you can manually construct tool calls and execute them. In Java, you typically use subgraphs that delegate tool orchestration to the LLM.

```
val nodeExecuteCustomTool by node<String, String>("node_name") { input ->
    val toolCall = MessagePart.Tool.Call(
        id = UUID.randomUUID().toString(),
        tool = toolName,
        args = Json.encodeToString(ToolArgs(arg1 = input, arg2 = 42)) // Use the input as tool arguments
    )

    val result = environment.executeTool(toolCall)
    result.output
}
```

```
// In Java, direct tool execution (as shown in the Kotlin example) is not available
// through the Java builder API. Instead, use a subgraph that delegates tool calls
// to the LLM, which decides when and how to invoke the tools:
var toolSubgraph = AIAgentSubgraph.builder("tool-subgraph")
    .withInput(String.class)
    .withOutput(String.class)
    .withTask(input -> "Use my_tool with input: " + input)
    .build();
```

Note

The Kotlin example demonstrates low-level tool execution by manually constructing a `MessagePart.Tool.Call` and calling `environment.executeTool()`. The Java API encourages a higher-level approach using subgraphs with `withTask()`, where the LLM orchestrates tool calls automatically. To restrict which tools are available, chain `.limitedTools(List.of(myTool))` before `.withInput()`.
