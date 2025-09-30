package ai.koog.agents.a2a.core

import ai.koog.a2a.model.Role
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.xml.xml
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
 *
 * @param clock The clock to use for the timestamp. Defaults to [Clock.System].
 */
public fun A2AMessage.toKoogMessage(
    clock: Clock = Clock.System,
): Message {
    // Convert to the actual message content and attachments.
    val (messageContent, attachments) = parts.map { it.toKoogPart() }.toContentWithAttachments()

    val content = xml {
        tag("message_content") {
            +messageContent
        }

        // Put ids information in the text content, since Koog doesn't have special fields for them.
        tag("a2a_message_metadata") {
            contextId?.let {
                tag("context_id") { +it }
            }

            taskId?.let {
                tag("task_id") { +it }
            }

            referenceTaskIds
                ?.takeIf { it.isNotEmpty() }
                ?.let { referenceIds ->
                    tag("reference_task_ids") {
                        referenceIds.forEach {
                            tag("id") { +it }
                        }
                    }
                }
        }
    }

    return when (role) {
        Role.User -> Message.User(
            content = content.toString(),
            metaInfo = RequestMetaInfo(
                timestamp = clock.now()
            ),
            attachments = attachments.toList(),
        )

        Role.Agent -> Message.Assistant(
            content = content.toString(),
            metaInfo = ResponseMetaInfo(
                timestamp = clock.now()
            ),
            attachments = attachments,
        )
    }
}

/**
 * Converts Koog's [Message] to [A2AMessage].
 *
 * @see ai.koog.a2a.model.Message
 */
@OptIn(ExperimentalUuidApi::class)
public fun Message.toA2AMessage(
    messageId: String = Uuid.random().toString(),
    contextId: String? = null,
    taskId: String? = null,
    referenceTaskIds: List<String>? = null,
    metadata: JsonObject? = null,
    extensions: List<String>? = null,
): A2AMessage {
    val role = when (this) {
        is Message.User -> Role.User
        is Message.Assistant -> Role.Agent
        else -> throw IllegalArgumentException("A2A can't handle this Koog message type: $this")
    }

    // Add parts
    val parts = (listOf(KoogContentPart(content)) + attachments.map { KoogAttachmentPart(it) })
        .map { it.toA2APart() }

    return A2AMessage(
        messageId = messageId,
        role = role,
        parts = parts,
        extensions = extensions,
        taskId = taskId,
        referenceTaskIds = referenceTaskIds,
        contextId = contextId,
        metadata = metadata
    )
}
