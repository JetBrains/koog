package ai.grazie.code.agents.core.tools.serialization

import ai.grazie.code.agents.core.tools.SimpleTool
import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterType
import kotlinx.serialization.Serializable

internal class SampleTool(name: String) : SimpleTool<SampleTool.Args>() {
    @Serializable
    data class Args(val arg1: String, val arg2: Int) : Tool.Args

    override val argsSerializer = Args.serializer()

    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = name,
        description = "First tool description",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "arg1",
                description = "First tool argument 1",
                type = ToolParameterType.String,
                defaultValue = "hello"
            ),
        )
    )

    override suspend fun doExecute(args: Args): String = "Do nothing $args"
}
