package ai.koog.agents.core.agent.config

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel

/**
 * Base interface for AI agent configs.
 */
public interface AIAgentConfigBase {

    public val prompt: Prompt

    public val model: LLModel

    public val maxAgentIterations: Int

    public val missingToolsConversionStrategy: MissingToolsConversionStrategy
}
