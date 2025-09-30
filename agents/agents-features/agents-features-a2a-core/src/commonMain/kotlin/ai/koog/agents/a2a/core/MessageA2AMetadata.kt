package ai.koog.agents.a2a.core

import ai.koog.prompt.message.MessageMetaInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Key used to store [MessageA2AMetadata] in [ai.koog.prompt.message.MessageMetaInfo.metadata]
 */
public const val MESSAGE_A2A_METADATA_KEY: String = "a2a_metadata"

/**
 * Represents additional A2A-related message metadata stored in [ai.koog.prompt.message.MessageMetaInfo.metadata]
 * For more info on each field, check [ai.koog.a2a.model.Message]
 */
@Serializable
public data class MessageA2AMetadata(
    val messageId: String,
    val contextId: String? = null,
    val taskId: String? = null,
    val referenceTaskIds: List<String>? = null,
    val metadata: JsonObject? = null,
    val extensions: List<String>? = null,
)

/**
 * Retrieves [MessageA2AMetadata] from [MessageMetaInfo.metadata], if [MESSAGE_A2A_METADATA_KEY] is present.
 */
public fun MessageMetaInfo.getA2AMetadata(): MessageA2AMetadata? {
    return metadata
        ?.get(MESSAGE_A2A_METADATA_KEY)
        ?.let { a2aMetadata ->
            A2AFeatureJson.decodeFromJsonElement<MessageA2AMetadata>(a2aMetadata)
        }
}

/**
 * Updates provided [JsonObject], overwriting [MESSAGE_A2A_METADATA_KEY] with [a2aMetadata].
 */
public fun JsonObject.withA2AMetadata(a2aMetadata: MessageA2AMetadata): JsonObject {
    return toMutableMap()
        .apply {
            put(MESSAGE_A2A_METADATA_KEY, A2AFeatureJson.encodeToJsonElement(a2aMetadata))
        }
        .let { JsonObject(it) }
}
