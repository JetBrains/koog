package ai.koog.agents.core.agent.config

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel

/**
 * Base interface for AI agent configs.
 */
public interface AIAgentConfigBase {

    /**
     * Defines the `Prompt` to be used in the AI agent's configuration.
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
}
