# Custom node implementation

This page provides detailed instructions on how to implement your own custom nodes in the Koog framework. 
Custom nodes let you extend the functionality of agent workflows by creating reusable components that perform specific
operations.

To learn more about what graph nodes are, their usage, and existing default nodes, see [Graph nodes](nodes-and-components.md).

## Node architecture overview

Before diving into implementation details, it is important to understand the architecture of nodes in the Koog framework. Nodes are the fundamental building blocks of agent workflows, where each node represents a specific operation or transformation in the workflow. You connect nodes using edges, which define the flow of execution between nodes.

Each node has an `execute` method that takes an input and produces an output, which is then passed to the next node in the workflow.

## Implementing a custom node

Custom node implementations range from simple implementations that perform a basic logic on the input data and return
an output, to more complex node implementations that accept parameters and maintain state between runs.

### Basic node implementation

The simplest way to implement a custom node in a graph and define your own custom logic would be to use the following pattern:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    typealias Input = String
    typealias Output = Int
    val returnValue = 42
    val str = strategy<Input, Output>("my-strategy") {
    -->
    <!--- SUFFIX
    }
    -->
    ```kotlin
    val myNode by node<Input, Output>("node_name") { input ->
        // Processing
        returnValue
    }
    ```
    <!--- KNIT example-custom-nodes-01.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.AIAgentNode;
    class exampleCustomNodesJava01 {
        static class Input {}
        static class Output {}
        static Output returnValue = new Output();
        public static void main(String[] args) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    var myNode = AIAgentNode.builder("node_name")
        .withInput(Input.class)
        .withOutput(Output.class)
        .withAction((input, ctx) -> {
            // Processing
            return returnValue;
        })
        .build();
    ```
    <!--- KNIT exampleCustomNodesJava01.java -->

The code above represents a custom node `myNode` with predefined `Input` and `Output` types, with the optional name
string parameter (`node_name`). In Kotlin, you use the `node` DSL function. In Java, you use the `AIAgentNode.builder()` 
pattern.

In an actual example, here is a simple node that takes a string input and returns the input's length:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    val str = strategy<String, Int>("my-strategy") {
    -->
    <!--- SUFFIX
    }
    -->
    ```kotlin
    val myNode by node<String, Int>("node_name") { input ->
        // Processing
        input.length
    }
    ```
    <!--- KNIT example-custom-nodes-02.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.AIAgentNode;
    class exampleCustomNodesJava02 {
        public static void main(String[] args) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    var myNode = AIAgentNode.builder("node_name")
        .withInput(String.class)
        .withOutput(Integer.class)
        .withAction((input, ctx) -> {
            // Processing
            return input.length();
        })
        .build();
    ```
    <!--- KNIT exampleCustomNodesJava02.java -->

Another way to create a custom node is to extract it into a reusable function. In Kotlin, you define an extension 
function on `AIAgentSubgraphBuilderBase` that calls the `node` function. In Java, you extract the node builder call into
a helper method.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
    import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    typealias Input = String
    typealias Output = String
    val strategy = strategy<String, String>("strategy_name") {
    -->
    <!--- SUFFIX
    }
    -->
    ```kotlin
    fun AIAgentSubgraphBuilderBase<*, *>.myCustomNode(
        name: String? = null
    ): AIAgentNodeDelegate<Input, Output> = node(name) { input ->
        // Custom logic
        input // Return the input as output (pass-through)
    }

    val myCustomNode by myCustomNode("node_name")
    ```
    <!--- KNIT example-custom-nodes-03.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.AIAgentNode;
    class exampleCustomNodesJava03 {
        public static void main(String[] args) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    var myCustomNode = AIAgentNode.builder("node_name")
        .withInput(String.class)
        .withOutput(String.class)
        .withAction((input, ctx) -> {
            // Custom logic
            return input; // Return the input as output (pass-through)
        })
        .build();
    ```
    <!--- KNIT exampleCustomNodesJava03.java -->

This creates a pass-through node that performs some custom logic but returns the input as the output without 
modification.

### Nodes with additional arguments

You can create nodes that accept arguments to customize their behavior:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
    import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    typealias Input = String
    typealias Output = String
    val strategy = strategy<String, String>("strategy_name") {
    -->
    <!--- SUFFIX
    }
    -->
    ```kotlin
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
    <!--- KNIT example-custom-nodes-04.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.AIAgentNode;
    class exampleCustomNodesJava04 {
        public static void main(String[] args) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
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
    <!--- KNIT exampleCustomNodesJava04.java -->


### Parameterized nodes

You can define nodes with generic input and output type parameters. In Kotlin, you use `inline` functions with `reified` 
type parameters. In Java, you specify the types explicitly when building the node.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
    import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    -->
    ```kotlin
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
    <!--- KNIT example-custom-nodes-05.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.AIAgentNode;
    class exampleCustomNodesJava05 {
        public static void main(String[] args) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
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
    <!--- KNIT exampleCustomNodesJava05.java -->

### Stateful nodes

If your node needs to maintain state between runs, you can use closure variables. In Kotlin, you declare a variable in 
the enclosing function. In Java, you use thread-safe wrappers like `AtomicInteger` since lambda captures must be 
effectively final.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
    import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
    import ai.koog.agents.core.dsl.builder.node
    typealias Input = Unit
    typealias Output = Unit
    -->
    ```kotlin
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
    <!--- KNIT example-custom-nodes-06.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.AIAgentNode;
    import java.util.concurrent.atomic.AtomicInteger;
    class exampleCustomNodesJava06 {
        public static void main(String[] args) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
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
    <!--- KNIT exampleCustomNodesJava06.java -->

## Node input and output types

Nodes can have different input and output types. In both Kotlin and Java, these are specified as generic type 
parameters:

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    val strategy = strategy<String, String>("strategy_name") {
    -->
    <!--- SUFFIX
    }
    -->
    ```kotlin
    val stringToIntNode by node<String, Int>("node_name") { input: String ->
        // Processing
        input.toInt() // Convert string to integer
    }
    ```
    <!--- KNIT example-custom-nodes-07.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.AIAgentNode;
    class exampleCustomNodesJava07 {
        public static void main(String[] args) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    var stringToIntNode = AIAgentNode.builder("node_name")
        .withInput(String.class)
        .withOutput(Integer.class)
        .withAction((input, ctx) -> {
            // Processing
            return Integer.parseInt(input); // Convert string to integer
        })
        .build();
    ```
    <!--- KNIT exampleCustomNodesJava07.java -->

!!! note
    The input and output types determine how the node can be connected to other nodes in the workflow. Nodes can only be connected if the output type of the source node is compatible with the input type of the target node.

## Best practices

When implementing custom nodes, follow these best practices:

1. **Keep nodes focused**: each node should perform a single, well-defined operation.
2. **Use descriptive names**: node names should clearly indicate their purpose.
3. **Document parameters**: provide clear documentation for all parameters.
4. **Handle errors gracefully**: implement proper error handling to prevent workflow failures.
5. **Make nodes reusable**: design nodes to be reusable across different workflows.
6. **Use type parameters**: use generic type parameters when appropriate to make nodes more flexible.
7. **Provide default values**: when possible, provide sensible default values for parameters.

## Common patterns

The following sections provide some common patterns for implementing custom nodes.

### Pass-through nodes

Nodes that perform an operation but return the input as the output.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    val strategy = strategy<String, String>("strategy_name") {
    -->
    <!--- SUFFIX
    }
    -->
    ```kotlin
    val loggingNode by node<String, String>("node_name") { input ->
        println("Processing input: $input")
        input // Return the input as the output
    }
    ```
    <!--- KNIT example-custom-nodes-08.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.AIAgentNode;
    class exampleCustomNodesJava08 {
        public static void main(String[] args) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    var loggingNode = AIAgentNode.builder("node_name")
        .withInput(String.class)
        .withOutput(String.class)
        .withAction((input, ctx) -> {
            System.out.println("Processing input: " + input);
            return input; // Return the input as the output
        })
        .build();
    ```
    <!--- KNIT exampleCustomNodesJava08.java -->

### Transformation nodes

Nodes that transform the input data and produce a modified output.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    val strategy = strategy<String, String>("strategy_name") {
    -->
    <!--- SUFFIX
    }
    -->
    ```kotlin
    val upperCaseNode by node<String, String>("node_name") { input ->
        println("Processing input: $input")
        input.uppercase() // Transform the input to uppercase
    }
    ```
    <!--- KNIT example-custom-nodes-09.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.AIAgentNode;
    class exampleCustomNodesJava09 {
        public static void main(String[] args) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    var upperCaseNode = AIAgentNode.builder("node_name")
        .withInput(String.class)
        .withOutput(String.class)
        .withAction((input, ctx) -> {
            System.out.println("Processing input: " + input);
            return input.toUpperCase(); // Transform the input to uppercase
        })
        .build();
    ```
    <!--- KNIT exampleCustomNodesJava09.java -->

### LLM interaction nodes

Nodes that interact with the LLM. In Kotlin, you have fine-grained control over the LLM session. In Java, you typically 
use pre-built factory methods like `AIAgentNode.llmRequest()` that handle prompt construction automatically.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    val strategy = strategy<String, String>("strategy_name") {
    -->
    <!--- SUFFIX
    }
    -->
    ```kotlin
    val summarizeTextNode by node<String, String>("node_name") { input ->
        llm.writeSession {
            appendPrompt {
                user("Please summarize the following text: $input")
            }

            val response = requestLLMWithoutTools()
            response.content
        }
    }
    ```
    <!--- KNIT example-custom-nodes-10.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.AIAgentNode;
    import ai.koog.prompt.message.Message;
    class exampleCustomNodesJava10 {
        public static void main(String[] args) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    // In Java, LLM interaction is handled using pre-built factory nodes.
    // AIAgentNode.llmRequest() creates a node that sends the input string as a user
    // message to the LLM and returns the response. The prompt text is provided as
    // the node's input when it is executed in the graph.
    var summarizeTextNode = AIAgentNode.llmRequest(true, "node_name");

    // To extract the text content from the LLM response, chain a separate node:
    var extractContent = AIAgentNode.builder("extract-content")
        .withInput(Message.Response.class)
        .withOutput(String.class)
        .withAction((response, ctx) -> response.getContent())
        .build();
    ```
    <!--- KNIT exampleCustomNodesJava10.java -->

!!! note
    The Kotlin example above shows fine-grained control over the LLM session (custom prompt construction, explicit `requestLLMWithoutTools` call). The Java API provides higher-level factory methods like `AIAgentNode.llmRequest()` that handle prompt construction automatically, where the input string becomes the user message. For advanced prompt customization, compose multiple nodes or use a custom subgraph.

### Tool run node

Custom nodes that execute tools. In Kotlin, you can manually construct tool calls and execute them. In Java, you 
typically use subgraphs that delegate tool orchestration to the LLM.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.core.dsl.builder.node
    import ai.koog.prompt.message.Message
    import ai.koog.prompt.message.ResponseMetaInfo
    import ai.koog.utils.time.KoogClock
    import kotlinx.serialization.Serializable
    import kotlinx.serialization.json.Json
    import java.util.*
    val toolName = "my-custom-tool"
    @Serializable
    data class ToolArgs(val arg1: String, val arg2: Int)
    val strategy = strategy<String, String>("strategy_name") {
    -->
    <!--- SUFFIX
    }
    -->
    ```kotlin
    val nodeExecuteCustomTool by node<String, String>("node_name") { input ->
        val toolCall = Message.Tool.Call(
            id = UUID.randomUUID().toString(),
            tool = toolName,
            metaInfo = ResponseMetaInfo.create(KoogClock.System),
            content = Json.encodeToString(ToolArgs(arg1 = input, arg2 = 42)) // Use the input as tool arguments
        )

        val result = environment.executeTool(toolCall)
        result.content
    }
    ```
    <!--- KNIT example-custom-nodes-11.kt -->

=== "Java"

    <!--- INCLUDE
    import ai.koog.agents.core.agent.entity.AIAgentSubgraph;
    class exampleCustomNodesJava11 {
        public static void main(String[] args) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    // In Java, direct tool execution (as shown in the Kotlin example) is not available
    // through the Java builder API. Instead, use a subgraph that delegates tool calls
    // to the LLM, which decides when and how to invoke the tools:
    var toolSubgraph = AIAgentSubgraph.builder("tool-subgraph")
        .withInput(String.class)
        .withOutput(String.class)
        .withTask(input -> "Use my_tool with input: " + input)
        .build();
    ```
    <!--- KNIT exampleCustomNodesJava11.java -->

!!! note
    The Kotlin example demonstrates low-level tool execution by manually constructing a `Message.Tool.Call` and calling `environment.executeTool()`. The Java API encourages a higher-level approach using subgraphs with `withTask()`, where the LLM orchestrates tool calls automatically. To restrict which tools are available, chain `.limitedTools(List.of(myTool))` before `.withInput()`.
