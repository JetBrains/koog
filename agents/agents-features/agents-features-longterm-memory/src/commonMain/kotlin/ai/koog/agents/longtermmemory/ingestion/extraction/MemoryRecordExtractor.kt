package ai.koog.agents.longtermmemory.ingestion.extraction

import ai.koog.agents.longtermmemory.model.MemoryRecord
import ai.koog.prompt.message.Message

/**
 * Extractor of memory records during message ingestion.
 *
 * This is a functional interface (SAM) that defines how a list of messages
 * should be transformed into a list of [MemoryRecord]s for storage.
 * It provides flexibility in how messages are filtered, transformed, and
 * converted into memory records while maintaining type safety.
 *
 * Pre-built implementations are available for common ingestion patterns:
 * - [FilteringMemoryRecordExtractor] - Filters messages by role
 *
 * ### Usage Examples
 *
 * **Using pre-built extractors (Kotlin):**
 * ```kotlin
 * // Extract User and Assistant messages (default)
 * val extractor = FilteringMemoryRecordExtractor()
 *
 * // Extract only User messages
 * val extractor = FilteringMemoryRecordExtractor(
 *     messageRolesToExtract = setOf(Message.Role.User)
 * )
 * ```
 *
 * **Custom implementation as lambda (Kotlin):**
 * ```kotlin
 * val customExtractor = MemoryRecordExtractor { messages ->
 *     messages
 *         .filter { it.role == Message.Role.Assistant }
 *         .extract { MemoryRecord.Plain(content = summarize(it.content)) }
 * }
 * ```
 *
 * **Custom implementation as lambda (Java):**
 * ```java
 * MemoryRecordExtractor customExtractor = (messages) ->
 *     messages.stream()
 *         .filter(m -> m.getRole() == Message.Role.Assistant)
 *         .extract(m -> new MemoryRecord.Plain(m.getContent()))
 *         .collect(Collectors.toList());
 * ```
 */
public fun interface MemoryRecordExtractor {
    /**
     * Transforms a list of messages into a list of memory records for storage.
     *
     * @param messages The messages to transform into memory records
     * @return List of memory records to be stored
     */
    public suspend fun extract(messages: List<Message>): List<MemoryRecord>

    /**
     * Companion object with a builder method.
     */
    public companion object {
        /**
         * Returns a builder that lets you choose a default [MemoryRecordExtractor] implementation.
         *
         * Example usage (Java):
         * ```java
         * MemoryRecordExtractor.builder()
         *     .filtering()
         *     .withExtractRoles(new HashSet<>(Arrays.asList(Message.Role.User, Message.Role.Assistant)))
         *     .build()
         * ```
         */
        @kotlin.jvm.JvmStatic
        public fun builder(): MemoryRecordExtractorBuilder = MemoryRecordExtractorBuilder()
    }
}

/**
 * Intermediate builder that lets callers select a [MemoryRecordExtractor] implementation.
 */
public class MemoryRecordExtractorBuilder {
    /**
     * Select the [FilteringMemoryRecordExtractor] implementation.
     * Returns its [FilteringMemoryRecordExtractor.Builder] for further configuration.
     */
    public fun filtering(): FilteringMemoryRecordExtractor.Builder = FilteringMemoryRecordExtractor.Builder()
}
