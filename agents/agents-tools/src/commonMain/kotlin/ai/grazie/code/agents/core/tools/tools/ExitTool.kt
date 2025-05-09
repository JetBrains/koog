package ai.grazie.code.agents.core.tools.tools

import ai.grazie.code.agents.core.tools.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

object ExitTool : SimpleTool<ExitTool.Args>() {
    @Serializable
    data class Args(val message: String) : Tool.Args

    override suspend fun doExecute(args: Args): String {
        return "DONE"
    }

    override val argsSerializer: KSerializer<Args>
        get() = Args.serializer()

    override val descriptor: ToolDescriptor
        get() = ToolDescriptor(
            name = "__exit__",
            description = "Service tool, used by the agent to end conversation on user request or agent decision",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "message", description = "Final message of the agent", type = ToolParameterType.String
                )
            )
        )
}
