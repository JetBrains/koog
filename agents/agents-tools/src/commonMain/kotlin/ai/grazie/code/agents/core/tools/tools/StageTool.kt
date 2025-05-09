package ai.grazie.code.agents.core.tools.tools

import ai.grazie.code.agents.core.tools.Tool

class StageTool(
    val name: String,
    val tools: List<Tool<*, *>>,
) {
    fun getToolOrNull(tool: String): Tool<*, *>? {
        return tools.firstOrNull { it.name == tool }
    }

    fun getTool(tool: String): Tool<*, *> {
        return getToolOrNull(tool) ?: throw IllegalArgumentException("Tool \"$tool\" is not defined in stage \"$name\"")
    }

    class Builder internal constructor(
        private val stageName: String,
        private val toolListName: String,
    ) {
        private val tools = mutableListOf<Tool<*, *>>()

        fun tool(tool: Tool<*, *>) {
            require(tool.name != toolListName) { "Stage can't define \"$toolListName\" tool, it's a reserved tool with a list of tools" }
            require(tool.name !in tools.map { it.name }) { "Tool \"${tool.name}\" is already defined" }

            tools.add(tool)
        }

        fun tools(tools: List<Tool<*, *>>) = tools.forEach { tool(it) }

        internal fun build(): StageTool {
            require(tools.isNotEmpty()) { "No tools defined" }

            return StageTool(
                name = stageName,
                tools = tools + CollectToolsForStageTool(toolListName, tools.map { it.descriptor })
            )
        }
    }

    companion object {
        const val DEFAULT_STAGE_NAME = "default"
        const val STAGE_PARAM_NAME = "label"
        const val DEFAULT_TOOL_LIST_NAME = "__tools_list__"

        operator fun invoke(
            stageName: String = DEFAULT_STAGE_NAME,
            toolListName: String = DEFAULT_TOOL_LIST_NAME,
            init: Builder.() -> Unit
        ): StageTool = Builder(stageName, toolListName).apply(init).build()
    }
}

