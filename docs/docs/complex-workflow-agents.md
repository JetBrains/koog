# Complex workflow agents

In addition to basic agents, the `AIAgent` class lets you build agents that handle complex workflows by defining 
custom strategies, tools, configurations, and custom input/output types.

!!! tip
    If you are new to Koog and want to create the simplest agent, start with [Basic agents](basic-agents.md).

The process of creating and configuring such an agent typically includes the following steps:

1. Provide a prompt executor to communicate with the LLM.
2. Define a strategy that controls the agent workflow.
3. Configure agent behavior.
4. Implement tools for the agent to use.
5. Add optional features like event handling, memory, or tracing.
6. Run the agent with user input.

## Prerequisites

- You have a valid API key from the LLM provider used to implement an AI agent. For a list of all available providers, see [LLM providers](llm-providers.md).

!!! tip
    Use environment variables or a secure configuration management system to store your API keys.
    Avoid hardcoding API keys directly in your source code.

## Creating a complex workflow agent

### 1. Add dependencies

To use the `AIAgent` functionality, include all necessary dependencies in your build configuration:

```
dependencies {
    implementation("ai.koog:koog-agents:VERSION")
}
```

For all available installation methods, see [Install Koog](getting-started.md#install-koog).

### 2. Provide a prompt executor

Prompt executors manage and run prompts.
You can choose a prompt executor based on the LLM provider you plan to use.
Also, you can create a custom prompt executor using one of the available LLM clients.
To learn more, see [Prompt executors](prompt-api.md#running-prompts-with-prompt-executors).

For example, to provide the OpenAI prompt executor, you need to call the `simpleOpenAIExecutor` function and provide it with the API key required for authentication with the OpenAI service:

<!--- INCLUDE
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor

const val token = ""
-->
```kotlin
val promptExecutor = simpleOpenAIExecutor(token)
```
<!--- KNIT example-complex-workflow-agents-01.kt -->

To create a prompt executor that works with multiple LLM providers, do the following:

1) Configure clients for the required LLM providers with the corresponding API keys. For example:
<!--- INCLUDE
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
-->
```kotlin
val openAIClient = OpenAILLMClient(System.getenv("OPENAI_KEY"))
val anthropicClient = AnthropicLLMClient(System.getenv("ANTHROPIC_KEY"))
val googleClient = GoogleLLMClient(System.getenv("GOOGLE_KEY"))
```
<!--- KNIT example-complex-workflow-agents-02.kt -->
2) Pass the configured clients to the `DefaultMultiLLMPromptExecutor` class constructor to create a prompt executor with multiple LLM providers:
<!--- INCLUDE
import ai.koog.agents.example.exampleComplexWorkflowAgents02.anthropicClient
import ai.koog.agents.example.exampleComplexWorkflowAgents02.googleClient
import ai.koog.agents.example.exampleComplexWorkflowAgents02.openAIClient
import ai.koog.prompt.executor.llms.all.DefaultMultiLLMPromptExecutor
-->
```kotlin
val multiExecutor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient, googleClient)
```
<!--- KNIT example-complex-workflow-agents-03.kt -->

### 3. Define a strategy

A strategy defines the workflow of your agent by using nodes and edges. It can have arbitrary input and output types, 
which can be specified in `strategy` function generic parameters. These will be input/output types of the `AIAgent` as well.
Default type for both input and output is `String`.

!!! tip
    To learn more about strategies, see [Custom strategy graphs](custom-strategy-graphs.md)

#### 3.1. Understand nodes and edges

Nodes and edges are the building blocks of the strategy.

Nodes represent processing steps in your agent strategy.

<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.strategy
class InputType

class OutputType

val transformedOutput = OutputType()
val strategy = strategy<InputType, OutputType>("Simple calculator") {
-->
<!--- SUFFIX
}
-->
```kotlin
val processNode by node<InputType, OutputType> { input ->
    // Process the input and return an output
    // You can use llm.writeSession to interact with the LLM
    // You can call tools using callTool, callToolRaw, etc.
    transformedOutput
}
```
<!--- KNIT example-complex-workflow-agents-04.kt -->

!!! tip
    There are also pre-defined nodes that you can use in your agent strategy. To learn more, see [Predefined nodes and components](nodes-and-components.md).

Edges define the connections between nodes.

<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy

const val transformedOutput = "transformed-output"

val strategy = strategy<String, String>("Simple calculator") {

    val sourceNode by node<String, String> { input ->
        // Process the input and return an output
        // You can use llm.writeSession to interact with the LLM
        // You can call tools using callTool, callToolRaw, etc.
        transformedOutput
    }

    val targetNode by node<String, String> { input ->
        // Process the input and return an output
        // You can use llm.writeSession to interact with the LLM
        // You can call tools using callTool, callToolRaw, etc.
        transformedOutput
    }
-->
<!--- SUFFIX
}
-->
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
<!--- KNIT example-complex-workflow-agents-05.kt -->

#### 3.2. Implement the strategy

