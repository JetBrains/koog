@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")

package ai.koog.agents.core.agent.session

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel

public actual class AIAgentLLMReadSession actual constructor(
    tools: List<ToolDescriptor>,
    executor: PromptExecutor,
    prompt: Prompt,
    model: LLModel,
    config: AIAgentConfig,
) : AIAgentLLMSession by AIAgentLLMSessionImpl(executor, tools, prompt, model, config)
