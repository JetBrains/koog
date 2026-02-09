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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlin.coroutines.CoroutineContext

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
    // FIXME: It acquires a read lock but performs a write operation. This can lead to lost updates.
    public override suspend fun withPrompt(block: Prompt.() -> Prompt): Unit = rwLock.withReadLock {
        this.prompt = prompt.block()
    }

    @OptIn(ExperimentalStdlibApi::class)
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
    ): AIAgentLLMContext {
        val currentSessionContext = currentCoroutineContext()[sessionContextKey]

        // If we're already in a session (read or write), we have read access
        // No need to acquire lock again - avoids deadlock when called from writeSession
        return if (currentSessionContext != null) {
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
        } else {
            rwLock.withReadLock {
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
        }
    }

    private val rwLock = RWLock()
    private val sessionContextKey = SessionContextKey(this)

    @OptIn(ExperimentalStdlibApi::class)
    public override suspend fun <T> writeSession(block: suspend AIAgentLLMWriteSession.() -> T): T {
        val currentSessionContext = currentCoroutineContext()[sessionContextKey]

        // If we're already in a write session, reuse the existing session
        if (currentSessionContext?.writeSession != null) {
            return currentSessionContext.writeSession.block()
        }

        // If we're in a read session and try to upgrade to write, this would deadlock
        if (currentSessionContext?.readSession != null) {
            throw IllegalStateException(
                "Cannot acquire write session while in read session - would cause deadlock. " +
                    "Session upgrade from read to write is not supported."
            )
        }

        return rwLock.withWriteLock {
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
                val result = withContext(SessionContextElement(sessionContextKey, writeSession = it)) {
                    it.block()
                }

                // update tools and prompt after session execution
                this.prompt = it.prompt
                this.tools = it.tools
                this.model = it.model

                result
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    public override suspend fun <T> readSession(block: suspend AIAgentLLMReadSession.() -> T): T {
        val currentSessionContext = currentCoroutineContext()[sessionContextKey]

        // If we're already in a write session, we can read (write implies read access)
        if (currentSessionContext?.writeSession != null) {
            // Create a read session view from the write session's current state
            val readSession = AIAgentLLMReadSession(
                currentSessionContext.writeSession.tools,
                promptExecutor,
                currentSessionContext.writeSession.prompt,
                currentSessionContext.writeSession.model,
                currentSessionContext.writeSession.responseProcessor,
                config
            )
            return readSession.use { block(it) }
        }

        // If we're already in a read session, reuse it
        if (currentSessionContext?.readSession != null) {
            return currentSessionContext.readSession.block()
        }

        return rwLock.withReadLock {
            val session = AIAgentLLMReadSession(tools, promptExecutor, prompt, model, responseProcessor, config)

            session.use {
                withContext(SessionContextElement(sessionContextKey, readSession = it)) {
                    block(it)
                }
            }
        }
    }

    /**
     * CAVEAT: Reads mutable state (tools, prompt, model, responseProcessor) without acquiring any lock.
     * If another coroutine is in a writeSession modifying these fields, copy can observe partially updated/inconsistent state.
     */
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

/**
 * Coroutine context key for tracking session state per AIAgentLLMContextImpl instance.
 */
private data class SessionContextKey(
    val context: AIAgentLLMContextImpl
) : CoroutineContext.Key<SessionContextElement>

/**
 * Coroutine context element that holds the current session state.
 * Used to detect nested session calls and enable re-entrancy.
 */
private class SessionContextElement(
    override val key: SessionContextKey,
    val writeSession: AIAgentLLMWriteSession? = null,
    val readSession: AIAgentLLMReadSession? = null
) : CoroutineContext.Element
