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
 * - [MessagePassingDocumentExtractor] - Filters messages by role
 *
 * ### Usage Examples
 *
 * **Using pre-built extractors (Kotlin):**
 * ```kotlin
 * // Extract User and Assistant messages (default)
 * val extractor = MessagePassingDocumentExtractor()
 *
 * // Extract only User messages
 * val extractor = MessagePassingDocumentExtractor(
 *     messageRolesToExtract = setOf(Message.Role.User)
 * )
 * ```
 *
 * **Custom implementation as lambda (Kotlin):**
 * ```kotlin
 * val customExtractor = DocumentExtractor { messages ->
 *     messages
 *         .filter { it.role == Message.Role.Assistant }
 *         .map { MemoryRecord(content = summarize(it.content)) }
 * }
 * ```
 *
 * **Custom implementation as lambda (Java):**
 * ```java
 * DocumentExtractor customExtractor = (messages) ->
 *     messages.stream()
 *         .filter(m -> m.getRole() == Message.Role.Assistant)
 *         .map(m -> new MemoryRecord(m.getContent()))
 *         .collect(Collectors.toList());
 * ```
 */
public fun interface DocumentExtractor {
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
         * Returns a builder that lets you choose a default [DocumentExtractor] implementation.
         *
         * Example usage (Java):
         * ```java
         * DocumentExtractor.builder()
         *     .filtering()
         *     .withExtractRoles(new HashSet<>(Arrays.asList(Message.Role.User, Message.Role.Assistant)))
         *     .build()
         * ```
         */
        @kotlin.jvm.JvmStatic
        public fun builder(): DocumentExtractorBuilder = DocumentExtractorBuilder()
    }
}

/**
 * Intermediate builder that lets callers select a [DocumentExtractor] implementation.
 */
public class DocumentExtractorBuilder {
    /**
     * Select the [MessagePassingDocumentExtractor] implementation.
     * Returns its [MessagePassingDocumentExtractor.Builder] for further configuration.
     */
    public fun filtering(): MessagePassingDocumentExtractor.Builder = MessagePassingDocumentExtractor.Builder()
}
