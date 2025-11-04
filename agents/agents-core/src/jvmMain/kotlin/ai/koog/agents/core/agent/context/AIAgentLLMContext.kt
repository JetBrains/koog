@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent.context

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.session.AIAgentLLMReadSession
import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.runOnStrategyDispatcher
import ai.koog.agents.core.utils.submitToMainDispatcher
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import kotlinx.datetime.Clock
import java.util.function.Function

@OptIn(DetachedPromptExecutorAPI::class)
public actual open class AIAgentLLMContext @JvmOverloads actual constructor(
    tools: List<ToolDescriptor>,
    toolRegistry: ToolRegistry,
    prompt: Prompt,
    model: LLModel,
    promptExecutor: PromptExecutor,
    environment: AIAgentEnvironment,
    config: AIAgentConfig,
    clock: Clock,
    internal actual val delegate: AIAgentLLMContextImpl
) : AIAgentLLMContextAPI by AIAgentLLMContextImpl(
    tools,
    toolRegistry,
    prompt,
    model,
    promptExecutor,
    environment,
    config,
    clock
) {

    @JvmOverloads
    public actual final override suspend fun copy(
        tools: List<ToolDescriptor>,
        toolRegistry: ToolRegistry,
        prompt: Prompt,
        model: LLModel,
        promptExecutor: PromptExecutor,
        environment: AIAgentEnvironment,
        config: AIAgentConfig,
        clock: Clock
    ): AIAgentLLMContext = delegate.copy(tools, toolRegistry, prompt, model, promptExecutor, environment, config, clock)

    /**
     * Executes a block of code within a write session for the AI Agent LLM context.
     *
     * This function provides a mechanism to modify or interact with the AI Agent's state in a mutable
     * manner, ensuring proper synchronization and execution within the main dispatcher.
     *
     * @param T The return type of the operation performed within the write session.
     * @param block A function that takes an instance of `AIAgentLLMWriteSession` as its receiver and
     *              performs operations on it. The result of this block is the return value of the enclosing function.
     * @return The result produced by the provided block of code.
     */
    @JavaAPI
    public fun <T> writeSession(block: Function<AIAgentLLMWriteSession, T>): T = config.runOnStrategyDispatcher {
        writeSession {
            config.submitToMainDispatcher {
                block.apply(this)
            }
        }
    }

    /**
     * Executes a read-only session within the context of `AIAgentLLMReadSession` and returns the result of the provided block.
     *
     * This method is designed for Java interop and facilitates read operations on the `AIAgentLLMReadSession`,
     * ensuring the provided block is executed within the specified context.
     *
     * @param block A function that performs actions within the `AIAgentLLMReadSession` and computes a result of type `T`.
     * @return The result of executing the provided block within the `AIAgentLLMReadSession` context.
     */
    @JavaAPI
    public fun <T> readSession(block: Function<AIAgentLLMReadSession, T>): T = config.runOnStrategyDispatcher {
        readSession {
            config.submitToMainDispatcher {
                block.apply(this)
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    public actual override suspend fun <T> readSession(block: suspend AIAgentLLMReadSession.() -> T): T =
        delegate.readSession(block)

    @JvmOverloads
    public actual final override fun copy(
        tools: List<ToolDescriptor>,
        prompt: Prompt,
        model: LLModel,
        promptExecutor: PromptExecutor,
        environment: AIAgentEnvironment,
        config: AIAgentConfig,
        clock: Clock
    ): AIAgentLLMContext = delegate.copy(tools, prompt, model, promptExecutor, environment, config, clock)
}
