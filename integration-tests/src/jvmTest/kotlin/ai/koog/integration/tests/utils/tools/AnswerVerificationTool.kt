package ai.koog.integration.tests.utils.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

object AnswerVerificationTool : SimpleTool<AnswerVerificationTool.Args>(
    argsSerializer = Args.serializer(),
    name = "answer_verification_tool",
    description = "A tool for verifying the correctness of answers with optional confidence rating"
) {
    @Serializable
    data class Args(
        @property:LLMDescription("The answer text to verify for correctness")
        val answer: String,
        @property:LLMDescription("Confidence level in the verification (1-100, where 100 is highest confidence)")
        val confidence: Int? = null
    )

    override suspend fun execute(args: Args): String {
        return "Answer verification completed for: '${args.answer}', confidence level: ${args.confidence ?: "not specified"}"
    }
}
