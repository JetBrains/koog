package ai.koog.agents.features.tokenizer.feature

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

abstract class TestTool(override val name: String) : SimpleTool<TestTool.Args>() {
    @Serializable
    data class Args(
        @property:LLMDescription("question description")
        val question: String
    )

    override val argsSerializer: KSerializer<Args> = Args.serializer()

    override val description: String = "$name description"

    override suspend fun doExecute(args: Args): String {
        return "Answer to ${args.question} from tool `$name`"
    }
}

object TestTool1 : TestTool("testTool1")
object TestTool2 : TestTool("testTool2")
