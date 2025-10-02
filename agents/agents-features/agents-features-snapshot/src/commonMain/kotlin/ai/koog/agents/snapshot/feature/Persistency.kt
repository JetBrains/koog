package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.AIAgent.Companion.State.Running
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AgentContextData
import ai.koog.agents.core.agent.context.RollbackStrategy
import ai.koog.agents.core.agent.context.store
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.AIAgentSubgraph
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.AIAgentGraphPipeline
import ai.koog.agents.core.feature.InterceptContext
import ai.koog.agents.core.tools.DirectToolCallsEnabler
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.snapshot.providers.PersistenceStorageProvider
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import kotlin.reflect.KType
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Deprecated(
    "`Persistency` has been renamed to `Persistence`",
    replaceWith = ReplaceWith(
        expression = "Persistence",
        "ai.koog.agents.snapshot.feature.Persistence"
    )
)
public typealias Persistency = Persistence

/**
 * A feature that provides checkpoint functionality for AI agents.
 *
 * This class allows saving and restoring the state of an agent at specific points during execution.
 * Checkpoints capture the agent's message history, current node, and input data, enabling:
 * - Resuming agent execution from a specific point
 * - Rolling back to previous states
 * - Persisting agent state across sessions
 *
 * The feature can be configured to automatically create checkpoints after each node execution
 * using the [PersistenceFeatureConfig.enableAutomaticPersistence] option.
 *
 * @property persistenceStorageProvider The provider responsible for storing and retrieving checkpoints
 * @property currentNodeId The ID of the node currently being executed
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class, InternalAgentsApi::class)
public class Persistence(
    private val persistenceStorageProvider: PersistenceStorageProvider,
    internal val clock: Clock = Clock.System,
) {
    /**
     * Determines the strategy to use during rollback operations for the agent's state.
     *
     * The `rollbackStrategy` defines the extent of state restoration when rolling back
     * to a previous checkpoint. It impacts which parts of the agent's session data
     * (e.g., message history, context) are restored during a rollback. Available
     * strategies include restoring the full state or limiting restoration to specific parts.
     *
     * By default, the strategy is set to `RollbackStrategy.Default`, which restores the
     * entire context of the agent, including message history and other stateful data.
     * Alternative strategies, such as `MessageHistoryOnly`, can be used for partial rollbacks.
     */
    public var rollbackStrategy: RollbackStrategy = RollbackStrategy.Default

    /**
     * A registry for managing rollback tools within the persistence system.
     *
     * The `rollbackToolRegistry` plays a key role in supporting the rollback mechanism in the
     * persistence operations, allowing seamless state restoration for tools **with side-effects** to specified or latest
     * checkpoints as needed.
     *
     */
    public var rollbackToolRegistry: RollbackToolRegistry = RollbackToolRegistry {}

    /**
     * Represents the identifier of the current node being executed within the agent pipeline.
     *
     * This property is used to track the state of the agent's execution and is updated whenever
     * the agent begins processing a new node.
     * It plays a crucial role in maintaining the agent's
     * state across checkpoints and ensuring accurate state restoration during rollbacks.
     *
     * The value is nullable, indicating that there might be no current node under execution
     * (e.g., when the pipeline is idle or has not started).
     */
    public var currentNodeId: String? = null
        private set

    /**
     * Feature companion object that implements [AIAgentFeature] for the checkpoint functionality.
     */
    public companion object Feature : AIAgentGraphFeature<PersistenceFeatureConfig, Persistence> {
        private val logger = KotlinLogging.logger { }

        private val json = Json {
            prettyPrint = true
        }

        /**
         * The storage key used to identify this feature in the agent's feature registry.
         */
        override val key: AIAgentStorageKey<Persistence> = AIAgentStorageKey("agents-features-snapshot")

        /**
         * Creates the default configuration for this feature.
         *
         * @return A new instance of [PersistenceFeatureConfig] with default settings
         */
        override fun createInitialConfig(): PersistenceFeatureConfig = PersistenceFeatureConfig()

        /**
         * Installs the checkpoint feature into the agent pipeline.
         *
         * This method sets up the necessary interceptors to:
         * - Restore the latest checkpoint when the agent starts
         * - Track the current node being executed
         * - Create checkpoints after node execution (if continuous persistence is enabled)
         *
         * @param config The configuration for the checkpoint feature
         * @param pipeline The agent pipeline to install the feature into
         */
        override fun install(
            config: PersistenceFeatureConfig,
            pipeline: AIAgentGraphPipeline
        ) {
            val featureImpl = Persistence(config.storage)
            featureImpl.rollbackStrategy = config.rollbackStrategy
            featureImpl.rollbackToolRegistry = config.rollbackToolRegistry
            val interceptContext = InterceptContext(this, featureImpl)

            pipeline.interceptContextAgentFeature(this) { _ ->
                return@interceptContextAgentFeature featureImpl
            }

            pipeline.interceptStrategyStarting(interceptContext) { ctx ->
                val strategy = ctx.strategy as AIAgentGraphStrategy<*, *>

                require(strategy.metadata.uniqueNames) {
                    "Checkpoint feature requires unique node names in the strategy metadata"
                }

                val checkpoint = ctx.feature.rollbackToLatestCheckpoint(ctx.context)

                if (checkpoint != null) {
                    logger.info { "Restoring checkpoint: ${checkpoint.checkpointId} to node ${checkpoint.nodeId}" }
                } else {
                    logger.info { "No non-tombstone checkpoint found, starting from the beginning" }
                }
            }

            pipeline.interceptNodeExecutionCompleted(interceptContext) { eventCtx ->
                if (isTechnicalNode(eventCtx.node.id)) {
                    return@interceptNodeExecutionCompleted
                }

                if (config.enableAutomaticPersistence) {
                    createCheckpoint(
                        agentContext = eventCtx.context,
                        nodeId = eventCtx.node.id,
                        lastInput = eventCtx.input,
                        lastInputType = eventCtx.inputType,
                    )
                }
            }

            pipeline.interceptNodeExecutionStarting(interceptContext) { eventCtx ->
                featureImpl.currentNodeId = eventCtx.node.id
            }

            pipeline.interceptStrategyCompleted(interceptContext) { ctx ->
                if (config.enableAutomaticPersistence && config.rollbackStrategy == RollbackStrategy.Default) {
                    ctx.feature.createTombstoneCheckpoint(ctx.feature.clock.now())
                }
            }
        }
    }

    private fun isTechnicalNode(nodeId: String): Boolean =
        nodeId.startsWith(AIAgentSubgraph.FINISH_NODE_PREFIX) ||
            nodeId.startsWith(AIAgentSubgraph.START_NODE_PREFIX)

    /**
     * Creates a checkpoint of the agent's current state.
     *
     * This method captures the agent's message history, current node, and input data
     * and stores it as a checkpoint using the configured storage provider.
     *
     * @param agentContext The context of the agent containing the state to checkpoint
     * @param nodeId The ID of the node where the checkpoint is created
     * @param lastInput The input data to include in the checkpoint
     * @param checkpointId Optional ID for the checkpoint; a random UUID is generated if not provided
     * @return The created checkpoint data
     */
    public suspend fun createCheckpoint(
        agentContext: AIAgentContext,
        nodeId: String,
        lastInput: Any?,
        lastInputType: KType,
        checkpointId: String? = null
    ): AgentCheckpointData? {
        val inputJson = trySerializeInput(lastInput, lastInputType)

        if (inputJson == null) {
            logger.warn {
                "Failed to serialize input of type $lastInputType for checkpoint creation for $nodeId, skipping..."
            }
            return null
        }

        val checkpoint = agentContext.llm.readSession {
            return@readSession AgentCheckpointData(
                checkpointId = checkpointId ?: Uuid.random().toString(),
                messageHistory = prompt.messages,
                nodeId = nodeId,
                lastInput = inputJson,
                createdAt = Clock.System.now()
            )
        }

        saveCheckpoint(checkpoint)
        return checkpoint
    }

    /**
     * Creates and saves a tombstone checkpoint for an agent's session.
     *
     * A tombstone checkpoint represents a placeholder state with no interactions or messages,
     * marking a terminated or invalid session. The method generates the tombstone checkpoint
     * and persists it using the appropriate storage mechanism.
     *
     * @return The created tombstone checkpoint data.
     */
    @InternalAgentsApi
    public suspend fun createTombstoneCheckpoint(time: Instant): AgentCheckpointData {
        val checkpoint = tombstoneCheckpoint(time)
        saveCheckpoint(checkpoint)
        return checkpoint
    }

    private fun trySerializeInput(input: Any?, inputType: KType): JsonElement? {
        return try {
            json.encodeToJsonElement(json.serializersModule.serializer(inputType), input)
        } catch (_: SerializationException) {
            return null
        }
    }

    /**
     * Saves a checkpoint using the configured storage provider.
     *
     * @param checkpointData The checkpoint data to save
     */
    public suspend fun saveCheckpoint(checkpointData: AgentCheckpointData) {
        persistenceStorageProvider.saveCheckpoint(checkpointData)
    }

    /**
     * Retrieves the latest checkpoint for the specified agent.
     *
     * @return The latest checkpoint data, or null if no checkpoint exists
     */
    public suspend fun getLatestCheckpoint(): AgentCheckpointData? =
        persistenceStorageProvider.getLatestCheckpoint()

    /**
     * Retrieves a specific checkpoint by ID for the specified agent.
     *
     * @param checkpointId The ID of the checkpoint to retrieve
     * @return The checkpoint data with the specified ID, or null if not found
     */
    public suspend fun getCheckpointById(checkpointId: String): AgentCheckpointData? =
        persistenceStorageProvider.getCheckpoints().firstOrNull { it.checkpointId == checkpointId }

    /**
     * Sets the execution point of an agent to a specific state.
     *
     * This method directly modifies the agent's context to force execution from a specific point,
     * with the specified message history and input data.
     *
     * @param agentContext The context of the agent to modify
     * @param nodeId The ID of the node to set as the current execution point
     * @param messageHistory The message history to set for the agent
     * @param input The input data to set for the agent
     */
    public fun setExecutionPoint(
        agentContext: AIAgentContext,
        nodeId: String,
        messageHistory: List<Message>,
        input: JsonElement
    ) {
        agentContext.store(AgentContextData(messageHistory, nodeId, input, rollbackStrategy))
    }

    /**
     * Rolls back an agent's state to a specific checkpoint.
     *
     * This method retrieves the checkpoint with the specified ID and, if found,
     * sets the agent's context to the state captured in that checkpoint.
     *
     * **Note: If some of your tools had side-effects and you need to roll back to some older state, please consider
     * providing [RollbackToolRegistry]. This would only work if you are always trying to rollback BACKWARDS in time!**
     *
     * @param checkpointId The ID of the checkpoint to roll back to
     * @param agentContext The context of the agent to roll back
     * @return The checkpoint data that was restored or null if the checkpoint was not found
     */
    @OptIn(InternalAgentToolsApi::class)
    public suspend fun rollbackToCheckpoint(
        checkpointId: String,
        agentContext: AIAgentContext
    ): AgentCheckpointData? {
        val checkpoint: AgentCheckpointData? = getCheckpointById(checkpointId)
        if (checkpoint != null) {
            agentContext.store(
                checkpoint.toAgentContextData(rollbackStrategy) { context ->
                    messageHistoryDiff(
                        currentMessages = context.llm.prompt.messages,
                        checkpointMessages = checkpoint.messageHistory
                    )
                        .filterIsInstance<Message.Tool.Call>()
                        .reversed()
                        .forEach { toolCall ->
                            rollbackToolRegistry.getRollbackTool(toolCall.tool)?.let { rollbackTool ->
                                val toolArgs = rollbackTool.decodeArgs(toolCall.contentJson)

                                rollbackTool.executeUnsafe(toolArgs, DirectToolCallsEnablerImpl)
                            }
                        }
                }
            )
        }

        return checkpoint
    }

    /**
     * Returns the difference only.
     * ex: current messages: [1, 2, 3, 4, 5, 6, 7], checkpoint messages: [1, 2, 3, 4, 5] -> diff messages: 6, 7
     *
     * Only works for the scenario when current chat histor is AHEAD of the checkpoint (i.e. we are restoring BACKWARDS in time),
     * otherwise will return an empty list!
     * */
    private fun messageHistoryDiff(currentMessages: List<Message>, checkpointMessages: List<Message>): List<Message> {
        if (checkpointMessages.size > currentMessages.size) {
            return emptyList()
        }

        checkpointMessages.forEachIndexed { index, message ->
            if (currentMessages[index] != message) {
                return emptyList()
            }
        }

        return currentMessages.takeLast(currentMessages.size - checkpointMessages.size)
    }

    /**
     * Rolls back an agent's state to the latest checkpoint.
     *
     * This method retrieves the most recent checkpoint for the agent and,
     * if found, sets the agent's context to the state captured in that checkpoint.
     *
     * @param agentContext The context of the agent to roll back
     * @return The checkpoint data that was restored or null if no checkpoint was found
     */
    public suspend fun rollbackToLatestCheckpoint(
        agentContext: AIAgentContext
    ): AgentCheckpointData? {
        val checkpoint: AgentCheckpointData? = getLatestCheckpoint()
        if (checkpoint?.isTombstone() ?: true) {
            return null
        }

        agentContext.store(checkpoint.toAgentContextData(rollbackStrategy))
        return checkpoint
    }
}

