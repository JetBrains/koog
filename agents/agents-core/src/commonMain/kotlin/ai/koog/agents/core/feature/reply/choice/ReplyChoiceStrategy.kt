package ai.koog.agents.core.feature.reply.choice

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.LLMReply

public interface ReplyChoiceStrategy {
    public suspend fun chooseReply(prompt: Prompt, replies: List<LLMReply>): LLMReply
}