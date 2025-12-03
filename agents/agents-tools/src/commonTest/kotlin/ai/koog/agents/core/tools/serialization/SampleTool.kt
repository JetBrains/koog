package ai.koog.agents.core.tools.serialization

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

internal class SampleTool(name: String) : SimpleTool<SampleTool.Args>(
    argsSerializer = Args.serializer(),
    name = name,
    description = "First tool description",
) {
    @Serializable
    data class Args(
        @property:LLMDescription("First tool argument 1")
        val arg1: String,
        val arg2: Int
    )

    override suspend fun execute(args: Args): String = "Do nothing $args"
}
