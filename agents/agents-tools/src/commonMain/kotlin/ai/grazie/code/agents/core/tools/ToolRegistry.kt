package ai.grazie.code.agents.core.tools

import ai.grazie.code.agents.core.tools.tools.ToolStage

/**
 * A registry that manages and organizes tools into stages for use by agents.
 * 
 * ToolRegistry serves as a central repository for all tools available to an agent,
 * organizing them into logical stages. Each stage represents a group of tools that
 * might be relevant for a particular phase or context of agent operation.
 * 
 * Key features:
 * - Organizes tools into named stages
 * - Provides methods to retrieve tools by name or type
 * - Allows finding stages by name or by the tools they contain
 * - Supports merging multiple registries
 * 
 * Usage examples:
 * 1. Creating a registry with a single stage:
 *    ```
 *    val registry = SimpleToolRegistry {
 *        tool(MyCustomTool())
 *        tool(AnotherTool())
 *    }
 *    ```
 * 
 * 2. Creating a registry with multiple stages:
 *    ```
 *    val registry = ToolRegistry {
 *        stage("planning") {
 *            tool(PlanningTool())
 *        }
 *        stage("execution") {
 *            tool(ExecutionTool())
 *        }
 *    }
 *    ```
 * 
 * 3. Merging registries:
 *    ```
 *    val combinedRegistry = registry1 + registry2
 *    ```
 * 
 * @property stages The list of tool stages contained in this registry
 */
public class ToolRegistry private constructor(
    public val stages: List<ToolStage>
) {
    /**
     * A lazily initialized map that associates stage names with lists of tool descriptors.
     * 
     * This property provides a convenient way to access the descriptors of all tools
     * organized by stage, which is useful for presenting available tools to agents
     * or for serialization purposes.
     * 
     * @return A map where keys are stage names and values are lists of tool descriptors
     */
    public val stagesToolDescriptors: Map<String, List<ToolDescriptor>> by lazy {
        stages.associate {
            it.name to it.tools.map { tool -> tool.descriptor }
        }
    }

    /**
     * Retrieves a tool by its name from any stage in the registry.
     * 
     * This method searches across all stages for a tool with the specified name.
     * 
     * @param toolName The name of the tool to retrieve
     * @return The tool with the specified name
     * @throws IllegalArgumentException if no tool with the specified name is found
     */
    public fun getTool(toolName: String): Tool<*, *> {
        return stages
            .flatMap { it.tools }
            .firstOrNull { it.name == toolName }
            ?: throw IllegalArgumentException("Tool \"$toolName\" is not defined")
    }

    /**
     * Retrieves a tool by its type from any stage in the registry.
     * 
     * This method searches across all stages for a tool of the specified type.
     * 
     * @param T The type of tool to retrieve
     * @return The tool of the specified type
     * @throws IllegalArgumentException if no tool of the specified type is found
     */
    public inline fun <reified T : Tool<*, *>> getTool(): T {
        return stages
            .flatMap { it.tools }
            .firstOrNull { it::class == T::class }
            ?.let { it as? T }
            ?: throw IllegalArgumentException("Tool with type ${T::class} is not defined")
    }

    /**
     * Finds the stage that contains a tool with the specified name.
     * 
     * @param tool The name of the tool to search for
     * @return The stage containing the tool, or null if no stage contains a tool with the specified name
     */
    public fun getStageByToolOrNull(tool: String): ToolStage? {
        return stages.firstOrNull { tool in it.tools.map { tool -> tool.name } }
    }

    /**
     * Finds the stage that contains a tool with the specified name.
     * 
     * @param tool The name of the tool to search for
     * @return The stage containing the tool
     * @throws IllegalArgumentException if no stage contains a tool with the specified name
     */
    public fun getStageByTool(tool: String): ToolStage {
        return getStageByToolOrNull(tool) ?: throw IllegalArgumentException("Tool \"$tool\" is not defined")
    }

    /**
     * Finds a stage by its name.
     * 
     * @param name The name of the stage to find
     * @return The stage with the specified name, or null if no stage with that name exists
     */
    public fun getStageByNameOrNull(name: String): ToolStage? {
        return stages.firstOrNull { it.name == name }
    }

    /**
     * Finds a stage by its name.
     * 
     * @param name The name of the stage to find
     * @return The stage with the specified name
     * @throws IllegalArgumentException if no stage with the specified name exists
     */
    public fun getStageByName(name: String): ToolStage {
        return getStageByNameOrNull(name) ?: throw IllegalArgumentException("Stage \"$name\" is not defined")
    }

    public infix fun with(toolRegistry: ToolRegistry): ToolRegistry {
        val thisStages: Map<String, List<Tool<*, *>>> = stages.associate { it.name to it.tools }
        val otherStages: Map<String, List<Tool<*, *>>> = toolRegistry.stages.associate { it.name to it.tools }

        val mergedStages = (thisStages.keys + otherStages.keys).map { stageName ->
            val thisStageTools = thisStages[stageName] ?: emptyList()
            val otherStageTools = otherStages[stageName] ?: emptyList()

            val mergedTools = (thisStageTools + otherStageTools).distinctBy { it.name }
            return@map ToolStage(stageName, mergedTools)
        }

        return ToolRegistry(mergedStages)
    }

    public operator fun plus(toolRegistry: ToolRegistry): ToolRegistry = this with toolRegistry

    public class Builder internal constructor() {
        private val stages = mutableListOf<ToolStage>()

        /**
         * Provide a pre-built stage, e.g., when using a tool stage from some pre-defined collection.
         */
        public fun stage(stage: ToolStage) {
            require(stage.name !in stages.map { it.name }) { "Stage \"${stage.name}\" is already defined" }

            val nonUniqueTools = stage.tools
                .map { it.name }
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .keys

            require(nonUniqueTools.isEmpty()) { "All tools must have unique names across stages, but got duplicates: $nonUniqueTools" }


            stages.add(stage)
        }

        /**
         * Build a new tool stage in place
         */
        public fun stage(
            stageName: String = ToolStage.DEFAULT_STAGE_NAME,
            toolListName: String = ToolStage.DEFAULT_TOOL_LIST_NAME,
            init: ToolStage.Builder.() -> Unit
        ): Unit = stage(ToolStage(stageName, toolListName, init))

        internal fun build(): ToolRegistry {
            return ToolRegistry(stages)
        }
    }

    /**
     * Companion object providing factory methods and constants for ToolRegistry.
     */
    public companion object {
        /**
         * Creates a new ToolRegistry using the provided builder initialization block.
         * 
         * @param init A lambda that configures the registry by adding stages and tools
         * @return A new ToolRegistry instance configured according to the initialization block
         */
        public operator fun invoke(init: Builder.() -> Unit): ToolRegistry = Builder().apply(init).build()

        /**
         * A constant representing an empty registry with no stages or tools.
         */
        public val EMPTY: ToolRegistry = ToolRegistry(emptyList())
    }
}

/**
 * Creates a simple ToolRegistry with a single default stage.
 * 
 * This is a convenience function for creating a registry with just one stage
 * containing the specified tools. The stage is named using the default stage name.
 * 
 * @param init A lambda that configures the stage by adding tools
 * @return A new ToolRegistry instance with a single stage containing the specified tools
 */
@Suppress("FunctionName")
public fun SimpleToolRegistry(init: ToolStage.Builder.() -> Unit): ToolRegistry = ToolRegistry { stage { init() } }
