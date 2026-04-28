package ai.koog.agents.ext.llm.choice

import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message

/**
 * A node that sends multiple tool execution results to the LLM and gets multiple LLM choices.
 *
 * @param name Optional name for the node.
 */
@AIAgentBuilderDslMarker
public fun nodeLLMSendResultsMultipleChoices(
    name: String? = null
): AIAgentNodeDelegate<List<ReceivedToolResult>, LLMChoice> =
    node(name) { results ->
        llm.writeSession {
            appendPrompt {
                user {
                    results.forEach { toolResult(it.toMessagePart()) }
                }
            }

            requestLLMMultipleChoices()
        }
    }

/**
 * A node that chooses an LLM choice based on the given strategy.
 *
 * @param choiceSelectionStrategy The strategy used to choose an LLM choice.
 * @param name Optional name for the node.
 */
@AIAgentBuilderDslMarker
public fun nodeSelectLLMChoice(
    choiceSelectionStrategy: ChoiceSelectionStrategy,
    name: String? = null
): AIAgentNodeDelegate<LLMChoice, Message.Assistant> =
    node(name) { choices ->
        llm.writeSession {
            choiceSelectionStrategy.choose(prompt, choices).also { choice ->
                appendPrompt { message(choice) }
            }
        }
    }
