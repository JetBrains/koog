package ai.koog.agents.a2a.core

import ai.koog.a2a.model.DataPart
import ai.koog.a2a.model.FilePart
import ai.koog.a2a.model.FileWithBytes
import ai.koog.a2a.model.FileWithUri
import ai.koog.a2a.model.Part
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.TextPart
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
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

    val parts = parts.map { it.toKoogPart() }

    return when (role) {
        Role.User -> Message.User(
            parts = parts,
            metaInfo = RequestMetaInfo(
                timestamp = clock.now(),
                metadata = metadata,
            ),
        )

        Role.Agent -> Message.Assistant(
            parts = parts,
            metaInfo = ResponseMetaInfo(
                timestamp = clock.now(),
                metadata = metadata,
            ),
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

    val parts = parts.map { it.toA2APart() }

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

/**
 * Converts Koog [ContentPart] to A2A [Part].
 */
public fun ContentPart.toA2APart(): Part = when (this) {
    is ContentPart.Text -> TextPart(this.text)

    is ContentPart.Attachment -> {
        val file = when (val content = this.content) {
            // Plain text files are not supported, convert them to binary files.
            is AttachmentContent.PlainText -> FileWithBytes(
                bytes = AttachmentContent.Binary.Bytes(content.text.encodeToByteArray())
                    .asBase64(),
                name = this.fileName,
                mimeType = this.mimeType,
            )

            is AttachmentContent.Binary -> FileWithBytes(
                bytes = content.asBase64(),
                name = this.fileName,
                mimeType = this.mimeType,
            )

            is AttachmentContent.URL -> FileWithUri(
                uri = content.url,
                name = this.fileName,
                mimeType = this.mimeType,
            )
        }

        FilePart(file)
    }
}

/**
 * Converts A2A [Part] to Koog [ContentPart].
 */
public fun Part.toKoogPart(): ContentPart = when (this) {
    is TextPart -> ContentPart.Text(this.text)
    // Koog doesn't support structured data as a separate type, treat it as a content part.

    is DataPart -> ContentPart.Text(A2AFeatureJson.encodeToString(this.data))

    is FilePart -> {
        val file = this.file // to enable smart cast

        val part = ContentPart.File(
            // do not have that information separately in A2A
            format = "",
            // if no mime type is provided, assume it's arbitrary binary data
            mimeType = file.mimeType ?: "application/octet-stream",
            fileName = file.name,
            content = when (file) {
                is FileWithBytes -> AttachmentContent.Binary.Base64(file.bytes)
                is FileWithUri -> AttachmentContent.URL(file.uri)
            }
        )

        part
    }
}
