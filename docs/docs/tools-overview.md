# Overview

 Agents use tools to perform specific tasks or access external systems.

## Tool workflow

The Koog framework offers the following workflow for working with tools:

1. Create a custom tool or use one of the built-in tools.
2. Add the tool to a tool registry.
3. Pass the tool registry to an agent.
4. Use the tool with the agent.

### Available tool types

There are three types of tools in the Koog framework:

- Built-in tools that provide functionality for agent-user interaction and conversation management. For details, see [Built-in tools](built-in-tools.md).
- Annotation-based custom tools that let you expose functions as tools to LLMs. For details, see [Annotation-based tools](annotation-based-tools.md).
- Custom tools that are created using the advanced API and let you control tool parameters, metadata, execution logic, and how it is registered and invoked. For details, see [Advanced
  implementation](advanced-tool-implementation.md).

### Tool registry

Before you can use a tool in an agent, you must add it to a tool registry.
The tool registry manages all tools available to the agent.

The key features of the tool registry:

- Organizes tools.
- Supports merging of multiple tool registries.
- Provides methods to retrieve tools by name or type.

To learn more, see [ToolRegistry](https://api.koog.ai/agents/agents-tools/ai.koog.agents.core.tools/-tool-registry/index.html).

Here is an example of how to create the tool registry and add the tool to it:

```kotlin
val toolRegistry = ToolRegistry {
    tool(SayToUser)
}
```

To merge multiple tool registries, do the following:

```kotlin
val firstToolRegistry = ToolRegistry {
    tool(FirstSampleTool())
}

val secondToolRegistry = ToolRegistry {
    tool(SecondSampleTool())
}

val newRegistry = firstToolRegistry + secondToolRegistry
```

### Passing tools to an agent

To enable an agent to use a tool, you need to provide a tool registry that contains this tool as an argument when creating the agent:

```kotlin
// Agent initialization
val agent = AIAgent(
    executor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
    systemPrompt = "You are a helpful assistant with strong mathematical skills.",
    llmModel = OpenAIModels.Chat.GPT4o,
    // Pass your tool registry to the agent
    toolRegistry = toolRegistry
)
```

### Calling tools

There are several ways to call tools within your agent code. The recommended approach is to use the provided methods
in the agent context rather than calling tools directly, as this ensures proper handling of tool operation within the
agent environment.

!!! tip
    Ensure you have implemented proper [error handling](agent-events.md) in your tools to prevent agent failure.

The tools are called within a specific session context represented by `AIAgentLLMWriteSession`.
It provides several methods for calling tools so that you can:

- Call a tool with the given arguments.
- Call a tool by its name and the given arguments.
- Call a tool by the provided tool class and arguments.
- Call a tool of the specified type with the given arguments.
- Call a tool that returns a raw string result.

For more details, see [API reference](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.agent.session/-a-i-agent-l-l-m-write-session/index.html).

#### Parallel tool calls

You can also call tools in parallel using the `toParallelToolCallsRaw` extension. For example:

```kotlin
@Serializable
data class Book(
    val bookName: String,
    val author: String,
    val description: String
) : ToolArgs

/*...*/

val myNode by node<Unit, Unit> { _ ->
    llm.writeSession {
        flow {
            emit(Book("Book 1", "Author 1", "Description 1"))
        }.toParallelToolCallsRaw(BookTool::class).collect()
    }
}
```

#### Calling tools from nodes

When building agent workflows with nodes, you can use special nodes to call tools:

* **nodeExecuteTool**: calls a single tool call and returns its result. For details, see [API reference](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-execute-tool.html).

* **nodeExecuteSingleTool** that calls a specific tool with the provided arguments. For details, see [API reference](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-execute-single-tool.html).

* **nodeExecuteMultipleTools** that performs multiple tool calls and returns their results. For details, see [API reference](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-execute-multiple-tools.html).

* **nodeLLMSendToolResult** that sends a tool result to the LLM and gets a response. For details, see [API reference](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-l-l-m-send-tool-result.html).

* **nodeLLMSendMultipleToolResults** that sends multiple tool results to the LLM. For details, see [API reference](https://api.koog.ai/agents/agents-core/ai.koog.agents.core.dsl.extension/node-l-l-m-send-multiple-tool-results.html).