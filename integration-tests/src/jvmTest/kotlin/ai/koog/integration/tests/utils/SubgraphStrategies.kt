package ai.koog.integration.tests.utils

import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.prompt.llm.LLModel

object SubgraphStrategies {

    /**
     * Creates a strategy with two sequential subgraphs for calculator operations.
     *
     * The first subgraph performs an addition operation, and the second subgraph
     * performs a multiplication operation on the result.
     *
     * @param tools The list of tools available in the subgraphs.
     * @param model The LLM model to use for the subgraphs.
     * @return A graph strategy with two sequential subgraphs.
     */
    @JvmStatic
    fun calculatorWithSubgraphs(
        model: LLModel,
    ): AIAgentGraphStrategy<String, String> = strategy("calculator-subgraph-strategy") {
        val calculationSubgraph by subgraphWithTask<String, String>(
            llmModel = model,
            runMode = ToolCalls.SEQUENTIAL,
        ) { input ->
            "You are a calculator assistant. Use the available tools to solve the following calculation: $input. " +
                "You MUST use tools to perform calculations. DO NOT calculate in your head."
        }

        nodeStart then calculationSubgraph then nodeFinish
    }
}
