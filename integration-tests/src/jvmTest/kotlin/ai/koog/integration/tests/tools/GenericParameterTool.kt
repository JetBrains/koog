package ai.koog.integration.tests.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

object GenericParameterTool : SimpleTool<GenericParameterTool.Args>() {
    @Serializable
    data class Args(
        @property:LLMDescription("A required string parameter that must be provided")
        val requiredArg: String,
        @property:LLMDescription("An optional string parameter that can be omitted")
        val optionalArg: String? = null
    )

    override val argsSerializer = Args.serializer()

    override val name: String = "generic_parameter_tool"
    override val description: String = "A tool that demonstrates handling of required and optional parameters"

    override suspend fun doExecute(args: Args): String {
        return "Generic parameter tool executed with requiredArg: ${args.requiredArg}, optionalArg: ${args.optionalArg ?: "not provided"}"
    }
}
