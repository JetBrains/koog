package ai.koog.integration.tests.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.Serializable

object AnswerVerificationTool : SimpleTool<AnswerVerificationTool.Args>() {
    @Serializable
    data class Args(
        val answer: String,
        val confidence: Int? = null
    )

    override val argsSerializer = Args.serializer()

    override val descriptor = ToolDescriptor(
        name = "answer_verification_tool",
        description = "A tool for verifying the correctness of answers with optional confidence rating",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "answer",
                description = "The answer text to verify for correctness",
                type = ToolParameterType.String
            )
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor(
                name = "confidence",
                description = "Confidence level in the verification (1-100, where 100 is highest confidence)",
                type = ToolParameterType.Integer
            )
        )
    )

    override suspend fun doExecute(args: Args): String {
        return "Answer verification completed for: '${args.answer}', confidence level: ${args.confidence ?: "not specified"}"
    }
}
