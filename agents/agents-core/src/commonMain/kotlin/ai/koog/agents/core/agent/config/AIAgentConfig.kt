package ai.koog.agents.core.agent.config

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLModel

/**
 * Configuration class for a AI agent that specifies the prompt, execution parameters, and behavior.
 *
 * This class is responsible for defining the various settings and components required
 * for an AI agent to operate. It includes the prompt configuration, iteration limits,
 * and strategies for handling missing tools during execution.
 *
 * @param prompt The initial prompt configuration for the agent, encapsulating messages, model, and parameters.
 * @param model The model to use for the agent's prompt execution
 * @param maxAgentIterations The maximum number of iterations allowed for an agent during its execution to prevent infinite loops.
 * @param missingToolsConversionStrategy Strategy to handle missing tool definitions in the prompt. Defaults to applying formatting for missing tools. Ex.: if in the LLM history, there are some tools that are currently undefined in the agent (sub)graph.
 */
public class AIAgentConfig(
    override val prompt: Prompt,
    override val model: LLModel,
    override val maxAgentIterations: Int,
    override val missingToolsConversionStrategy: MissingToolsConversionStrategy = MissingToolsConversionStrategy.Missing(
        ToolCallDescriber.JSON
    )
): AIAgentConfigBase {

    public companion object {

        public fun withSystemPrompt(
            prompt: String,
            llm: LLModel = OpenAIModels.Chat.GPT4o,
            id: String = "code-engine-agents",
            maxAgentIterations: Int = 3,
        ): AIAgentConfigBase =
            AIAgentConfig(
                prompt = prompt(id) {
                    system(prompt)
                },
                model = llm,
                maxAgentIterations = maxAgentIterations
            )
    }
}