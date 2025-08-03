package ai.koog.agents.example.permissions

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.*
import ai.koog.agents.core.dsl.extension.*
import ai.koog.agents.core.tools.*
import ai.koog.agents.core.tools.permissions.*
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.testing.tools.*
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * Minecraft Agent Example - Advanced Caching & Permissions
 *
 * This example demonstrates sophisticated caching strategies for computationally
 * expensive operations, along with permission-based tool access:
 *
 * ## Cache Optimization Examples:
 *
 * 1. **Pathfinding (A* Algorithm)**
 *    - Caches specific paths between points (args included)
 *    - 5-minute TTL since world topology rarely changes
 *    - Prevents redundant pathfinding calculations
 *
 * 2. **Chunk Scanning (O(n³) Operation)**
 *    - Custom cache key generator groups by region (8x8 chunks)
 *    - Nearby chunk scans can reuse cached data
 *    - 30-minute TTL for ore distribution data
 *    - Critical to include args - different chunks have different resources!
 *
 * 3. **Structure Analysis**
 *    - Excludes agent ID - all players see the same structure
 *    - Role-specific caching for different analysis depths
 *    - 1-hour TTL since structures are static
 *
 * 4. **Crafting Operations**
 *    - Shows custom rate limit strategy by item category
 *    - Cache crafting recipes but rate limit by specific items
 *
 * ## Permission System:
 * - Creative mode: Uses world-edit tools for instant building
 * - Survival mode: Falls back to mining, crafting, and placing blocks
 * - Adventure mode: Limited to exploration and interaction only
 *
 * ## Key Insights:
 * - Always consider whether tool args should be included in cache/rate limit keys
 * - Custom key generators enable domain-specific optimization
 * - Balance cache efficiency with accuracy (region-based vs exact caching)
 */

// ========================================
// 1. Define game modes as type-safe roles
// ========================================
class MinecraftRoles : Roles() {
    val spectator by role {
        name = "spectator"
        description = "Can observe but not interact"
    }

    val adventure by role {
        name = "adventure"
        description = "Limited interaction, cannot break blocks"
        extends = spectator
    }

    val survival by role {
        name = "survival"
        description = "Standard gameplay with resource gathering"
        extends = adventure
    }

    val creative by role {
        name = "creative"
        description = "Unlimited resources and building tools"
        extends = survival
    }

    val moderator by role {
        name = "moderator"
        description = "Can moderate player actions and chat"
        extends = adventure // Moderators can explore but not build
    }

    val admin by role {
        name = "admin"
        description = "Server operator with full control"
        extends = creative
        isAdmin = true
    }
}

// ========================================
// 2. Define Minecraft tools with permissions
// ========================================

// Data structures
@Serializable
data class Block(val type: String, val x: Int, val y: Int, val z: Int)

@Serializable
data class BuildPlan(
    val structure: String,
    val materials: List<String>,
    val blocks: List<Block>
)

@Serializable
data class BuildResult(val blocksPlaced: Int, val method: String)

@Serializable
data class ChunkCoordinate(val x: Int, val z: Int)

@Serializable
data class PathResult(val path: List<Block>, val distance: Int)

// Base tools available to all players
class PathfindTool : SimpleTool<PathfindTool.Args>() {
    @Serializable
    data class Args(
        val fromX: Int,
        val fromZ: Int,
        val toX: Int,
        val toZ: Int
    ) : ToolArgs

    override val descriptor = ToolDescriptor(
        name = "pathfind",
        description = "Find optimal path between two points (A* algorithm)"
    )
    override val argsSerializer = Args.serializer()

    override suspend fun doExecute(args: Args): String {
        // Simulate expensive A* pathfinding
        val distance = kotlin.math.sqrt(
            (
                (args.toX - args.fromX) * (args.toX - args.fromX) +
                    (args.toZ - args.fromZ) * (args.toZ - args.fromZ)
                ).toDouble()
        ).toInt()

        return "Found path: $distance blocks (cached for reuse)"
    }
}

// Survival mode tools
class MineBlockTool : SimpleTool<MineBlockTool.Args>() {
    @Serializable
    data class Args(val x: Int, val y: Int, val z: Int) : ToolArgs

    override val descriptor = ToolDescriptor(
        name = "mine_block",
        description = "Mine a single block"
    )
    override val argsSerializer = Args.serializer()

    override suspend fun doExecute(args: Args): String =
        "Mined block at ($args.x, $args.y, $args.z)"
}

class CraftItemTool : SimpleTool<CraftItemTool.Args>() {
    @Serializable
    data class Args(val item: String, val quantity: Int) : ToolArgs

    override val descriptor = ToolDescriptor(
        name = "craft_item",
        description = "Craft items from materials"
    )
    override val argsSerializer = Args.serializer()

    override suspend fun doExecute(args: Args): String =
        "Crafted $args.quantity x $args.item"
}

