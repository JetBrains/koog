package ai.koog.agents.example.snapshot

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.example.calculator.CalculatorTools
import ai.koog.agents.memory.config.MemoryScopesProfile
import ai.koog.agents.memory.feature.AgentMemory
import ai.koog.agents.memory.model.*
import ai.koog.agents.memory.providers.InMemoryAgentMemoryProvider
import ai.koog.agents.snapshot.feature.*
import ai.koog.agents.snapshot.providers.InMemoryPersistencyStorageProvider
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.uuid.ExperimentalUuidApi

/**
 * Example demonstrating PortableAgent - unified agent state snapshots
 * that include execution context, memory facts, and custom data for
 * complete agent restoration across environments.
 */
@OptIn(ExperimentalUuidApi::class)
fun main() = runBlocking {
    val executor: PromptExecutor = simpleOllamaAIExecutor()

    val toolRegistry = ToolRegistry {
        tools(CalculatorTools().asTools())
    }

    val memoryProvider = InMemoryAgentMemoryProvider()
    val snapshotProvider = InMemoryPersistencyStorageProvider("portable-agent-example")

    // Custom data structure for demonstration
    @Serializable
    data class GameState(
        val level: Int,
        val score: Int,
        val playerName: String,
        val inventory: List<String>
    )

    println("=== PortableAgent Example ===")
    println("Creating agent with memory and custom data capabilities...")

    val agent = AIAgent(
        executor = executor,
        llmModel = OllamaModels.Meta.LLAMA_3_2,
        strategy = singleRunStrategy(ToolCalls.SEQUENTIAL),
        toolRegistry = toolRegistry,
        systemPrompt = "You are a helpful assistant with memory capabilities. Remember facts about calculations.",
        temperature = 0.0,
    ) {
        install(AgentMemory) {
            provider = memoryProvider
            scopes = MemoryScopesProfile()
        }

        install(Persistency) {
            storage = snapshotProvider
            includeMemorySnapshot = true // Enable memory snapshots
            extraSnapshotDataProvider = {
                // Custom data provider - capture game state
                val gameState = GameState(
                    level = 5,
                    score = 1250,
                    playerName = "Player1",
                    inventory = listOf("sword", "potion", "key")
                )
                Json.encodeToJsonElement(gameState).jsonObject
            }
        }
    }

    // Phase 1: Run agent and build memory
    println("\n--- Phase 1: Building Knowledge Base ---")
    
    // Store some facts in memory
    val mathConcept = Concept("math_operations", "Mathematical operations performed", FactType.LIST)
    val calculationFact = ListFact(
        concept = mathConcept,
        timestamp = Clock.System.now().toEpochMilliseconds(),
        values = listOf("5 + 3 = 8", "10 - 4 = 6")
    )
    
    agent.memory().save(calculationFact, MemorySubject.Everything, MemoryScope.CrossProduct)
    
    val result1 = agent.run("Calculate 5 + 3 and remember this calculation")
    println("Agent result: $result1")

    // Phase 2: Create checkpoint with memory and custom data
    println("\n--- Phase 2: Creating PortableAgent Snapshot ---")
    
    val checkpoint = agent.persistency().createCheckpoint(
        agentContext = agent,
        nodeId = "calculation-complete",
        lastInput = "5 + 3",
        lastInputType = typeOf<String>()
    )
    
    println("Checkpoint created with ID: ${checkpoint?.checkpointId}")
    println("Memory snapshot included: ${checkpoint?.memorySnapshot != null}")
    println("Custom data included: ${checkpoint?.extraSnapshotData != null}")

    // Phase 3: Export complete portable agent bundle
    println("\n--- Phase 3: Exporting PortableAgent Bundle ---")
    
    val snapshotBundle = agent.exportSnapshotBundle(
        checkpointId = checkpoint!!.checkpointId,
        metadata = buildJsonObject {
            put("environment", "development")
            put("exportReason", "agent_transfer")
            put("notes", "Complete agent state with calculations and game data")
        }
    )
    
    println("Snapshot bundle created:")
    println("- Agent ID: ${snapshotBundle?.agentId}")
    println("- Bundle ID: ${snapshotBundle?.bundleId}")
    println("- Version: ${snapshotBundle?.version}")
    println("- Compatible: ${snapshotBundle?.isCompatible()}")
    println("\nBundle Summary:")
    println(snapshotBundle?.summary())

    // Serialize bundle for transfer/storage
    val bundleJson = snapshotBundle?.toJson()
    val compressedBytes = snapshotBundle?.toCompressedBytes()
    
    println("\nSerialization results:")
    println("- JSON size: ${bundleJson?.length} characters")
    println("- Compressed size: ${compressedBytes?.size} bytes")

    // Phase 4: Create new agent and restore from bundle
    println("\n--- Phase 4: Creating New Agent Instance ---")
    
    val newMemoryProvider = InMemoryAgentMemoryProvider()
    val newAgent = AIAgent(
        executor = executor,
        llmModel = OllamaModels.Meta.LLAMA_3_2,
        strategy = singleRunStrategy(ToolCalls.SEQUENTIAL),
        toolRegistry = toolRegistry,
        systemPrompt = "You are a helpful assistant with memory capabilities. Remember facts about calculations.",
        temperature = 0.0,
        id = agent.id // Same agent ID for restoration
    ) {
        install(AgentMemory) {
            provider = newMemoryProvider
            scopes = MemoryScopesProfile()
        }

        install(Persistency) {
            storage = snapshotProvider
            includeMemorySnapshot = true
        }
    }

    // Phase 5: Restore from portable agent bundle
    println("\n--- Phase 5: Restoring from PortableAgent ---")
    
    // Restore from JSON (simulating cross-environment transfer)
    val restoredBundle = AgentSnapshotBundle.fromJson(bundleJson!!)
    val customData = newAgent.restoreFromSnapshotBundle<GameState>(restoredBundle.bundleId)
    
    println("Restoration complete!")
    println("Custom data restored: $customData")

    // Verify memory restoration
    val restoredFacts = newAgent.memory().load(mathConcept, MemorySubject.Everything, MemoryScope.CrossProduct)
    println("Memory facts restored: ${restoredFacts.size} facts")
    restoredFacts.forEach { fact ->
        if (fact is ListFact) {
            println("- Calculations: ${fact.values}")
        }
    }

    // Phase 6: Continue execution with restored state
    println("\n--- Phase 6: Continuing with Restored Agent ---")
    
    val result2 = newAgent.run("What calculations do you remember? Also calculate 7 * 8")
    println("New agent result: $result2")

    // Phase 7: Demonstrate cold storage workflow
    println("\n--- Phase 7: Cold Storage Simulation ---")
    
    // Create memory-focused bundle for long-term storage
    val memoryBundle = agent.createMemorySnapshotBundle(
        metadata = buildJsonObject {
            put("purpose", "long_term_storage")
            put("compression", "high")
        }
    )
    
    println("Memory-focused bundle created for cold storage:")
    println("- Contains execution state: ${memoryBundle?.checkpoint != null}")
    println("- Contains memory: ${memoryBundle?.memorySnapshot != null}")
    println("- Metadata: ${memoryBundle?.metadata}")

    // Demonstrate bundle metadata enhancement
    val enhancedBundle = memoryBundle?.withMetadata(buildJsonObject {
        put("archive_date", Clock.System.now().toString())
        put("retention_policy", "5_years")
    })
    
    println("\nEnhanced bundle metadata:")
    enhancedBundle?.metadata?.forEach { (key, value) ->
        println("- $key: ${value.jsonPrimitive.content}")
    }

    println("\n=== PortableAgent Example Complete ===")
    println("Demonstrated: Memory snapshots, custom data, bundle export/restore, cross-environment transfer")
}