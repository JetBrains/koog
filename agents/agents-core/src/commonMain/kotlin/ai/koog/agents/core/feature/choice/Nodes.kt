package ai.koog.agents.core.feature.choice

import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.result
import ai.koog.prompt.executor.model.LLMChoice

/**
 * A node that sends multiple tool execution results to the LLM and gets multiple LLM choices.
 *
 * @param name Optional name for the node.
 */
public fun AIAgentSubgraphBuilderBase<*, *>.nodeLLMSendResultsMultipleChoices(
    name: String? = null
): AIAgentNodeDelegate<List<ReceivedToolResult>, List<LLMChoice>> =
    node(name) { results ->
        llm.writeSession {
            updatePrompt {
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
public fun AIAgentSubgraphBuilderBase<*, *>.nodeSelectLLMChoice(
    choiceSelectionStrategy: ChoiceSelectionStrategy,
    name: String? = null
): AIAgentNodeDelegate<List<LLMChoice>, LLMChoice> =
    node(name) { choices ->
        llm.writeSession {
            choiceSelectionStrategy.choose(prompt, choices).also { choice ->
                choice.forEach { updatePrompt { message(it)} }
            }
        }
    }