@file:OptIn(DetachedPromptExecutorAPI::class, InternalAgentsApi::class)
@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.session.AIAgentLLMReadSession
import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.RWLock
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.processor.ResponseProcessor
import kotlinx.datetime.Clock

internal class AIAgentLLMContextImpl(
    override var tools: List<ToolDescriptor>,
    override val toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    override var prompt: Prompt,
    override var model: LLModel,
    override var responseProcessor: ResponseProcessor?,
    override val promptExecutor: PromptExecutor,
    override val environment: AIAgentEnvironment,
    override val config: AIAgentConfig,
    override val clock: Clock
) : AIAgentLLMContextAPI {
    public override suspend fun withPrompt(block: Prompt.() -> Prompt): Unit = rwLock.withReadLock {
        this.prompt = prompt.block()
    }

    public override suspend fun copy(
        tools: List<ToolDescriptor>,
        toolRegistry: ToolRegistry,
        prompt: Prompt,
        model: LLModel,
        responseProcessor: ResponseProcessor?,
        promptExecutor: PromptExecutor,
        environment: AIAgentEnvironment,
        config: AIAgentConfig,
        clock: Clock
    ): AIAgentLLMContext = rwLock.withReadLock {
        AIAgentLLMContext(
            tools = tools,
            toolRegistry = toolRegistry,
            prompt = prompt,
            model = model,
            promptExecutor = promptExecutor,
            environment = environment,
            config = config,
            clock = clock,
            responseProcessor = responseProcessor
        )
    }

    private val rwLock = RWLock()

    @OptIn(ExperimentalStdlibApi::class)
    public override suspend fun <T> writeSession(block: suspend AIAgentLLMWriteSession.() -> T): T =
        rwLock.withWriteLock {
            val session =
                AIAgentLLMWriteSession(
                    environment,
                    promptExecutor,
                    tools,
                    toolRegistry,
                    prompt,
                    model,
                    responseProcessor,
                    config,
                    clock
                )

            session.use {
                val result = it.block()

                // update tools and prompt after session execution
                this.prompt = it.prompt
                this.tools = it.tools
                this.model = it.model

                result
            }
        }

    @OptIn(ExperimentalStdlibApi::class)
    public override suspend fun <T> readSession(block: suspend AIAgentLLMReadSession.() -> T): T = rwLock.withReadLock {
        val session = AIAgentLLMReadSession(tools, promptExecutor, prompt, model, responseProcessor, config)

        session.use { block(it) }
    }

    public override fun copy(
        tools: List<ToolDescriptor>,
        prompt: Prompt,
        model: LLModel,
        responseProcessor: ResponseProcessor?,
        promptExecutor: PromptExecutor,
        environment: AIAgentEnvironment,
        config: AIAgentConfig,
        clock: Clock,
    ): AIAgentLLMContext {
        return AIAgentLLMContext(
            tools,
            toolRegistry,
            prompt,
            model,
            responseProcessor,
            promptExecutor,
            environment,
            config,
            clock
        )
    }
}
