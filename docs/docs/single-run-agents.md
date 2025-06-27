# Single-run agents

The `AIAgent` class is the core component that lets you create AI agents in your Kotlin applications.

You can build simple agents with minimal configuration or create sophisticated agents with advanced capabilities by
defining custom strategies, tools, and configurations.

This page guides you through the steps necessary to create a single-run agent with customizable tools and configurations.

A single-run agent processes a single input and provides a response.
It operates within a single cycle of tool-calling to complete its task and provide a response.
This agent can return either a message or a tool result.
The tool result is returned if the tool registry is provided to the agent.

If your goal is to build a simple agent to experiment with, you can provide only a prompt executor and LLM when creating it.
But if you want more flexibility and customization, you can pass optional parameters to configure the agent.
To learn more about configuration options, see [API reference](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent/-a-i-agent/-a-i-agent.html).

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

### 2. Create an agent 

To create an agent, create an instance of the `AIAgent` class and provide the `executor` and `llmModel` parameters:

```kotlin
val agent = AIAgent(
    executor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
    llmModel = OpenAIModels.Chat.GPT4o
)
```

### 3. Add a system prompt

A system prompt is used to define agent behavior. To provide the prompt, use the `systemPrompt` parameter:

```kotlin
val agent = AIAgent(
    executor = simpleOpenAIExecutor(System.getenv("YOUR_API_KEY")),
    systemPrompt = "You are a helpful assistant. Answer user questions concisely.",
    llmModel = OpenAIModels.Chat.GPT4o
)
```

### 4. Configure LLM output

Provide a temperature of LLM output generation using the `temperature` parameter:

```kotlin
val agent = AIAgent(
    executor = simpleOpenAIExecutor(System.getenv("YOUR_API_KEY")),
    systemPrompt = "You are a helpful assistant. Answer user questions concisely.",
    llmModel = OpenAIModels.Chat.GPT4o,
    temperature = 0.7
)
```

### 5. Add tools

Agents use tools to complete specific tasks.
You can use the built-in tools or implement your own custom tools if needed.

To configure tools, use the `toolRegistry` parameter that defines the tools available to the agent:

```kotlin
val agent = AIAgent(
    executor = simpleOpenAIExecutor(System.getenv("YOUR_API_KEY")),
    systemPrompt = "You are a helpful assistant. Answer user questions concisely.",
    llmModel = OpenAIModels.Chat.GPT4o,
    temperature = 0.7,
    toolRegistry = ToolRegistry {
        tool(SayToUser)
    }
)
```
In the example, `SayToUser` is the built-in tool. To learn how to create a custom tool, see [Tools](tools-overview.md).

### 6. Adjust agent iterations

Provide the maximum number of steps the agent can take before it is forced to stop using the `maxIterations` parameter:

```kotlin
val agent = AIAgent(
    executor = simpleOpenAIExecutor(System.getenv("YOUR_API_KEY")),
    systemPrompt = "You are a helpful assistant. Answer user questions concisely.",
    llmModel = OpenAIModels.Chat.GPT4o,
    temperature = 0.7,
    toolRegistry = ToolRegistry {
        tool(SayToUser)
    },
    maxIterations = 30
)
```

### 7. Handle events during agent runtime

Single-run agents support custom event handlers.
While having an event handler is not required for creating an agent, it might be helpful for testing, debugging, or making hooks for chained agent interactions.

For more information on how to use the `EventHandler` feature for monitoring your agent interactions, see [Agent events](agent-events.md).

### 8. Run the agent

To run the agent, use the `run()` function:

```kotlin
val agent = AIAgent(
    executor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
    systemPrompt = "You are a helpful assistant. Answer user questions concisely.",
    llmModel = OpenAIModels.Chat.GPT4o,
    temperature = 0.7,
    toolRegistry = ToolRegistry {
        tool(SayToUser)
    },
    maxIterations = 100
)

fun main() = runBlocking {
    val result = agent.run("Hello! How can you help me?")
}
```

The agent produces the following output:

```
Agent says: Hello! I'm here to assist you with a variety of tasks. Whether you have questions, need information, or require help with specific tasks, feel free to ask. How can I assist you today?
```
