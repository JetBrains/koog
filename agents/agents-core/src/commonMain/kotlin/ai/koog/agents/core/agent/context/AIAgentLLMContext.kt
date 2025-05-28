package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.agents.core.agent.session.AIAgentLLMReadSession
import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.RWLock
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import kotlinx.datetime.Clock

/**
 * Represents the context for an AI agent LLM, managing tools, prompt handling, and interaction with the
 * environment and execution layers. It provides mechanisms for concurrent read and write operations
 * through sessions, ensuring thread safety.
 *
 * @property tools A list of tool descriptors available for the context.
 * @property toolRegistry A registry that contains metadata about available tools.
 * @property prompt The current LLM prompt being used or updated in write sessions.
 * @property model The current LLM model being used or updated in write sessions.
 * @property promptExecutor The executor responsible for performing operations based on the current prompt.
 * @property environment The environment that manages tool execution and interaction with external dependencies.
 * @property clock The clock used for timestamps of messages
 */
public data class AIAgentLLMContext(
    internal var tools: List<ToolDescriptor>,
    val toolRegistry: ToolRegistry = ToolRegistry.Companion.EMPTY,
    private var prompt: Prompt,
    private var model: LLModel,
    internal val promptExecutor: PromptExecutor,
    private val environment: AIAgentEnvironment,
    private val config: AIAgentConfigBase,
    private val clock: Clock
) {

    private val rwLock = RWLock()

    /**
     * Executes a write session on the [AIAgentLLMContext], ensuring that all active write and read sessions
     * are completed before initiating the write session.
     */
    @OptIn(ExperimentalStdlibApi::class)
    public suspend fun <T> writeSession(block: suspend AIAgentLLMWriteSession.() -> T): T = rwLock.withWriteLock {
        val session = AIAgentLLMWriteSession(environment, promptExecutor, tools, toolRegistry, prompt, model, config, clock)

        session.use {
            val result = it.block()

            // update tools and prompt after session execution
            this.prompt = it.prompt
            this.tools = it.tools
            this.model = it.model

            result
        }
    }

    /**
     * Executes a read session within the [AIAgentLLMContext], ensuring concurrent safety
     * with active write session and other read sessions.
     */
    @OptIn(ExperimentalStdlibApi::class)
    public suspend fun <T> readSession(block: suspend AIAgentLLMReadSession.() -> T): T = rwLock.withReadLock {
        val session = AIAgentLLMReadSession(tools, promptExecutor, prompt, model, config)

        session.use { block(it) }
    }
}