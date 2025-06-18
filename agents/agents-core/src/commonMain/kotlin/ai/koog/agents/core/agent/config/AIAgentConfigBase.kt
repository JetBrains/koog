package ai.koog.agents.core.agent.config

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel

/**
 * Base interface for AI agent configs.
 */
public interface AIAgentConfigBase {

    /**
     * Defines the `Prompt` to be utilized in the AI agent's configuration.
     *
     * The `prompt` serves as the input structure for generating outputs from the language model and consists
     * of a list of messages, a unique identifier, and optional parameters. This property plays a role
     * in managing conversational state, input prompts, and configurations for the language model.
     */
    public val prompt: Prompt

    /**
     * Specifies the Large Language Model (LLM) used by the AI agent for generating responses.
     *
     * The model defines configurations such as the specific LLM provider, its identifier,
     * and supported capabilities (e.g., temperature control, tool usage). It plays a
     * vital role in determining how the AI agent processes and generates outputs
     * in response to user prompts and tasks.
     */
    public val model: LLModel

    /**
     * Specifies the maximum number of iterations an AI agent is allowed to perform during its operation.
     *
     * This value acts as a safeguard to prevent infinite loops in scenarios where the agent's logic
     * might otherwise continue indefinitely. It determines the number of steps the agent can take
     * while executing its task, and exceeding this limit will halt the agent's processing.
     */
    public val maxAgentIterations: Int

    /**
     * Strategy used to determine how tool calls, present in the prompt but lacking definitions,
     * are handled during agent execution.
     *
     * This property provides a mechanism to convert or format missing tool call and result messages when they occur,
     * typically due to differences in tool sets between steps or subgraphs while the same history is reused. This ensures
     * the prompt remains consistent and readable for the model, even with undefined tools.
     *
     * The specific behavior is determined by the selected `MissingToolsConversionStrategy`, which may either replace
     * all tool-related messages or only those corresponding to missing tool definitions.
     */
    public val missingToolsConversionStrategy: MissingToolsConversionStrategy
}
