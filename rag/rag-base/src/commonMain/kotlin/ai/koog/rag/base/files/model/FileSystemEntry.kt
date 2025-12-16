package ai.koog.rag.base.files.model

import ai.koog.rag.base.files.DocumentProvider
import ai.koog.rag.base.files.FileMetadata
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Provides a common interface for files and directories in a filesystem.
 *
 * @property name filename or directory name
 * @property extension file extension without dot, or `null` for directories
 * @property path complete filesystem path
 * @property hidden whether this entry is hidden
 */
@Serializable
public sealed interface FileSystemEntry {
    /**
     * Represents the name as a string value.
     * This variable is typically used to hold textual data corresponding to a name.
     */
    public val name: String

    /**
     * Represents the optional file extension of the file system entry.
     *
     * The extension is the portion of the filename following the last dot ('.'),
     * excluding the dot itself. For example, in the file name "document.txt",
     * the extension would be "txt". If the file does not have an extension or
     * the entry is not a file, this value may be `null`.
     */
    public val extension: String?

    /**
     * Represents a filesystem or resource path.
     *
     * This variable is typically used to store the string representation
     * of an absolute or relative path to a file, directory, or resource.
     */
    public val path: String

    /**
     * Indicates whether the file system entry is hidden.
     *
     * A hidden file or folder is typically not visible in directory listings
     * unless explicitly configured to show hidden items. The visibility may
     * depend on the underlying file system's settings or user preferences.
     */
    public val hidden: Boolean

    /**
     * Visits this entry and its descendants in depth-first order.
     *
     * @param depth maximum depth to traverse; 0 visits only this entry
     * @param visitor function called for each visited entry
     */
    public suspend fun visit(depth: Int, visitor: suspend (FileSystemEntry) -> Unit)

    /**
     * Represents a file in the filesystem.
     *
     * @property size list of [FileSize] measurements for this file
     * @property contentType file content type from [FileMetadata.FileContentType]
     * @property content file content, defaults to [Content.None]
     */
    @Serializable
    public data class File(
        override val name: String,
        override val extension: String?,
        override val path: String,
        override val hidden: Boolean,
        public val size: List<FileSize>,
        @SerialName("content_type") public val contentType: FileMetadata.FileContentType,
        public val content: Content = Content.None,
    ) : FileSystemEntry {
        /**
         * Visits this file by calling [visitor] once.
         *
         * @param depth ignored for files
         * @param visitor function called with this file
         */
        override suspend fun visit(depth: Int, visitor: suspend (FileSystemEntry) -> Unit) {
            visitor(this)
        }

        /**
         * Represents file content as none, full text, or excerpt.
         */
        @Serializable
        public sealed interface Content {
            /**
             * Represents no file content.
             */
            @Serializable
            public data object None : Content

            /**
             * Represents full file content.
             *
             * @property text complete file text
             */
            @Serializable
            public data class Text(val text: String) : Content

            /**
             * Represents multiple separate text selections from a file.
             *
             * Each snippet contains text from a different part of the file, allowing
             * non-contiguous selections.
             *
             * @property snippets text selections with their file positions
             */
            @Serializable
            public data class Excerpt(val snippets: List<Snippet>) : Content {
                /**
                 * Creates an [Excerpt] from multiple [Snippet]s.
                 *
                 * @param snippets the snippets to include
                 */
                public constructor(vararg snippets: Snippet) : this(snippets.toList())

                /**
                 * Represents a text selection with its location in the source file.
                 *
                 * @property text the selected text
                 * @property range position in the file (zero-based, start inclusive, end exclusive)
                 */
                @Serializable
                public data class Snippet(
                    val text: String,
                    val range: DocumentProvider.DocumentRange,
                )
            }
        }
    }

    /**
     * Represents a folder in a filesystem.
     *
     * A folder is a directory that can contain other entries, including files and subfolders.
     * This class implements [FileSystemEntry] and includes metadata and optional child entries.
     *
     * @property name the name of the folder
     * @property path the absolute path to the folder
     * @property hidden whether the folder is hidden
     * @property entries the list of child entries (files and folders), or null if not specified
     */
    @Serializable
    public data class Folder(
        override val name: String,
        override val path: String,
        override val hidden: Boolean,
        val entries: List<FileSystemEntry>? = null,
    ) : FileSystemEntry {
        /** Always null since directories have no file extensions. */
        override val extension: String? = null

        /**
         * Visits this folder and its descendants up to the specified depth.
         *
         * @param depth how deep to traverse (0 = this folder only, negative values treated as 0)
         * @param visitor function called for each visited entry
         */
        override suspend fun visit(depth: Int, visitor: suspend (FileSystemEntry) -> Unit) {
            visitor(this)
            if (depth <= 0) return
            entries?.forEach { it.visit(depth - 1, visitor) }
        }
    }
}
