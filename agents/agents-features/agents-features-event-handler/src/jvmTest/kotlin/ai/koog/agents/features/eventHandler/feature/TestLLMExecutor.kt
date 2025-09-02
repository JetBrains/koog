package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock

class TestLLMExecutor(val clock: Clock) : PromptExecutor {
    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        return listOf(handlePrompt(prompt))
    }

    override fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> = flow {
        emit(handlePrompt(prompt).content)
    }

    private fun handlePrompt(prompt: Prompt): Message.Response {
        // For a compression test, return a summary
        if (prompt.messages.any { it.content.contains("Summarize all the main achievements") }) {
            return Message.Assistant(
                "Here's a summary of the conversation: Test user asked questions and received responses.",
                metaInfo = ResponseMetaInfo.create(clock)
            )
        }

        return Message.Assistant("Default test response", metaInfo = ResponseMetaInfo.create(clock))
    }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult {
        throw UnsupportedOperationException("Moderation is not needed here")
    }
}
