package ai.grazie.code.agents.core.tools.tools

import ai.grazie.code.agents.core.tools.Tool

public class ToolStage(
    public val name: String,
    public val tools: List<Tool<*, *>>,
) {
    public fun getToolOrNull(tool: String): Tool<*, *>? {
        return tools.firstOrNull { it.name == tool }
    }

    public fun getTool(tool: String): Tool<*, *> {
        return getToolOrNull(tool) ?: throw IllegalArgumentException("Tool \"$tool\" is not defined in stage \"$name\"")
    }

    public class Builder internal constructor(
        private val stageName: String,
        private val toolListName: String,
    ) {
        private val tools = mutableListOf<Tool<*, *>>()

        public fun tool(tool: Tool<*, *>) {
            require(tool.name != toolListName) { "Stage can't define \"$toolListName\" tool, it's a reserved tool with a list of tools" }
            require(tool.name !in tools.map { it.name }) { "Tool \"${tool.name}\" is already defined" }

            tools.add(tool)
        }

        public fun tools(tools: List<Tool<*, *>>): Unit = tools.forEach { tool(it) }

        internal fun build(): ToolStage {
            require(tools.isNotEmpty()) { "No tools defined" }

            return ToolStage(
                name = stageName,
                tools = tools + CollectToolsForStageTool(toolListName, tools.map { it.descriptor })
            )
        }
    }

    public companion object {
        public const val DEFAULT_STAGE_NAME: String = "default"
        public const val STAGE_PARAM_NAME: String = "label"
        public const val DEFAULT_TOOL_LIST_NAME: String = "__tools_list__"

        public operator fun invoke(
            stageName: String = DEFAULT_STAGE_NAME,
            toolListName: String = DEFAULT_TOOL_LIST_NAME,
            init: Builder.() -> Unit
        ): ToolStage = Builder(stageName, toolListName).apply(init).build()
    }
}

