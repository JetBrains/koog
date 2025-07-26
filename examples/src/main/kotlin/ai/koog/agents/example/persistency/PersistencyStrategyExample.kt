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
import ai.koog.agents.snapshot.strategy.CoordinationStrategies
import ai.koog.agents.snapshot.strategy.ProviderRegistry
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.runBlocking
import kotlin.uuid.ExperimentalUuidApi

/**
 * This example demonstrates the open-ended PersistencyStrategy coordination patterns
 * available for agent checkpoint persistence.
 * 
 * The PersistencyStrategy pattern provides unlimited customization for coordinating
 * multiple persistence providers:
 * - Fixed coordination with built-in patterns
 * - Dynamic coordination selection based on agent context
 * - Custom coordination logic for specialized use cases
 * - LLM-powered coordination selection from predefined options
 */
@OptIn(ExperimentalUuidApi::class)
fun main() = runBlocking {
    println("=== PersistencyStrategy Examples ===\n")
    
    // Example 1: Single Strategy
    singleStrategyExample()
    
    // Example 2: Dynamic Strategy
    dynamicStrategyExample()
    
    // Example 3: AutoSelectCoordination Strategy
    autoSelectCoordinationExample()
}

/**
 * Example 1: Fixed Strategy with Single Coordination
 * The simplest coordination - uses a single persistence provider for all operations.
 * This is equivalent to the traditional approach but uses the new registry system.
 */
@OptIn(ExperimentalUuidApi::class)
suspend fun singleStrategyExample() {
    println("1. Fixed Strategy with Single Coordination Example")
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
            val registry = getRegistry()
            val provider = InMemoryPersistencyStorageProvider("single-agent")
            val providerId = registry.register(provider, "main")
            
            // Using Fixed strategy with Single coordination
            strategy = PersistencyStrategy.Fixed(
                CoordinationStrategies.Single(providerId)
            )
            
            enableAutomaticPersistency = true
        }
    }
    
    val result = agent.run("Hello, Single Strategy!")
    println("Result: $result")
    println()
}


/**
 * Example 2: Dynamic Strategy
 * Selects coordination patterns based on agent context.
 * Enables sophisticated routing logic with unlimited customization.
 */
@OptIn(ExperimentalUuidApi::class)
suspend fun dynamicStrategyExample() {
    println("2. Dynamic Strategy Example")
    println("Different coordination patterns based on agent context")
    
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
        systemPrompt = "You are an intelligent assistant with context-aware persistence.",
        id = "dynamic-strategy-agent"
    ) {
        install(Persistency) {
            val registry = getRegistry()
            
            // Register providers
            val fastProvider = InMemoryPersistencyStorageProvider("fast")
            val durableProvider = InMemoryPersistencyStorageProvider("durable")
            val archiveProvider = InMemoryPersistencyStorageProvider("archive")
            
            val fastId = registry.register(fastProvider, "fast")
            val durableId = registry.register(durableProvider, "durable")
            val archiveId = registry.register(archiveProvider, "archive")
            
            // Dynamic strategy that selects coordination based on agent context
            strategy = PersistencyStrategy.Dynamic { context, registry ->
                when {
                    // Critical agents use write-to-all for redundancy
                    context.agentContext.id.contains("critical") -> 
                        CoordinationStrategies.WriteToAll(listOf(durableId, archiveId))
                    
                    // Fast agents use single fast provider
                    context.agentContext.id.contains("fast") -> 
                        CoordinationStrategies.Single(fastId)
                    
                    // Default agents use durable with fast backup
                    else -> 
                        CoordinationStrategies.WriteWithBackup(durableId, listOf(fastId))
                }
            }
            
            enableAutomaticPersistency = true
        }
    }
    
    val result = agent.run("Test dynamic coordination selection")
    println("Result: $result")
    println()
}

/**
 * Example 3: AutoSelectCoordination Strategy
 * Uses LLM to intelligently select the best coordination pattern based on task context.
 * The LLM chooses from predefined coordination options.
 */
@OptIn(ExperimentalUuidApi::class)
suspend fun autoSelectCoordinationExample() {
    println("3. AutoSelectCoordination Strategy Example")
    println("LLM-driven coordination selection from predefined options")
    
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
        systemPrompt = """You are a real-time trading agent that processes market data 
                         and executes trades with sub-second latency requirements.""",
        id = "auto-select-agent"
    ) {
        install(Persistency) {
            val registry = getRegistry()
            
            // Register providers with descriptive names
            val redisProvider = InMemoryPersistencyStorageProvider("redis-cache")
            val postgresProvider = InMemoryPersistencyStorageProvider("postgres-db")
            val s3Provider = InMemoryPersistencyStorageProvider("s3-archive")
            
            val redisId = registry.register(redisProvider, "redis")
            val postgresId = registry.register(postgresProvider, "postgres")
            val s3Id = registry.register(s3Provider, "s3")
            
            // Define coordination options for LLM to choose from
            val coordinationOptions = listOf(
                CoordinationStrategies.Single(redisId), // Fast single provider
                CoordinationStrategies.Single(postgresId), // Reliable single provider
                CoordinationStrategies.WriteToAll(listOf(redisId, postgresId)), // High availability
                CoordinationStrategies.WriteWithBackup(postgresId, listOf(s3Id)) // Durable with backup
            )
            
            strategy = PersistencyStrategy.AutoSelectCoordination(
                taskDescription = "Real-time trading agent requiring sub-second latency and reliable checkpoint recovery",
                options = coordinationOptions,
                registry = registry,
                maxRetries = 3
            )
            
            enableAutomaticPersistency = true
        }
    }
    
    val result = agent.run("Execute high-frequency trade")
    println("Result: $result")
    println("\nNote: In production, the LLM analyzes the task description")
    println("and selects the most appropriate coordination pattern based on:")
    println("- Task-specific performance requirements")
    println("- Data retention and durability needs")
    println("- Coordination pattern characteristics")
    println("- Provider capabilities and trade-offs")
    println()
}