To implement the agent strategy, call the `strategy` function and define nodes and edges. For example:

<!--- INCLUDE
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.*
-->
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
<!--- KNIT example-complex-workflow-agents-06.kt -->
!!! tip
    The `strategy` function lets you define multiple subgraphs, each containing its own set of nodes and edges.
    This approach offers more flexibility and functionality compared to using simplified strategy builders.
    To learn more about subgraphs, see [Subgraphs](subgraphs-overview.md).

### 4. Configure the agent

Define agent behavior with a configuration:
<!--- INCLUDE
import ai.koog.agents.core.agent.config.AIAgentConfig
-->
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
<!--- KNIT example-complex-workflow-agents-07.kt -->

For more advanced configuration, you can specify which LLM the agent will use and set the maximum number of iterations the agent can perform to respond:
<!--- INCLUDE
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
-->
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
<!--- KNIT example-complex-workflow-agents-08.kt -->

### 5. Implement tools and set up a tool registry

Tools let your agent perform specific tasks.
To make a tool available for the agent, add it to a tool registry.
For example:
<!--- INCLUDE
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.tools
-->
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
<!--- KNIT example-complex-workflow-agents-09.kt -->

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
<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.feature.handler.agent.AgentCompletedContext
import ai.koog.agents.core.feature.handler.agent.AgentStartingContext
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.OllamaModels

val agent = AIAgent(
    promptExecutor = simpleOllamaAIExecutor(),
    llmModel = OllamaModels.Meta.LLAMA_3_2,
-->
<!--- SUFFIX
)
-->
```kotlin
// install the EventHandler feature
installFeatures = {
    install(EventHandler) {
        onAgentStarting { eventContext: AgentStartingContext ->
            println("Starting agent: ${eventContext.agent.id}")
        }
        onAgentCompleted { eventContext: AgentCompletedContext ->
            println("Result: ${eventContext.result}")
        }
    }
}
```
<!--- KNIT example-complex-workflow-agents-10.kt -->

To learn more about feature configuration, see the dedicated page.

### 7. Run the agent

Create the agent with the configuration option created in the previous stages and run it with the provided input:
<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.feature.handler.agent.AgentCompletedContext
import ai.koog.agents.core.feature.handler.agent.AgentStartingContext
import ai.koog.agents.example.exampleComplexWorkflowAgents01.promptExecutor
import ai.koog.agents.example.exampleComplexWorkflowAgents06.agentStrategy
import ai.koog.agents.example.exampleComplexWorkflowAgents07.agentConfig
import ai.koog.agents.example.exampleComplexWorkflowAgents09.toolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import kotlinx.coroutines.runBlocking
-->
```kotlin
val agent = AIAgent(
    promptExecutor = promptExecutor,
    toolRegistry = toolRegistry,
    strategy = agentStrategy,
    agentConfig = agentConfig,
    installFeatures = {
        install(EventHandler) {
            onAgentStarting { eventContext: AgentStartingContext ->
                println("Starting agent: ${eventContext.agent.id}")
            }
            onAgentCompleted { eventContext: AgentCompletedContext ->
                println("Result: ${eventContext.result}")
            }
        }
    }
)

fun main() {
    runBlocking {
        println("Enter two numbers to add (e.g., 'add 5 and 7' or '5 + 7'):")

        // Read the user input and send it to the agent
        val userInput = readlnOrNull() ?: ""
        val agentResult = agent.run(userInput)
        println("The agent returned: $agentResult")
    }
}
```
<!--- KNIT example-complex-workflow-agents-11.kt -->

## Working with structured data

The `AIAgent` can process structured data from LLM outputs. For more details, see [Structured data processing](structured-output.md).

## Using parallel tool calls

The `AIAgent` supports parallel tool calls. This feature lets you process multiple tools concurrently, improving performance for independent operations.

For more details, see [Parallel tool calls](tools-overview.md#parallel-tool-calls).

## Full code sample

Here is the complete implementation of the agent:
<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.*
import ai.koog.agents.core.feature.handler.agent.AgentCompletedContext
import ai.koog.agents.core.feature.handler.agent.AgentStartingContext
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking

-->
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
    toolRegistry = toolRegistry,
    strategy = agentStrategy,
    agentConfig = agentConfig,
    installFeatures = {
        install(EventHandler) {
            onAgentStarting { eventContext: AgentStartingContext ->
                println("Starting agent: ${eventContext.agent.id}")
            }
            onAgentCompleted { eventContext: AgentCompletedContext ->
                println("Result: ${eventContext.result}")
            }
        }
    }
)

fun main() {
    runBlocking {
        println("Enter two numbers to add (e.g., 'add 5 and 7' or '5 + 7'):")

        // Read the user input and send it to the agent
        val userInput = readlnOrNull() ?: ""
        val agentResult = agent.run(userInput)
        println("The agent returned: $agentResult")
    }
}
```
<!--- KNIT example-complex-workflow-agents-12.kt -->
