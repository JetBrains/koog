package ai.koog.spring.ai.vectorstore

import ai.koog.rag.base.TextDocument

/**
 * Vector-store document model used by this starter.
 *
 * Metadata values are restricted to primitive types (String, Number, Boolean)
 * to match Spring AI [org.springframework.ai.document.Document] metadata constraints.
 */
public data class DocumentWithMetadata @JvmOverloads constructor(
    override val content: String,
    override val metadata: Map<String, Any> = emptyMap(),
    override val id: String? = null
) : TextDocument {
    init {
        metadata.forEach { (key, value) ->
            require(value is String || value is Boolean || value is Number) {
                "Metadata value for key '$key' must be a primitive type " +
                    "(String, Number, or Boolean), but was ${value::class.qualifiedName}"
            }
        }
    }

    /**
     * Java-friendly builder for [DocumentWithMetadata].
     *
     * Usage from Java:
     * ```java
     * DocumentWithMetadata doc = new DocumentWithMetadata.Builder("Hello world")
     *     .metadata("author", "Alice")
     *     .metadata("year", 2024)
     *     .id("doc-1")
     *     .build();
     * ```
     */
    public class Builder(private val content: String) {
        private val metadata: MutableMap<String, Any> = mutableMapOf()
        private var id: String? = null

        /** Adds a single metadata entry. */
        public fun metadata(key: String, value: Any): Builder = apply { metadata[key] = value }

        /** Replaces all metadata with the given map. */
        public fun metadata(map: Map<String, Any>): Builder = apply {
            metadata.clear()
            metadata.putAll(map)
        }

        /** Sets the document id. */
        public fun id(id: String?): Builder = apply { this.id = id }

        /** Builds the [DocumentWithMetadata] instance. */
        public fun build(): DocumentWithMetadata = DocumentWithMetadata(content, metadata, id)
    }
}
