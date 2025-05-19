package ai.koog.agents.core.agent.session

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel

/**
 * Represents a session for interacting with a language model (LLM) in a read-only context within an AI agent setup.
 * This session is configured with a set of tools, an executor for handling prompt execution, a prompt definition,
 * a language model, and specific session configurations.
 *
 * @constructor Internal constructor to initialize a new read session for the AI agent.
 * @param tools A list of tool descriptors that define the tools available for this session.
 * @param executor The `PromptExecutor` responsible for handling execution of prompts within this session.
 * @param prompt The `Prompt` object specifying the input messages and parameters for the session.
 * @param model The language model instance to be used for processing prompts in this session.
 * @param config The configuration settings for the AI agent session.
 */
public class AIAgentLLMReadSession internal constructor(
    tools: List<ToolDescriptor>,
    executor: PromptExecutor,
    prompt: Prompt,
    model: LLModel,
    config: AIAgentConfig,
) : AIAgentLLMSession(executor, tools, prompt, model, config)
