package ai.koog.agents.snapshot.examples

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.snapshot.feature.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.Json

/**
 * Example demonstrating "PortableAgent" with Minecraft agent state.
 * 
 * This example shows how to:
 * 1. Configure persistency with memory snapshots and custom data
 * 2. Capture comprehensive agent state including world context
 * 3. Export portable bundles for storage/transfer
 * 4. Restore complete agent state across sessions
 * 
 * Use cases:
 * - Save portable agent state when player logs out
 * - Transfer portable agents between different Minecraft servers
 * - Create agent backups before risky operations
 * - Debug agent behavior by replaying from saved states
 */

/**
 * Custom data structures for Minecraft agent state.
 */
@Serializable
public data class MinecraftPosition(
    val x: Double,
    val y: Double, 
    val z: Double,
    val dimension: String = "overworld"
)

@Serializable
public data class ItemStack(
    val type: String,
    val count: Int,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
public data class MinecraftAgentState(
    val position: MinecraftPosition,
    val inventory: List<ItemStack>,
    val health: Float,
    val hunger: Int,
    val experience: Int,
    val gameMode: String,
    val currentTask: String?,
    val worldSeed: Long
)

/**
 * Example of configuring an agent with PortableAgent support.
 */
public suspend fun configureMinecraftAgent(): Unit {
    // Configure persistency with memory snapshots and Minecraft state
    /*
    val agent = AIAgent(
        executor = minecraftExecutor,
        llmModel = model
    ) {
        install(AgentMemory) {
            memoryProvider = KottageAgentMemoryProvider(...)
            includeMemorySnapshot = true
        }
        
        install(Persistency) {
            storage = FilePersistencyStorageProvider(...)
            enableAutomaticPersistency = true
            includeMemorySnapshot = true
            
            // Capture Minecraft-specific state with each checkpoint
            extraSnapshotDataProvider = {
                captureMinecraftState(this)
            }
        }
    }
    */
}

/**
 * Captures current Minecraft world state for inclusion in checkpoints.
 */
public suspend fun captureMinecraftState(agentContext: AIAgentContextBase): kotlinx.serialization.json.JsonObject = buildJsonObject {
    // Simulate capturing Minecraft state
    val minecraftState = MinecraftAgentState(
        position = MinecraftPosition(-123.45, 64.0, 789.12, "overworld"),
        inventory = listOf(
            ItemStack("minecraft:diamond_sword", 1, mapOf("enchantments" to "sharpness_5")),
            ItemStack("minecraft:bread", 32),
            ItemStack("minecraft:cobblestone", 64)
        ),
        health = 18.5f,
        hunger = 17,
        experience = 1250,
        gameMode = "survival",
        currentTask = "Build castle walls",
        worldSeed = 1234567890L
    )
    
    put("minecraft", Json.encodeToJsonElement(minecraftState))
    put("captureReason", kotlinx.serialization.json.JsonPrimitive("automatic_checkpoint"))
    put("serverInfo", buildJsonObject {
        put("serverName", kotlinx.serialization.json.JsonPrimitive("Creative Builders"))
        put("playerCount", kotlinx.serialization.json.JsonPrimitive(12))
        put("timeOfDay", kotlinx.serialization.json.JsonPrimitive("day"))
    })
}

/**
 * Example of exporting a complete portable agent for backup or transfer.
 */
public suspend fun exportPortableAgent(agentContext: AIAgentContextBase): Unit {
    // Create comprehensive portable agent bundle
    val bundle = agentContext.exportSnapshotBundle(
        includeMemory = true,
        includeKVStore = true,
        metadata = buildJsonObject {
            put("exportReason", kotlinx.serialization.json.JsonPrimitive("daily_backup"))
            put("environment", kotlinx.serialization.json.JsonPrimitive("production_server"))
            put("minecraftServer", kotlinx.serialization.json.JsonPrimitive("creative-builders.example.com"))
            put("exportedBy", kotlinx.serialization.json.JsonPrimitive("backup_system"))
        }
    )
    
    // Bundle contains everything needed to restore the agent:
    // - Execution state (current strategy node, message history)  
    // - Memory facts (what the agent has learned about the player/world)
    // - Minecraft state (position, inventory, health, current task)
    // - Metadata for management and debugging
    
    println("Exported portable agent:")
    println(bundle.summary())
    
    // Save to external storage
    val bundleJson = bundle.toJson()
    saveToExternalStorage("portable-agents/${bundle.agentId}/${bundle.bundleId}.json", bundleJson)
    
    // Or compress for efficient storage
    val compressedBytes = bundle.toCompressedBytes()
    saveToExternalStorage("portable-agents/${bundle.agentId}/${bundle.bundleId}.bin", compressedBytes)
}

/**
 * Example of restoring agent from a portable agent bundle.
 */
public suspend fun restorePortableAgent(agentContext: AIAgentContextBase, bundleId: String): Unit {
    // Load bundle from storage
    val bundleJson = loadFromExternalStorage("portable-agents/${agentContext.id}/$bundleId.json")
    val bundle = AgentSnapshotBundle.fromJson(bundleJson)
    
    println("Restoring portable agent:")
    println(bundle.summary())
    
    // Restore complete agent state
    val restoredCustomData = agentContext.restoreFromSnapshotBundle<kotlinx.serialization.json.JsonObject>(bundle.bundleId)
    
    // Extract and apply Minecraft-specific state
    val minecraftData = bundle.checkpoint.extraSnapshotData?.get("minecraft")
    if (minecraftData != null) {
        val minecraftState = Json.decodeFromJsonElement(MinecraftAgentState.serializer(), minecraftData)
        
        // Restore Minecraft world state
        teleportPlayer(minecraftState.position)
        restoreInventory(minecraftState.inventory)
        setPlayerHealth(minecraftState.health)
        setPlayerHunger(minecraftState.hunger)
        
        println("Restored agent to Minecraft state:")
        println("  Position: ${minecraftState.position}")
        println("  Task: ${minecraftState.currentTask}")
        println("  Inventory: ${minecraftState.inventory.size} items")
    }
    
    // The agent now has complete context:
    // - Knows where it was in the strategy execution
    // - Remembers all facts about the player and world
    // - Has the exact Minecraft world state (position, inventory, etc.)
    // - Can continue exactly where it left off
}

/**
 * Example of cross-environment agent transfer.
 */
public suspend fun transferAgentBetweenServers(
    sourceAgent: AIAgentContextBase,
    targetAgent: AIAgentContextBase
) {
    // Export from source environment
    val bundle = sourceAgent.exportSnapshotBundle(
        includeMemory = true,
        includeKVStore = true,
        metadata = buildJsonObject {
            put("transferReason", kotlinx.serialization.json.JsonPrimitive("server_migration"))
            put("sourceServer", kotlinx.serialization.json.JsonPrimitive("creative-builders.example.com"))
            put("targetServer", kotlinx.serialization.json.JsonPrimitive("survival-world.example.com"))
        }
    )
    
    // Transfer bundle (could be via API, file system, database, etc.)
    val transferPayload = bundle.toJson()
    
    // Restore in target environment
    val transferredBundle = AgentSnapshotBundle.fromJson(transferPayload)
    targetAgent.restoreFromSnapshotBundle(transferredBundle)
    
    println("Successfully transferred portable agent between servers!")
}

// Stub functions for Minecraft integration
private fun saveToExternalStorage(path: String, data: String): Unit = println("Saving to $path")
private fun saveToExternalStorage(path: String, data: ByteArray): Unit = println("Saving ${data.size} bytes to $path")
private fun loadFromExternalStorage(path: String): String = "{}" // Stub implementation
private fun teleportPlayer(position: MinecraftPosition): Unit = println("Teleporting to $position")
private fun restoreInventory(inventory: List<ItemStack>): Unit = println("Restoring ${inventory.size} items")
private fun setPlayerHealth(health: Float): Unit = println("Setting health to $health")
private fun setPlayerHunger(hunger: Int): Unit = println("Setting hunger to $hunger")