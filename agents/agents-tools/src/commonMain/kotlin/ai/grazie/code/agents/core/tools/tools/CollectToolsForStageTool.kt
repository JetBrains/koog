package ai.grazie.code.agents.core.tools.tools

import ai.grazie.code.agents.core.tools.SimpleTool
import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.serialization.serializeToolDescriptorsToJsonString


class CollectToolsForStageTool internal constructor(
    name: String,
    toolDescriptors: List<ToolDescriptor>
) : SimpleTool<Tool.EmptyArgs>() {
    override val argsSerializer = EmptyArgs.serializer()

    override val descriptor = ToolDescriptor(
        name = name,
        description = "Service tool. Returns a list of available tools for a given stage."
    )

    private val toolDescriptorsJson by lazy { serializeToolDescriptorsToJsonString(toolDescriptors) }

    override suspend fun doExecute(args: EmptyArgs): String {
        return toolDescriptorsJson
    }
}
