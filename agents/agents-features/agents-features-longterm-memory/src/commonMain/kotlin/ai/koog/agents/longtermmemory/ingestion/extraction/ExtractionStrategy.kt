package ai.koog.agents.longtermmemory.ingestion.extraction

import ai.koog.prompt.message.Message
import ai.koog.rag.base.TextDocument

/**
 * Extractor of memory records during message ingestion.
 *
 * This is a functional interface (SAM) that defines how a list of messages
 * should be transformed into a list of [TextDocument]s for storage.
 * It provides flexibility in how messages are filtered, transformed, and
 * converted into memory records while maintaining type safety.
 *
 * Pre-built implementations are available for common ingestion patterns:
 * - [FilteringExtractionStrategy] - Filters messages by role
 *
 * ### Usage Examples
 *
 * **Using pre-built extractors (Kotlin):**
 * ```kotlin
 * // Extract User and Assistant messages (default)
 * val extractor = FilteringExtractionStrategy()
 *
 * // Extract only User messages
 * val extractor = FilteringExtractionStrategy(
 *     messageRolesToExtract = setOf(Message.Role.User)
 * )
 * ```
 *
 * **Custom implementation as lambda (Kotlin):**
 * ```kotlin
 * val customExtractor = ExtractionStrategy { messages ->
 *     messages
 *         .filter { it.role == Message.Role.Assistant }
 *         .extract { MemoryRecord(content = summarize(it.content)) }
 * }
 * ```
 *
 * **Custom implementation as lambda (Java):**
 * ```java
 * ExtractionStrategy customExtractor = (messages) ->
 *     messages.stream()
 *         .filter(m -> m.getRole() == Message.Role.Assistant)
 *         .extract(m -> new MemoryRecord(m.getContent()))
 *         .collect(Collectors.toList());
 * ```
 */
public fun interface ExtractionStrategy {
    /**
     * Transforms a list of messages into a list of memory records for storage.
     *
     * @param messages The messages to transform into memory records
     * @return List of memory records to be stored
     */
    public suspend fun extract(messages: List<Message>): List<TextDocument>

    /**
     * Companion object with a builder method.
     */
    public companion object {
        /**
         * Returns a builder that lets you choose a default [ExtractionStrategy] implementation.
         *
         * Example usage (Java):
         * ```java
         * ExtractionStrategy.builder()
         *     .filtering()
         *     .withExtractRoles(new HashSet<>(Arrays.asList(Message.Role.User, Message.Role.Assistant)))
         *     .build()
         * ```
         */
        @kotlin.jvm.JvmStatic
        public fun builder(): ExtractionStrategyBuilder = ExtractionStrategyBuilder()
    }
}

/**
 * Intermediate builder that lets callers select a [ExtractionStrategy] implementation.
 */
public class ExtractionStrategyBuilder {
    /**
     * Select the [FilteringExtractionStrategy] implementation.
     * Returns its [FilteringExtractionStrategy.Builder] for further configuration.
     */
    public fun filtering(): FilteringExtractionStrategy.Builder = FilteringExtractionStrategy.Builder()
}
