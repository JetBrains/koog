@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi

/**
 * Common chained implementation for [AIAgentServiceBuilder] actual classes.
 */
public abstract class AIAgentServiceBuilderCommon<Self : AIAgentServiceBuilderCommon<Self>> internal constructor() : AIAgentBuilderBase<Self>() {

    /**
     * Configure a graph strategy and continue with a graph service builder.
     */
    public fun <Input, Output> graphStrategy(
        strategy: AIAgentGraphStrategy<Input, Output>
    ): GraphAgentServiceBuilder<Input, Output> = GraphAgentServiceBuilder(
        strategy = strategy,
        inputType = strategy.inputType,
        outputType = strategy.outputType,
        prompt = this.prompt,
        llmModel = this.llmModel,
        temperature = this.temperature,
        numberOfChoices = this.numberOfChoices,
        maxIterations = this.maxIterations,
        clock = this.clock,
    ).also {
        it.promptExecutor = this.promptExecutor
        it.toolRegistry = this.toolRegistry
    }

    /**
     * Configure a functional strategy and continue with a functional service builder.
     */
    public fun <Input, Output> functionalStrategy(
        strategy: AIAgentFunctionalStrategy<Input, Output>
    ): FunctionalAgentServiceBuilder<Input, Output> = FunctionalAgentServiceBuilder(
        strategy = strategy,
        prompt = this.prompt,
        llmModel = this.llmModel,
        temperature = this.temperature,
        numberOfChoices = this.numberOfChoices,
        maxIterations = this.maxIterations,
        clock = this.clock,
    ).also {
        it.promptExecutor = this.promptExecutor
        it.toolRegistry = this.toolRegistry
    }

    /**
     * Convenience build for GraphAIAgentService<String, String> using singleRunStrategy.
     */
    public fun build(): GraphAIAgentService<String, String> {
        return AIAgentServiceHelper(
            promptExecutor = validatedPromptExecutor,
            agentConfig = agentConfig,
            strategy = singleRunStrategy(),
            toolRegistry = toolRegistry,
        ) {}
    }
}