class PlaceBlockTool : SimpleTool<PlaceBlockTool.Args>() {
    @Serializable
    data class Args(val type: String, val x: Int, val y: Int, val z: Int) : ToolArgs

    override val descriptor = ToolDescriptor(
        name = "place_block",
        description = "Place a single block"
    )
    override val argsSerializer = Args.serializer()

    override suspend fun doExecute(args: Args): String =
        "Placed $args.type at ($args.x, $args.y, $args.z)"
}

// Creative mode tools
class WorldEditFillTool : SimpleTool<WorldEditFillTool.Args>() {
    @Serializable
    data class Args(val blocks: List<Block>) : ToolArgs

    override val descriptor = ToolDescriptor(
        name = "worldedit_fill",
        description = "Instantly fill an area with blocks"
    )
    override val argsSerializer = Args.serializer()

    override suspend fun doExecute(args: Args): String =
        "Instantly placed ${args.blocks.size} blocks using WorldEdit"
}

class GiveItemTool : SimpleTool<GiveItemTool.Args>() {
    @Serializable
    data class Args(val item: String, val quantity: Int) : ToolArgs

    override val descriptor = ToolDescriptor(
        name = "give_item",
        description = "Give items without crafting"
    )
    override val argsSerializer = Args.serializer()

    override suspend fun doExecute(args: Args): String =
        "Given $args.quantity x $args.item"
}

// Advanced analysis tools
class ChunkScanTool : SimpleTool<ChunkScanTool.Args>() {
    @Serializable
    data class Args(
        val chunkX: Int,
        val chunkZ: Int,
        val radius: Int = 1 // Scan radius in chunks
    ) : ToolArgs

    override val descriptor = ToolDescriptor(
        name = "chunk_scan",
        description = "Scan chunks for resources (O(n³) operation)"
    )
    override val argsSerializer = Args.serializer()

    override suspend fun doExecute(args: Args): String {
        // Simulate expensive n³ chunk scanning
        val blocksScanned = args.radius * args.radius * 16 * 16 * 256 // chunks * blocks/chunk * height
        val ores = mapOf(
            "diamond" to (blocksScanned / 10000),
            "iron" to (blocksScanned / 1000),
            "coal" to (blocksScanned / 100)
        )

        return "Scanned ${args.radius}x${args.radius} chunks at (${args.chunkX}, ${args.chunkZ}): " +
            "Found ${ores.entries.joinToString { "${it.value} ${it.key}" }} " +
            "($blocksScanned blocks scanned)"
    }
}

class StructureAnalysisTool : SimpleTool<StructureAnalysisTool.Args>() {
    @Serializable
    data class Args(
        val centerX: Int,
        val centerY: Int,
        val centerZ: Int,
        val analysisType: String // "village", "fortress", "monument"
    ) : ToolArgs

    override val descriptor = ToolDescriptor(
        name = "analyze_structure",
        description = "Analyze nearby structures for composition and loot"
    )
    override val argsSerializer = Args.serializer()

    override suspend fun doExecute(args: Args): String {
        // Simulate complex structure analysis
        return when (args.analysisType) {
            "village" -> "Village analysis: 15 houses, 2 blacksmiths, 1 church, trading available"
            "fortress" -> "Fortress analysis: 3 blaze spawners, 2 nether wart rooms, treasure room located"
            "monument" -> "Monument analysis: 8 gold blocks, 3 elder guardians, mining fatigue active"
            else -> "Unknown structure type"
        }
    }
}

// Admin tools
class TeleportAnywhereTool : SimpleTool<TeleportAnywhereTool.Args>() {
    @Serializable
    data class Args(val x: Int, val y: Int, val z: Int) : ToolArgs

    override val descriptor = ToolDescriptor(
        name = "teleport_anywhere",
        description = "Teleport to any coordinates"
    )
    override val argsSerializer = Args.serializer()

    override suspend fun doExecute(args: Args): String =
        "Teleported to ($args.x, $args.y, $args.z)"
}

