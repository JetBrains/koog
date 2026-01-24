package ai.koog.agents.ext.llm.choice

import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.result
import ai.koog.prompt.message.LLMChoice

/**
 * A node that sends multiple tool execution results to the LLM and gets multiple LLM choices.
 *
 * @param name Optional name for the node.
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeLLMSendResultsMultipleChoices(
    name: String? = null
): AIAgentNodeDelegate<List<ReceivedToolResult>, List<LLMChoice>> =
    node(name) { results ->
        llm.writeSession {
            appendPrompt {
                tool {
                    results.forEach { result(it) }
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
public fun AIAgentSubgraphBuilderBase<*, *>.nodeSelectLLMChoice(
    choiceSelectionStrategy: ChoiceSelectionStrategy,
    name: String? = null
): AIAgentNodeDelegate<List<LLMChoice>, LLMChoice> =
    node(name) { choices ->
        llm.writeSession {
            choiceSelectionStrategy.choose(prompt, choices).also { choice ->
                choice.forEach { appendPrompt { message(it) } }
            }
        }
    }
