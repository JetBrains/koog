package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.lock.RWLock
import ai.koog.serialization.TypeToken

/**
 * The `AIAgentGraphContextBase` interface extends the [AIAgentContext] interface
 * to provide a foundational context specifically tailored for AI agents operating
 * within a graph structure.
 *
 * This interface inherits the core capabilities from [AIAgentContext], including
 * environment management, configuration access, session tracking, state management,
 * and custom workflows. By building upon these features, it serves as a base for
 * defining additional constructs and behaviors that facilitate the agent's execution
 * in graph-based workflows or execution pipelines.
 *
 * Implementations of this interface are expected to leverage the provided capabilities
 * to handle graph-specific logic, such as node traversal, input/output management,
 * and handling complex dependencies between graph nodes.
 */
public interface AIAgentGraphContextBase : AIAgentContext {

    override val pipeline: AIAgentGraphPipeline

    /**
     * [TypeToken] representing the type of the [agentInput]
     */
    public val agentInputType: TypeToken

    /**
     * Creates a copy of the current [AIAgentGraphContext], allowing for selective overriding of its properties.
     *
     * @param environment The [AIAgentEnvironment] to be used in the new context, or the current one if not specified.
     * @param agentId The unique agent identifier, or the current one if not specified.
     * @param agentInput The input data for the agent, or the current input if not specified.
     * @param agentInputType The [TypeToken] representing the type of [agentInput], or the current type if not specified.
     * @param config The [AIAgentConfig] for the new context, or the current configuration if not specified.
     * @param llm The [AIAgentLLMContext] to be used, or the current LLM context if not specified.
     * @param stateManager The [AIAgentStateManager] to be used, or the current state manager if not specified.
     * @param storage The [AIAgentStorage] to be used, or the current storage if not specified.
     * @param runId The run identifier, or the current run ID if not specified.
     * @param strategyName The strategy name, or the current strategy name if not specified.
     * @param pipeline The [AIAgentGraphPipeline] to be used, or the current pipeline if not specified.
     * @param executionInfo The [AgentExecutionInfo] to be used, or the current execution info if not specified.
     * @param parentContext The parent context, or the current instance if not specified.
     */
    public fun copy(
        environment: AIAgentEnvironment = this.environment,
        agentId: String = this.agentId,
        agentInput: Any? = this.agentInput,
        agentInputType: TypeToken = this.agentInputType,
        config: AIAgentConfig = this.config,
        llm: AIAgentLLMContext = this.llm,
        stateManager: AIAgentStateManager = this.stateManager,
        storage: AIAgentStorage = this.storage,
        runId: String = this.runId,
        strategyName: String = this.strategyName,
        pipeline: AIAgentGraphPipeline = this.pipeline,
        executionInfo: AgentExecutionInfo = this.executionInfo,
        parentContext: AIAgentGraphContextBase? = this,
    ): AIAgentGraphContextBase {
        val clone = AIAgentGraphContext(
            environment = environment,
            agentId = agentId,
            agentInput = agentInput,
            agentInputType = agentInputType,
            config = config,
            llm = llm,
            stateManager = stateManager,
            storage = storage,
            runId = runId,
            strategyName = strategyName,
            pipeline = pipeline,
            executionInfo = executionInfo,
            parentContext = parentContext,
        )

        return clone
    }

    /**
     * Creates a copy of the current [AIAgentGraphContext] with deep copies of all mutable properties.
     *
     * @return A new instance of [AIAgentGraphContext] with copies of all mutable properties.
     */
    public suspend fun fork(): AIAgentGraphContextBase

    /**
     * Replaces the current context with the provided context.
     * This method is used to update the current context with values from another context,
     * particularly useful in scenarios like parallel node execution where contexts need to be merged.
     *
     * @param context The context to replace the current context with.
     */
    public suspend fun replace(context: AIAgentContext)
}

/**
 * Implements the [AIAgentGraphContext] interface, providing the context required for an AI agent's execution.
 * This class encapsulates configurations, the execution pipeline,
 * agent environment, and tools for handling agent lifecycles and interactions.
 *
 * @constructor Creates an instance of the context with the given parameters.
 *
 * @param environment The AI agent environment responsible for tool execution and problem reporting.
 * @param agentInput The input message to be used for the agent's interaction with the environment.
 * @param config The configuration settings of the AI agent.
 * @param llm The contextual data and execution utilities for the AI agent's interaction with LLMs.
 * @param stateManager Manages the internal state of the AI agent.
 * @param storage Concurrent-safe storage for managing key-value data across the agent's lifecycle.
 * @param runId The unique identifier for the agent session.
 * @param strategyName The identifier for the selected strategy in the agent's lifecycle.
 * @param pipeline The AI agent pipeline responsible for coordinating AI agent execution and processing.
 */
