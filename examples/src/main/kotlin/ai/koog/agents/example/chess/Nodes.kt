package ai.koog.agents.example.chess

import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegateBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase

/**
 * Chess position is (almost) completely defined by the board state,
 * So we can trim the history of the LLM to only contain the system prompt and the last move.
 */
fun <T> AIAgentSubgraphBuilderBase<*, *>.nodeTrimHistory(
    name: String? = null
): AIAgentNodeDelegateBase<T, T> =
    node(name) { result ->
        llm.writeSession {
            rewritePrompt { prompt ->
                val messages = prompt.messages

                prompt.copy(
                    messages = listOf(
                        messages.first(),
                        messages.last(),
                    )
                )
            }
        }

        result
    }