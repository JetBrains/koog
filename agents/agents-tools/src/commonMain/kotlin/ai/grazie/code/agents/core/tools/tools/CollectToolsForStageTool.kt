package ai.grazie.code.agents.core.tools.tools

import ai.grazie.code.agents.core.tools.SimpleTool
import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.serialization.serializeToolDescriptorsToJsonString


/**
 * A specialized service tool that returns a list of available tools for a specific stage.
 *
 * This class extends `SimpleTool` and utilizes an empty argument type. It retrieves the
 * tool information in the form of a JSON string, which encapsulates details about the available tools.
 *
 * @constructor Creates an instance of `CollectToolsForStageTool`.
 * @param name The name of the tool.
 * @param toolDescriptors A list of `ToolDescriptor` objects that define the tools available for a given stage.
 */
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
