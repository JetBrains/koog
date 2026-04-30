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
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.kotlinx.toKotlinxJsonElement
import ai.koog.serialization.typeToken
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
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Represents the checkpoint data for an agent's state during a session.
 *
 * @property checkpointId The unique identifier of the checkpoint. This allows tracking and restoring the agent's session to a specific state.
 * @property messageHistory A list of messages exchanged in the session up to the checkpoint. Messages include interactions between the user, system, assistant, and tools.
 * @property properties Additional data associated with the checkpoint. This can be used to store additional information about the agent's state.
 * @property createdAt The timestamp when the checkpoint was created.
 * @property version The version of the checkpoint data structure
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = AgentCheckpointDataSerializer::class)
@KeepGeneratedSerializer
public data class AgentCheckpointData(
    val checkpointId: String,
    val createdAt: Instant,
    val messageHistory: List<Message>,
    val version: Long,
    val properties: JSONObject
) {

    /**
     * Creates an instance of `AgentCheckpointData` using graph properties.
     */
    @Deprecated("The nodePath, lastInput, and lastOutput should be put in the properties")
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
        checkpointId,
        createdAt,
        messageHistory,
        version,
        JSONObject(
            buildMap {
                properties?.entries?.let { putAll(it) }
                put("nodePath", JSONPrimitive(nodePath))
                put("lastInput", lastInput ?: JSONNull)
                put("lastOutput", lastOutput ?: JSONNull)
            }
        )
    )

    /**
     * The identifier of the node where the checkpoint was created.
     */
    @Deprecated("nodePath is deprecated, use properties[\"nodePath\"] instead")
    public val nodePath: String
        get() = properties
            .entries["nodePath"]
            ?.toKotlinxJsonElement()
            ?.jsonPrimitive
            ?.content
            ?: error("nodePath is not set")

    /**
     * Serialized input received for node with [nodePath]
     */
    @Deprecated("lstInput is deprecated, use properties[\"lastInput\"] instead")
    public val lastInput: JSONElement
        get() = properties
            .entries["lastInput"]
            ?: error("lastInput is not set")

    /**
     * Serialized output received from node with [nodePath]
     */
    @Deprecated("lstOutput is deprecated, use properties[\"lastOutput\"] instead")
    public val lastOutput: JSONElement
        get() = properties
            .entries["lastOutput"]
            ?: error("lastOutput is not set")
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

        val mergedProperties = buildJsonObject {
            (element["properties"] as? JsonObject)?.forEach { (k, v) -> put(k, v) }
            put("nodePath", nodePath)
            put("lastInput", lastInput)
            put("lastOutput", lastOutput)
        }

        return buildJsonObject {
            for ((k, v) in element) {
                if (k !in setOf("nodePath", "lastInput", "lastOutput", "properties")) put(k, v)
            }
            put("properties", mergedProperties)
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
        version = version,
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
) {
    init {
        require(lastInput == JSONNull || lastOutput == JSONNull) { "`lastInput` and `lastOutput` cannot be both set" }
        require(lastInput != JSONNull || lastOutput != JSONNull) { "`lastInput` (until 0.6.0) or `lastOutput` (since 0.6.1) must be set" }
    }
}

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
    serializer: JSONSerializer = KotlinxSerializer(),
    additionalRollbackActions: suspend (AIAgentContext) -> Unit = {}
): AgentContextData? {
    runCatching {
        serializer.decodeFromJSONElement<GraphCheckpointProperties>(
            properties,
            typeToken<GraphCheckpointProperties>()
        )
    }.getOrNull()?.let { graphProperties ->
        return GraphAgentContextData(
            messageHistory = messageHistory,
            nodePath = graphProperties.nodePath,
            lastInput = graphProperties.lastInput,
            lastOutput = graphProperties.lastOutput,
            rollbackStrategy = rollbackStrategy,
            additionalRollbackActions = additionalRollbackActions
        )
    }

    runCatching {
        serializer.decodeFromJSONElement<PlannerCheckpointProperties>(
            properties,
            typeToken<PlannerCheckpointProperties>()
        )
    }.getOrNull()?.let { plannerProperties ->
        return PlannerAgentContextData(
            messageHistory = messageHistory,
            state = plannerProperties.state,
            plan = plannerProperties.plan,
            executionPoint = plannerProperties.executionPoint,
            rollbackStrategy = rollbackStrategy,
            additionalRollbackActions = additionalRollbackActions
        )
    }

    return null
}

/**
 * Checks whether the `AgentCheckpointData` instance is marked as a tombstone.
 */
public fun AgentCheckpointData.isTombstone(): Boolean =
    properties.entries[PersistenceUtils.TOMBSTONE_CHECKPOINT_NAME] == JSONPrimitive(true)
