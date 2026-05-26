package ai.koog.agents.planner.llm

import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.planner.AIAgentPlannerStrategy

/**
 * Builder for LLM-based [AIAgentPlannerStrategy] operating on a [String] state.
 *
 * Obtain via [ai.koog.agents.planner.Planners.llmBased].
 */
public class SimpleLLMPlannerBuilder(private val name: String) {
    private var compressionStrategy: HistoryCompressionStrategy = HistoryCompressionStrategy.NoCompression
    private var useCritic: Boolean = false

    /**
     * Configures the history compression strategy for the planner.
     */
    public fun withHistoryCompression(
        historyCompressionStrategy: HistoryCompressionStrategy
    ): SimpleLLMPlannerBuilder = apply {
        compressionStrategy = historyCompressionStrategy
    }

    /**
     * Enables the critic for the planner.
     */
    public fun withCritic(): SimpleLLMPlannerBuilder = apply { useCritic = true }

    /**
     * Builds the [AIAgentPlannerStrategy] with the specified configuration.
     */
    public fun build(): AIAgentPlannerStrategy<String, String> = AIAgentPlannerStrategy(
        name = name,
        planner = if (useCritic) {
            SimpleLLMWithCriticPlanner(compressionStrategy)
        } else {
            SimpleLLMPlanner(compressionStrategy)
        }
    )
}
