package ai.koog.agents.features.chathistory.aws

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import aws.sdk.kotlin.services.bedrockagentcore.model.Content
import aws.sdk.kotlin.services.bedrockagentcore.model.Conversational
import aws.sdk.kotlin.services.bedrockagentcore.model.PayloadType
import aws.sdk.kotlin.services.bedrockagentcore.model.Role
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

/**
 * Metadata key for storing the AgentCore eventId in message metadata.
 * Used for delta detection — messages with this key have already been persisted.
 */
public const val EVENT_ID_METADATA_KEY: String = "agentcore.eventId"

/**
 * Converts between Koog [Message] instances and Bedrock AgentCore [PayloadType.Conversational] payloads.
 *
 * This converter is intentionally limited to conversational message types only:
 * [Message.User] and [Message.Assistant]. Non-conversational message types
 * (System, Tool, Reasoning, attachments) are outside the scope of this provider
 * and are silently skipped when [ignoreUnsupportedValues] is `true`, or cause an
 * [IllegalStateException] when `false`.
 *
 * When messages contain attachments or mixed content parts, only the text portions
 * are extracted. Messages with no text content at all are skipped or rejected
 * based on [ignoreUnsupportedValues].
 *
 * @see AgentcoreConversationIdParser
 * @see AgentcoreChatHistoryProvider
 */
public object AgentcoreMessageConverter {

    /**
     * Converts a Koog [Message] to a Bedrock AgentCore [PayloadType.Conversational].
     *
     * Only [Message.User] and [Message.Assistant] are supported.
     * If a message contains attachments or mixed content parts, only the text parts are extracted.
     * Messages with no text content at all are treated as unsupported.
     *
     * @param message The Koog message to convert.
     * @param ignoreUnsupportedValues If `true`, unsupported message types or non-text content return `null`.
     *   If `false`, they throw [IllegalStateException].
     * @return The converted payload, or `null` if the message is unsupported
     *   and [ignoreUnsupportedValues] is `true`.
     * @throws IllegalStateException if the message is unsupported and [ignoreUnsupportedValues] is `false`.
     */
    internal fun messageToPayload(message: Message, ignoreUnsupportedValues: Boolean = true): PayloadType.Conversational? {
        val role = when (message) {
            is Message.User -> Role.User
            is Message.Assistant -> Role.Assistant
            else -> {
                if (ignoreUnsupportedValues) {
                    return null
                } else {
                    throw IllegalStateException(
                        "Unsupported message type: ${message::class.simpleName}"
                    )
                }
            }
        }

        // Extract text content; reject messages with no text at all
        val textContent = message.content
        if (textContent.isEmpty()) {
            if (ignoreUnsupportedValues) {
                return null
            } else {
                throw IllegalStateException(
                    "Unsupported content in ${message::class.simpleName}: message has no text content"
                )
            }
        }

        val conversational = Conversational {
            this.role = role
            this.content = Content.Text(textContent)
        }

        return PayloadType.Conversational(conversational)
    }

    /**
     * Converts a Bedrock AgentCore [Conversational] payload back to a Koog [Message],
     * attaching the originating [eventId] and [eventTimestamp] in the message's metadata.
     *
     * Only [Role.User] and [Role.Assistant] are supported. Other roles are
     * non-conversational and intentionally outside the scope of this provider.
     *
     * Only [Content.Text] is supported. Non-text content returns `null` or throws
     * based on [ignoreUnsupportedValues].
     *
     * @param conversational The AgentCore conversational payload.
     * @param eventId The AgentCore event ID to attach as metadata.
     * @param eventTimestamp The event timestamp to use for the message.
     * @param ignoreUnsupportedValues If `true`, unsupported roles or non-text content return `null`.
     *   If `false`, they throw [IllegalStateException].
     * @return The converted message, or `null` if unsupported and [ignoreUnsupportedValues] is `true`.
     * @throws IllegalStateException if unsupported and [ignoreUnsupportedValues] is `false`.
     */
    internal fun conversationalToMessage(
        conversational: Conversational,
        eventId: String,
        eventTimestamp: Instant,
        ignoreUnsupportedValues: Boolean = true
    ): Message? {
        val textContent = conversational.content as? Content.Text
        if (textContent == null) {
            if (ignoreUnsupportedValues) {
                // Skip non-text AWS content
                return null
            } else {
                throw IllegalStateException(
                    "Unsupported AWS content type: ${conversational.content!!::class.simpleName}. Only Content.Text is supported."
                )
            }
        }
        val content = textContent.value
        val metadata = JsonObject(mapOf(EVENT_ID_METADATA_KEY to JsonPrimitive(eventId)))

        return when (conversational.role) {
            Role.User -> Message.User(
                content,
                RequestMetaInfo(timestamp = eventTimestamp, metadata = metadata)
            )

            Role.Assistant -> Message.Assistant(
                content,
                ResponseMetaInfo(timestamp = eventTimestamp, metadata = metadata)
            )

            else -> {
                if (ignoreUnsupportedValues) {
                    null
                } else {
                    throw IllegalStateException(
                        "Unsupported role: ${conversational.role}"
                    )
                }
            }
        }
    }

    /**
     * Returns the eventId stored in a message's metadata, or `null` if absent.
     */
    internal fun getEventId(message: Message): String? {
        val metadata = message.metaInfo.metadata ?: return null
        return (metadata[EVENT_ID_METADATA_KEY] as? JsonPrimitive)?.content
    }
}
