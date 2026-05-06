package ai.koog.agents.example.contextawaretool

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.SimpleContextAwareTool
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.serialization.typeToken
import kotlinx.serialization.Serializable

/**
 * Demonstrates a context-aware tool: a [Tool][ai.koog.agents.core.tools.Tool] subclass that also
 * implements [AIAgentContextAwareTool] so it receives the current [AIAgentContext] alongside its
 * typed arguments.
 *
 * The [NoteTool] in this example uses [AIAgentContext.storage] to remember notes across multiple
 * tool invocations within the same agent run, and reads [AIAgentContext.agentId] to tag each note.
 *
 * Run with `./gradlew runExampleContextAwareTool` (requires a local Ollama instance running at
 * `http://localhost:11434`; pull the model first with `ollama pull llama3.2`).
 */
suspend fun main() {
    val toolRegistry = ToolRegistry {
        tool(SayToUser)
        tool(NoteTool())
    }

    val agentConfig = AIAgentConfig(
        prompt = prompt("notes-demo") {
            system(
                """
                    You help the user keep short notes.
                    Use the `note` tool with action="add" to record a note, action="list" to read them
                    back, and `__say_to_user__` to talk to the user.
                """.trimIndent()
            )
        },
        model = OllamaModels.Meta.LLAMA_3_2,
        maxAgentIterations = 20,
    )

    simpleOllamaAIExecutor("http://localhost:11434").use { executor ->
        val agent = AIAgent(
            promptExecutor = executor,
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
            id = "notes-agent",
        ) {
            handleEvents {
                onToolCallStarting { e -> println("[tool] ${e.toolName} args=${e.toolArgs}") }
                onAgentCompleted { e -> println("[done] ${e.result}") }
            }
        }

        agent.run("Take two notes for me: 'buy milk' and 'finish the plan', then list everything.")
    }
}

private val notesKey = AIAgentStorageKey<MutableList<String>>("notes")

/**
 * A context-aware tool. Subclassing [SimpleContextAwareTool] gives this tool access to the
 * current [AIAgentContext] while keeping the same one-method shape as a regular tool — only the
 * context-aware [execute] needs to be implemented.
 */
private class NoteTool : SimpleContextAwareTool<NoteTool.Args>(
    argsType = typeToken<Args>(),
    name = "note",
    description = "Stores or lists short notes scoped to the current agent run.",
) {

    @Serializable
    data class Args(val action: String, val text: String? = null)

    override suspend fun execute(args: Args, context: AIAgentContext): String {
        val notes = context.storage.get(notesKey) ?: mutableListOf<String>().also {
            context.storage.set(notesKey, it)
        }
        return when (args.action) {
            "add" -> {
                val text = args.text ?: return "Missing 'text' argument."
                notes += "[${context.agentId}] $text"
                "Recorded note (#${notes.size})."
            }
            "list" -> if (notes.isEmpty()) "No notes yet." else notes.joinToString("\n")
            else -> "Unknown action '${args.action}'. Expected 'add' or 'list'."
        }
    }
}
