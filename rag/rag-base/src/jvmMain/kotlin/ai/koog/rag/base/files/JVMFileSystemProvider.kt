package ai.koog.rag.base.files

import ai.koog.rag.base.files.FileMetadata.FileContent
import ai.koog.rag.base.files.FileMetadata.FileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.use

/**
 * Provides access to the JVM-specific file system functionality. This object includes implementations
 * for operations like serialization of `Path` objects, file selection, read, write, and combined read-write operations.
 */
public object JVMFileSystemProvider {
    /**
     * Provides functionality for serializing and deserializing file paths and handling metadata related to file paths.
     * Implements the `FileSystemProvider.Serialization` interface for `Path` type.
     */
    public object Serialization : FileSystemProvider.Serialization<Path> {
        /**
         * Converts the given [Path] to a normalized string representation.
         * This function is deprecated and should be replaced by the `toAbsolutePathString` method.
         *
         * @param path the [Path] to be converted to a string
         * @return the string representation of the normalized [Path]
         */
        @Deprecated("Use toAbsolutePathString instead", replaceWith = ReplaceWith("toAbsolutePathString(path)"))
        override fun toPathString(path: Path): String = path.normalize().pathString
        /**
         * Converts the given [path] to its absolute path representation as a string.
         * The path is normalized before being converted.
         *
         * @param path the relative or absolute path to be converted to an absolute string representation
         * @return the absolute path string representation of the given [path]
         */
        override fun toAbsolutePathString(path: Path): String = path.normalize().absolutePathString()
        /**
         * Converts the given absolute file path string to a normalized Path object.
         *
         * @param path The absolute file path as a string.
         * @return The normalized Path representation of the given string.
         */
        override fun fromAbsoluteString(path: String): Path = Path.of(toSystemDependentName(path)).normalize()
        /**
         * Converts a relative string representation of a path into a normalized Path object
         * based on the provided base Path.
         *
         * @param base The base path against which the relative path will be resolved.
         * @param path The relative path as a string to be resolved.
         * @return A normalized Path object representing the resolved path.
         */
        override fun fromRelativeString(base: Path, path: String): Path = base.resolve(path).normalize()

        /**
         * Retrieves the name of the given file path.
         *
         * @param path the file path from which to extract the name
         * @return the name of the file or directory represented by the provided path
         */
        override suspend fun name(path: Path): String = path.name
        /**
         * Retrieves the file extension of the specified path.
         *
         * @param path the path from which to extract the file extension.
         * @return the file extension as a string.
         */
        override suspend fun extension(path: Path): String = path.extension

        /**
         * Converts a given file path to a system-dependent format by replacing universal separators
         * with the appropriate separator for the current file system.
         *
         * @param path The file path as a string, which may contain universal separators (`/` or `\`).
         * @return The file path string adapted to use the system-dependent file separator.
         */
        private fun toSystemDependentName(path: String): String {
            val separator = FileSystems.getDefault().separator
            val adjustedPath = path.replace("/", separator).replace("\\", separator)
            // remove the leading slash for windows, it breaks further parsing
            if (separator == "\\" && adjustedPath.contains(':')) {
                return adjustedPath.trimStart('\\')
            }
            return adjustedPath
        }
    }

    /**
     * Object implementing file system operations and serialization for file paths.
     * Provides functionality for retrieving metadata, listing directory contents,
     * determining parent paths, checking the existence of paths, and computing relative paths.
     * Delegates serialization-related functionality to the `Serialization` interface.
     */
    public object Select : FileSystemProvider.Select<Path>, FileSystemProvider.Serialization<Path> by Serialization {
        /**
         * Retrieves metadata for a given file or directory path.
         *
         * The method determines whether the provided path represents a regular file or directory,
         * and constructs the metadata accordingly. If the path does not represent a regular file
         * or directory, it returns null.
         *
         * @param path The path for which the metadata is to be retrieved.
         * @return A [FileMetadata] instance containing information about the file or directory,
         * or null if the path does not represent a valid file or directory.
         */
        override suspend fun metadata(path: Path): FileMetadata? {
            return if (path.isRegularFile()) {
                FileMetadata(FileType.File, path.isHidden(), path.contentType())
            } else if (path.isDirectory()) {
                FileMetadata(FileType.Directory, path.isHidden(), path.contentType())
            } else {
                null
            }
        }

        /**
         * Retrieves a sorted list of paths within the specified directory.
         *
         * @param path The directory path whose contents are to be listed.
         * @return A list of paths within the specified directory, sorted by name.
         *         Returns an empty list if an error occurs or the directory is empty.
         */
        override suspend fun list(path: Path): List<Path> = runCatching { Files.list(path).use {
            it.sorted { a, b -> a.name.compareTo(b.name) }.toList()
        }}.getOrElse { emptyList() }

