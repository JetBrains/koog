package ai.koog.rag.base.files

import kotlinx.io.Sink
import kotlinx.io.Source

/**
 * Provides [ReadOnly] and [ReadWrite] interfaces
 * for interacting with a filesystem through file operations and content reading/writing.
 */
public object FileSystemProvider {
    /**
     * Provides operations for path serialization, structure navigation, and content reading
     * in a read-only manner without modifying the filesystem.
     */
    public interface ReadOnly<Path> {
        /**
         * Converts a [path] to its absolute path string representation.
         * This method works with the path structure
         * and doesn't check if the path actually exists in the filesystem.
         *
         * @param path The path to convert.
         * @return Absolute path as a string.
         */
        public fun toAbsolutePathString(path: Path): String

        /**
         * Creates a [Path] object from an absolute path string.
         * This method works with the path structure
         * and doesn't check if the path actually exists in the filesystem.
         *
         * @param path The absolute path string to convert.
         * @return A path object representing the absolute path.
         * @throws IllegalArgumentException if [path] is not absolute.
         */
        public fun fromAbsoluteString(path: String): Path

        /**
         * Resolves a [path] string against a [base] path.
         * This method works with the path structure
         * and doesn't check if the path actually exists in the filesystem.
         *
         * @param base The base path for resolution.
         * @param path The path string to resolve.
         * @return The resolved path object.
         * @throws IllegalArgumentException if [path] is an absolute path.
         */
        public fun fromRelativeString(base: Path, path: String): Path

        /**
         * Gets the name component of a [path].
         * This method works with the path structure
         * and doesn't check if the path actually exists in the filesystem.
         *
         * @param path The path to examine.
         * @return The name of the file or directory, or an empty string if the path has no name component.
         */
        public fun name(path: Path): String

        /**
         * Gets the extension of a [path].
         * This method works with the path structure
         * and doesn't check if the path actually exists in the filesystem.
         *
         * @param path The path to examine.
         * @return The extension of [path] or empty string if [path] doesn't have an extension.
         */
        public fun extension(path: Path): String

        /**
         * Retrieves metadata for a file or directory using a [path].
         *
         * @param path The path to examine.
         * @return [FileMetadata] object or null if the path doesn't exist or isn't a regular file or directory.
         * @throws IOException if an I/O error occurs while retrieving metadata.
         */
        public suspend fun metadata(path: Path): FileMetadata?

        /**
         * Detects the type of content stored in a file using a [path].
         *
         * @param path The path to a file whose content type is to be detected.
         * @return [FileMetadata.FileContentType.Text] for text files, [FileMetadata.FileContentType.Binary] for binary files.
         * @throws IllegalArgumentException if the path doesn't exist or isn't a regular file.
         * @throws IOException if an I/O error occurs while detecting the file content type.
         */
        public suspend fun getFileContentType(path: Path): FileMetadata.FileContentType

        /**
         * Lists contents of a [directory].
         * Children are sorted by name.
         * The listing is not recursive.
         *
         * @param directory The directory path to list.
         * @return List of paths contained in the directory, sorted by name.
         *         Returns an empty list if the directory is empty.
         * @throws IllegalArgumentException if [directory] is not a directory or doesn't exist.
         * @throws IOException if an I/O error occurs during listing.
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
         * @throws IOException if an I/O error occurs while checking [path] existence.
         */
        public suspend fun exists(path: Path): Boolean

        /**
         * Reads the content of a file at the specified [path].
         *
         * @param path The path to read.
         * @return The file content as a byte array.
         * @throws IllegalArgumentException if the path doesn't exist or isn't a regular file.
         * @throws IOException if an I/O error occurs during reading.
         */
        public suspend fun read(path: Path): ByteArray

        /**
         * Creates a [Source] for reading from a file at the specified [path].
         * The returned Source is buffered.
         *
         * @param path The path to read from.
         * @return A buffered Source object for reading.
         * @throws IllegalArgumentException if [path] doesn't exist or isn't a regular file.
         * @throws IOException if an I/O error occurs during source creation.
         */
        public suspend fun source(path: Path): Source

        /**
         * Gets the size of a file in bytes.
         *
         * @param path The path to examine.
         * @return The file size in bytes.
         * @throws IllegalArgumentException if the path doesn't exist or isn't a regular file.
         * @throws IOException if an I/O error occurs while determining the file size.
         */
        public suspend fun size(path: Path): Long
    }

    /**
     * This is the most comprehensive interface, offering complete filesystem operations
     * including reading, writing, and path manipulation.
     */
    public interface ReadWrite<Path> : ReadOnly<Path> {
        /**
         * Creates a new file or directory inside [parent] with specified [name] and [type].
         * Parent directories will be created if they don't exist.
         *
         * @param parent The parent directory path.
         * @param name The name of the new file or directory.
         * @param type The type (file or directory) to create.
         * @throws IOException or its inheritor if a file or directory with [name] already exists in [parent],
         *   or [name] is invalid (e.g., contains reserved characters), or if any other I/O error occurs.
         */
        public suspend fun create(parent: Path, name: String, type: FileMetadata.FileType)

        /**
         * Moves a file or directory from [source] to [target].
         * If the source is a directory, all its contents are moved recursively.
         * Parent directories of the target will be created if they don't exist.
         *
         * @param source The source path to move from.
         * @param target The target path to move to.
         * @throws IOException or its inheritor if the [source] doesn't exist, isn't a file or directory,
         *   [target] already exists, or any I/O error occurs.
         */
        public suspend fun move(source: Path, target: Path)

        /**
         * Writes content to a file.
         * If the file doesn't exist, it will be created.
         * If the file exists, its content will be overwritten.
         * Parent directories will be created if they don't exist.
         *
         * @param path The path to write to.
         * @param content The content to write as a byte array.
         * @throws IOException if the path is a directory or any other I/O error occurs during writing.
         */
        public suspend fun write(path: Path, content: ByteArray)

        /**
         * Creates a [Sink] for writing to a file.
         * If the file doesn't exist, it will be created.
         * If the parent directories don't exist, they will be created.
         * The returned [Sink] is buffered.
         *
         * @param path The path where Sink will be created.
         * @param append Append to existing content (true) or overwrite (false). Default is false (overwrite).
         * @return A buffered Sink object for writing.
         * @throws IOException if the path is a directory or any other I/O error occurs during sink creation.
         */
        public suspend fun sink(path: Path, append: Boolean = false): Sink

        /**
         * Deletes a file or directory from [parent] using [name].
         * If the item is a directory, it will be deleted recursively with all its contents.
         *
         * @param parent The parent directory containing the item to delete.
         * @param name The name of the item to delete.
         * @throws IOException or its inheritor if the file or directory doesn't exist or can't be deleted for any other reason.
         */
        public suspend fun delete(parent: Path, name: String)
    }
}
