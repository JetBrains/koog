package ai.koog.rag.base.files

import kotlinx.io.Sink
import kotlinx.io.Source

/**
 * Provides a broad set of functionalities for interacting with a filesystem or a similar hierarchy of data.
 * This object contains nested interfaces for serialization, reading, writing, and managing file or directory paths.
 */
public object FileSystemProvider {

    /**
     * Interface defining methods for serializing and deserializing file paths and handling related metadata.
     * This is a generic interface that operates with paths of a specified type.
     */
    public interface Serialization<Path> {
        /**
         * Converts the given path to a string representation of the file path.
         *
         * @param path The path to convert into a string.
         * @return A string representation of the supplied path.
         * @deprecated Use toAbsolutePathString instead for generating string representations of absolute paths.
         */
        @Deprecated("Use toAbsolutePathString instead", ReplaceWith("toAbsolutePathString(path)"))
        public fun toPathString(path: Path): String
        /**
         * Converts a given Path object into its absolute path representation as a string.
         *
         * @param path The Path object to be converted into an absolute path string.
         * @return The absolute path string representation of the provided Path object.
         */
        public fun toAbsolutePathString(path: Path): String
        /**
         * Converts a string representation of an absolute file path into a Path object.
         *
         * @param path The absolute path as a string.
         * @return A Path object representing the absolute path.
         */
        public fun fromAbsoluteString(path: String): Path
        /**
         * Converts a relative path string to a `Path` object by resolving it against a base path.
         *
         * @param base The base `Path` against which the relative path will be resolved.
         * @param path The relative path in string form to be resolved.
         * @return A `Path` object representing the resolved path.
         */
        public fun fromRelativeString(base: Path, path: String): Path

        /**
         * Retrieves the name of the file or directory represented by the given path.
         *
         * @param path The path representing a file or directory whose name is to be retrieved.
         * @return The name of the file or directory as a string.
         */
        public suspend fun name(path: Path): String
        /**
         * Retrieves the file extension from the specified path.
         *
         * @param path The file path from which to extract the extension.
         * @return The file extension as a string, or an empty string if the path does not have an extension.
         */
        public suspend fun extension(path: Path): String
    }

    /**
     * Represents an interface for handling file and directory operations within a filesystem context.
     * Extends the `Serialization` interface to support path serialization and deserialization.
     *
     * @param Path The type representing the file or directory path.
     */
    public interface Select<Path> : Serialization<Path> {
        /**
         * Retrieves the metadata associated with the specified file or directory.
         *
         * @param path The path to the file or directory whose metadata is being retrieved.
         * @return The metadata of the specified file or directory as a [ai.koog.agents.memory.providers.FileMetadata] object,
         * or null if the metadata could not be retrieved or the path does not exist.
         */
        public suspend fun metadata(path: Path): FileMetadata?
        /**
         * Lists all the paths under a specified directory or file path.
         *
         * This method retrieves a list of paths that are either contained within a directory
         * or represent the current path, depending on the type of the given `path`.
         *
         * @param path The path for which to list associated paths. If the path is a directory,
         *             it retrieves paths contained within it. If it is a file, it returns the file itself.
         * @return A list of paths under the given directory or file path.
         */
        public suspend fun list(path: Path): List<Path>
        /**
         * Retrieves the parent directory/path of the given path.
         *
         * @param path The path for which the parent directory is to be determined.
         * @return The parent path of the given path, or null if the path has no parent.
         */
        public suspend fun parent(path: Path): Path?
        /**
         * Computes the relative path from a given root path to the specified path.
         *
         * @param root The root directory path from which the relative path is calculated.
         * @param path The target path to calculate the relative path to.
         * @return The relative path as a string, or null if the paths cannot be relativized.
         */
        @Deprecated("Use relativize instead", ReplaceWith("relativize(root, path)"))
        public suspend fun relative(root: Path, path: Path): String? = relativize(root, path)
        /**
         * Relativizes the given path `path` based on the specified `root` path.
         * This function calculates the relative path from the `root` to the `path`.
         *
         * @param root The base path against which the relative path should be computed.
         * @param path The target path to compute its relation to the root.
         * @return A string representing the relative path from the root to the path, or null if the paths cannot be relativized.
         */
        public suspend fun relativize(root: Path, path: Path): String?
        /**
         * Checks if a given path exists in the file system.
         *
         * @param path The path to be checked for existence.
         * @return `true` if the path exists, `false` otherwise.
         */
        public suspend fun exists(path: Path): Boolean
    }

