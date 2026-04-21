package ai.koog.agents.features.tokenizer.feature

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

abstract class TestTool(name: String) : SimpleTool<TestTool.Args>(
    argsSerializer = Args.serializer(),
    name = name,
    description = "$name description"
) {
    @Serializable
    data class Args(
        @property:LLMDescription("question description")
        val question: String
    )

    override suspend fun execute(args: Args): String {
        return "Answer to ${args.question} from tool `$name`"
    }
}

object TestTool1 : TestTool("testTool1")
object TestTool2 : TestTool("testTool2")
