@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent.session

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.processor.ResponseProcessor

/**
 * Represents a session for interacting with a language model (LLM) in a read-only context within an AI agent setup.
 */
public expect class AIAgentLLMReadSession
/**
 * Internal constructor used by the session infrastructure to create a read-only LLM session.
 *
 * @param tools A list of tool descriptors available in this session.
 * @param executor The prompt executor used to send requests to the model.
 * @param prompt The current prompt used as input for requests.
 * @param model The active model used to execute requests.
 * @param responseProcessor Optional response post-processor for model outputs.
 * @param config Agent configuration used by this session.
 */
internal constructor(
    tools: List<ToolDescriptor>,
    executor: PromptExecutor,
    prompt: Prompt,
    model: LLModel,
    responseProcessor: ResponseProcessor?,
    config: AIAgentConfig,
) : AIAgentLLMReadSessionCommon
