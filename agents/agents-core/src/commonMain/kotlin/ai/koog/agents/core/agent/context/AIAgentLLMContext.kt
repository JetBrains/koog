@file:OptIn(DetachedPromptExecutorAPI::class)

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
 * Annotation for marking APIs as detached prompt executors within the `AIAgentLLMContext`.
 *
 * Using APIs annotated with this requires opting in, as calls to `PromptExecutor` will be disconnected
 * from the agent logic. This means these calls will not affect the agent's state or adhere to the
 * `ToolsConversionStrategy`.
 *
 * This API should be used with caution, as it provides functionality that operates outside the
 * standard agent lifecycle and processing logic.
 */
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Calls to PromptExecutor used from `AIAgentLLMContext` will not be connected to the agent logic, " +
        "and will not impact the agent's state. " +
        "Other than that, `ToolsConversionStrategy` will not be applied. " +
        "Please be cautious when using this API."
)
public annotation class DetachedPromptExecutorAPI

/**
 * Represents the context for an AI agent LLM, managing tools, prompt handling, and interaction with the
 * environment and execution layers. It provides mechanisms for concurrent read and write operations
 * through sessions, ensuring thread safety.
 *
 * @property tools A list of tool descriptors available for the context.
 * @property toolRegistry A registry that contains metadata about available tools.
 * @property prompt The current LLM prompt being used or updated in write sessions.
 * @property model The current LLM model being used or updated in write sessions.
 * @property promptExecutor The [PromptExecutor] responsible for performing operations on the current prompt.
 * @property environment The environment that manages tool execution and interaction with external dependencies.
 * @property clock The clock used for timestamps of messages
 */
public class AIAgentLLMContext(
    tools: List<ToolDescriptor>,
    public val toolRegistry: ToolRegistry = ToolRegistry.Companion.EMPTY,
    prompt: Prompt,
    model: LLModel,
    @property:DetachedPromptExecutorAPI
    public val promptExecutor: PromptExecutor,
    private val environment: AIAgentEnvironment,
    private val config: AIAgentConfigBase,
    private val clock: Clock
) {
    /**
     * List of current tools associated with this agent context.
     */
    @DetachedPromptExecutorAPI
    public var tools: List<ToolDescriptor> = tools
        private set

    /**
     * LLM currently associated with this context.
     */
    @DetachedPromptExecutorAPI
    public var model: LLModel = model
        private set

    /**
     * The current prompt used within the `AIAgentLLMContext`.
     *
     * This property defines the main [Prompt] instance used by the context and is updated as needed to reflect
     * modifications or new inputs for the language model operations. It is thread-safe, ensuring that updates
     * and access are managed correctly within concurrent environments.
     *
     * This variable can only be modified internally via specific methods, maintaining control over state changes.
     */
    public var prompt: Prompt = prompt
        private set

    /**
     * Updates the current `AIAgentLLMContext` with a new prompt and ensures thread-safe access using a read lock.
     *
     * @param prompt The new [Prompt] to be set for the context.
     * @return The current instance of [AIAgentLLMContext] with the updated prompt.
     */
    public suspend fun withPrompt(block: Prompt.() -> Prompt): AIAgentLLMContext = rwLock.withReadLock {
        this.prompt = prompt.block()
        this
    }

    /**
     * Creates a deep copy of this LLM context.
     *
     * @return A new instance of [AIAgentLLMContext] with deep copies of mutable properties.
     */
    public suspend fun copy(
        tools: List<ToolDescriptor> = this.tools,
        toolRegistry: ToolRegistry = this.toolRegistry,
        prompt: Prompt = this.prompt,
        model: LLModel = this.model,
        promptExecutor: PromptExecutor = this.promptExecutor,
        environment: AIAgentEnvironment = this.environment,
        config: AIAgentConfigBase = this.config,
        clock: Clock = this.clock,
    ): AIAgentLLMContext = rwLock.withReadLock {
        AIAgentLLMContext(
            tools = tools,
            toolRegistry = toolRegistry,
            prompt = prompt,
            model = model,
            promptExecutor = promptExecutor,
            environment = environment,
            config = config,
            clock = clock
        )
    }

    private val rwLock = RWLock()

    /**
     * Executes a write session on the [AIAgentLLMContext], ensuring that all active write and read sessions
     * are completed before initiating the write session.
     */
    @OptIn(ExperimentalStdlibApi::class)
    public suspend fun <T> writeSession(block: suspend AIAgentLLMWriteSession.() -> T): T = rwLock.withWriteLock {
        val session =
            AIAgentLLMWriteSession(environment, promptExecutor, tools, toolRegistry, prompt, model, config, clock)

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

    /**
     * Returns the current prompt used in the LLM context.
     *
     * @return The current [Prompt] instance.
     */
    public fun copy(
        tools: List<ToolDescriptor> = this.tools,
        prompt: Prompt = this.prompt,
        model: LLModel = this.model,
        promptExecutor: PromptExecutor = this.promptExecutor,
        environment: AIAgentEnvironment = this.environment,
        config: AIAgentConfigBase = this.config,
        clock: Clock = this.clock
    ): AIAgentLLMContext {
        return AIAgentLLMContext(tools, toolRegistry, prompt, model, promptExecutor, environment, config, clock)
    }
}
