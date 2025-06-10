package ai.koog.agents.core.feature.reply.choice

import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegateBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.result
import ai.koog.prompt.executor.model.LLMReply
import kotlin.invoke
import kotlin.text.iterator

/**
 * A node that sends multiple tool execution results to the LLM and gets multiple LLM replies.
 *
 * @param name Optional name for the node.
 */
public fun AIAgentSubgraphBuilderBase<*, *>.nodeLLMSendResultsMultipleReplies(
    name: String? = null
): AIAgentNodeDelegateBase<List<ReceivedToolResult>, List<LLMReply>> =
    node(name) { results ->
        llm.writeSession {
            updatePrompt {
                tool {
                    results.forEach { result(it) }
                }
            }

            requestLLMMultipleReplies()
        }
    }


/**
 * A node that chooses an LLM reply based on the given strategy.
 *
 * @param name Optional name for the node.
 */
public fun AIAgentSubgraphBuilderBase<*, *>.nodeChooseLLMReply(
    replyChoiceStrategy: ReplyChoiceStrategy,
    name: String? = null
): AIAgentNodeDelegateBase<List<LLMReply>, LLMReply> =
    node(name) { replies ->
        llm.writeSession {
            replyChoiceStrategy.chooseReply(prompt, replies).also { reply ->
                reply.forEach { updatePrompt { message(it)} }
            }
        }
    }