/**
 * Extension function to access the checkpoint feature from an agent context.
 *
 * @return The [Persistence] feature instance for this agent
 * @throws IllegalStateException if the checkpoint feature is not installed
 */
public fun AIAgentContext.persistence(): Persistence = agent.persistence()

/**
 * Retrieves the persistence feature for the AI agent.
 *
 * @return The persistence feature associated with the AI agent.
 * @throws IllegalStateException if the persistence feature is not available.
 */
public fun AIAgent<*, *>.persistence(): Persistence = featureOrThrow(Persistence.Feature)

/**
 * Executes the provided action within the context of the AI agent's persistence layer.
 *
 * This function enhances agents with persistent state management capabilities by leveraging the `Persistence` component
 * within the current `AIAgentContext`. The supplied action is executed with the persistence layer, enabling operations
 * that require consistent and reliable state management across the lifecycle of the agent.
 *
 * @param action A suspendable lambda function that receives the `Persistence` instance and the current `AIAgentContext`
 *               as its parameters. This allows custom logic that interacts with the persistence layer to be executed.
 * @return A result of type [T] produced by the execution of the provided action.
 */
public suspend fun <T> AIAgentContext.withPersistence(
    action: suspend Persistence.(AIAgentContext) -> T
): T = this.persistence().action(this)

/**
 * Executes the provided action within the context of the agent's persistence layer if the agent is in a running state.
 *
 * This function allows interaction with the persistence mechanism associated with the agent, ensuring that
 * the operation is carried out in the correct execution context.
 *
 * @param action A suspending function defining operations to perform using the agent's persistence mechanism
 *               and the current agent context.
 * @return The result of the execution of the provided action.
 * @throws IllegalStateException If the agent is not in a running state when this function is called.
 */
@OptIn(InternalAgentsApi::class)
public suspend fun <T> AIAgent<*, *>.withPersistence(
    action: suspend Persistence.(AIAgentContext) -> T
): T = when (val state = getState()) {
    is Running<*> -> this.persistence().action(state.rootContext)
    else -> throw IllegalStateException("Agent is not running. Current agents's state: $state")
}

@OptIn(InternalAgentToolsApi::class)
private object DirectToolCallsEnablerImpl : DirectToolCallsEnabler
