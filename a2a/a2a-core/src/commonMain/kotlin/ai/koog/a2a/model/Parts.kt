package ai.koog.a2a.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Represents a part of a message or artifact.
 */
@Serializable(with = PartSerializer::class)
public sealed interface Part {
    /**
     * The type of the part, used as discriminator.
     */
    public val kind: String

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
    override val metadata: JsonObject? = null,
) : Part {
    @EncodeDefault
    override val kind: String = "text"
}

/**
 * Represents a file part. The file content can be provided either directly as bytes or as a URI.
 *
 * @property file The file content.
 */
@Serializable
public data class FilePart(
    public val file: File,
    override val metadata: JsonObject? = null,
) : Part {
    @EncodeDefault
    override val kind: String = "file"
}

/**
 * Represents a file within a part.
 */
@Serializable(with = FileSerializer::class)
public sealed interface File {
    /**
     * An optional name for the file (e.g., "document.pdf").
     */
    public val name: String?

    /**
     * An optional MIME type of the file (e.g., "application/pdf").
     */
    public val mimeType: String?
}

/**
 * Represents a file with its content provided directly as a base64-encoded string.
 *
 * @property bytes The base64-encoded content of the file.
 */
@Serializable
public data class FileWithBytes(
    public val bytes: String,
    override val name: String? = null,
    override val mimeType: String? = null,
) : File

/**
 * Represents a file with its content located at a specific URI.
 *
 * @property uri A URL pointing to the file's content.
 */
@Serializable
public data class FileWithUri(
    public val uri: String,
    override val name: String? = null,
    override val mimeType: String? = null,
) : File

/**
 * Represents a structured data part (e.g., JSON).
 *
 * @property data The structured data content.
 */
@Serializable
public data class DataPart(
    public val data: JsonObject,
    override val metadata: JsonObject? = null,
) : Part {
    @EncodeDefault
    override val kind: String = "data"
}
