package ai.koog.agents.a2a.core

import ai.koog.a2a.model.Role
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Alias to A2A message type, to avoid clashing with Koog's message type.
 * @see [ai.koog.a2a.model.Message]
 */
public typealias A2AMessage = ai.koog.a2a.model.Message

/**
 * Converts [A2AMessage] to Koog's [Message].
 * Returned message will contain [MessageA2AMetadata] at [MESSAGE_A2A_METADATA_KEY] in [ai.koog.prompt.message.MessageMetaInfo.metadata],
 * which can be retrieved with helper method [getA2AMetadata].
 *
 * @param clock The clock to use for the timestamp. Defaults to [Clock.System].
 */
public fun A2AMessage.toKoogMessage(
    clock: Clock = Clock.System,
): Message {
    // Convert to the actual message content and attachments.
    val (content, attachments) = parts.map { it.toKoogPart() }.toContentWithAttachments()

    // Create metadata
    val metadata = JsonObject(emptyMap()).withA2AMetadata(
        MessageA2AMetadata(
            messageId = messageId,
            contextId = contextId,
            taskId = taskId,
            referenceTaskIds = referenceTaskIds,
            metadata = metadata,
            extensions = extensions,
        )
    )

    return when (role) {
        Role.User -> Message.User(
            content = content,
            metaInfo = RequestMetaInfo(
                timestamp = clock.now(),
                metadata = metadata,
            ),
            attachments = attachments.toList(),
        )

        Role.Agent -> Message.Assistant(
            content = content,
            metaInfo = ResponseMetaInfo(
                timestamp = clock.now(),
                metadata = metadata,
            ),
            attachments = attachments,
        )
    }
}

/**
 * Converts Koog's [Message] to [A2AMessage].
 * To fill A2A-specific fields, it will attempt to read [MessageA2AMetadata] from [ai.koog.prompt.message.MessageMetaInfo.metadata],
 * but it also can be overridden with [a2aMetadata]
 *
 * @param a2aMetadata The A2A-specific metadata to override exiting in this [Message].
 * @see ai.koog.a2a.model.Message
 */
@OptIn(ExperimentalUuidApi::class)
public fun Message.toA2AMessage(
    a2aMetadata: MessageA2AMetadata? = null,
): A2AMessage {
    val actualMetadata = a2aMetadata ?: metaInfo.getA2AMetadata()

    val role = when (this) {
        is Message.User -> Role.User
        is Message.Assistant -> Role.Agent
        else -> throw IllegalArgumentException("A2A can't handle this Koog message type: $this")
    }

    // Add parts
    val parts = (listOf(KoogContentPart(content)) + attachments.map { KoogAttachmentPart(it) })
        .map { it.toA2APart() }

    return A2AMessage(
        messageId = actualMetadata?.messageId ?: Uuid.random().toString(),
        role = role,
        parts = parts,
        extensions = actualMetadata?.extensions,
        taskId = actualMetadata?.taskId,
        referenceTaskIds = actualMetadata?.referenceTaskIds,
        contextId = actualMetadata?.contextId,
        metadata = actualMetadata?.metadata
    )
}
