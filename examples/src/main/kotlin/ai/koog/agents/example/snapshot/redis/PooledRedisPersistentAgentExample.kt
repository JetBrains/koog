package ai.koog.agents.example.snapshot.redis

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.example.snapshot.SnapshotStrategy
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.snapshot.feature.Persistency
import ai.koog.agents.features.redis.providers.PooledJVMRedisPersistencyStorageProvider
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.OllamaModels
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisURI
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.uuid.ExperimentalUuidApi

/**
 * This example demonstrates how to use the Pooled Redis-based checkpoint provider for high-concurrency scenarios.
 * 
 * The PooledJVMRedisPersistencyStorageProvider uses connection pooling to handle multiple concurrent
 * checkpoint operations efficiently, making it ideal for production environments with:
 * - Multiple agent instances running simultaneously
 * - High-frequency checkpoint operations
 * - Applications requiring better resource utilization
 * 
 * This example shows:
 * 1. How to create a pooled Redis-based checkpoint provider with custom pool configuration
 * 2. How to monitor connection pool statistics
 * 3. How to handle concurrent agent operations safely
 * 4. When to use the pooled provider vs the basic provider
 * 5. Proper resource cleanup and pool management
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalLettuceCoroutinesApi::class)
fun main() = runBlocking {
    // Create a prompt executor for the agents
    val executor: PromptExecutor = simpleOllamaAIExecutor()

    // Configure Redis connection
    val redisUri = System.getenv("REDIS_URI") ?: "redis://localhost:6379"
    println("Connecting to Redis at: $redisUri")
    
    // Configure connection pool for production use
    val poolConfig = PooledJVMRedisPersistencyStorageProvider.PoolConfig(
        minIdle = 2,        // Keep at least 2 idle connections
        maxIdle = 8,        // Maximum 8 idle connections  
        maxTotal = 20,      // Maximum 20 total connections
        testOnBorrow = true, // Validate connections when borrowing
        testOnReturn = true  // Validate connections when returning
    )
    
    // Create the pooled Redis-based checkpoint provider
    val provider = PooledJVMRedisPersistencyStorageProvider(
        persistenceId = "pooled-agent-example",
        redisUri = RedisURI.create(redisUri),
        keyPrefix = "agent:pooled",
        ttlSeconds = 3600, // 1 hour TTL for checkpoints
        poolConfig = poolConfig
    )
    
    println("Pool configuration:")
    println("  Min idle: ${poolConfig.minIdle}")
    println("  Max idle: ${poolConfig.maxIdle}")
    println("  Max total: ${poolConfig.maxTotal}")
    
    // Show initial pool statistics
    val initialStats = provider.getPoolStats()
    println("\nInitial pool statistics:")
    printPoolStats(initialStats)
    
    // Create tool registry with basic tools
    val toolRegistry = ToolRegistry {
        tool(AskUser)
        tool(SayToUser)
    }

    // Create agent config
    val agentConfig = AIAgentConfig(
        prompt = prompt("pooled-agent") {
            system("You are a helpful assistant that can handle concurrent requests efficiently.")
        },
        model = OllamaModels.Meta.LLAMA_3_2,
        maxAgentIterations = 10
    )

    println("\nDemonstrating concurrent agent operations...")
    
    // Create multiple agents concurrently to demonstrate pool behavior
    val numberOfAgents = 3
    val concurrentAgents = (1..numberOfAgents).map { i ->
        async {
            val agentId = "pooled-agent-$i"
            
            // Create agent with pooled provider
            val agent = AIAgent(
                promptExecutor = executor,
                strategy = SnapshotStrategy.strategy,
                agentConfig = agentConfig,
                toolRegistry = toolRegistry,
                id = agentId
            ) {
                install(Persistency) {
                    storage = provider // Use the pooled Redis-based checkpoint provider
                    enableAutomaticPersistency = true
                }
            }

            // Run the agent
            val result = agent.run("Hello! I'm agent $i. Can you help me?")
            println("Agent $i completed with result: $result")
            agentId
        }
    }
    
    // Wait for all agents to complete
    val completedAgents = concurrentAgents.awaitAll()
    println("\nAll ${completedAgents.size} agents completed successfully!")
    
    // Show pool statistics after concurrent operations
    val afterOpsStats = provider.getPoolStats()
    println("\nPool statistics after concurrent operations:")
    printPoolStats(afterOpsStats)
    
    // Retrieve and display all checkpoints
    val allCheckpoints = provider.getCheckpoints()
    println("\nRetrieved ${allCheckpoints.size} total checkpoints from all agents")
    
    // Group checkpoints by agent
    val checkpointsByAgent = allCheckpoints.groupBy { checkpoint ->
        // Extract agent ID from checkpoint data
        checkpoint.toString().substringAfter("pooled-agent-").take(1)
    }
    
    checkpointsByAgent.forEach { (agentNum, checkpoints) ->
        println("Agent $agentNum: ${checkpoints.size} checkpoints")
    }

    println("\nDemonstrating pool monitoring during operations...")
    
    // Create another agent to show pool utilization
    val monitoringAgent = AIAgent(
        promptExecutor = executor,
        strategy = SnapshotStrategy.strategy,
        agentConfig = agentConfig,
        toolRegistry = toolRegistry,
        id = "monitoring-agent"
    ) {
        install(Persistency) {
            storage = provider
            enableAutomaticPersistency = true
        }
    }
    
    // Run the monitoring agent while checking pool stats
    val monitoringResult = monitoringAgent.run("I'm the monitoring agent. Please help me.")
    println("Monitoring agent result: $monitoringResult")
    
    // Final pool statistics
    val finalStats = provider.getPoolStats()
    println("\nFinal pool statistics:")
    printPoolStats(finalStats)
    
    // Show total checkpoint count across all agents
    val totalCheckpointCount = provider.getCheckpointCount()
    println("\nTotal checkpoints across all agents: $totalCheckpointCount")
    
    // Demonstrate high utilization warning
    if (finalStats.isHighUtilization) {
        println("⚠️  Warning: Pool utilization is high (${finalStats.utilizationPercent}%)")
        println("   Consider increasing maxTotal or optimizing checkpoint frequency")
    } else {
        println("✅ Pool utilization is healthy (${finalStats.utilizationPercent}%)")
    }
    
    // Clean up: close the pooled provider
    println("\nClosing pooled Redis provider...")
    provider.close()
    println("Pool closed successfully")
}

/**
 * Helper function to print pool statistics in a readable format
 */
private fun printPoolStats(stats: PooledJVMRedisPersistencyStorageProvider.PoolStats) {
    println("  Active connections: ${stats.numActive}")
    println("  Idle connections: ${stats.numIdle}")
    println("  Max total: ${stats.maxTotal}")
    println("  Max idle: ${stats.maxIdle}")
    println("  Min idle: ${stats.minIdle}")
    println("  Utilization: ${"%.1f".format(stats.utilizationPercent)}%")
    println("  High utilization: ${if (stats.isHighUtilization) "⚠️  YES" else "✅ NO"}")
}