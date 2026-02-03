@file:OptIn(DetachedPromptExecutorAPI::class, InternalAgentsApi::class, ExperimentalUuidApi::class)
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.session.AIAgentLLMReadSession
import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.processor.ResponseProcessor
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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
 * API for the [AIAgentLLMContext]
 */
public interface AIAgentLLMContextAPI {
    /**
     * A [ToolRegistry] that contains metadata about available tools.
     * */
    public val toolRegistry: ToolRegistry

    /**
     * The [PromptExecutor] responsible for performing operations on the current prompt.
     * */
    @property:DetachedPromptExecutorAPI
    public val promptExecutor: PromptExecutor

    /**
     * Represents the execution environment associated with an AI agent within the context of the LLM (Large Language Model) framework.
     *
     * This property provides a mechanism for interfacing with an external environment, which allows the agent to perform tasks
     * such as executing tools, reporting issues, and sending termination or result messages. The environment is central
     * to facilitating interactions between the AI agent and its operational surroundings.
     *
     * Marked with [InternalAgentsApi], indicating it is intended for internal use within agent-related implementations
     * and not designed for general application development. Changes to this API may occur without notice.
     */
    @InternalAgentsApi
    public val environment: AIAgentEnvironment

    /**
     * Provides access to the configuration settings for an AI agent within the LLM context.
     *
     * This property encapsulates an instance of [AIAgentConfig], which defines the prompt,
     * execution parameters, and behavior of the agent. It is marked with the `@InternalAgentsApi`
     * annotation, indicating its internal use for agent-related implementations and signaling
     * that it is not intended for public-facing applications.
     *
     * The configuration includes settings such as the prompt definition, model specifications,
     * iteration limits, and strategies to handle missing tools during execution. It plays a
     * critical role in defining how the AI agent processes input, generates output, and interacts
     * with other components of the system.
     *
     * Note: This property is accessible with a custom name `config` when interacting with JVM-based
     * systems, as indicated by the `@get:JvmName("config")` annotation.
     */
    @InternalAgentsApi
    public val config: AIAgentConfig

    /**
     * Represents the clock instance used for time-related operations and scheduling within the
     * `AIAgentLLMContextAPI`. This property is intended for internal use in managing timing and
     * scheduling functionalities across the LLM context.
     *
     * As an `@InternalAgentsApi` element, it is not part of the public API and may be
     * subject to changes, removal, or modifications without notice.
     *
     * Use of this property requires an understanding of its role in the internal infrastructure
     * of the AI agents and should be approached with caution in specialized use cases.
     */
    @InternalAgentsApi
    public val clock: Clock

    /**
     * List of current tools associated with this agent context.
     */
    @DetachedPromptExecutorAPI
    public var tools: List<ToolDescriptor>
        @InternalAgentsApi set

    /**
     * LLM currently associated with this context.
     */
    @DetachedPromptExecutorAPI
    public var model: LLModel
        @InternalAgentsApi set

    /**
     * Response processor currently associated with this context.
     */
    @DetachedPromptExecutorAPI
    public var responseProcessor: ResponseProcessor?
        @InternalAgentsApi set

    /**
     * The current prompt used within the `AIAgentLLMContext`.
     *
     * This property defines the main [Prompt] instance used by the context and is updated as needed to reflect
     * modifications or new inputs for the language model operations. It is thread-safe, ensuring that updates
     * and access are managed correctly within concurrent environments.
     *
     * This variable can only be modified internally via specific methods, maintaining control over state changes.
     */
    public var prompt: Prompt

    /**
     * Atomically updates the prompt using the provided transformation block.
     *
     * This method acquires an exclusive write lock to ensure thread-safe read-modify-write operations.
     * Multiple concurrent calls to this method are serialized.
     *
     * CAVEAT: Do NOT call this method from within [readSession] - this will deadlock
     * due to lock upgrade not being supported.
     *
     * @param block A transformation function that receives the current [Prompt] and returns the new [Prompt].
     */
    public suspend fun withPrompt(block: Prompt.() -> Prompt)

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
        responseProcessor: ResponseProcessor? = this.responseProcessor,
        promptExecutor: PromptExecutor = this.promptExecutor,
        environment: AIAgentEnvironment = this.environment,
        config: AIAgentConfig = this.config,
        clock: Clock = this.clock,
    ): AIAgentLLMContext

    /**
     * Executes a write session on the [AIAgentLLMContext] with exclusive access.
     *
     * Behavior:
     * - Only one write session can be active at a time (serialized via Mutex)
     * - If a write session with the same [sessionId] is already active, the caller reuses that session (reentrancy)
     * - If a write session with a different [sessionId] is active, the caller waits for it to complete
     * - Changes are committed atomically when the original session completes
     *
     * For reentrancy across thread boundaries (e.g., Java ExecutorService), capture the sessionId
     * provided to the block and pass it to nested writeSession calls:
     * ```kotlin
     * context.writeSession { sessionId ->
     *     executor.submit {
     *         runBlocking {
     *             context.writeSession(sessionId) { _ ->
     *                 // Reuses the outer session - no deadlock!
     *             }
     *         }
     *     }.get()
     * }
     * ```
     *
     * @param sessionId Unique identifier for this session. Default is a random UUID.
     *   - Same ID as active session: Reuses the active session (reentrancy)
     *   - Different ID than active session: Waits for active session to complete
     * @param block The block to execute within the write session. Receives the sessionId for propagation.
     * @return The result of the block execution.
     */
    @OptIn(ExperimentalStdlibApi::class)
    public suspend fun <T> writeSession(
        sessionId: String = Uuid.random().toString(),
        block: suspend AIAgentLLMWriteSession.(sessionId: String) -> T
    ): T

    /**
     * Executes a read session with shared access to the current state.
     *
     * Behavior:
     * - Multiple readers can execute concurrently (shared read lock)
     * - Readers are blocked while a writer holds the write lock
     * - Readers see a consistent snapshot of all mutable fields
     * - If called while a [writeSession] is active, reads from the write session's current (uncommitted) state
     *
     * CAVEAT: Do NOT call [writeSession] from within readSession - this will deadlock.
     *
     * CAVEAT: When a write session is active, this method reads from the uncommitted
     * state regardless of whether the caller is logically part of that write session.
     * This means concurrent readers may observe uncommitted changes that could be
     * rolled back if the writer throws an exception.
     *
     * @param block The block to execute within the read session.
     * @return The result of the block execution.
     */
    @OptIn(ExperimentalStdlibApi::class)
    public suspend fun <T> readSession(block: suspend AIAgentLLMReadSession.() -> T): T

    /**
     * Returns the current prompt used in the LLM context.
     *
     * @return The current [Prompt] instance.
     */
    public fun copy(
        tools: List<ToolDescriptor> = this.tools,
        prompt: Prompt = this.prompt,
        model: LLModel = this.model,
        responseProcessor: ResponseProcessor? = this.responseProcessor,
        promptExecutor: PromptExecutor = this.promptExecutor,
        environment: AIAgentEnvironment = this.environment,
        config: AIAgentConfig = this.config,
        clock: Clock = this.clock
    ): AIAgentLLMContext
}
