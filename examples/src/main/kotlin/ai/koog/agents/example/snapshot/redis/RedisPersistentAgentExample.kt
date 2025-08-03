package ai.koog.agents.example.snapshot.redis

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.example.snapshot.SnapshotStrategy
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.snapshot.feature.Persistency
import ai.koog.agents.features.redis.providers.JVMRedisPersistencyStorageProvider
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.OllamaModels
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisURI
import kotlinx.coroutines.runBlocking
import kotlin.uuid.ExperimentalUuidApi

/**
 * This example demonstrates how to use the Redis-based checkpoint provider with a persistent agent.
 * 
 * The JVMRedisPersistencyStorageProvider stores agent checkpoints in Redis,
 * allowing the agent's state to persist across multiple runs and be shared across instances.
 * 
 * This example shows:
 * 1. How to create a Redis-based checkpoint provider
 * 2. How to configure an agent with the Redis-based checkpoint provider
 * 3. How to run an agent that automatically creates checkpoints
 * 4. How to restore an agent from a checkpoint
 * 5. How to manage checkpoint lifecycle with TTL
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalLettuceCoroutinesApi::class)
fun main() = runBlocking {
    // Create a prompt executor for the agent
    val executor: PromptExecutor = simpleOllamaAIExecutor()

    // Configure Redis connection
    // You can customize the Redis URI based on your setup
    val redisUri = System.getenv("REDIS_URI") ?: "redis://localhost:6379"
    println("Connecting to Redis at: $redisUri")
    
    // Create the Redis-based checkpoint provider
    val provider = JVMRedisPersistencyStorageProvider(
        persistenceId = "persistent-agent-example",
        redisUri = RedisURI.create(redisUri),
        keyPrefix = "agent:example",
        ttlSeconds = 3600 // 1 hour TTL for checkpoints
    )
    
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
    
    // Create the agent with the Redis-based checkpoint provider and continuous persistence
    val agent = AIAgent(
        promptExecutor = executor,
        strategy = SnapshotStrategy.strategy,
        agentConfig = agentConfig,
        toolRegistry = toolRegistry,
        id = agentId
    ) {
        install(Persistency) {
            storage = provider // Use the Redis-based checkpoint provider
            enableAutomaticPersistency = true // Enable automatic checkpoint creation
        }
    }

    // Run the agent with an initial input
    val result = agent.run("Hello, can you help me with a task?")
    println("Agent result: $result")

    // Retrieve all checkpoints created during the agent's execution
    val checkpoints = provider.getCheckpoints()
    println("\nRetrieved ${checkpoints.size} checkpoints for agent $agentId")

    // Print checkpoint details
    checkpoints.forEachIndexed { index, checkpoint ->
        println("Checkpoint ${index + 1}:")
        println("  ID: ${checkpoint.checkpointId}")
        println("  Created at: ${checkpoint.createdAt}")
        println("  Node ID: ${checkpoint.nodeId}")
        println("  Message history size: ${checkpoint.messageHistory.size}")
    }

    // Show checkpoint count in Redis
    val checkpointCount = provider.getCheckpointCount()
    println("\nTotal checkpoints in Redis: $checkpointCount")

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
            storage = provider // Use the Redis-based checkpoint provider
            enableAutomaticPersistency = true // Enable automatic checkpoint creation
        }
    }
    
    // Run the restored agent with a new input
    // The agent will continue the conversation from where it left off
    val restoredResult = restoredAgent.run("Now I need help with my project.")
    println("Restored agent result: $restoredResult")
    
    // Get the latest checkpoint after the second run
    val latestCheckpoint = provider.getLatestCheckpoint()
    println("\nLatest checkpoint after restoration:")
    println("  ID: ${latestCheckpoint?.checkpointId}")
    println("  Created at: ${latestCheckpoint?.createdAt}")
    println("  Node ID: ${latestCheckpoint?.nodeId}")
    println("  Message history size: ${latestCheckpoint?.messageHistory?.size}")
    
    // Optional: Demonstrate checkpoint deletion
    println("\nDemonstrating checkpoint management:")
    val oldestCheckpoint = checkpoints.firstOrNull()
    if (oldestCheckpoint != null) {
        println("Deleting oldest checkpoint: ${oldestCheckpoint.checkpointId}")
        provider.deleteCheckpoint(oldestCheckpoint.checkpointId)
        
        val remainingCount = provider.getCheckpointCount()
        println("Remaining checkpoints: $remainingCount")
    }
    
    // Clean up: close the Redis connection
    println("\nClosing Redis connection...")
    provider.close()
}