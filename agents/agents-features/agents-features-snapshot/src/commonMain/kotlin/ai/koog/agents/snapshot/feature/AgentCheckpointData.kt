@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AgentContextData
import ai.koog.agents.core.agent.context.GraphAgentContextData
import ai.koog.agents.core.agent.context.PlannerAgentContextData
import ai.koog.agents.core.agent.context.RollbackStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.planner.PlannerAgentExecutionPoint
import ai.koog.agents.snapshot.providers.PersistenceUtils
import ai.koog.prompt.message.Message
import ai.koog.serialization.JSONElement
import ai.koog.serialization.JSONNull
import ai.koog.serialization.JSONObject
import ai.koog.serialization.JSONPrimitive
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Represents the checkpoint data for an agent's state during a session.
 *
 * @property checkpointId The unique identifier of the checkpoint. This allows tracking and restoring the agent's session to a specific state.
 * @property createdAt The timestamp when the checkpoint was created.
 * @property nodePath The identifier of the node where the checkpoint was created.
 * @property lastInput Serialized input received for node with [nodePath]
 * @property lastOutput Serialized output received from node with [nodePath]
 * @property messageHistory A list of messages exchanged in the session up to the checkpoint. Messages include interactions between the user, system, assistant, and tools.
 * @property storage Serialized [ai.koog.agents.core.agent.entity.AIAgentStorage]
 * @property properties Additional data associated with the checkpoint. This can be used to store additional information about the agent's state.
 * @property version The version of the checkpoint data structure
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = AgentCheckpointDataSerializer::class)
@KeepGeneratedSerializer
public data class AgentCheckpointData internal constructor(
    val checkpointId: String,
    val createdAt: Instant,
    val messageHistory: List<Message>,
    val storage: JSONObject? = null,
    val version: Long,
    val graphProperties: GraphCheckpointProperties?,
    val plannerProperties: PlannerCheckpointProperties?,
    val properties: JSONObject?
) {

    /**
     * Creates an instance of `AgentCheckpointData` with graph properties.
     */
    @Deprecated("Use the constructor with `GraphCheckpointProperties` instead")
    public constructor(
        checkpointId: String,
        createdAt: Instant,
        nodePath: String,
        lastInput: JSONElement? = null,
        lastOutput: JSONElement? = null,
        messageHistory: List<Message>,
        version: Long,
        properties: JSONObject? = null
    ) : this(
        checkpointId = checkpointId,
        createdAt = createdAt,
        messageHistory = messageHistory,
        storage = null,
        version = version,
        graphProperties = GraphCheckpointProperties(nodePath, lastInput ?: JSONNull, lastOutput ?: JSONNull),
        properties = JSONObject(
            buildMap {
                properties?.entries?.let { putAll(it) }
            }
        )
    )

    /**
     * Creates an instance of `AgentCheckpointData` with graph properties.
     */
    public constructor(
        checkpointId: String,
        createdAt: Instant,
        messageHistory: List<Message>,
        storage: JSONObject? = null,
        version: Long,
        graphProperties: GraphCheckpointProperties,
        properties: JSONObject? = null,
    ) : this(
        checkpointId = checkpointId,
        createdAt = createdAt,
        messageHistory = messageHistory,
        storage = storage,
        version = version,
        graphProperties = graphProperties,
        plannerProperties = null,
        properties = properties
    )

    /**
     * Creates an instance of `AgentCheckpointData` with planner properties.
     */
    public constructor(
        checkpointId: String,
        createdAt: Instant,
        messageHistory: List<Message>,
        storage: JSONObject? = null,
        version: Long,
        plannerProperties: PlannerCheckpointProperties,
        properties: JSONObject? = null,
    ) : this(
        checkpointId = checkpointId,
        createdAt = createdAt,
        messageHistory = messageHistory,
        storage = storage,
        version = version,
        graphProperties = null,
        plannerProperties = plannerProperties,
        properties = properties
    )

    /**
     * The identifier of the node where the checkpoint was created.
     */
    @Deprecated("nodePath is deprecated, use properties[\"nodePath\"] instead")
    public val nodePath: String
        get() = graphProperties?.nodePath ?: error("nodePath is not set")

    /**
     * Serialized input received for node with [nodePath]
     */
    @Deprecated("lstInput is deprecated, use properties[\"lastInput\"] instead")
    public val lastInput: JSONElement
        get() = graphProperties?.lastInput ?: error("lastInput is not set")

    /**
     * Serialized output received from node with [nodePath]
     */
    @Deprecated("lstOutput is deprecated, use properties[\"lastOutput\"] instead")
    public val lastOutput: JSONElement
        get() = graphProperties?.lastOutput ?: error("lastOutput is not set")
}

