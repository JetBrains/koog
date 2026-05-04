@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AgentContextData
import ai.koog.agents.core.agent.context.RollbackStrategy
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.snapshot.providers.PersistenceUtils
import ai.koog.prompt.message.Message
import ai.koog.serialization.JSONElement
import ai.koog.serialization.JSONNull
import ai.koog.serialization.JSONObject
import ai.koog.serialization.JSONPrimitive
import ai.koog.serialization.kotlinx.toKoogJSONElement
import ai.koog.serialization.kotlinx.toKoogJSONObject
import ai.koog.serialization.kotlinx.toKotlinxJsonElement
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.jvm.JvmOverloads
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val logger = KotlinLogging.logger { }

/**
 * Represents the checkpoint data for an agent's state during a session.
 *
 * @property checkpointId The unique identifier of the checkpoint. This allows tracking and restoring the agent's session to a specific state.
 * @property messageHistory A list of messages exchanged in the session up to the checkpoint. Messages include interactions between the user, system, assistant, and tools.
 * @property nodePath The identifier of the node where the checkpoint was created.
 * @property lastInput Serialized input received for node with [nodePath]
 * @property lastOutput Serialized output received from node with [nodePath]
 * @property properties Additional data associated with the checkpoint. This can be used to store additional information about the agent's state.
 * @property createdAt The timestamp when the checkpoint was created.
 * @property version The version of the checkpoint data structure.
 * @property storage Encoded `AIAgentContext.storage` entries opted in via
 *  [PersistenceFeatureConfig.persistedKeys]. Keys are the storage key names and values are the
 *  serialized form produced with the registered [kotlinx.serialization.KSerializer]. `null` (the
 *  default) means no storage was captured, which is what older checkpoints look like - they keep
 *  deserializing without a structure version bump.
 */
@Serializable
public data class AgentCheckpointData @JvmOverloads constructor(
    val checkpointId: String,
    val createdAt: Instant,
    val nodePath: String,
    @Deprecated("Use lastOutput instead, lastOutput will be removed in future versions")
    val lastInput: JSONElement? = null,
    val lastOutput: JSONElement? = null,
    val messageHistory: List<Message>,
    val version: Long,
    val properties: JSONObject? = null,
    val storage: JSONObject? = null,
) {
    /**
     * Constructs an instance of the class with the specified parameters.
     * This constructor is marked as deprecated and may be removed in the future.
     *
     * @param checkpointId A unique identifier for the checkpoint.
     * @param createdAt The timestamp indicating when the checkpoint was created.
     * @param nodePath The path of the node associated with this checkpoint.
     * @param lastInput The last input state, represented as a JSON element.
     *                  This parameter is deprecated. Use `lastOutput` instead.
     * @param lastOutput The last output state, represented as a JSON element.
     * @param messageHistory The history of messages associated with this checkpoint.
     * @param version The version number of the checkpoint data.
     * @param properties Additional properties associated with the checkpoint, represented as a JSON object.
     */
    @Deprecated("Use AgentCheckpointData constructor that accepts koog.JSONElement instead of kotlinx.JsonElement")
    public constructor(
        checkpointId: String,
        createdAt: Instant,
        nodePath: String,
        lastInput: JsonElement? = null,
        lastOutput: JsonElement? = null,
        messageHistory: List<Message>,
        version: Long,
        properties: JsonObject? = null
    ) : this(
        checkpointId = checkpointId,
        createdAt = createdAt,
        nodePath = nodePath,
        lastInput = lastInput?.toKoogJSONElement(),
        lastOutput = lastOutput?.toKoogJSONElement(),
        messageHistory = messageHistory,
        version = version,
        properties = properties?.toKoogJSONObject(),
        storage = null,
    )

    init {
        if (nodePath != PersistenceUtils.TOMBSTONE_CHECKPOINT_NAME) {
            require(lastInput == null || lastOutput == null) { "`lastInput` and `lastOutput` cannot be both set" }
            require(lastInput != null || lastOutput != null) { "`lastInput` (until 0.6.0) or `lastOutput` (since 0.6.1) must be set" }
        }
    }

    private fun eq(json1: JSONElement?, json2: JSONElement?): Boolean =
        json1 == json2 || ((json1 == null || json1 == JSONNull) && (json2 == null || json2 == JSONNull))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AgentCheckpointData) return false
        return checkpointId == other.checkpointId &&
            nodePath == other.nodePath &&
            createdAt == other.createdAt &&
            eq(lastInput, other.lastInput) &&
            eq(lastOutput, other.lastOutput) &&
            messageHistory == other.messageHistory &&
            version == other.version &&
            properties == other.properties &&
            storage == other.storage
    }
}

/**
 * Creates a tombstone checkpoint for an agent's session.
 * A tombstone checkpoint represents a placeholder state with no real interactions or messages,
 * intended to mark a terminated or invalid session.
 *
 * @return An `AgentCheckpointData` instance with predefined properties indicating a tombstone state.
 */
@OptIn(ExperimentalUuidApi::class)
public fun tombstoneCheckpoint(time: Instant, version: Long): AgentCheckpointData {
    return AgentCheckpointData(
        checkpointId = Uuid.random().toString(),
        createdAt = time,
        nodePath = PersistenceUtils.TOMBSTONE_CHECKPOINT_NAME,
        lastOutput = JSONNull,
        messageHistory = emptyList(),
        properties = JSONObject(
            mapOf(
                PersistenceUtils.TOMBSTONE_CHECKPOINT_NAME to JSONPrimitive(true)
            )
        ),
        version = version
    )
}

