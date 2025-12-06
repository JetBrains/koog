package ai.koog.agents.core.agent.config

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.processor.ResponseProcessor

/**
 * Configuration class for an AI agent that specifies the prompt, execution parameters, and behavior.
 *
 * This class is responsible for defining the various settings and components required
 * for an AI agent to operate. It includes the prompt configuration, iteration limits,
 * and strategies for handling missing tools during execution.
 *
 * @param prompt The initial prompt configuration for the agent, encapsulating messages, model, and parameters.
 * @param model The model to use for the agent's prompt execution
 * @param maxAgentIterations The maximum number of iterations allowed for an agent during its execution to prevent infinite loops.
 * @param missingToolsConversionStrategy Strategy used to determine how tool calls,
 *        present in the prompt but lacking definitions, are handled during agent execution.
 *        This property provides a mechanism to convert or format missing tool calls and result messages when they occur,
 *        typically due to differences in tool sets between steps or subgraphs while the same history is reused.
 *        This ensures the prompt remains consistent and readable for the model, even with undefined tools.
 * @param responseProcessor Optional processor for the agent's responses. If specified, will modify the responses from the llm.
 */
public class AIAgentConfig(
    override val prompt: Prompt,
    override val model: LLModel,
    public val maxAgentIterations: Int,
    public val missingToolsConversionStrategy: MissingToolsConversionStrategy =
        MissingToolsConversionStrategy.Missing(ToolCallDescriber.JSON),
    public val responseProcessor: ResponseProcessor? = null,
) : AIAgentConfigBase {

    init {
        require(maxAgentIterations > 0) { "maxAgentIterations must be greater than 0" }
    }

    /**
     * Companion object for providing utility methods related to `AIAgentConfig`.
     */
    public companion object {

        /**
         * Creates an AI agent configuration with a specified system prompt.
         *
         * This function initializes an instance of `AIAgentConfig` using the provided system-level prompt
         * and other optional parameters, such as the language model, configuration ID, and maximum agent iterations.
         *
         * @param prompt The content of the system prompts to define the context and instructions for the AI agent.
         * @param llm The Large Language Model (LLM) to be used for the AI agent. Defaults to OpenAIModels.Chat.GPT4o.
         * @param id The identifier for the agent configuration. Defaults to "koog-agents".
         * @param maxAgentIterations The maximum number of iterations the agent can perform to avoid infinite loops. Defaults to 3.
         * @return An instance of `AIAgentConfigBase` representing the AI agent configuration with the specified parameters.
         */
        public fun withSystemPrompt(
            prompt: String,
            llm: LLModel = OpenAIModels.Chat.GPT4o,
            id: String = "koog-agents",
            maxAgentIterations: Int = 3,
        ): AIAgentConfig =
            AIAgentConfig(
                prompt = prompt(id) {
                    system(prompt)
                },
                model = llm,
                maxAgentIterations = maxAgentIterations
            )
    }
}
