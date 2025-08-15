package ai.koog.agents.features.tokenizer.feature

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

abstract class TestTool(toolName: String) : SimpleTool<TestTool.Args>() {
    @Serializable
    data class Args(val question: String) : ToolArgs

    override val argsSerializer: KSerializer<Args> = Args.serializer()

    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = toolName,
        description = "$toolName description",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "question",
                description = "question description",
                type = ToolParameterType.String
            )
        )
    )

    override suspend fun doExecute(args: Args): String {
        return "Answer to ${args.question} from tool `$name`"
    }
}

object TestTool1 : TestTool("testTool1")
object TestTool2 : TestTool("testTool2")