/**
 * Converts an instance of [AgentCheckpointData] to [AgentContextData].
 *
 * In addition to the execution-position fields (`messageHistory`, `nodePath`, `lastInput`/`lastOutput`),
 * any [storage] entries whose key name matches an entry in [persistedKeys] are decoded with the
 * registered [kotlinx.serialization.KSerializer] and surfaced through [AgentContextData.storageEntries],
 * so the strategy can write them back into the agent's storage under the original key instances.
 *
 * Entries in [storage] without a matching registration are dropped with a warning - that case usually
 * means the user removed a key from [PersistenceFeatureConfig.persistedKeys] but still has older
 * checkpoints around. Decoding errors are also dropped per-entry rather than failing the whole resume,
 * because losing one piece of intermediate state is usually preferable to losing the whole session.
 *
 * @param rollbackStrategy How execution state should be rolled back when restoring.
 * @param additionalRollbackAction Optional extra cleanup (for example, side-effecting tool rollbacks).
 * @param persistedKeys Storage keys to restore. Defaults to empty for callers that don't use storage
 *  persistence; pass the same list registered with [PersistenceFeatureConfig.persistedKeys] when
 *  resuming an agent that captured storage entries.
 */
public fun AgentCheckpointData.toAgentContextData(
    rollbackStrategy: RollbackStrategy,
    additionalRollbackAction: suspend (AIAgentContext) -> Unit = {},
    persistedKeys: List<PersistableStorageKey<*>> = emptyList(),
): AgentContextData {
    @Suppress("DEPRECATION")
    return AgentContextData(
        messageHistory = messageHistory,
        nodePath = nodePath,
        lastInput = lastInput,
        lastOutput = lastOutput,
        rollbackStrategy = rollbackStrategy,
        additionalRollbackActions = additionalRollbackAction,
        storageEntries = decodeStorageEntries(storage, persistedKeys),
    )
}

/**
 * Decodes [encodedStorage] into a typed map keyed by the original [AIAgentStorageKey] instances
 * supplied through [persistedKeys]. Returns an empty map if there is nothing to decode.
 */
private fun decodeStorageEntries(
    encodedStorage: JSONObject?,
    persistedKeys: List<PersistableStorageKey<*>>,
): Map<AIAgentStorageKey<*>, Any> {
    if (encodedStorage == null || encodedStorage.entries.isEmpty()) return emptyMap()
    if (persistedKeys.isEmpty()) {
        logger.warn {
            "Checkpoint contains ${encodedStorage.entries.size} persisted storage entr" +
                "${if (encodedStorage.entries.size == 1) "y" else "ies"} but no PersistableStorageKey is " +
                "registered for restore - storage will not be restored."
        }
        return emptyMap()
    }
    val keysByName = persistedKeys.associateBy { it.key.name }
    val json = PersistenceUtils.defaultCheckpointJson
    val result = mutableMapOf<AIAgentStorageKey<*>, Any>()
    for ((name, encoded) in encodedStorage.entries) {
        val persistable = keysByName[name]
        if (persistable == null) {
            logger.warn {
                "Checkpoint contains storage entry '$name' with no matching PersistableStorageKey - skipping."
            }
            continue
        }
        val decoded = try {
            json.decodeFromJsonElement(persistable.serializer, encoded.toKotlinxJsonElement())
        } catch (e: Exception) {
            logger.warn(e) { "Failed to decode persisted storage entry '$name', skipping." }
            null
        }
        if (decoded != null) {
            result[persistable.key] = decoded
        }
    }
    return result
}

/**
 * Encodes the storage entries that match [persistedKeys] into a [JSONObject] suitable for
 * [AgentCheckpointData.storage]. Entries whose key is not in [storageMap] are omitted; encoding
 * failures are logged and skipped per-entry rather than failing the whole snapshot.
 *
 * Returns `null` when no entries were encoded so the field is omitted from the serialized form.
 */
internal fun encodeStorageEntries(
    storageMap: Map<AIAgentStorageKey<*>, Any>,
    persistedKeys: List<PersistableStorageKey<*>>,
): JSONObject? {
    if (persistedKeys.isEmpty() || storageMap.isEmpty()) return null
    val json = PersistenceUtils.defaultCheckpointJson
    val encoded = mutableMapOf<String, JSONElement>()
    for (persistable in persistedKeys) {
        val value = storageMap[persistable.key] ?: continue
        try {
            @Suppress("UNCHECKED_CAST")
            val serializer = persistable.serializer as kotlinx.serialization.KSerializer<Any>
            encoded[persistable.key.name] = json.encodeToJsonElement(serializer, value).toKoogJSONElement()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to encode persisted storage entry '${persistable.key.name}', skipping." }
        }
    }
    return if (encoded.isEmpty()) null else JSONObject(encoded)
}

/**
 * Checks whether the `AgentCheckpointData` instance is marked as a tombstone.
 *
 * A tombstone typically indicates that the checkpoint represents a terminated or inactive state.
 *
 * @return `true` if the `properties` map contains a key-value pair where the key is "tombstone"
 *         and the value is a JSON primitive set to `true`, otherwise `false`.
 */
public fun AgentCheckpointData.isTombstone(): Boolean =
    properties?.entries?.get(PersistenceUtils.TOMBSTONE_CHECKPOINT_NAME) == JSONPrimitive(true)
