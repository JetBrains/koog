# Built-in tools

The Koog framework provides built-in tools that handle common scenarios of agent-user interaction.

The following built-in tools are available:

| Tool      | <div style="width:115px">Name</div> | Description                                                                                                           |
|-----------|-------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| SayToUser | `__say_to_user__`                   | Lets the agent send a message to the user. It prints the agent message to the console with the `Agent says: ` prefix. |
| AskUser   | `__ask_user__`                      | Lets the agent ask the user for input. It prints the agent message to the console and waits for user response.        |
| ExitTool  | `__exit__`                          | Lets the agent finish the conversation and terminate the session.                                                     |


## Registering built-in tools

Like any other tool, a built-in tool must be added to the tool registry to become available for an agent. Here is an example:

```kotlin
// Create a tool registry with all built-in tools
val toolRegistry = ToolRegistry {
    tool(SayToUser())
    tool(AskUser())
    tool(ExitTool())
}

// Pass the registry when creating an agent
val agent = AIAgent(
    executor = simpleOpenAIExecutor(apiToken),
    systemPrompt = "You are a helpful assistant.",
    llmModel = OpenAIModels.Chat.GPT4o,
    toolRegistry = toolRegistry
)
```

You can create a comprehensive set of capabilities for your agent by combining built-in tools and custom tools within the same registry.
To learn more about custom tools, see [Annotation-based tools](annotation-based-tools.md) and [Advanced implementation](advanced-tool-implementation.md).
