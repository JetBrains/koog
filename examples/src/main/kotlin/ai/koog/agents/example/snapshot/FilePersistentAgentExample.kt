package ai.koog.agents.example.snapshot

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.snapshot.feature.Persistency
import ai.koog.agents.snapshot.providers.file.JVMFilePersistencyStorageProvider
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.uuid.ExperimentalUuidApi

/**
 * This example demonstrates how to use the file-based checkpoint provider with a persistent agent.
 *
 * The JVMFileAgentCheckpointStorageProvider stores agent checkpoints in a file system,
 * allowing the agent's state to persist across multiple runs.
 *
 * This example shows:
 * 1. How to create a file-based checkpoint provider
 * 2. How to configure an agent with the file-based checkpoint provider
 * 3. How to run an agent that automatically creates checkpoints
 * 4. How to restore an agent from a checkpoint
 */
@OptIn(ExperimentalUuidApi::class)
fun main() = runBlocking {
    // Create a prompt executor for the agent
    val executor: PromptExecutor = simpleOllamaAIExecutor()

    // Create a temporary directory for storing checkpoints
    val checkpointDir = Files.createTempDirectory("agent-checkpoints")
    println("Checkpoint directory: $checkpointDir")

    // Create the file-based checkpoint provider
    val provider = JVMFilePersistencyStorageProvider(checkpointDir)

    // Create a unique agent ID to identify this agent's checkpoints
    val agentId = "persistent-agent-example"

    // Create tool registry with basic tools
    val toolRegistry = ToolRegistry {
        tool(AskUser)
        tool(SayToUser)
    }

    // Create agent config with a system prompt
    val agentConfig = AIAgentConfig(
        prompt = prompt("persistent-agent") {
            system("You are a helpful assistant that remembers conversations across sessions.")
        },
        model = OllamaModels.Meta.LLAMA_3_2,
        maxAgentIterations = 20
    )

    println("Creating and running agent with continuous persistence...")

    // Create the agent with the file-based checkpoint provider and continuous persistence
    val agent = AIAgent(
        promptExecutor = executor,
        strategy = SnapshotStrategy.strategy,
        agentConfig = agentConfig,
        toolRegistry = toolRegistry,
        id = agentId
    ) {
        install(Persistency) {
            storage = provider // Use the file-based checkpoint provider
            enableAutomaticPersistency = true // Enable automatic checkpoint creation
        }
    }

    // Run the agent with an initial input
    val result = agent.run("Hello, can you help me with a task?")
    println("Agent result: $result")

    // Retrieve all checkpoints created during the agent's execution
    // Note: In real usage, you would get this from the agent context
    // For this example, we'll create a mock context or use the agent's context
    println("\nCheckpoints are now context-aware - they're automatically filtered by agent context")

    // Checkpoint details are now managed automatically by the agent context
    println("Checkpoint management is now handled transparently!")

    // Verify that the checkpoint files exist in the file system
    val checkpointsDir = checkpointDir.resolve("checkpoints").resolve(agentId)
    if (checkpointsDir.exists()) {
        println("\nCheckpoint files in directory: ${checkpointsDir.toFile().list()?.joinToString()}")
    }

    println("\nNow creating a new agent instance with the same ID to demonstrate restoration...")

    // Create a new agent instance with the same ID
    // It will automatically restore from the latest checkpoint
    val restoredAgent = AIAgent(
        promptExecutor = executor,
        strategy = SnapshotStrategy.strategy,
        agentConfig = agentConfig,
        toolRegistry = toolRegistry,
        id = agentId
    ) {
        install(Persistency) {
            storage = provider // Use the file-based checkpoint provider
            enableAutomaticPersistency = true // Enable automatic checkpoint creation
        }
    }

    // Run the restored agent with a new input
    // The agent will continue the conversation from where it left off
    val restoredResult = restoredAgent.run("Now I need help with my project.")
    println("Restored agent result: $restoredResult")

    // The latest checkpoint is automatically retrieved during agent startup
    // and is context-aware, so each agent only sees its own checkpoints
    println("\nCheckpoints are now automatically managed with context awareness!")
    println("Each agent instance only accesses its own checkpoints.")
}