@OptIn(InternalAgentsApi::class)
public class AIAgentGraphContext(
    environment: AIAgentEnvironment,
    override val agentId: String,
    override val agentInputType: TypeToken,
    override val agentInput: Any?,
    override val config: AIAgentConfig,
    llm: AIAgentLLMContext,
    stateManager: AIAgentStateManager,
    storage: AIAgentStorage,
    override val runId: String,
    override val strategyName: String,
    override val pipeline: AIAgentGraphPipeline,
    executionInfo: AgentExecutionInfo,
    override val parentContext: AIAgentGraphContextBase?,
) : AIAgentGraphContextBase {
    private val mutableAIAgentContext = MutableAIAgentContext(llm, stateManager, storage, environment, executionInfo)

    override val llm: AIAgentLLMContext
        get() = mutableAIAgentContext.llm

    override val storage: AIAgentStorage
        get() = mutableAIAgentContext.storage

    override val stateManager: AIAgentStateManager
        get() = mutableAIAgentContext.stateManager

    override val environment: AIAgentEnvironment
        get() = mutableAIAgentContext.environment

    override var executionInfo: AgentExecutionInfo
        get() = mutableAIAgentContext.executionInfo
        set(value) {
            mutableAIAgentContext.executionInfo = value
        }

    /**
     * Mutable wrapper around the context's stateful fields ([llm], [stateManager], [storage], [environment],
     * [executionInfo]), protected by an internal [RWLock].
     *
     * Concurrency caveats:
     * - The lock is **not reentrant**. In particular [copy] must not be called from inside [replace] on the
     *   same instance (and vice versa) — doing so will deadlock (see [ai.koog.agents.core.utils.RWLock]).
     * - Direct reads of the `var` fields through the getters of the enclosing [AIAgentGraphContext]
     *   ([AIAgentGraphContext.llm], [AIAgentGraphContext.storage], …) **bypass** the lock and may observe a
     *   value that is concurrently being swapped by [replace]. If a consistent snapshot is required, obtain it
     *   via [copy] or via a higher-level context operation such as [AIAgentGraphContext.fork].
     * - [copy] delegates to [AIAgentLLMContext.copy], [AIAgentStateManager.copy], [AIAgentStorage.copy] and
     *   [AgentExecutionInfo.copy]; those downstream copies may themselves suspend on their own locks.
     */
    internal class MutableAIAgentContext(
        var llm: AIAgentLLMContext,
        var stateManager: AIAgentStateManager,
        var storage: AIAgentStorage,
        var environment: AIAgentEnvironment,
        var executionInfo: AgentExecutionInfo
    ) {
        private val rwLock = RWLock()

        /**
         * Creates a copy of the current [MutableAIAgentContext].
         * @return A new instance of [MutableAIAgentContext] with copies of all mutable properties.
         */
        suspend fun copy(): MutableAIAgentContext {
            return rwLock.withReadLock {
                MutableAIAgentContext(llm.copy(), stateManager.copy(), storage.copy(), environment, executionInfo.copy())
            }
        }

        /**
         * Replaces the current context with the provided context.
         *
         * @param llm The LLM context to replace the current context with.
         * @param stateManager The state manager to replace the current context with.
         * @param storage The storage to replace the current context with.
         */
        suspend fun replace(
            llm: AIAgentLLMContext?,
            stateManager: AIAgentStateManager?,
            storage: AIAgentStorage?,
            environment: AIAgentEnvironment?,
            executionInfo: AgentExecutionInfo?,
        ) {
            rwLock.withWriteLock {
                llm?.let { this.llm = it }
                stateManager?.let { this.stateManager = it }
                storage?.let { this.storage = it }
                environment?.let { this.environment = it }
                executionInfo?.let { this.executionInfo = it }
            }
        }
    }

    /**
     * Creates a new instance of [AIAgentContext] with an updated list of tools, replacing the current tools
     * in the LLM context with the provided list.
     *
     * @param tools The new list of tools to be used in the LLM context, represented as [ToolDescriptor] objects.
     * @return A new instance of [AIAgentContext] with the updated tools configuration.
     */
    @InternalAgentsApi
    public fun copyWithTools(tools: List<ToolDescriptor>): AIAgentContext {
        return this.copy(llm = llm.copy(tools = tools))
    }

    /**
     * Creates an independent fork of this context, taking consistent snapshots of the LLM context, storage,
     * state manager and execution info. Each `copy()` is performed under the corresponding lock of the source
     * object, so the returned context is safe to mutate concurrently with the original.
     *
     * Concurrency caveat: each downstream `copy()` acquires its own read lock (or equivalent); these locks are
     * acquired sequentially, and the snapshot of the whole context is therefore **not** atomic across all four
     * fields. Two fields may be observed at slightly different points in time if another coroutine is
     * concurrently mutating this context.
     *
     * Note also that the in-memory [storeMap] (populated via [store]) is **not** copied here — the returned
     * context starts with an empty local store.
     */
    override suspend fun fork(): AIAgentGraphContextBase = copy(
        llm = this.llm.copy(),
        storage = this.storage.copy(),
        stateManager = this.stateManager.copy(),
        executionInfo = this.executionInfo.copy(),
    )

    /**
     * Atomically replaces the stateful fields of this context with those of [context], under the write lock of
     * [mutableAIAgentContext].
     *
     * Concurrency caveats:
     * - [mutableAIAgentContext]'s lock is not reentrant; do not call [replace] from inside another operation
     *   that already holds it (e.g. from within a transformation running under [MutableAIAgentContext.copy]).
     * - Consumers that read fields directly (e.g. `ctx.llm`, `ctx.storage`) without going through the
     *   [MutableAIAgentContext] lock may observe a non-atomic mix of old and new values while a concurrent
     *   [replace] is in progress.
     */
    override suspend fun replace(context: AIAgentContext) {
        mutableAIAgentContext.replace(
            context.llm,
            context.stateManager,
            context.storage,
            context.environment,
            context.executionInfo,
        )
    }
}
