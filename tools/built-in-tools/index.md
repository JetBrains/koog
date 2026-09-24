# Built-in tools

Koog provides built-in tools for Kotlin and Java to help you quickly prototype and experiment with agent-user interaction. These tools are not intended for production use. To use them, add `ai.koog:agents-ext` to your dependencies. The following built-in tools are available:

| Tool              | Name                 | Description                                                                                                              |
| ----------------- | -------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| SayToUser         | `__say_to_user__`    | Lets the agent send a message to the user. It prints the agent message to the console with the `Agent says:` prefix.     |
| AskUser           | `__ask_user__`       | Lets the agent ask the user for input. It prints the agent message to the console and waits for user response.           |
| ExitTool          | `__exit__`           | Lets the agent finish the conversation and terminate the session.                                                        |
| ReadFileTool      | `__read_file__`      | Reads text file with optional line range selection. Returns formatted content with metadata using 0-based line indexing. |
| EditFileTool      | `__edit_file__`      | Makes a single, targeted text replacement in a file; can also create new files or fully replace contents.                |
| ListDirectoryTool | `__list_directory__` | Lists directory contents as a hierarchical tree with optional depth control and glob filtering.                          |
| WriteFileTool     | `__write_file__`     | Writes text content to a file (creating parent directories if needed).                                                   |

## Registering built-in tools

Like any other tool, a built-in tool must be added to the tool registry to become available for an agent. Here is an example:

```
// Create a tool registry with all built-in tools
val toolRegistry = ToolRegistry {
    tool(SayToUser)
    tool(AskUser)
    tool(ExitTool)
    tool(ReadFileTool(JVMFileSystemProvider.ReadOnly))
    tool(ListDirectoryTool(JVMFileSystemProvider.ReadOnly))
    tool(WriteFileTool(JVMFileSystemProvider.ReadWrite))
}

// Pass the registry when creating an agent
val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(apiToken),
    systemPrompt = "You are a helpful assistant.",
    llmModel = OpenAIModels.Chat.GPT4o,
    toolRegistry = toolRegistry
)
```

You can create a comprehensive set of capabilities for your agent by combining built-in tools and custom tools within the same registry in both Kotlin and Java. To learn more about custom tools, see [Annotation-based tools](../annotation-based-tools/) and [Class-based tools](../class-based-tools/).
