package ai.grazie.code.agents.core.tools

import ai.grazie.code.agents.core.tools.tools.StageTool

class ToolRegistry private constructor(
    val stages: List<StageTool>
) {
    val stagesToolDescriptors: Map<String, List<ToolDescriptor>> by lazy {
        stages.associate {
            it.name to it.tools.map { tool -> tool.descriptor }
        }
    }

    fun getTool(toolName: String): Tool<*, *> {
        return stages
            .flatMap { it.tools }
            .firstOrNull { it.name == toolName }
            ?: throw IllegalArgumentException("Tool \"$toolName\" is not defined")
    }

    inline fun <reified T : Tool<*, *>> getTool(): T {
        return stages
            .flatMap { it.tools }
            .firstOrNull { it::class == T::class }
            ?.let { it as? T }
            ?: throw IllegalArgumentException("Tool with type ${T::class} is not defined")
    }

    fun getStageByToolOrNull(tool: String): StageTool? {
        return stages.firstOrNull { tool in it.tools.map { tool -> tool.name } }
    }

    fun getStageByTool(tool: String): StageTool {
        return getStageByToolOrNull(tool) ?: throw IllegalArgumentException("Tool \"$tool\" is not defined")
    }

    fun getStageByNameOrNull(name: String): StageTool? {
        return stages.firstOrNull { it.name == name }
    }

    fun getStageByName(name: String): StageTool {
        return getStageByNameOrNull(name) ?: throw IllegalArgumentException("Stage \"$name\" is not defined")
    }

    infix fun with(toolRegistry: ToolRegistry): ToolRegistry {
        val thisStages: Map<String, List<Tool<*, *>>> = stages.associate { it.name to it.tools }
        val otherStages: Map<String, List<Tool<*, *>>> = toolRegistry.stages.associate { it.name to it.tools }

        val mergedStages = (thisStages.keys + otherStages.keys).map { stageName ->
            val thisStageTools = thisStages[stageName] ?: emptyList()
            val otherStageTools = otherStages[stageName] ?: emptyList()

            val mergedTools = (thisStageTools + otherStageTools).distinctBy { it.name }
            return@map StageTool(stageName, mergedTools)
        }

        return ToolRegistry(mergedStages)
    }

    operator fun plus(toolRegistry: ToolRegistry) = this with toolRegistry

    class Builder internal constructor() {
        private val stages = mutableListOf<StageTool>()

        /**
         * Provide a pre-built stage, e.g., when using a tool stage from some pre-defined collection.
         */
        fun stage(stage: StageTool) {
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
        fun stage(
            stageName: String = StageTool.DEFAULT_STAGE_NAME,
            toolListName: String = StageTool.DEFAULT_TOOL_LIST_NAME,
            init: StageTool.Builder.() -> Unit
        ) = stage(StageTool(stageName, toolListName, init))

        internal fun build(): ToolRegistry {
            return ToolRegistry(stages)
        }
    }

    companion object {
        operator fun invoke(init: Builder.() -> Unit): ToolRegistry = Builder().apply(init).build()

        val EMPTY = ToolRegistry(emptyList())
    }
}

@Suppress("FunctionName")
fun SimpleToolRegistry(init: StageTool.Builder.() -> Unit): ToolRegistry = ToolRegistry { stage { init() } }
