@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.processor.ResponseProcessor
import kotlinx.datetime.Clock

public actual open class AIAgentLLMContext internal actual constructor(
    internal actual val delegate: AIAgentLLMContextImpl
) : AIAgentLLMContextAPI by delegate {
    public actual constructor(
        tools: List<ToolDescriptor>,
        toolRegistry: ToolRegistry,
        prompt: Prompt,
        model: LLModel,
        responseProcessor: ResponseProcessor?,
        promptExecutor: PromptExecutor,
        environment: AIAgentEnvironment,
        config: AIAgentConfig,
        clock: Clock
    ) : this(
        delegate = AIAgentLLMContextImpl(
            tools, toolRegistry, prompt, model, responseProcessor, promptExecutor, environment, config, clock
        )
    )
}
