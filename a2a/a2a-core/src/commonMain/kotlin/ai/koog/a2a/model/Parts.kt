package ai.koog.a2a.model

import ai.koog.a2a.serialization.ByteArrayAsBase64Serializer
import ai.koog.a2a.serialization.PartSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.jvm.JvmStatic

/**
 * Represents a part of a message or artifact.
 */
@Serializable(with = PartSerializer::class)
public sealed interface Part {
    /**
     * Filename for the file
     */
    public val filename: String?

    /**
     * The `media_type` (MIME type) of the part content (e.g., "text/plain", "application/json", "image/png").
     */
    public val mediaType: String?

    /**
     * Optional metadata associated with this part.
     */
    public val metadata: JsonObject?
}

/**
 * Represents a text part.
 *
 * @property text The string content of the text part.
 */
@Serializable
public data class TextPart(
    public val text: String,
    override val filename: String? = null,
    override val mediaType: String? = null,
    override val metadata: JsonObject? = null,
) : Part {
    public companion object {
        @JvmStatic
        public const val KIND: String = "text"
    }
}

/**
 * Represents a file part with content provided as bytes.
 *
 * @property raw The raw bytes of the file content.
 */
@Serializable
public data class FileBytesPart(
    @Serializable(with = ByteArrayAsBase64Serializer::class)
    public val raw: ByteArray,
    override val filename: String? = null,
    override val mediaType: String? = null,
    override val metadata: JsonObject? = null,
) : Part {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileBytesPart) return false

        if (!raw.contentEquals(other.raw)) return false
        if (filename != other.filename) return false
        if (mediaType != other.mediaType) return false
        if (metadata != other.metadata) return false

        return true
    }

    override fun hashCode(): Int {
        var result = raw.contentHashCode()
        result = 31 * result + (filename?.hashCode() ?: 0)
        result = 31 * result + (mediaType?.hashCode() ?: 0)
        result = 31 * result + (metadata?.hashCode() ?: 0)
        return result
    }

    public companion object {
        @JvmStatic
        public const val KIND: String = "raw"
    }
}

/**
 * Represents a file part with content provided as a URL.
 *
 * @property url The URL pointing to the file's content.
 */
@Serializable
public data class FileUrlPart(
    public val url: String,
    override val filename: String? = null,
    override val mediaType: String? = null,
    override val metadata: JsonObject? = null,
) : Part {
    public companion object {
        @JvmStatic
        public const val KIND: String = "url"
    }
}

/**
 * Represents a structured data part (e.g., JSON).
 *
 * @property data The structured data content.
 */
@Serializable
public data class DataPart(
    public val data: JsonObject,
    override val filename: String? = null,
    override val mediaType: String? = null,
    override val metadata: JsonObject? = null,
) : Part {
    public companion object {
        @JvmStatic
        public const val KIND: String = "data"
    }
}