/**
 * Custom serializer for [AgentCheckpointData] that adds backward compatibility with the
 * pre-refactoring format, where [AgentCheckpointData.nodePath], [AgentCheckpointData.lastInput],
 * and [AgentCheckpointData.lastOutput] were top-level fields instead of entries in [AgentCheckpointData.properties].
 *
 * Old format: `{ "checkpointId": ..., "nodePath": "x", "lastOutput": "y", "messageHistory": [...], ... }`
 * New format: `{ "checkpointId": ..., "messageHistory": [...], "properties": { "nodePath": "x", "lastOutput": "y", ... }, ... }`
 */
@OptIn(ExperimentalSerializationApi::class)
public object AgentCheckpointDataSerializer : JsonTransformingSerializer<AgentCheckpointData>(
    AgentCheckpointData.generatedSerializer()
) {
    /**
     * Returns true if [jsonString] was produced by the old [AgentCheckpointData] format, where
     * `nodePath`, `lastInput`, and `lastOutput` appeared as top-level JSON fields.
     */
    public fun isOldFormat(jsonString: String): Boolean =
        isOldFormat(Json.parseToJsonElement(jsonString).jsonObject)

    private fun isOldFormat(element: JsonObject): Boolean = "nodePath" in element

    private fun migrateFromOldFormat(element: JsonObject): JsonObject {
        val nodePath = element["nodePath"] ?: return element
        val lastInput = element["lastInput"] ?: JsonNull
        val lastOutput = element["lastOutput"] ?: JsonNull

        val graphProperties = buildJsonObject {
            put("nodePath", nodePath)
            put("lastInput", lastInput)
            put("lastOutput", lastOutput)
        }

        return buildJsonObject {
            for ((k, v) in element) {
                if (k !in setOf("nodePath", "lastInput", "lastOutput")) put(k, v)
            }
            put("graphProperties", graphProperties)
        }
    }

    override fun transformDeserialize(element: JsonElement): JsonElement {
        val obj = element.jsonObject
        return if (isOldFormat(obj)) migrateFromOldFormat(obj) else element
    }
}

/**
 * Creates a tombstone checkpoint for an agent's session.
 */
@OptIn(ExperimentalUuidApi::class)
public fun tombstoneCheckpoint(
    createdAt: Instant,
    version: Long,
): AgentCheckpointData {
    return AgentCheckpointData(
        checkpointId = Uuid.random().toString(),
        createdAt = createdAt,
        messageHistory = emptyList(),
        storage = null,
        version = version,
        graphProperties = null,
        plannerProperties = null,
        properties = JSONObject(
            mapOf(
                PersistenceUtils.TOMBSTONE_CHECKPOINT_NAME to JSONPrimitive(true)
            )
        )
    )
}

/**
 * Specialized data for graph-based agents, including execution path and input/output states.
 *
 * @property nodePath The identifier of the node where the checkpoint was created.
 * @property lastInput Deprecated. Serialized input received for the node with [nodePath].
 * @property lastOutput Serialized output received from the node with [nodePath].
 */
@Serializable
public data class GraphCheckpointProperties(
    public val nodePath: String,
    @Deprecated("Use lastOutput instead, lastOutput will be removed in future versions")
    public val lastInput: JSONElement = JSONNull,
    public val lastOutput: JSONElement = JSONNull
)

/**
 * Specialized data for planner agents, capturing state, plan, and current execution point.
 *
 * @property executionPoint The current point in the planner's execution cycle.
 * @property state Serialized state of the planner agent.
 * @property plan Serialized current plan.
 */
@Serializable
public data class PlannerCheckpointProperties(
    public val executionPoint: PlannerAgentExecutionPoint,
    public val state: JSONElement,
    public val plan: JSONElement
)

/**
 * Converts an instance of [AgentCheckpointData] to [AgentContextData].
 */
@InternalAgentsApi
public fun AgentCheckpointData.toAgentContextData(
    rollbackStrategy: RollbackStrategy,
    additionalRollbackActions: suspend (AIAgentContext) -> Unit = {}
): AgentContextData? {
    return when {
        graphProperties != null -> GraphAgentContextData(
            messageHistory = messageHistory,
            storage = storage ?: JSONObject(emptyMap()),
            nodePath = graphProperties.nodePath,
            lastInput = graphProperties.lastInput,
            lastOutput = graphProperties.lastOutput,
            rollbackStrategy = rollbackStrategy,
            additionalRollbackActions = additionalRollbackActions
        )
        plannerProperties != null -> PlannerAgentContextData(
            messageHistory = messageHistory,
            state = plannerProperties.state,
            plan = plannerProperties.plan,
            executionPoint = plannerProperties.executionPoint,
            rollbackStrategy = rollbackStrategy,
            additionalRollbackActions = additionalRollbackActions
        )
        else -> null
    }
}

/**
 * Checks whether the `AgentCheckpointData` instance is marked as a tombstone.
 */
public fun AgentCheckpointData.isTombstone(): Boolean =
    properties?.entries[PersistenceUtils.TOMBSTONE_CHECKPOINT_NAME] == JSONPrimitive(true)
