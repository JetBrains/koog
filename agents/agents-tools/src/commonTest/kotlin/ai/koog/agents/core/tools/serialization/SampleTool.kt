package ai.koog.agents.core.tools.serialization

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

internal class SampleTool(name: String) : SimpleTool<SampleTool.Args>() {
    @Serializable
    data class Args(
        @property:LLMDescription("First tool argument 1")
        val arg1: String,
        val arg2: Int
    )

    override val argsSerializer = Args.serializer()

    override val name = name

    override val description: String = "First tool description"

    override suspend fun doExecute(args: Args): String = "Do nothing $args"
}
