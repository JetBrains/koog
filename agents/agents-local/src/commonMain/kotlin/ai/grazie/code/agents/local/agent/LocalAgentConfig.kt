package ai.grazie.code.agents.local.agent

import ai.grazie.code.agents.core.model.agent.AIAgentConfig
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import ai.jetbrains.code.prompt.llm.LLModel

/**
 * Configuration class for a local AI agent that specifies the prompt, execution parameters, and behavior.
 *
 * This class is responsible for defining the various settings and components required
 * for an AI agent to operate locally. It includes the prompt configuration, iteration limits,
 * and strategies for handling missing tools during execution.
 *
 * @param prompt The initial prompt configuration for the agent, encapsulating messages, model, and parameters.
 * @param maxAgentIterations The maximum number of iterations allowed for an agent during its execution, to prevent infinite loops.
 * @param missingToolsConversionStrategy Strategy to handle missing tool definitions in the prompt. Defaults to applying formatting for missing tools. Ex.: if in the LLM history, there are some tools that are currently undefined in the agent (sub)graph.
 */
open class LocalAgentConfig(
    val prompt: Prompt,
    val maxAgentIterations: Int,
    val missingToolsConversionStrategy: MissingToolsConversionStrategy = MissingToolsConversionStrategy.Missing(
        ToolCallDescriber.JSON
    ),
) : AIAgentConfig {
    companion object {
        fun withSystemPrompt(
            prompt: String,
            llm: LLModel = OpenAIModels.GPT4o,
            id: String = "code-engine-agents",
            maxAgentIterations: Int = 3,
        ): LocalAgentConfig {
            return LocalAgentConfig(
                prompt = prompt(llm, id) {
                    system(prompt)
                },
                maxAgentIterations = maxAgentIterations
            )
        }
    }
}