    /**
     * An interface that defines the operations for reading file or content-related data.
     * Extends the [Serialization] interface to provide additional methods for reading byte data,
     * retrieving data sources, and determining the size of given paths.
     *
     * @param Path The type that represents the file or directory path.
     */
    public interface Read<Path> : Serialization<Path> {
        /**
         * Reads the content of the file at the specified path.
         *
         * @param path The path of the file to read.
         * @return The content of the file as a ByteArray.
         */
        public suspend fun read(path: Path): ByteArray
        /**
         * Provides access to a data source represented by the given path.
         *
         * @param path The path to the source from which data should be accessed.
         * @return A Source object associated with the specified path.
         */
        public suspend fun source(path: Path): Source
        /**
         * Retrieves the size of the file or directory at the specified path.
         *
         * @param path The path of the file or directory whose size is to be determined.
         * @return The size of the file or directory in bytes. For directories, this may
         * represent the cumulative size of its contents, depending on the implementation.
         */
        public suspend fun size(path: Path): Long
    }

    /**
     * Defines a read-only interface that combines the functionalities of serialization, file selection,
     * and file reading in a file system or comparable data structure.
     *
     * @param Path Represents the type of the path used to reference files or directories.
     *
     * This interface inherits methods from the following:
     * - `Serialization`: Serialization and deserialization of file paths into string representations.
     * - `Select`: Operations to fetch metadata, list contents, navigate parent directories, and check existence.
     * - `Read`: Functionality to read file content, access file content as a source, and determine file size.
     *
     * Implementers of this interface are expected to provide implementations for all functionality
     * associated with serialization, selection, and reading, ensuring a complete read-only perspective
     * of the file system or equivalent storage mechanisms.
     *
     * This interface is useful in scenarios where modification operations are either undesired or disallowed,
     * offering controlled access to an underlying file or directory structure.
     */
    public interface ReadOnly<Path>: Serialization<Path>, Select<Path>, Read<Path>  {}

    /**
     * Provides functionalities for creating, moving, writing, and deleting files or directories
     * while maintaining a serialization context.
     *
     * This interface is designed to interact with a file system hierarchy in a structured way,
     * allowing operations to manage file content and metadata effectively.
     *
     * @param Path A type representing a file system location.
     */
    public interface Write<Path> : Serialization<Path> {
        /**
         * Creates a new file or directory within a specified parent directory.
         *
         * @param parent The path of the parent directory where the item will be created.
         * @param name The name of the new file or directory to be created.
         * @param type The type of the item to create, either a file or a directory, as defined by [ai.koog.agents.memory.providers.FileMetadata.FileType].
         */
        public suspend fun create(parent: Path, name: String, type: FileMetadata.FileType)
        /**
         * Moves a file or directory from the source path to the target path.
         *
         * @param source The path of the file or directory to be moved.
         * @param target The destination path where the file or directory will be moved.
         */
        public suspend fun move(source: Path, target: Path)
        /**
         * Writes the specified content to the given path.
         *
         * @param path The file system path where the content should be written.
         * @param content The data to be written as a byte array.
         */
        public suspend fun write(path: Path, content: ByteArray)
        /**
         * Creates and returns a Sink for the given path.
         * The Sink can be used to write data to the file or directory represented by the specified path.
         *
         * @param path The path where the sink will be created, representing the file or directory target.
         * @param append A boolean indicating whether to append to the file if it exists.
         *               Defaults to false, meaning the file will be overwritten if it exists.
         * @return A Sink instance for writing to the specified path.
         */
        public suspend fun sink(path: Path, append: Boolean = false): Sink
        /**
         * Deletes a child file or directory with the specified name from the parent directory.
         *
         * @param parent The path to the parent directory from which the file or directory should be deleted.
         * @param name The name of the file or directory to be deleted.
         */
        public suspend fun delete(parent: Path, name: String)
    }

    /**
     * Represents a combined interface for read and write operations on a given pathway or resource.
     *
     * This interface extends both the [ReadOnly] and [Write] interfaces, allowing full control
     * over interacting with resources associated with a specific `Path`. This includes retrieval,
     * modification, and creation operations. Suitable for cases where both read and write capabilities
     * are required, without compromising the ability to manage the underlying resource effectively.
     *
     * The `ReadWrite` interface inherits the contract of the following:
     * - [ReadOnly]: Provides read-only access, including serialization, selection, and data retrieval operations.
     * - [Write]: Enables data creation, modification, and deletion, supporting essential write-related functionality.
     *
     * @param Path The type of the resource pathway or identifier handled by this interface.
     */
    public interface ReadWrite<Path> : ReadOnly<Path>, Write<Path>
}