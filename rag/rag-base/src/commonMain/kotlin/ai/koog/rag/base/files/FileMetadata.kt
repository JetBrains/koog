package ai.koog.rag.base.files

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents metadata associated with a file or directory.
 *
 * @property type The type of the file, indicating whether it is a file or a directory.
 * @property hidden A flag indicating whether the file or directory is hidden.
 * @property content The type of content stored in the file.
 */
@Serializable
public data class FileMetadata(
    @SerialName("content_type")
    val type: FileType,
    val hidden: Boolean,
    val content: FileContent,
) {
    /**
     * Represents the type of a file in the context of file metadata.
     */
    public enum class FileType {
        /**
         * Represents a file in the system.
         * Used to denote a single file entity within a hierarchical filesystem structure.
         * This enumeration value is part of the `FileType` enum which distinguishes
         * between files and directories.
         */
        File,
        /**
         * Represents a directory in the file system.
         *
         * The `Directory` class is used to model a directory and its behavior
         * within a file system. The class distinguishes between different file types,
         * with `FileType.Directory` being explicitly associated with instances of this class.
         *
         * This abstraction is useful for operations that involve hierarchical file structures,
         * such as listing contents, locating parent directories, or performing file system navigation.
         *
         * Features:
         * - Encapsulates information and behavior specific to directories.
         * - Used in conjunction with other classes (e.g., `FileSystemProvider`) for file system management.
         *
         * Considerations:
         * - Instances of `Directory` are not directly concerned with file contents
         *   but with structure and navigation within the file system.
         * - The class may be utilized for tasks like directory traversal,
         *   metadata analysis, or adopting domain-specific implementations.
         */
        Directory
    }

    /**
     * Enum representing the nature of a file's content.
     *
     * This can be used to classify files based on their content type or determine
     * how to handle specific file operations and interactions.
     *
     * @property display A string representation of the content type.
     */
    public enum class FileContent(public val display: String) {
        /**
         * Represents textual file content.
         * Associated with the string display value "text".
         */
        Text("text"),
        /**
         * Represents the binary file content type within the FileContent enum.
         *
         * This value is used to denote that the content of a file is binary data, as opposed to plain text or other types.
         * It provides a display string ("binary") to identify this content type.
         */
        Binary("binary"),
        /**
         * Represents a file content type that is not applicable or relevant.
         * Typically used in scenarios where the file content type does not
         * conform to valid or expected classifications.
         *
         * This enumeration is part of the `FileContent` enum.
         */
        Inapplicable("inapplicable");
    }
}