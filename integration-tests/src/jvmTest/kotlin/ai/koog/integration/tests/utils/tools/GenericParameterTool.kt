package ai.koog.integration.tests.utils.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

object GenericParameterTool : SimpleTool<GenericParameterTool.Args>(
    argsSerializer = Args.serializer(),
    name = "generic_parameter_tool",
    description = "A tool that demonstrates handling of required and optional parameters"
) {
    @Serializable
    data class Args(
        @property:LLMDescription("A required string parameter that must be provided")
        val requiredArg: String,
        @property:LLMDescription("An optional string parameter that can be omitted")
        val optionalArg: String? = null
    )

    override suspend fun execute(args: Args): String {
        return "Generic parameter tool executed with requiredArg: ${args.requiredArg}, optionalArg: ${args.optionalArg ?: "not provided"}"
    }
}
