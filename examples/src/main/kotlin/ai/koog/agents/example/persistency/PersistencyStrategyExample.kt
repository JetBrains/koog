package ai.koog.agents.example.persistency

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.Persistency
import ai.koog.agents.snapshot.providers.InMemoryPersistencyStorageProvider
import ai.koog.agents.snapshot.strategy.PersistencyStrategy
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.runBlocking
import kotlin.uuid.ExperimentalUuidApi

/**
 * This example demonstrates the various PersistencyStrategy patterns available
 * for agent checkpoint persistence.
 * 
 * The PersistencyStrategy pattern allows flexible configuration of persistence
 * providers, enabling:
 * - Single provider usage (backward compatible)
 * - Dynamic provider selection based on operation context
 * - LLM-powered intelligent provider selection using @LLMDescription annotations
 */
@OptIn(ExperimentalUuidApi::class)
fun main() = runBlocking {
    println("=== PersistencyStrategy Examples ===\n")
    
    // Example 1: Single Strategy
    singleStrategyExample()
    
    // Example 2: Dynamic Strategy
    dynamicStrategyExample()
    
    // Example 5: AutoSelectForTask Strategy
    autoSelectForTaskExample()
}

/**
 * Example 1: Single Strategy
 * The simplest strategy - uses a single persistence provider for all operations.
 * This is equivalent to the traditional approach.
 */
@OptIn(ExperimentalUuidApi::class)
suspend fun singleStrategyExample() {
    println("1. Single Strategy Example")
    println("Using a single InMemory provider for all persistence")
    
    val executor: PromptExecutor = simpleOllamaAIExecutor()
    
    val toolRegistry = ToolRegistry {
        tool(AskUser)
        tool(SayToUser)
    }
    
    val agent = AIAgent(
        executor = executor,
        llmModel = OllamaModels.Meta.LLAMA_3_2,
        strategy = singleRunStrategy(ToolCalls.SEQUENTIAL),
        toolRegistry = toolRegistry,
        systemPrompt = "You are a helpful assistant demonstrating persistence strategies.",
        id = "single-strategy-agent"
    ) {
        install(Persistency) {
            // Traditional approach (backward compatible)
            // storage = InMemoryPersistencyStorageProvider("single-agent")
            
            // Using strategy pattern
            strategy = PersistencyStrategy.Single(
                provider = InMemoryPersistencyStorageProvider("single-agent")
            )
            
            enableAutomaticPersistency = true
        }
    }
    
    val result = agent.run("Hello, Single Strategy!")
    println("Result: $result")
    println()
}


/**
 * Example 3: Dynamic Strategy
 * Selects providers based on operation context.
 * Enables sophisticated routing logic.
 */
@OptIn(ExperimentalUuidApi::class)
suspend fun dynamicStrategyExample() {
    println("3. Dynamic Strategy Example")
    println("Different providers for different operations")
    
    val executor: PromptExecutor = simpleOllamaAIExecutor()
    
    val toolRegistry = ToolRegistry {
        tool(AskUser)
        tool(SayToUser)
    }
    
    val fastProvider = InMemoryPersistencyStorageProvider("fast")
    val durableProvider = InMemoryPersistencyStorageProvider("durable")
    val archiveProvider = InMemoryPersistencyStorageProvider("archive")
    
    val agent = AIAgent(
        executor = executor,
        llmModel = OllamaModels.Meta.LLAMA_3_2,
        strategy = singleRunStrategy(ToolCalls.SEQUENTIAL),
        toolRegistry = toolRegistry,
        systemPrompt = "You are an intelligent assistant with context-aware persistence.",
        id = "dynamic-strategy-agent"
    ) {
        install(Persistency) {
            strategy = PersistencyStrategy.Dynamic(
                providers = mapOf(
                    "fast" to fastProvider,
                    "durable" to durableProvider,
                    "archive" to archiveProvider
                ),
                selector = { context ->
                    when {
                        // Save operations during execution use fast storage
                        context.operation is PersistencyStrategy.Dynamic.Operation.SaveCheckpoint &&
                        context.checkpoint?.nodeId?.contains("processing") == true -> "fast"
                        
                        // Critical checkpoints use durable storage
                        context.checkpoint?.nodeId?.contains("critical") == true -> "durable"
                        
                        // Old checkpoints go to archive
                        context.operation is PersistencyStrategy.Dynamic.Operation.GetCheckpoints -> "archive"
                        
                        // Default to durable
                        else -> "durable"
                    }
                }
            )
            enableAutomaticPersistency = true
        }
    }
    
    val result = agent.run("Test dynamic routing")
    println("Result: $result")
    println()
}

/**
 * Example 3: AutoSelectForTask Strategy
 * Uses LLM to intelligently select the best provider based on task context.
 * Similar to ToolSelectionStrategy.AutoSelectForTask but for persistence.
 */
@OptIn(ExperimentalUuidApi::class)
suspend fun autoSelectForTaskExample() {
    println("5. AutoSelectForTask Strategy Example")
    println("LLM-driven provider selection based on task requirements")
    
    val executor: PromptExecutor = simpleOllamaAIExecutor()
    
    val toolRegistry = ToolRegistry {
        tool(AskUser)
        tool(SayToUser)
    }
    
    // Define providers with @LLMDescription annotations
    @LLMDescription("Fast in-memory cache with TTL support, ideal for temporary data and high-frequency operations")
    class RedisLikeProvider : InMemoryPersistencyStorageProvider("redis-like")
    
    @LLMDescription("Reliable SQL database with ACID compliance, perfect for long-term storage and complex queries")
    class PostgresLikeProvider : InMemoryPersistencyStorageProvider("postgres-like")
    
    @LLMDescription("Object storage for archival and compliance, cost-effective for large volumes")
    class S3LikeProvider : InMemoryPersistencyStorageProvider("s3-like")
    
    val providers = mapOf(
        "redis" to RedisLikeProvider(),
        "postgres" to PostgresLikeProvider(),
        "s3" to S3LikeProvider()
    )
    
    val agent = AIAgent(
        executor = executor,
        llmModel = OllamaModels.Meta.LLAMA_3_2,
        strategy = singleRunStrategy(ToolCalls.SEQUENTIAL),
        toolRegistry = toolRegistry,
        systemPrompt = """You are a real-time trading agent that processes market data 
                         and executes trades with sub-second latency requirements.""",
        id = "auto-select-agent"
    ) {
        install(Persistency) {
            strategy = PersistencyStrategy.AutoSelectForTask(
                providers = providers,
                taskDescription = "Real-time trading agent that processes market data and executes trades with sub-second latency requirements",
                maxRetries = 3
            )
            
            enableAutomaticPersistency = true
        }
    }
    
    val result = agent.run("Execute high-frequency trade")
    println("Result: $result")
    println("\nNote: In production, the LLM analyzes the task description")
    println("and selects the most appropriate provider based on:")
    println("- Task-specific performance requirements")
    println("- Data retention and durability needs")
    println("- Cost and resource constraints")
    println("- Provider capabilities from @LLMDescription annotations")
    println()
}