package ai.koog.spring.ai.vectorstore

/**
 * Vector-store document model used by this starter.
 *
 * Metadata values are restricted to primitive types (String, Number, Boolean)
 * to match Spring AI [org.springframework.ai.document.Document] metadata constraints.
 */
public data class DocumentWithMetadata @JvmOverloads constructor(
    public val content: String,
    public val metadata: Map<String, Any> = emptyMap(),
    public val id: String? = null
) {
    init {
        metadata.forEach { (key, value) ->
            require(value is String || value is Boolean || value is Number) {
                "Metadata value for key '$key' must be a primitive type " +
                    "(String, Number, or Boolean), but was ${value::class.qualifiedName}"
            }
        }
    }
}
