package ai.koog.agents.longtermmemory.ingestion.extraction

import ai.koog.agents.longtermmemory.model.MemoryRecord
import ai.koog.prompt.message.Message
import ai.koog.rag.base.TextDocument

/**
 * Default extractor that filters messages by role.
 *
 * This extractor filters messages to only include those with roles in
 * [messageRolesToExtract], then converts each message's content into [MemoryRecord]s.
 *
 * When [lastMessageOnly] is `true`, only the last message for each role in [messageRolesToExtract]
 * is extracted. This is useful with [ai.koog.agents.longtermmemory.ingestion.IngestionTiming.ON_LLM_CALL] to avoid re-ingesting
 * messages that were already stored in previous calls.
 *
 * @property messageRolesToExtract The set of message roles to extract and persist.
 *   Defaults to `setOf(Message.Role.User, Message.Role.Assistant)`.
 * @property lastMessageOnly When `true`, only the last message for each matching role is extracted.
 *   Defaults to `false`.
 */
public class FilteringExtractionStrategy(
    public val messageRolesToExtract: Set<Message.Role> = setOf(Message.Role.User, Message.Role.Assistant),
    public val lastMessageOnly: Boolean = false,
) : ExtractionStrategy {

    private companion object {
        private const val MESSAGE_ROLE_FIELD_NAME = "messageRole"
        private const val TIMESTAMP_FIELD_NAME = "timestampMs"
    }

    /**
     * Builder for [FilteringExtractionStrategy].
     *
     * Provides a fluent API for constructing a [FilteringExtractionStrategy],
     * which is convenient for Java users.
     *
     * Example usage (Java):
     * ```java
     * new FilteringExtractionStrategy.Builder()
     *     .withExtractRoles(new HashSet<>(Arrays.asList(Message.Role.User, Message.Role.Assistant)))
     *     .withLastMessageOnly(true)
     *     .build()
     * ```
     */
    public class Builder {
        /**
         * The set of message roles to extract. Defaults to User and Assistant.
         */
        public var extractRoles: Set<Message.Role> = setOf(Message.Role.User, Message.Role.Assistant)

        /**
         * When true, only the last message for each matching role is extracted.
         * Defaults to false.
         */
        public var lastMessageOnly: Boolean = false

        /** Fluent setter for [extractRoles]. */
        public fun withExtractRoles(roles: Set<Message.Role>): Builder =
            apply { this.extractRoles = roles }

        /** Fluent setter for [lastMessageOnly]. */
        public fun withLastMessageOnly(lastMessageOnly: Boolean): Builder =
            apply { this.lastMessageOnly = lastMessageOnly }

        /** Builds a [FilteringExtractionStrategy] from the current settings. */
        public fun build(): FilteringExtractionStrategy =
            FilteringExtractionStrategy(extractRoles, lastMessageOnly)
    }

    override suspend fun extract(messages: List<Message>): List<TextDocument> {
        val filtered: List<Message> = if (lastMessageOnly) {
            messageRolesToExtract.mapNotNull { role ->
                messages.lastOrNull { it.role == role }
            }
        } else {
            messages.filter { it.role in messageRolesToExtract }
        }
        return filtered.map { messageToMemoryRecord(it) }
    }

    private fun messageToMemoryRecord(message: Message): TextDocument = MemoryRecord(
        content = message.content,
        metadata = mapOf(
            MESSAGE_ROLE_FIELD_NAME to message.role.name,
            TIMESTAMP_FIELD_NAME to message.metaInfo.timestamp.toEpochMilliseconds()
        ),
    )
}
