package ai.koog.integration.tests.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.Serializable

object GenericParameterTool : SimpleTool<GenericParameterTool.Args>() {
    @Serializable
    data class Args(
        val requiredArg: String,
        val optionalArg: String? = null
    )

    override val argsSerializer = Args.serializer()

    override val descriptor = ToolDescriptor(
        name = "generic_parameter_tool",
        description = "A tool that demonstrates handling of required and optional parameters",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "requiredArg",
                description = "A required string parameter that must be provided",
                type = ToolParameterType.String
            )
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor(
                name = "optionalArg",
                description = "An optional string parameter that can be omitted",
                type = ToolParameterType.String
            )
        )
    )

    override suspend fun doExecute(args: Args): String {
        return "Generic parameter tool executed with requiredArg: ${args.requiredArg}, optionalArg: ${args.optionalArg ?: "not provided"}"
    }
}