        /**
         * Retrieves the parent directory of the specified path, if it exists.
         *
         * @param path the path for which to retrieve the parent directory
         * @return the parent path if it exists, or null if the path does not have a parent
         */
        override suspend fun parent(path: Path): Path? = path.parent

        /**
         * Computes the relative path from the specified root to the given path.
         *
         * @param root The root path to which the relative computation is performed.
         * @param path The path for which the relative computation is performed.
         * @return The relative path as a string, or null if the relative computation is not possible.
         */
        @Deprecated("Use relativize instead", replaceWith = ReplaceWith("relativize(root, path)"))
        override suspend fun relative(root: Path, path: Path): String? =
            path.relativeToOrNull(root)?.normalize()?.pathString

        /**
         * Computes the relative path from the given root to the specified path.
         *
         * @param root the root path to which the provided path will be relativized.
         * @param path the path for which the relative path needs to be determined.
         * @return the relative path from the root to the given path as a normalized string, or null if the paths have no common prefix.
         */
        override suspend fun relativize(root: Path, path: Path): String? {
            return path.relativeToOrNull(root)?.normalize()?.pathString
        }

        /**
         * Checks if the specified file or directory exists at the given path.
         *
         * @param path The path to the file or directory to be checked.
         * @return `true` if the file or directory exists, `false` otherwise.
         */
        override suspend fun exists(path: Path): Boolean = path.exists()
    }

    /**
     * Provides utility methods for reading file data and retrieving file-related metadata.
     * Implements the `FileSystemProvider.Read` and delegates file serialization operations to the `Serialization` interface.
     */
    public object Read : FileSystemProvider.Read<Path>, FileSystemProvider.Serialization<Path> by Serialization {
        /**
         * Reads the contents of the file located at the specified path.
         *
         * @param path the path of the file to read, which must be a regular file and must exist.
         * @return a ByteArray containing the contents of the file.
         * @throws IllegalArgumentException if the specified path is not a regular file or does not exist.
         */
        override suspend fun read(path: Path): ByteArray {
            require(path.isRegularFile()) { "Path must be a regular file" }
            require(path.exists()) { "Path must exist" }

            return withContext(Dispatchers.IO) { path.readBytes() }
        }
        /**
         * Opens a source to read from the specified file path.
         *
         * @param path The file path from which the source will be opened.
         * @return A buffered source for reading from the file.
         */
        override suspend fun source(path: Path): Source = withContext(Dispatchers.IO) { SystemFileSystem.source(path = kotlinx.io.files.Path(path.pathString)).buffered() }
        /**
         * Returns the size of the regular file at the specified path.
         *
         * @param path the path to the file whose size is to be obtained.
         * Must be a regular file and exist.
         * @return the size of the file in bytes.
         * @throws IllegalArgumentException if the provided path is not a regular file.
         * @throws IllegalArgumentException if the provided path does not exist.
         */
        override suspend fun size(path: Path): Long {
            require(path.isRegularFile()) { "Path must be a regular file" }
            require(path.exists()) { "Path must exist" }
            return withContext(Dispatchers.IO) { path.fileSize() }
        }
    }

    /**
     * A read-only object implementing file system provider interfaces for handling file
     * serialization, selection, and reading functionalities.
     *
     * This object combines the functionalities of `FileSystemProvider.ReadOnly`,
     * `FileSystemProvider.Select`, and `FileSystemProvider.Read` using delegation.
     * It provides operations for path serialization, structure navigation, and
     * content reading in a read-only manner.
     */
    public object ReadOnly: FileSystemProvider.ReadOnly<Path>,
        FileSystemProvider.Select<Path> by Select,
        FileSystemProvider.Read<Path> by Read {

        /**
         * Converts the specified [path] to its string representation.
         *
         * @param path the file system path to convert to a string.
         * @return the string representation of the specified path.
         * @deprecated Use toAbsolutePathString instead.
         */
        @Deprecated("Use toAbsolutePathString instead", replaceWith = ReplaceWith("toAbsolutePathString(path)"))
        override fun toPathString(path: Path): String = Serialization.toPathString(path)
        /**
         * Converts the given Path to its absolute path string representation.
         *
         * @param path the path to be converted to an absolute path string
         * @return the absolute path string representation of the given path
         */
        override fun toAbsolutePathString(path: Path): String = Serialization.toAbsolutePathString(path)

        /**
         * Converts an absolute string representation of a file path into a normalized Path object.
         *
         * @param path The absolute string representation of the file path to convert.
         * @return A normalized Path object corresponding to the given absolute string.
         */
        override fun fromAbsoluteString(path: String): Path = Serialization.fromAbsoluteString(path)
        /**
         * Resolves a given relative path string against a base path and returns the normalized path.
         *
         * @param base The base path to resolve the relative string against.
         * @param path The relative path string to be resolved.
         * @return The normalized absolute path obtained by resolving the relative path string against the base path.
         */
        override fun fromRelativeString(base: Path, path: String): Path = Serialization.fromRelativeString(base, path)

        /**
         * Retrieves the name of the given path.
         *
         * @param path the Path object from which the name will be extracted.
         * @return the name of the provided path as a String.
         */
        override suspend fun name(path: Path): String = Serialization.name(path)
        /**
         * Retrieves the file extension from the specified path using the serialization logic.
         *
         * @param path The path from which to extract the file extension.
         * @return The file extension as a string.
         */
        override suspend fun extension(path: Path): String = Serialization.extension(path)

    }

