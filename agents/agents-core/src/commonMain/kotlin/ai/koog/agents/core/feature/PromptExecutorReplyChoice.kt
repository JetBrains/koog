package ai.koog.agents.core.feature

import ai.koog.agents.core.feature.reply.choice.ReplyChoiceStrategy
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message

public class PromptExecutorReplyChoice(
    private val executor: PromptExecutor,
    private val replyChoice: ReplyChoiceStrategy,
) : PromptExecutor by executor {
    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        val replies = executor.executeMultipleReplies(prompt, model, tools)

        return replyChoice.chooseReply(prompt, replies)
    }
}