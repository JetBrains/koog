# Skills usage

Koog skills let an agent discover reusable capability bundles from the filesystem and expose them to the model through a generated prompt section.

At a high level, usage has three parts:

1. Discover skills from one or more root directories.
2. Generate a skills prompt block from discovered metadata.
3. Add that generated block to the agent `system` prompt and provide tools the agent can use to inspect files and execute skill scripts.

## Example: Adding skills to system prompt

```kotlin

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.file.ListDirectoryTool
import ai.koog.agents.ext.tool.file.ReadFileTool
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.rag.base.files.JVMFileSystemProvider
import ai.koog.skills.discovery.discoverSkills
import ai.koog.skills.prompt.SkillsPromptFormat
import ai.koog.skills.prompt.generateSkillsPrompt
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val skillsRoot = "/absolute/path/to/skills"
    val discoveredSkills = discoverSkills(JVMFileSystemProvider.ReadOnly, listOf(skillsRoot))
    val generatedSkillsPrompt = generateSkillsPrompt(discoveredSkills, SkillsPromptFormat.XML)

    // Replace with your script execution tool implementation.
    val apiKey = System.getenv("OPENAI_API_KEY")
        ?: error("The API key is not set.")

    val agent = AIAgent(
        promptExecutor = simpleOpenAIExecutor(System.getenv("YOUR_API_KEY")),
        systemPrompt = """
                You are a careful assistant.
                Use the available skills listed below.
                Before using a skill script, disclose the skills by listing and reading files with tools.

                $generatedSkillsPrompt
                """.trimIndent(),
        llmModel = OpenAIModels.Chat.GPT4o,
        toolRegistry = ToolRegistry {
            tool(ListDirectoryTool(JVMFileSystemProvider.ReadOnly))
            tool(ReadFileTool(JVMFileSystemProvider.ReadOnly))
            // Additional tools...
        },
    )
}

```
<!--- KNIT example-skills-usage-01.kt -->

## Required pieces

- `discoverSkills(...)` scans the configured directories and returns discovered skill descriptors.
- `generateSkillsPrompt(...)` converts discovered skills into prompt text (`SkillsPromptFormat.XML` is a common choice).
- The generated text should be embedded into the agent `system` prompt so the model can reason about available skills.
- The tool registry must include tools needed by your workflow, typically:
  - file discovery/read tools (for transparent skill disclosure),
  - one or more execution tools used to execute skill scripts.

## Behavior expectations

When the skills prompt is present and matching tools are registered, the agent can:

- Discover skill files,
- Read skill definitions,
- Run execution tools for relevant tasks (for example, running python scripts with appropriate arguments).

See [Agent Skills](https://agentskills.io/home) documentation for details.


## Practical tips

- Keep skills in a dedicated directory and pass absolute paths in runtime environments where relative roots may vary.
- Use a read-only file provider for discovery when skills are static (for example, `JVMFileSystemProvider.ReadOnly`).
- Keep script-execution tools narrow and type-safe (structured args/result), and validate script path handling.
