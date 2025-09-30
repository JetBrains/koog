# SimpleAPI Quickstart Guide

## Overview

The SimpleAPI provides an easy way to create and run AI agents using the JetBrains AI API. It offers a simplified interface for creating chat agents and single-run agents with customizable tools and configurations.

## Prerequisites

- JetBrains AI API token
- Kotlin project with coroutine support

## Installation

Add the Koog dependencies to your project:

```kotlin
dependencies {
    implementation("ai.koog:agents-core:VERSION")
    // Other dependencies as needed
}
```

## Basic Usage
### Creating a Simple Agent

A single-run agent processes a single input and provides a response:

```kotlin
fun main() = runBlocking {
    val apiToken = "YOUR_API_TOKEN"

    val agent = AIAgent(
        executor = simpleOpenAIExecutor("API_TOKEN"),
        systemPrompt = "You are a code assistant. Provide concise code examples."
    )

    agent.run("Write a Kotlin function to calculate factorial")
}
```

Please note that the single-run agent doesn't have any tools by default.

## Configuration Options

`AIAgent` accept the following parameters:

- `executor` (required): Your LLM prompt executor
- `systemPrompt`: Initial system prompt for the agent (default: empty string)
- `llmModel`: LLM model to use (default: OpenAIModels.Chat.GPT4o)
- `temperature`: Temperature for LLM generation (default: 1.0)
- `eventHandler`: Custom event handler (default: empty handler)
- `toolRegistry`: Custom tool registry (default: built-in tools for chat agent, empty for single-run agent)
- `maxIterations`: Maximum number of agent iterations (default: 50)

## Available Tools

### Built-in Tools

The SimpleAPI provides the following built-in tools:

1. **SayToUser**: Allows the agent to output a message to the user
   - Prints the agent's message to the console with "Agent says: " prefix
   - Tool name: `__say_to_user__`

2. **AskUser**: Allows the agent to ask the user for input
   - Prints the agent's message to the console
   - Reads user input and returns it to the agent
   - Tool name: `__ask_user__`

3. **ExitTool**: Allows the agent to end the conversation
   - Used in chat agents to terminate the session
   - Tool name: `__exit__`

### Custom Tools

You can create custom tools by extending the `SimpleTool` class:

```kotlin
object CalculatorTool : SimpleTool<CalculatorToolArgs>() {
    @Serializable
    data class Args(val expression: String)

    override val argsSerializer = Args.serializer()

    override val descriptor = ToolDescriptor(
        name = "calculator",
        description = "Evaluates a mathematical expression",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "expression",
                description = "Mathematical expression to evaluate",
                type = ToolParameterType.String
            )
        )
    )

    override suspend fun doExecute(args: Args): String {
        // Implement expression evaluation
        return "Result: ${evaluateExpression(args.expression)}"
    }

    private fun evaluateExpression(expression: String): Double {
        // Simple implementation for demonstration
        return expression.toDoubleOrNull() ?: 0.0
    }
}
```

### Registering Custom Tools

Register custom tools when creating an agent:

```kotlin
val toolRegistry = ToolRegistry {
    tool(CalculatorTool)
    // Add more tools as needed
}

val agent = AIAgent(
    executor = simpleOpenAIExecutor("API_TOKEN"),
    systemPrompt = "You are a helpful assistant with calculator capabilities.",
    toolRegistry = toolRegistry
)
```

## Best Practices

1. **System Prompts**: Provide clear and concise system prompts to guide the agent's behavior.

2. **Error Handling**: Implement proper error handling in custom tools to prevent agent failures.

3. **Tool Design**: Design tools with clear descriptions and parameter names to help the LLM understand how to use them.

4. **Resource Management**: Use appropriate coroutine scopes and cancel them when no longer needed to avoid resource leaks.

5. **API Token Security**: Never hardcode API tokens in your code. Use environment variables or secure configuration management.

## Example: Creating a Code Assistant

```kotlin
object GenerateCodeTool : SimpleTool<GenerateCodeToolArgs>() {
    @Serializable
    data class Args(val language: String, val task: String)

    override val argsSerializer = Args.serializer()

    override val descriptor = ToolDescriptor(
        name = "generate_code",
        description = "Generates code in the specified language for the given task",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "language",
                description = "Programming language (e.g., Kotlin, Java, Python)",
                type = ToolParameterType.String
            ),
            ToolParameterDescriptor(
                name = "task",
                description = "Description of the coding task",
                type = ToolParameterType.String
            )
        )
    )

    override suspend fun doExecute(args: Args): String {
        // In a real implementation, this might call another API or service
        return "Generated code for ${args.task} in ${args.language}"
    }
}

fun main() = runBlocking {
    val apiToken = System.getenv("LLM_API_TOKEN")
    val coroutineScope = CoroutineScope(Dispatchers.Default)

    val toolRegistry = ToolRegistry {
        tool(GenerateCodeTool)
    }

    val agent = AIAgent(
        executor = simpleOpenAIExecutor("API_TOKEN"),
        systemPrompt = "You are a code assistant. Use the generate_code tool to create code examples.",
        toolRegistry = toolRegistry
    )

    agent.run("I need help creating a function to sort a list in Kotlin")

    // Wait for the agent to complete
    delay(Long.MAX_VALUE)
}
```
