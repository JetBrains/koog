package ai.koog.agents.core.feature.reply.choice

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.LLMReply

public class AskUserReplyChoiceStrategy(
    private val promptShowToUser: (Prompt) -> String = { "Current prompt: $it" },
    private val replyShowToUser: (LLMReply) -> String = { "$it" },
    private val print: (String) -> Unit = ::println,
    private val read: () -> String? = ::readlnOrNull
) : ReplyChoiceStrategy {
    override suspend fun chooseReply(prompt: Prompt, replies: List<LLMReply>): LLMReply {
        print(promptShowToUser(prompt))

        print("Available LLM replies")
        
        replies.withIndex().forEach { (index, reply) ->
            print("Reply number ${index + 1}: ${replyShowToUser(reply)}")
        }

        var replyNumber = ask(replies.size)
        while (replyNumber == null) {
            print("Invalid response.")
            replyNumber = ask(replies.size)
        }

        return replies[replyNumber - 1]
    }

    private fun ask(numReplies: Int): Int? {
        print("Please choose a reply. Enter a number between 1 and $numReplies: ")

        return read()?.toIntOrNull()?.takeIf { 1 <= it && it <= numReplies}
    }
}