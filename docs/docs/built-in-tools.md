# Built-in tools

The Koog framework provides built-in tools that handle common scenarios of agent-user interaction.

The following built-in tools are available:

| Tool              | <div style="width:115px">Name</div> | Description                                                                                                              |
|-------------------|-------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| SayToUser         | `__say_to_user__`                   | Lets the agent send a message to the user. It prints the agent message to the console with the `Agent says: ` prefix.    |
| AskUser           | `__ask_user__`                      | Lets the agent ask the user for input. It prints the agent message to the console and waits for user response.           |
| ExitTool          | `__exit__`                          | Lets the agent finish the conversation and terminate the session.                                                        |
| ReadFileTool      | `__read_file__`                     | Reads text file with optional line range selection. Returns formatted content with metadata using 0-based line indexing. |
| EditFileTool      | `__edit_file__`                     | Makes a single, targeted text replacement in a file; can also create new files or fully replace contents.                |
| ListDirectoryTool | `__list_directory__`                | Lists directory contents as a hierarchical tree with optional depth control and glob filtering.                          |
| WriteFileTool     | `__write_file__`                    | Writes text content to a file (creating parent directories if needed).                                                   |


## Registering built-in tools

Like any other tool, a built-in tool must be added to the tool registry to become available for an agent. Here is an example:

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.ExitTool
import ai.koog.agents.ext.tool.file.ListDirectoryTool
import ai.koog.agents.ext.tool.file.ReadFileTool
import ai.koog.agents.ext.tool.file.WriteFileTool
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.rag.base.files.JVMFileSystemProvider

const val apiToken = ""

-->
```kotlin
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
<!--- KNIT example-built-in-tools-01.kt -->

You can create a comprehensive set of capabilities for your agent by combining built-in tools and custom tools within the same registry.
To learn more about custom tools, see [Annotation-based tools](annotation-based-tools.md) and [Class-based tools](class-based-tools.md).
