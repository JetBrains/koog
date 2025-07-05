package ai.koog.rag.base.files

import kotlinx.io.Sink
import kotlinx.io.Source

/**
 * Provides [ReadOnly] and [ReadWrite] interfaces
 * for interacting with a filesystem through file operations and content reading/writing.
 */
public object FileSystemProvider {

    /**
     * Handles serialization and deserialization of file paths.
     */
    @Deprecated("For internal use only.")
    public interface Serialization<Path> {

        /**
         * Converts a [path] to its absolute path string representation.
         *
         * @param path The path to convert.
         * @return Absolute path as a string.
         */
        public fun toAbsolutePathString(path: Path): String

        /**
         * Creates a [Path] object from a path string.
         * If a relative path is provided, it will remain relative.
         *
         * @param path The path string to convert. Can be absolute or relative.
         * @return A path object representing the given path.
         */
        public fun fromAbsoluteString(path: String): Path

        /**
         * Resolves a [path] string against a [base] path.
         *
         * @param base The base path for resolution.
         * @param path The path string to resolve.
         * If this is an absolute path, the [base] path is ignored and the absolute path is returned directly.
         * @return The resolved path object.
         */
        public fun fromRelativeString(base: Path, path: String): Path

        /**
         * Gets the name component of a [path].
         *
         * @param path The path to examine.
         * @return The name of the file or directory.
         */
        public fun name(path: Path): String

        /**
         * Gets the file extension from a [path].
         *
         * @param path The path to examine.
         * @return The file extension or empty string if none exists.
         */
        public fun extension(path: Path): String
    }

    /**
     * Provides operations for examining filesystem structure.
     *
     * @param Path The type representing file paths in the implementation.
     */
    public interface Select<Path> : Serialization<Path> {
        /**
         * Retrieves metadata for a file or directory using a [path].
         *
         * @param path The path to examine.
         * @return [FileMetadata] object or null if the path doesn't exist or isn't a regular file or directory.
         */
        public suspend fun metadata(path: Path): FileMetadata?

        /**
         * Lists contents of a directory using a [directory].
         *
         * @param directory The directory path to list.
         * @return List of paths contained in the directory.
         * @throws IllegalArgumentException if passed argument is not a directory, or it doesn't exist.
         */
        public suspend fun list(directory: Path): List<Path>

        /**
         * Gets the parent path of a given [path].
         * This method works with the path structure and doesn't check if the path actually exists in the filesystem.
         *
         * @param path The path to examine.
         * @return The parent path or null if no parent exists.
         */
        public fun parent(path: Path): Path?

        /**
         * Computes the relative path from a [root] to a target [path].
         * It doesn't check if the paths actually exist in the filesystem.
         *
         * @param root The root path.
         * @param path The target path.
         * @return The relative path as a string, or null if the paths cannot be relativized (e.g., they have no common prefix).
         */
        public fun relativize(root: Path, path: Path): String?

        /**
         * Checks if a [path] exists in the filesystem.
         *
         * @param path The path to check.
         * @return true if the path exists, false otherwise.
         */
        public suspend fun exists(path: Path): Boolean
    }

    /**
     * Provides operations for reading file content.
     *
     * @param Path The type representing file paths in the implementation.
     */
    public interface Read<Path> : Serialization<Path> {
        /**
         * Reads the content of a file at the specified [path].
         *
         * @param path The path to read.
         * @return The file content as a byte array.
         * @throws NoSuchFileException if the path doesn't exist.
         * @throws IllegalArgumentException if the path isn't a regular file.
         */
        public suspend fun read(path: Path): ByteArray

        /**
         * Creates a Source for reading from a file at the specified [path].
         *
         * @param path The path to read from.
         * @return A Source object for reading.
         * @throws IllegalArgumentException if the path isn't a regular file, or it doesn't exist.
         */
        public suspend fun source(path: Path): Source

        /**
         * Gets the size of a file in bytes.
         *
         * @param path The path to examine.
         * @return The file size in bytes.
         * @throws NoSuchFileException if the path doesn't exist.
         * @throws IllegalArgumentException if the path isn't a regular file.
         */
        public suspend fun size(path: Path): Long
    }

    /**
     * Provides a read-only interface that combines the functionalities of [FileSystemProvider.Serialization], [FileSystemProvider.Select],
     * and [FileSystemProvider.Read].
     *
     * It provides operations for path serialization, structure navigation, and content reading
     * in a read-only manner without modifying the filesystem.
     */
    public interface ReadOnly<Path> : Serialization<Path>, Select<Path>, Read<Path> {}

    /**
     * Provides operations for creating, moving, writing, and deleting files or directories.
     *
     * This interface focuses on write operations and complements the read operations
     * provided by other interfaces.
     */
    public interface Write<Path> : Serialization<Path> {
        /**
         * Creates a new file or directory inside [parent] with specified [name] and [type].
         * Parent directories will be created if they don't exist.
         *
         * @param parent The parent directory path.
         * @param name The name of the new file or directory.
         * @param type The type (file or directory) to create.
         * @throws IOException if the name is invalid (e.g., contains reserved characters) or an error occurs during creation.
         * @throws AccessDeniedException if there are not enough permissions to perform operation.
         */
        public suspend fun create(parent: Path, name: String, type: FileMetadata.FileType)

        /**
         * Moves a file or directory from [source] to [target].
         * If the source is a directory, all its contents are moved recursively.
         * Parent directories of the target will be created if they don't exist.
         *
         * @param source The source path to move from.
         * @param target The target path to move to.
         * @throws IOException if the source path doesn't exist, isn't a file or directory, or any IO error occurs.
         */
        public suspend fun move(source: Path, target: Path)

        /**
         * Writes content to a file.
         * If the file doesn't exist, it will be created.
         * Parent directories will be created if they don't exist.
         *
         * @param path The path to write to.
         * @param content The content to write as a byte array.
         * @throws IOException if an IO error occurs during writing.
         * @throws AccessDeniedException if there are not enough permissions to perform operation.
         */
        public suspend fun write(path: Path, content: ByteArray)

        /**
         * Creates a Sink for writing to a file.
         * If the file doesn't exist, it will be created.
         * If the parent directories don't exist, they will be created.
         *
         * @param path The path where Sink will be created.
         * @param append Append to existing content (true) or overwrite (false). Default is false (overwrite).
         * @return A Sink object for writing.
         */
        public suspend fun sink(path: Path, append: Boolean = false): Sink

        /**
         * Deletes a file or directory from [parent] using [name].
         * If the item is a directory, it will be deleted recursively with all its contents.
         * This operation is idempotent - it doesn't throw any errors if a file/directory doesn't exist.
         *
         * @param parent The parent directory containing the item to delete.
         * @param name The name of the item to delete.
         */
        public suspend fun delete(parent: Path, name: String)
    }

    /**
     * Provides a read-write interface that combines the functionalities of [FileSystemProvider.ReadOnly] and [FileSystemProvider.Write] for full filesystem access.
     *
     * This is the most comprehensive interface, offering complete filesystem operations
     * including reading, writing, and path manipulation.
     */
    public interface ReadWrite<Path> : ReadOnly<Path>, Write<Path>
}