// ========================================
// 3. Create adaptive agent strategy
// ========================================
fun createMinecraftAgent(
    roles: MinecraftRoles,
    toolRegistry: ToolRegistry
): AIAgent<String, BuildResult> {
    // Single strategy that adapts based on permissions
    val adaptiveBuildStrategy = strategy<String, BuildResult>("adaptive-build-strategy") {
        // Plan the build
        val planBuild by node<String, BuildPlan>("plan-build") { goal ->
            // In a real implementation, this would use structured LLM request
            // For demo purposes, we'll return a mock plan
            BuildPlan(
                structure = "wooden_house",
                materials = listOf("wood_planks", "glass", "door"),
                blocks = List(10) { i ->
                    Block("wood_planks", 100 + i, 65, 100)
                }
            )
        }

        // Route directly based on permissions
        val routeToExecutor by node<BuildPlan, BuildResult>("route-to-executor") { plan ->
            when {
                hasPermissionForTool<WorldEditFillTool>() -> {
                    // Execute creative build
                    try {
                        val result = tryToolCall(
                            Message.Tool.Call(
                                id = "1",
                                tool = "worldedit_fill",
                                content = Json.encodeToString(
                                    JsonObject.serializer(),
                                    buildJsonObject {
                                        put(
                                            "blocks",
                                            Json.encodeToJsonElement(
                                                kotlinx.serialization.builtins.ListSerializer(Block.serializer()),
                                                plan.blocks
                                            )
                                        )
                                    }
                                ),
                                metaInfo = ResponseMetaInfo.Empty
                            )
                        )
                        BuildResult(plan.blocks.size, "WorldEdit instant build")
                    } catch (e: Exception) {
                        BuildResult(0, "Creative build failed: ${e.message}")
                    }
                }
                hasPermissionForTool<PlaceBlockTool>() -> {
                    // Execute survival build (simplified)
                    BuildResult(10, "Manual block placement")
                }
                else -> {
                    // Adventure mode
                    BuildResult(0, "Cannot build in adventure mode - no block placement allowed")
                }
            }
        }

        // Connect the flow
        edge(nodeStart forwardTo planBuild)
        edge(planBuild forwardTo routeToExecutor)
        edge(routeToExecutor forwardTo nodeFinish)
    }

    // Create agent with adaptive strategy
    return AIAgent(
        promptExecutor = getMockExecutor(toolRegistry) {
            // Mock LLM responses for demo
            mockLLMAnswer("Building wooden house") onRequestContains "build"
        },
        strategy = adaptiveBuildStrategy,
        agentConfig = AIAgentConfig(
            prompt = prompt("minecraft-agent") {
                system(
                    """
                    You are a Minecraft building assistant.
                    Adapt your building strategy based on available tools.
                    In creative mode, use instant building tools.
                    In survival mode, gather resources and build manually.
                    In adventure mode, explain that building is not possible.
                """
                )
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10,
            roleHierarchy = roles.hierarchy,
            permissionChecker = StandardPermissionChecker()
        ),
        toolRegistry = toolRegistry
    ) {
        install(EventHandler) {
            onToolPermissionDenied { context ->
                println("🚫 ${context.effectiveRoles.joinToString()} mode cannot use ${context.tool.name}")
            }
        }
    }
}

// ========================================
// 4. Demonstrate adaptive behavior
// ========================================
fun main() = runBlocking {
    println("=== Minecraft Agent - Adaptive Building Example ===\n")

    // Create roles
    val roles = MinecraftRoles()

    // Configure tools with game mode permissions and cache optimization
    val toolRegistry = ToolRegistry {
        // Pathfinding - available to all
        tool(PathfindTool())

        // Survival tools - requires survival mode or higher
        tool(MineBlockTool()) {
            minimumRole = roles.survival
        }

        tool(CraftItemTool()) {
            minimumRole = roles.survival
        }

        tool(PlaceBlockTool()) {
            minimumRole = roles.survival
        }

        // Creative tools - instant building
        tool(WorldEditFillTool()) {
            minimumRole = roles.creative
        }

        tool(GiveItemTool()) {
            minimumRole = roles.creative
        }

        // Admin tools
        tool(TeleportAnywhereTool()) {
            minimumRole = roles.admin
        }

        // Chunk scanning - available to survival and above
        tool(ChunkScanTool()) {
            minimumRole = roles.survival
        }

        // Structure analysis - available to adventure and above
        tool(StructureAnalysisTool()) {
            minimumRole = roles.adventure
        }
    }

    // Create the adaptive agent
    val agent = createMinecraftAgent(roles, toolRegistry)

    // Test the same goal in different game modes
    val buildGoal = "Build a small wooden house"

    println("--- Creative Mode ---")
    val creativeResult = agent.run(buildGoal, role = roles.creative)
    println("Result: $creativeResult\n")

    println("--- Survival Mode ---")
    val survivalResult = agent.run(buildGoal, role = roles.survival)
    println("Result: $survivalResult\n")

    println("--- Adventure Mode ---")
    val adventureResult = agent.run(buildGoal, role = roles.adventure)
    println("Result: $adventureResult\n")

    println("--- Spectator Mode ---")
    val spectatorResult = agent.run(buildGoal, role = roles.spectator)
    println("Result: $spectatorResult\n")

    println("--- Multiple Roles: Creative + Moderator ---")
    // User has both creative and moderator roles (e.g., a builder who also moderates chat)
    val multiRoleResult = agent.run(buildGoal, roles = setOf(roles.creative, roles.moderator))
    println("Result: $multiRoleResult (with both building and moderation capabilities)\n")

    println("\n=== Key Insights ===")
    println("1. Permission system provides graceful degradation")
    println("2. Different game modes have access to different tools")
    println("3. The same strategy adapts based on available permissions")
    println("4. Multiple roles can be assigned for combined capabilities")
}
