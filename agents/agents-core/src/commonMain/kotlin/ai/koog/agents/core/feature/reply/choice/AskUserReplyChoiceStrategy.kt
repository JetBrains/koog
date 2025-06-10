package ai.koog.agents.core.feature.reply.choice

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.LLMReply

public class AskUserReplyChoiceStrategy(
    private val promptShowToUser: (Prompt) -> String = { "Current prompt: $it" },
    private val replyShowToUser: (LLMReply) -> String = { "$it" },
) : ReplyChoiceStrategy {
    override suspend fun chooseReply(prompt: Prompt, replies: List<LLMReply>): LLMReply {
        println(promptShowToUser(prompt))
        
        println("Available LLM replies")
        
        replies.withIndex().forEach { (index, reply) ->
            println("Reply number ${index + 1}: ${replyShowToUser(reply)}")
        }

        var replyNumber = ask(replies.size)
        while (replyNumber == null) {
            println("Invalid response.")
            replyNumber = ask(replies.size)
        }

        return replies[replyNumber - 1]
    }

    private fun ask(numReplies: Int): Int? {
        println("Please choose a reply. Enter a number between 1 and $numReplies: ")

        return readlnOrNull()?.toIntOrNull()?.takeIf { 1 <= it && it <= numReplies}
    }
}