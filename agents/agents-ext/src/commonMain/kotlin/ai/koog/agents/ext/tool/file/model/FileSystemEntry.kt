package ai.koog.agents.ext.tool.file.model

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
    public val name: String
    public val extension: String?
    public val path: String
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
     * Represents a directory in the filesystem.
     *
     * @property entries child files and directories, or null if not loaded
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
