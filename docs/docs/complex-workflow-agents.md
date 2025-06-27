# Complex workflow agents

In addition to single-run agents, the `AIAgent` class lets you build agents that handle complex workflows by defining custom strategies, tools, and configurations.

The process of creating and configuring such an agent typically includes the following steps:

1. Provide a prompt executor to communicate with the LLM.
2. Define a strategy that controls the agent workflow.
3. Configure agent behavior.
4. Implement tools for the agent to use.
5. Add optional features like event handling, memory, or tracing.
6. Run the agent with user input.

## Prerequisites

- You have a valid API key from the LLM provider used to implement an AI agent. For a list of all available providers, see [Overview](index.md).

!!! tip
    Use environment variables or a secure configuration management system to store your API keys.
    Avoid hardcoding API keys directly in your source code.

## Creating a single-run agent

### 1. Add dependencies

To use the `AIAgent` functionality, include all necessary dependencies in your build configuration:

```
dependencies {
    implementation("ai.koog:koog-agents:VERSION")
}
```

For all available installation methods, see [Installation](index.md#installation).

### 2. Provide a prompt executor

Prompt executors manage and run prompts.
You can choose a prompt executor based on the LLM provider you plan to use.
Also, you can create a custom prompt executor using one of the available LLM clients.
To learn more, see [Prompt executors](prompt-api.md#prompt-executors).

For example, to provide the OpenAI prompt executor, you need to call the `simpleOpenAIExecutor` function and provide it with the API key required for authentication with the OpenAI service:

```kotlin
val promptExecutor = simpleOpenAIExecutor(token)
```

To create a prompt executor that works with multiple LLM providers, do the following:

1. Configure clients for the required LLM providers with the corresponding API keys. For example:
```kotlin
val openAIClient = OpenAILLMClient(System.getenv("OPENAI_KEY"))
val anthropicClient = AnthropicLLMClient(System.getenv("ANTHROPIC_KEY"))
val googleClient = GoogleLLMClient(System.getenv("GOOGLE_KEY"))
```
2. Pass the configured clients to the `DefaultMultiLLMPromptExecutor` class constructor to create a prompt executor with multiple LLM providers:
```kotlin
val multiExecutor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient, googleClient)
```

### 3. Define a strategy

A strategy defines the workflow of your agent by using nodes and edges.

#### 3.1. Understand nodes and edges

Nodes and edges are the building blocks of the strategy.

Nodes represent processing steps in your agent strategy.

```kotlin
val processNode by node<InputType, OutputType> { input ->
    // Process the input and return an output
    // You can use llm.writeSession to interact with the LLM
    // You can call tools using callTool, callToolRaw, etc.
    transformedOutput
}
```
!!! tip
    There are also pre-defined nodes that you can use in your agent strategy. To learn more, see [Predefined nodes and components](nodes-and-components.md).

Edges define the connections between nodes.

```kotlin
// Basic edge
edge(sourceNode forwardTo targetNode)

// Edge with condition
edge(sourceNode forwardTo targetNode onCondition { output ->
    // Return true to follow this edge, false to skip it
    output.contains("specific text")
})

// Edge with transformation
edge(sourceNode forwardTo targetNode transformed { output ->
    // Transform the output before passing it to the target node
    "Modified: $output"
})

// Combined condition and transformation
edge(sourceNode forwardTo targetNode onCondition { it.isNotEmpty() } transformed { it.uppercase() })
```
#### 3.2. Implement the strategy

To implement the agent strategy, call the `strategy` function and define nodes and edges. For example:

```kotlin
val agentStrategy = strategy("Simple calculator") {
    // Define nodes for the strategy
    val nodeSendInput by nodeLLMRequest()
    val nodeExecuteTool by nodeExecuteTool()
    val nodeSendToolResult by nodeLLMSendToolResult()

    // Define edges between nodes
    // Start -> Send input
    edge(nodeStart forwardTo nodeSendInput)

    // Send input -> Finish
    edge(
        (nodeSendInput forwardTo nodeFinish)
                transformed { it }
                onAssistantMessage { true }
    )

    // Send input -> Execute tool
    edge(
        (nodeSendInput forwardTo nodeExecuteTool)
                onToolCall { true }
    )

    // Execute tool -> Send the tool result
    edge(nodeExecuteTool forwardTo nodeSendToolResult)

    // Send the tool result -> finish
    edge(
        (nodeSendToolResult forwardTo nodeFinish)
                transformed { it }
                onAssistantMessage { true }
    )
}
```
!!! tip
    The `strategy` function lets you define multiple subgraphs, each containing its own set of nodes and edges.
    This approach offers more flexibility and functionality compared to using simplified strategy builders.
    To learn more about subgraphs, see [Subgraphs](subgraphs-overview.md).

### 4. Configure the agent

Define agent behavior with a configuration:

```kotlin
val agentConfig = AIAgentConfig.withSystemPrompt(
    prompt = """
        You are a simple calculator assistant.
        You can add two numbers together using the calculator tool.
        When the user provides input, extract the numbers they want to add.
        The input might be in various formats like "add 5 and 7", "5 + 7", or just "5 7".
        Extract the two numbers and use the calculator tool to add them.
        Always respond with a clear, friendly message showing the calculation and result.
        """.trimIndent()
)
```

For more advanced configuration, you can specify which LLM the agent will use and set the maximum number of iterations the agent can perform to respond:

```kotlin
val agentConfig = AIAgentConfig(
    prompt = Prompt.build("simple-calculator") {
        system(
            """
                You are a simple calculator assistant.
                You can add two numbers together using the calculator tool.
                When the user provides input, extract the numbers they want to add.
                The input might be in various formats like "add 5 and 7", "5 + 7", or just "5 7".
                Extract the two numbers and use the calculator tool to add them.
                Always respond with a clear, friendly message showing the calculation and result.
                """.trimIndent()
        )
    },
    model = OpenAIModels.Chat.GPT4o,
    maxAgentIterations = 10
)
```

### 5. Implement tools and set up a tool registry

Tools let your agent perform specific tasks.
To make a tool available for the agent, add it to a tool registry.
For example:

```kotlin
// Implement a simple calculator tool that can add two numbers
@LLMDescription("Tools for performing basic arithmetic operations")
class CalculatorTools : ToolSet {
    @Tool
    @LLMDescription("Add two numbers together and return their sum")
    fun add(
        @LLMDescription("First number to add (integer value)")
        num1: Int,

        @LLMDescription("Second number to add (integer value)")
        num2: Int
    ): String {
        val sum = num1 + num2
        return "The sum of $num1 and $num2 is: $sum"
    }
}

// Add the tool to the tool registry
val toolRegistry = ToolRegistry {
    tools(CalculatorTools())
}
```

To learn more about tools, see [Tools](tools-overview.md).

### 6. Install features

Features let you add new capabilities to the agent, modify its behavior, provide access to external systems and resources,
and log and monitor events while the agent is running.
The following features are available:

- EventHandler
- AgentMemory
- Tracing

To install the feature, call the `install` function and provide the feature as an argument.
For example, to install the event handler feature, you need to do the following:

```kotlin
installFeatures = {
    // install the EventHandler feature
    install(EventHandler) {
        onBeforeAgentStarted { strategy: AIAgentStrategy, agent: AIAgent ->
            println("Starting strategy: ${strategy.name}")
        }
        onAgentFinished { strategyName: String, result: String? ->
            println("Result: $result")
        }
    }
}
```

To learn more about feature configuration, see the dedicated page.

### 7. Run the agent

Create the agent with the configuration option created in the previous stages and run it with the provided input:

```kotlin
val agent = AIAgent(
    promptExecutor = promptExecutor,
    strategy = agentStrategy,
    agentConfig = agentConfig,
    toolRegistry = toolRegistry,
    installFeatures = {
        install(EventHandler) {
            onBeforeAgentStarted = { strategy: AIAgentStrategy, agent: AIAgent ->
                println("Starting strategy: ${strategy.name}")
            }
            onAgentFinished = { strategyName: String, result: String? ->
                println("Result: $result")
            }
        }
    }
)

suspend fun main() = runBlocking {
    println("Enter two numbers to add (e.g., 'add 5 and 7' or '5 + 7'):")

    // Read the user input and send it to the agent
    val userInput = readlnOrNull() ?: ""
    agent.run(userInput)
}
```

## Working with structured data

The `AIAgent` can process structured data from LLM outputs. For more details, see [Streaming API](streaming-api.md).

## Using parallel tool calls

The `AIAgent` supports parallel tool calls. This feature lets you process multiple tools concurrently, improving performance for independent operations.

For more details, see [Parallel tool calls](tools-overview.md#parallel-tool-calls).

## Full code sample

Here is the complete implementation of the agent:

```kotlin
// Use the OpenAI executor with an API key from an environment variable
val promptExecutor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY"))

// Create a simple strategy
val agentStrategy = strategy("Simple calculator") {
    // Define nodes for the strategy
    val nodeSendInput by nodeLLMRequest()
    val nodeExecuteTool by nodeExecuteTool()
    val nodeSendToolResult by nodeLLMSendToolResult()

    // Define edges between nodes
    // Start -> Send input
    edge(nodeStart forwardTo nodeSendInput)

    // Send input -> Finish
    edge(
        (nodeSendInput forwardTo nodeFinish)
                transformed { it }
                onAssistantMessage { true }
    )

    // Send input -> Execute tool
    edge(
        (nodeSendInput forwardTo nodeExecuteTool)
                onToolCall { true }
    )

    // Execute tool -> Send the tool result
    edge(nodeExecuteTool forwardTo nodeSendToolResult)

    // Send the tool result -> finish
    edge(
        (nodeSendToolResult forwardTo nodeFinish)
                transformed { it }
                onAssistantMessage { true }
    )
}

// Configure the agent
val agentConfig = AIAgentConfig(
    prompt = Prompt.build("simple-calculator") {
        system(
            """
                You are a simple calculator assistant.
                You can add two numbers together using the calculator tool.
                When the user provides input, extract the numbers they want to add.
                The input might be in various formats like "add 5 and 7", "5 + 7", or just "5 7".
                Extract the two numbers and use the calculator tool to add them.
                Always respond with a clear, friendly message showing the calculation and result.
                """.trimIndent()
        )
    },
    model = OpenAIModels.Chat.GPT4o,
    maxAgentIterations = 10
)

// Implement a simple calculator tool that can add two numbers
@LLMDescription("Tools for performing basic arithmetic operations")
class CalculatorTools : ToolSet {
    @Tool
    @LLMDescription("Add two numbers together and return their sum")
    fun add(
        @LLMDescription("First number to add (integer value)")
        num1: Int,

        @LLMDescription("Second number to add (integer value)")
        num2: Int
    ): String {
        val sum = num1 + num2
        return "The sum of $num1 and $num2 is: $sum"
    }
}

// Add the tool to the tool registry
val toolRegistry = ToolRegistry {
    tools(CalculatorTools())
}

// Create the agent
val agent = AIAgent(
    promptExecutor = promptExecutor,
    strategy = agentStrategy,
    agentConfig = agentConfig,
    toolRegistry = toolRegistry,
    installFeatures = {
        // install the EventHandler feature
        install(EventHandler) {
            onBeforeAgentStarted { strategy: AIAgentStrategy, agent: AIAgent ->
                println("Starting strategy: ${strategy.name}")
            }
            onAgentFinished { strategyName: String, result: String? ->
                println("Result: $result")
            }
        }
    }
)

suspend fun main() = runBlocking {
    println("Enter two numbers to add (e.g., 'add 5 and 7' or '5 + 7'):")

    val userInput = readlnOrNull() ?: ""
    agent.run(userInput)
}
```
