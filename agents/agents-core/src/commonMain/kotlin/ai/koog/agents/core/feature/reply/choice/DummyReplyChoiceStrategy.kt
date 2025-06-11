package ai.koog.agents.core.feature.reply.choice

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.LLMReply

public class DummyReplyChoiceStrategy : ReplyChoiceStrategy {
    override suspend fun chooseReply(prompt: Prompt, replies: List<LLMReply>): LLMReply = replies.first()
}