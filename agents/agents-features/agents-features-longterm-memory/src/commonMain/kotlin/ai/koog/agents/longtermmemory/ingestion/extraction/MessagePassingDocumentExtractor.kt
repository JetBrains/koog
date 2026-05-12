package ai.koog.agents.longtermmemory.ingestion.extraction

import ai.koog.agents.longtermmemory.model.MemoryRecord
import ai.koog.prompt.message.Message
import ai.koog.rag.base.TextDocument

/**
 * Default extractor that filters messages by role.
 *
 * This extractor filters messages to only include those with roles in
 * [messageRolesToExtract], then converts each message's content into [TextDocument]s.
 *
 * @property messageRolesToExtract The set of message roles to extract and persist.
 *   Defaults to `setOf(Message.Role.User, Message.Role.Assistant)`.
 */
public class MessagePassingDocumentExtractor(
    public val messageRolesToExtract: Set<Message.Role> = setOf(Message.Role.User, Message.Role.Assistant),
) : DocumentExtractor {

    private companion object {
        private const val MESSAGE_ROLE_FIELD_NAME = "messageRole"
        private const val TIMESTAMP_FIELD_NAME = "timestampMs"
    }

    /**
     * Builder for [MessagePassingDocumentExtractor].
     *
     * Provides a fluent API for constructing a [MessagePassingDocumentExtractor],
     * which is convenient for Java users.
     *
     * Example usage (Java):
     * ```java
     * new MessagePassingDocumentExtractor.Builder()
     *     .withExtractRoles(new HashSet<>(Arrays.asList(Message.Role.User, Message.Role.Assistant)))
     *     .build()
     * ```
     */
    public class Builder {
        /**
         * The set of message roles to extract. Defaults to User and Assistant.
         */
        public var extractRoles: Set<Message.Role> = setOf(Message.Role.User, Message.Role.Assistant)

        /** Fluent setter for [extractRoles]. */
        public fun withExtractRoles(roles: Set<Message.Role>): Builder =
            apply { this.extractRoles = roles }

        /** Builds a [MessagePassingDocumentExtractor] from the current settings. */
        public fun build(): MessagePassingDocumentExtractor =
            MessagePassingDocumentExtractor(extractRoles)
    }

    override suspend fun extract(messages: List<Message>): List<TextDocument> {
        return messages
            .filter { it.role in messageRolesToExtract }
            .map { messageToMemoryRecord(it) }
    }

    private fun messageToMemoryRecord(message: Message): TextDocument = MemoryRecord(
        content = message.content,
        metadata = mapOf(
            MESSAGE_ROLE_FIELD_NAME to message.role.name,
            TIMESTAMP_FIELD_NAME to message.metaInfo.timestamp.toEpochMilliseconds()
        ),
    )
}
