package ai.koog.agents.longtermmemory.ingestion

import ai.koog.agents.longtermmemory.feature.FailurePolicy
import ai.koog.agents.longtermmemory.feature.LongTermMemory.Config
import ai.koog.agents.longtermmemory.ingestion.extraction.DocumentExtractor
import ai.koog.agents.longtermmemory.ingestion.extraction.MessagePassingDocumentExtractor
import ai.koog.rag.base.TextDocument
import ai.koog.rag.base.storage.WriteStorage

/**
 * Settings controlling how messages are persisted (ingested) into the memory repository.
 *
 * Ingestion happens once at agent completion: the final accumulated session prompt/history
 * is passed to the configured [documentExtractor] as a single batch.
 *
 * @param storage The ingestion storage where memory records will be persisted.
 * @param documentExtractor The extractor that defines how to transform messages into memory records.
 *   Pre-built ingesters are available:
 *   - [ai.koog.agents.longtermmemory.ingestion.extraction.MessagePassingDocumentExtractor] - Filters messages by role
 *   Custom ingesters can be provided as lambdas via the [ai.koog.agents.longtermmemory.ingestion.extraction.DocumentExtractor] SAM interface.
 * @param enableAutomaticIngestion When `true` (default), ingestion happens automatically on agent
 *   completion. When `false`, the storage is still accessible for manual use inside graph strategy
 *   nodes via [ai.koog.agents.longtermmemory.feature.withLongTermMemory].
 * @param namespace Namespace (table/collection name) for a request
 * @param failurePolicy How to react to failures from [storage] when persisting records.
 *   Defaults to [FailurePolicy.LOG_AND_CONTINUE] so transient ingestion errors do not abort
 *   the agent run. Set to [FailurePolicy.FAIL_FAST] for durable audit/logging use cases
 *   where losing memory records is worse than failing the run.
 */
public data class IngestionSettings(
    val storage: WriteStorage<TextDocument>,
    val documentExtractor: DocumentExtractor = MessagePassingDocumentExtractor(),
    val enableAutomaticIngestion: Boolean = true,
    val namespace: String? = null,
    val failurePolicy: FailurePolicy = FailurePolicy.LOG_AND_CONTINUE,
) {
    /**
     * Companion object for [IngestionSettingsBuilder].
     * */
    public companion object {
        /**
         * Creates a new instance of [IngestionSettingsBuilder], which is a builder
         * for constructing instances of [IngestionSettings].
         *
         * @return A new [IngestionSettingsBuilder] instance. This builder allows
         * configuring ingestion settings such as storage, document extraction,
         * automatic ingestion behavior, namespace, and failure policy.
         */
        public fun builder(): IngestionSettingsBuilder = IngestionSettingsBuilder()
    }
}


/**
 * Builder for [IngestionSettings] used in the [Config.ingestion] DSL block.
 */
public class IngestionSettingsBuilder {
    /**
     * The ingestion storage where memory records will be persisted.
     * Must be set explicitly in the ingestion { } block.
     */
    public var storage: WriteStorage<TextDocument>? = null

    /**
     * The extractor that defines how to transform messages into memory records.
     *
     * Pre-built ingesters are available:
     * - [ai.koog.agents.longtermmemory.ingestion.extraction.MessagePassingDocumentExtractor] - Filters messages by role
     *
     * Example usage:
     * ```kotlin
     * // Use pre-built extractor with parameters
     * documentExtractor = MessagePassingDocumentExtractor(
     *     messageRolesToExtract = setOf(Message.Role.User)
     * )
     *
     * // Or use lambda for custom logic
     * documentExtractor = DocumentExtractor { messages ->
     *     messages.map { TextDocument(content = it.content) }
     * }
     * ```
     */
    public var documentExtractor: DocumentExtractor = MessagePassingDocumentExtractor()

    /**
     * When `true` (default), ingestion happens automatically on agent completion.
     * When `false`, the storage is still accessible for manual use inside graph strategy nodes.
     */
    public var enableAutomaticIngestion: Boolean = true

    /**
     * Namespace (table/collection name) for a request.
     */
    public var namespace: String? = null

    /**
     * How to react to ingestion failures (e.g. storage outage).
     *
     * Defaults to [FailurePolicy.LOG_AND_CONTINUE] so transient ingestion errors do not
     * abort the agent run. Switch to [FailurePolicy.FAIL_FAST] for durable audit/logging
     * use cases where losing memory records is worse than failing the run.
     */
    public var failurePolicy: FailurePolicy = FailurePolicy.LOG_AND_CONTINUE

    /**
     * Fluent setter for [storage].
     */
    public fun withStorage(storage: WriteStorage<TextDocument>): IngestionSettingsBuilder =
        apply { this.storage = storage }

    /**
     * Fluent setter for [documentExtractor].
     */
    public fun withDocumentExtractor(documentExtractor: DocumentExtractor): IngestionSettingsBuilder =
        apply { this.documentExtractor = documentExtractor }

    /**
     * Fluent setter for [enableAutomaticIngestion].
     */
    public fun withEnableAutomaticIngestion(enable: Boolean): IngestionSettingsBuilder =
        apply { this.enableAutomaticIngestion = enable }

    /**
     * Fluent setter for [namespace].
     */
    public fun withNamespace(namespace: String): IngestionSettingsBuilder =
        apply { this.namespace = namespace }

    /**
     * Fluent setter for [failurePolicy].
     */
    public fun withFailurePolicy(failurePolicy: FailurePolicy): IngestionSettingsBuilder =
        apply { this.failurePolicy = failurePolicy }

    /**
     * IngestionSettings builder.
     */
    public fun build(): IngestionSettings {
        val ingestionStorage = requireNotNull(storage) { "storage must be set in ingestion { } block" }
        return IngestionSettings(
            ingestionStorage,
            documentExtractor,
            enableAutomaticIngestion,
            namespace,
            failurePolicy,
        )
    }
}