    /**
     * Provides a combined object interface for performing both read and write file system operations.
     *
     * `ReadWrite` implements the `FileSystemProvider.ReadWrite` interface, extending both
     * read-only and write capabilities. By delegating to `ReadOnly` and `Write` objects, it provides
     * comprehensive file system operations including reading, writing, serialization, and path manipulation.
     */
    public object ReadWrite: FileSystemProvider.ReadWrite<Path>,
        FileSystemProvider.ReadOnly<Path> by ReadOnly,
        FileSystemProvider.Write<Path> by Write {

        /**
         * Converts the given [path] to its string representation.
         * The method is deprecated and it is recommended to use `toAbsolutePathString` instead.
         *
         * @param path the path to be converted to a string.
         * @return the string representation of the provided path.
         */
        @Deprecated("Use toAbsolutePathString instead", replaceWith = ReplaceWith("toAbsolutePathString(path)"))
        override fun toPathString(path: Path): String = Serialization.toPathString(path)
        /**
         * Converts the specified [path] to its absolute path represented as a string.
         *
         * @param path the path to be converted into an absolute path string
         * @return the absolute path as a string
         */
        override fun toAbsolutePathString(path: Path): String = Serialization.toAbsolutePathString(path)
        /**
         * Converts an absolute string path into a normalized Path object suitable for the current file system.
         *
         * @param path The absolute string path to be converted.
         * @return A Path object representing the input absolute string, normalized for the current file system.
         */
        override fun fromAbsoluteString(path: String): Path = Serialization.fromAbsoluteString(path)
        /**
         * Resolves a relative path string against a given base path and normalizes the resulting path.
         *
         * @param base The base path against which the relative path string will be resolved.
         * @param path The relative path string to be resolved.
         * @return The resolved and normalized path as a `Path` object.
         */
        override fun fromRelativeString(base: Path, path: String): Path = Serialization.fromRelativeString(base, path)
        /**
         * Retrieves the name of the file or directory represented by the specified path.
         *
         * @param path the path from which the name should be extracted
         * @return the name of the file or directory as a string
         */
        override suspend fun name(path: Path): String = Serialization.name(path)
        /**
         * Gets the extension of the file represented by the given path.
         *
         * @param path The path of the file whose extension is to be determined.
         * @return The file extension as a string.
         */
        override suspend fun extension(path: Path): String = Serialization.extension(path)
    }

    /**
     * Singleton object that provides implementations for creating, moving, writing, and deleting files or directories,
     * while adhering to a serialization context. It also ensures compatibility with specific operating system constraints
     * such as Windows reserved names.
     * Implements the `FileSystemProvider.Write` interface with `Path` as the path type and delegates serialization functionalities
     * to a `Serialization` implementation.
     */
    public object Write : FileSystemProvider.Write<Path>, FileSystemProvider.Serialization<Path> by Serialization {

        /**
         * Contains a set of reserved file names in Windows operating systems.
         * These names are restricted for use as they are reserved by the system for
         * specific functions, devices, or legacy purposes. Attempting to create, read,
         * or write files with these names in a Windows environment may result in errors.
         *
         * The set includes names such as:
         * - "CON", "PRN", "AUX", "NUL"
         * - Device-specific names like "COM1" to "COM9" and "LPT1" to "LPT9".
         *
         * This variable is typically used to validate file or directory names
         * to ensure compatibility with Windows operating systems.
         */
        private val WINDOWS_RESERVED_NAMES = setOf(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
        )

        /**
         * Creates a new file or directory at the specified location.
         *
         * @param parent The parent directory where the file or directory will be created.
         * @param name The name of the file or directory to be created. Reserved names on Windows platforms are not allowed.
         * @param type The type of file system entity to create, either a file or a directory, represented by the [FileType] enum.
         * @throws IOException If the name is invalid or an error occurs during creation.
         */
        override suspend fun create(parent: Path, name: String, type: FileType) {
            withContext(Dispatchers.IO) {
                if (name in WINDOWS_RESERVED_NAMES && System.getProperty("os.name").lowercase().contains("win")) {
                    throw IOException("Invalid file name: $name")
                }

                val file = parent.resolve(name)

                file.createParentDirectories()

                when (type) {
                    FileType.File -> file.createFile()
                    FileType.Directory -> file.createDirectory()
                }
            }
        }

