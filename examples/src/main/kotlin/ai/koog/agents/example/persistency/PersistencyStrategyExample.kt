package ai.koog.agents.example.persistency

import ai.koog.agents.core.agent.agent
import ai.koog.agents.core.tools.tool
import ai.koog.agents.snapshot.feature.Persistency
import ai.koog.agents.snapshot.providers.InMemoryPersistencyStorageProvider
import ai.koog.agents.snapshot.strategy.PersistencyStrategy
import ai.koog.prompt.executor.llms.local.testing.getMockExecutor
import kotlinx.coroutines.runBlocking

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
}

/**
 * Example 1: Single Strategy
 * The simplest strategy - uses a single persistence provider for all operations.
 * This is equivalent to the traditional approach.
 */
suspend fun singleStrategyExample() {
    println("1. Single Strategy Example")
    println("Using a single InMemory provider for all persistence")
    
    val agent = agent {
        id = "single-strategy-agent"
        strategy {
            start { userMessage ->
                node(userMessage) {
                    instruction { "Process: $it" }
                    route { finish(it) }
                }
            }
        }
        features {
            install(Persistency) {
                // Traditional approach (backward compatible)
                storage = InMemoryPersistencyStorageProvider("single-agent")
                
                // Equivalent using strategy
                strategy = PersistencyStrategy.Single(
                    provider = InMemoryPersistencyStorageProvider("single-agent")
                )
                
                enableAutomaticPersistency = true
            }
        }
        prompting {
            executor = getMockExecutor { mockLLMAnswer("Processed!") }
        }
    }
    
    val result = agent.start("Hello, Single Strategy!")
    println("Result: $result")
    println()
}

/**
 * Example 2: Failover Strategy
 * Provides resilience by failing over to backup providers if primary fails.
 * Useful for high-availability scenarios.
 */
suspend fun failoverStrategyExample() {
    println("2. Failover Strategy Example")
    println("Primary provider with automatic failover to backup")
    
    // Simulate a provider that might fail
    class UnreliableProvider(
        persistenceId: String,
        private val failureRate: Double = 0.5
    ) : InMemoryPersistencyStorageProvider(persistenceId) {
        override suspend fun saveCheckpoint(agentCheckpointData: ai.koog.agents.snapshot.feature.AgentCheckpointData) {
            if (Math.random() < failureRate) {
                throw RuntimeException("Provider temporarily unavailable")
            }
            super.saveCheckpoint(agentCheckpointData)
        }
    }
    
    val agent = agent {
        id = "failover-strategy-agent"
        strategy {
            start { userMessage ->
                node(userMessage) {
                    instruction { "Process with failover: $it" }
                    route { finish(it) }
                }
            }
        }
        features {
            install(Persistency) {
                strategy = PersistencyStrategy.Failover(
                    providers = listOf(
                        UnreliableProvider("primary", failureRate = 0.8),
                        InMemoryPersistencyStorageProvider("backup1"),
                        InMemoryPersistencyStorageProvider("backup2")
                    )
                )
                enableAutomaticPersistency = true
            }
        }
        prompting {
            executor = getMockExecutor { mockLLMAnswer("Processed with failover!") }
        }
    }
    
    // Run multiple times to see failover in action
    repeat(3) { i ->
        try {
            val result = agent.start("Message $i")
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
suspend fun dynamicStrategyExample() {
    println("3. Dynamic Strategy Example")
    println("Different providers for different operations")
    
    val criticalTool = tool<String, String> {
        name = "critical_operation"
        description = "A critical operation that needs durable persistence"
        handler { input -> "Critical result: $input" }
    }
    
    val agent = agent {
        id = "dynamic-strategy-agent"
        strategy {
            start { userMessage ->
                node(userMessage) {
                    instruction { "Process dynamically: $it" }
                    route { finish(it) }
                }
            }
        }
        tools {
            register(criticalTool)
        }
        features {
            install(Persistency) {
                val fastProvider = InMemoryPersistencyStorageProvider("fast")
                val durableProvider = InMemoryPersistencyStorageProvider("durable")
                val archiveProvider = InMemoryPersistencyStorageProvider("archive")
                
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
        prompting {
            executor = getMockExecutor { mockLLMAnswer("Dynamically processed!") }
        }
    }
    
    val result = agent.start("Test dynamic routing")
    println("Result: $result")
    println()
}

/**
 * Example 4: Hybrid Strategy
 * Pre-configured strategy optimized for common patterns.
 * Separates ephemeral and durable persistence needs.
 */
suspend fun hybridStrategyExample() {
    println("4. Hybrid Strategy Example")
    println("Ephemeral for mid-execution, durable for session persistence")
    
    val agent = agent {
        id = "hybrid-strategy-agent"
        strategy {
            start { userMessage ->
                // Simulate a multi-step process
                val step1 = node(userMessage) {
                    instruction { "Step 1: Analyze $it" }
                    route { node2(it) }
                }
                
                val step2 = node<String, String>("step2") {
                    instruction { "Step 2: Process $it" }
                    route { node3(it) }
                }
                
                val step3 = node<String, String>("step3") {
                    instruction { "Step 3: Finalize $it" }
                    route { finish(it) }
                }
                
                connect(step1 to step2)
                connect(step2 to step3)
            }
        }
        features {
            install(Persistency) {
                // Simulating Redis-like ephemeral storage
                val ephemeralProvider = InMemoryPersistencyStorageProvider("ephemeral")
                
                // Simulating PostgreSQL-like durable storage  
                val durableProvider = InMemoryPersistencyStorageProvider("durable")
                
                // Optional critical provider for most important checkpoints
                val criticalProvider = InMemoryPersistencyStorageProvider("critical")
                
                strategy = PersistencyStrategy.Hybrid(
                    ephemeralProvider = ephemeralProvider,
                    durableProvider = durableProvider,
                    criticalProvider = criticalProvider,
                    selector = { context ->
                        // Custom logic to determine provider type
                        when {
                            // Use critical storage for final results
                            context.checkpoint?.nodeId == "step3" -> 
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
        prompting {
            executor = getMockExecutor {
                mockLLMAnswer("Step 1 complete") onRequestContains "Step 1"
                mockLLMAnswer("Step 2 complete") onRequestContains "Step 2"
                mockLLMAnswer("All steps complete!") onRequestContains "Step 3"
            }
        }
    }
    
    val result = agent.start("Multi-step process")
    println("Final result: $result")
    println()
}

// Extension properties for cleaner node references
private val node2 = "step2"
private val node3 = "step3"