package ai.koog.agents.example.persistency

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
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
 * - Single provider usage
 * - Failover between providers
 * - Dynamic provider selection based on context
 * - Hybrid strategies for different use cases
 */
@OptIn(ExperimentalUuidApi::class)
fun main() = runBlocking {
    println("=== PersistencyStrategy Examples ===\n")
    
    // Example 1: Single Strategy
    singleStrategyExample()
    
    // Example 2: Failover Strategy
    failoverStrategyExample()
    
    // Example 3: Dynamic Strategy
    dynamicStrategyExample()
    
    // Example 4: Hybrid Strategy
    hybridStrategyExample()
    
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
 * Example 2: Failover Strategy
 * Provides resilience by failing over to backup providers if primary fails.
 * Useful for high-availability scenarios.
 */
@OptIn(ExperimentalUuidApi::class)
suspend fun failoverStrategyExample() {
    println("2. Failover Strategy Example")
    println("Primary provider with automatic failover to backup")
    
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
        systemPrompt = "You are a resilient assistant with failover capabilities.",
        id = "failover-strategy-agent"
    ) {
        install(Persistency) {
            strategy = PersistencyStrategy.Failover(
                providers = listOf(
                    InMemoryPersistencyStorageProvider("primary"),
                    InMemoryPersistencyStorageProvider("backup1"),
                    InMemoryPersistencyStorageProvider("backup2")
                )
            )
            enableAutomaticPersistency = true
        }
    }
    
    // Run multiple times to see failover in action
    repeat(3) { i ->
        try {
            val result = agent.run("Message $i")
            println("Success on attempt ${i + 1}: $result")
        } catch (e: Exception) {
            println("Failed on attempt ${i + 1}: ${e.message}")
        }
    }
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
 * Example 4: Hybrid Strategy
 * Pre-configured strategy optimized for common patterns.
 * Separates ephemeral and durable persistence needs.
 */
@OptIn(ExperimentalUuidApi::class)
suspend fun hybridStrategyExample() {
    println("4. Hybrid Strategy Example")
    println("Ephemeral for mid-execution, durable for session persistence")
    
    val executor: PromptExecutor = simpleOllamaAIExecutor()
    
    val toolRegistry = ToolRegistry {
        tool(AskUser)
        tool(SayToUser)
    }
    
    // Simulating Redis-like ephemeral storage
    val ephemeralProvider = InMemoryPersistencyStorageProvider("ephemeral")
    
    // Simulating PostgreSQL-like durable storage  
    val durableProvider = InMemoryPersistencyStorageProvider("durable")
    
    // Optional critical provider for most important checkpoints
    val criticalProvider = InMemoryPersistencyStorageProvider("critical")
    
    val agent = AIAgent(
        executor = executor,
        llmModel = OllamaModels.Meta.LLAMA_3_2,
        strategy = singleRunStrategy(ToolCalls.SEQUENTIAL),
        toolRegistry = toolRegistry,
        systemPrompt = "You are a multi-step processing assistant with optimized persistence.",
        id = "hybrid-strategy-agent"
    ) {
        install(Persistency) {
            strategy = PersistencyStrategy.Hybrid(
                ephemeralProvider = ephemeralProvider,
                durableProvider = durableProvider,
                criticalProvider = criticalProvider,
                selector = { context ->
                    // Custom logic to determine provider type
                    when {
                        // Use critical storage for final results
                        context.checkpoint?.nodeId == "final" -> 
                            PersistencyStrategy.Hybrid.ProviderType.CRITICAL
                        
                        // Use ephemeral for intermediate steps
                        context.checkpoint?.nodeId?.startsWith("step") == true ->
                            PersistencyStrategy.Hybrid.ProviderType.EPHEMERAL
                        
                        // Default to durable
                        else -> PersistencyStrategy.Hybrid.ProviderType.DURABLE
                    }
                }
            )
            enableAutomaticPersistency = true
        }
    }
    
    val result = agent.run("Multi-step process")
    println("Final result: $result")
    println()
}

/**
 * Example 5: AutoSelectForTask Strategy
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
    
    // Define available providers with descriptions
    val providers = mapOf(
        "redis" to PersistencyStrategy.AutoSelectForTask.ProviderInfo(
            provider = InMemoryPersistencyStorageProvider("redis-like"),
            description = "Fast in-memory cache with TTL support, ideal for temporary data and high-frequency operations",
            capabilities = listOf("fast", "ephemeral", "distributed", "ttl-support")
        ),
        "postgres" to PersistencyStrategy.AutoSelectForTask.ProviderInfo(
            provider = InMemoryPersistencyStorageProvider("postgres-like"),
            description = "Reliable SQL database with ACID compliance, perfect for long-term storage and complex queries",
            capabilities = listOf("durable", "queryable", "transactional", "relational")
        ),
        "s3" to PersistencyStrategy.AutoSelectForTask.ProviderInfo(
            provider = InMemoryPersistencyStorageProvider("s3-like"),
            description = "Object storage for archival and compliance, cost-effective for large volumes",
            capabilities = listOf("archival", "compliant", "cost-effective", "scalable")
        )
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
    println("\nNote: In production, the LLM would analyze the operation context")
    println("and select the most appropriate provider based on:")
    println("- Operation type (save/retrieve)")
    println("- Performance requirements")
    println("- Data criticality")
    println("- Cost considerations")
    println()
}