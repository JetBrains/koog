package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.context.AgentContextData
import ai.koog.agents.core.agent.context.store
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.feature.InterceptContext
import ai.koog.agents.memory.feature.memory
import ai.koog.agents.snapshot.providers.PersistencyStorageProvider
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import kotlin.reflect.KType
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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
 * using the [PersistencyFeatureConfig.enableAutomaticPersistency] option.
 *
 * @property persistencyStorageProvider The provider responsible for storing and retrieving checkpoints
 * @property currentNodeId The ID of the node currently being executed
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class, InternalAgentsApi::class)
public class Persistency(
    private val persistencyStorageProvider: PersistencyStorageProvider,
    private val config: PersistencyFeatureConfig
) {
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
    public companion object Feature : AIAgentFeature<PersistencyFeatureConfig, Persistency> {
        private val logger = KotlinLogging.logger {  }

        private val json = Json {
            prettyPrint = true
        }

        /**
         * The storage key used to identify this feature in the agent's feature registry.
         */
        override val key: AIAgentStorageKey<Persistency>
            get() = AIAgentStorageKey("agents-features-snapshot")

        /**
         * Creates the default configuration for this feature.
         *
         * @return A new instance of [PersistencyFeatureConfig] with default settings
         */
        override fun createInitialConfig(): PersistencyFeatureConfig = PersistencyFeatureConfig()

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
            config: PersistencyFeatureConfig,
            pipeline: AIAgentPipeline
        ) {
            val featureImpl = Persistency(config.storage, config)
            val interceptContext = InterceptContext(this, featureImpl)

            pipeline.interceptContextAgentFeature(this) { ctx ->
                return@interceptContextAgentFeature featureImpl
            }

            pipeline.interceptBeforeAgentStarted(interceptContext) { ctx ->
                require(ctx.strategy.metadata.uniqueNames) { "Checkpoint feature requires unique node names in the strategy metadata" }
                val checkpoint = ctx.feature.getLatestCheckpoint()

                if (checkpoint != null) {
                    // Restore from the latest checkpoint
                    ctx.feature.rollbackToLatestCheckpoint<Unit>(ctx.context)
                    logger.info { "Restoring checkpoint: ${checkpoint.checkpointId} to node ${checkpoint.nodeId}" }
                } else {
                    logger.info { "No checkpoint found, starting from the beginning" }
                }
            }

            pipeline.interceptAfterNode(interceptContext) { eventCtx ->
                if (config.enableAutomaticPersistency) {
                    createCheckpoint(
                        agentContext = eventCtx.context,
                        nodeId = eventCtx.node.id,
                        lastInput = eventCtx.input,
                        lastInputType = eventCtx.inputType,
                    )
                }
            }

            pipeline.interceptBeforeNode(interceptContext) { eventCtx ->
                featureImpl.currentNodeId = eventCtx.node.id
            }
        }
    }

    /**
     * Creates a checkpoint of the agent's current state.
     *
     * This method captures the agent's message history, current node, input data,
     * and optionally memory snapshots and custom data, storing it as a comprehensive
     * checkpoint using the configured storage provider.
     *
     * @param agentContext The context of the agent containing the state to checkpoint
     * @param nodeId The ID of the node where the checkpoint is created
     * @param lastInput The input data to include in the checkpoint
     * @param checkpointId Optional ID for the checkpoint; a random UUID is generated if not provided
     * @return The created checkpoint data
     */
    public suspend fun createCheckpoint(
        agentContext: AIAgentContextBase,
        nodeId: String,
        lastInput: Any?,
        lastInputType: KType,
        checkpointId: String? = null
    ): AgentCheckpointData? {
        val inputJson = trySerializeInput(lastInput, lastInputType)

        if (inputJson == null) {
            logger.warn { "Failed to serialize input of type $lastInputType for checkpoint creation for $nodeId, skipping..." }
            return null
        }

        // Capture memory snapshot if configured
        val memorySnapshot = if (config.includeMemorySnapshot) {
            try {
                val memoryProvider = agentContext.memory().agentMemory
                config.memorySnapshotTransformer.captureSnapshot(memoryProvider)
            } catch (e: Exception) {
                logger.warn { "Failed to capture memory snapshot for checkpoint $nodeId: ${e.message}" }
                null
            }
        } else {
            null
        }

        // Capture custom snapshot data if configured
        val extraSnapshotData = config.extraSnapshotDataProvider?.let { provider ->
            try {
                provider.invoke(agentContext)
            } catch (e: Exception) {
                logger.warn { "Failed to capture extra snapshot data for checkpoint $nodeId: ${e.message}" }
                null
            }
        }

        val checkpoint = agentContext.llm.readSession {
            return@readSession AgentCheckpointData(
                checkpointId = checkpointId ?: Uuid.random().toString(),
                messageHistory = prompt.messages,
                nodeId = nodeId,
                lastInput = inputJson,
                createdAt = Clock.System.now(),
                memorySnapshot = memorySnapshot,
                extraSnapshotData = extraSnapshotData
            )
        }

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
        persistencyStorageProvider.saveCheckpoint(checkpointData)
    }

    /**
     * Retrieves the latest checkpoint for the specified agent.
     *
     * @return The latest checkpoint data, or null if no checkpoint exists
     */
    public suspend fun getLatestCheckpoint(): AgentCheckpointData? =
        persistencyStorageProvider.getLatestCheckpoint()

    /**
     * Retrieves a specific checkpoint by ID for the specified agent.
     *
     * @param checkpointId The ID of the checkpoint to retrieve
     * @return The checkpoint data with the specified ID, or null if not found
     */
    public suspend fun getCheckpointById(checkpointId: String): AgentCheckpointData? =
        persistencyStorageProvider.getCheckpoints().firstOrNull { it.checkpointId == checkpointId }

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
        agentContext: AIAgentContextBase,
        nodeId: String,
        messageHistory: List<Message>,
        input: JsonElement
    ) {
        agentContext.store(AgentContextData(messageHistory, nodeId, input))
    }

    /**
     * Rolls back an agent's state to a specific checkpoint.
     *
     * This method retrieves the checkpoint with the specified ID and, if found,
     * sets the agent's context to the state captured in that checkpoint,
     * restores memory snapshots, and returns any extra snapshot data.
     *
     * @param checkpointId The ID of the checkpoint to roll back to
     * @param agentContext The context of the agent to roll back
     * @return The extra snapshot data from the checkpoint, or null if checkpoint not found or no extra data
     */
    public suspend fun <T> rollbackToCheckpoint(
        checkpointId: String,
        agentContext: AIAgentContextBase
    ): T? {
        val checkpoint: AgentCheckpointData? = getCheckpointById(checkpointId)
        if (checkpoint != null) {
            // Restore execution context
            agentContext.store(checkpoint.toAgentContextData())
            
            // Restore memory snapshot if present
            restoreMemorySnapshot(checkpoint, agentContext)
            
            // Return extra snapshot data if present
            @Suppress("UNCHECKED_CAST")
            return checkpoint.extraSnapshotData as? T
        }
        return null
    }

    /**
     * Rolls back an agent's state to a specific checkpoint (JsonObject return type for backward compatibility).
     *
     * @param checkpointId The ID of the checkpoint to roll back to
     * @param agentContext The context of the agent to roll back
     * @return The extra snapshot data as JsonObject, or null if checkpoint not found or no extra data
     */
    public suspend fun rollbackToCheckpointAsJson(
        checkpointId: String,
        agentContext: AIAgentContextBase
    ): JsonObject? = rollbackToCheckpoint<JsonObject>(checkpointId, agentContext)

    /**
     * Rolls back an agent's state to the latest checkpoint.
     *
     * This method retrieves the most recent checkpoint for the agent and,
     * if found, sets the agent's context to the state captured in that checkpoint,
     * restores memory snapshots, and returns any extra snapshot data.
     *
     * @param agentContext The context of the agent to roll back
     * @return The extra snapshot data from the checkpoint, or null if no checkpoint found or no extra data
     */
    public suspend fun <T> rollbackToLatestCheckpoint(
        agentContext: AIAgentContextBase
    ): T? {
        val checkpoint: AgentCheckpointData? = getLatestCheckpoint()
        if (checkpoint != null) {
            // Restore execution context
            agentContext.store(checkpoint.toAgentContextData())
            
            // Restore memory snapshot if present
            restoreMemorySnapshot(checkpoint, agentContext)
            
            // Return extra snapshot data if present
            @Suppress("UNCHECKED_CAST")
            return checkpoint.extraSnapshotData as? T
        }
        return null
    }

    /**
     * Rolls back an agent's state to the latest checkpoint (JsonObject return type for backward compatibility).
     *
     * @param agentContext The context of the agent to roll back
     * @return The extra snapshot data as JsonObject, or null if no checkpoint found or no extra data
     */
    public suspend fun rollbackToLatestCheckpointAsJson(
        agentContext: AIAgentContextBase
    ): JsonObject? = rollbackToLatestCheckpoint<JsonObject>(agentContext)

    /**
     * Restores memory snapshot from a checkpoint if present and memory feature is available.
     *
     * @param checkpoint The checkpoint containing potential memory snapshot
     * @param agentContext The agent context to restore memory to
     */
    private suspend fun restoreMemorySnapshot(
        checkpoint: AgentCheckpointData,
        agentContext: AIAgentContextBase
    ) {
        checkpoint.memorySnapshot?.let { memorySnapshot ->
            try {
                val memoryProvider = agentContext.memory().agentMemory
                config.memorySnapshotTransformer.restoreSnapshot(memoryProvider, memorySnapshot)
                logger.info { "Successfully restored memory snapshot from checkpoint ${checkpoint.checkpointId}" }
            } catch (e: Exception) {
                logger.warn { "Failed to restore memory snapshot from checkpoint ${checkpoint.checkpointId}: ${e.message}" }
            }
        }
    }

    /**
     * Gets all checkpoints from the storage.
     *
     * @return List of all checkpoints
     */
    public suspend fun getCheckpoints(): List<AgentCheckpointData> {
        return persistencyStorageProvider.getCheckpoints()
    }

}

/**
 * Extension function to access the checkpoint feature from an agent context.
 *
 * @return The [Persistency] feature instance for this agent
 * @throws IllegalStateException if the checkpoint feature is not installed
 */
public fun AIAgentContextBase.persistency(): Persistency = featureOrThrow(Persistency.Feature)

/**
 * Extension function to perform an action with the checkpoint feature.
 *
 * This is a convenience function that retrieves the checkpoint feature and
 * executes the provided action with it.
 *
 * @param T The return type of the action
 * @param context The agent context to pass to the action
 * @param action The action to perform with the checkpoint feature
 * @return The result of the action
 */
public suspend fun <T> AIAgentContextBase.withPersistency(
    context: AIAgentContextBase,
    action: suspend Persistency.(AIAgentContextBase) -> T
): T = persistency().action(context)