        /**
         * Writes the provided content to the specified path. Ensures that any necessary parent directories
         * for the path are created before writing the content.
         *
         * @param path The path where the content will be written.
         * @param content The byte array content to be written to the specified path.
         */
        override suspend fun write(path: Path, content: ByteArray) {
            path.createParentDirectories()
            withContext(Dispatchers.IO) { path.writeBytes(content) }
        }

        /**
         * Creates and returns a Sink for the given file path, allowing data to be written to the file.
         * It ensures that parent directories of the file path are created if they do not already exist.
         * The operation is performed in the IO context.
         *
         * @param path The file path where the sink is to be created.
         * @param append A boolean value indicating whether to append data to the file
         *               if it already exists (true) or overwrite the file (false).
         * @return A buffered Sink for the specified path, ready for writing.
         */
        override suspend fun sink(path: Path, append: Boolean): Sink {
            return withContext(Dispatchers.IO) {
                path.createParentDirectories()
                SystemFileSystem.sink(path = kotlinx.io.files.Path(path.pathString), append = append).buffered()
            }
        }

        /**
         * Moves a file or directory from the source path to the target path.
         * If the source is a directory, all its contents are moved recursively.
         * If the source is a file, it is moved directly to the target.
         * Ensures operations are performed using IO dispatchers.
         *
         * @param source The source path of the file or directory to be moved.
         * @param target The target path where the file or directory should be moved.
         * @throws IOException If the source path is neither a file nor a directory, or if any IO error occurs.
         */
        override suspend fun move(source: Path, target: Path) {
            withContext(Dispatchers.IO) {
                if (source.isDirectory()) {
                    target.createDirectories()
                    Files.list(source).use { stream ->
                        stream.forEach { child ->
                            val targetChild = target.resolve(child.name)
                            child.moveTo(targetChild)
                        }
                    }
                    source.deleteExisting()
                } else if (source.isRegularFile()) {
                    source.moveTo(target)
                } else {
                    throw IOException("Source path is neither a file nor a directory: $source")
                }
            }
        }

        /**
         * Deletes a file or directory specified by the given parent path and name.
         * The deletion is performed in an IO-optimized context. If the target is a directory, it will be deleted recursively.
         *
         * @param parent The parent path in which the file or directory resides.
         * @param name The name of the file or directory to be deleted.
         */
        @OptIn(ExperimentalPathApi::class)
        override suspend fun delete(parent: Path, name: String) {
            withContext(Dispatchers.IO) {
                val path = parent.resolve(name)
                if (path.isDirectory()) {
                    path.deleteRecursively()
                } else {
                    path.deleteExisting()
                }
            }
        }

    }

    /**
     * Determines the type of content for a file represented by the Path.
     *
     * This function evaluates the file at the given Path to classify its content type as
     * either textual, binary, or inapplicable. It checks if the file's head data can be
     * classified as text using specific character sets, identifies binary content for regular files,
     * and returns inapplicable for all other cases.
     *
     * @return the file content type as one of the [FileContent] values: [FileContent.Text],
     * [FileContent.Binary], or [FileContent.Inapplicable].
     */
    private fun Path.contentType(): FileContent = when {
        isFileHeadTextBased() -> FileContent.Text
        isRegularFile() -> FileContent.Binary
        else -> FileContent.Inapplicable
    }

    /**
     * Determines if the beginning of a file's content is text-based, as opposed to binary.
     * This method reads a specified amount of data from the start of the file,
     * attempts decoding with a list of provided character sets, and checks if any succeed.
     *
     * @param headMaxSize The maximum number of bytes to read from the start of the file. Defaults to 1024 bytes.
     * @param charsetsToTry A list of character sets to attempt decoding the file's content. Defaults to a list containing UTF-8.
     * @return True if the file's head data is successfully decoded with one of the given character sets, otherwise false.
     */
    private fun Path.isFileHeadTextBased(
        headMaxSize: Int = 1024,
        charsetsToTry: List<Charset> = listOf(
            Charsets.UTF_8,
        )
    ): Boolean {
        return runCatching {
            val headData = inputStream().use { stream ->
                val buffer = ByteArray(headMaxSize)
                stream.read(buffer, 0, headMaxSize).let { ByteBuffer.wrap(buffer.copyOf(it)) }
            }
            charsetsToTry.any { runCatching { it.newDecoder().decode(headData) }.isSuccess }
        }.getOrElse { false }
    }
}
