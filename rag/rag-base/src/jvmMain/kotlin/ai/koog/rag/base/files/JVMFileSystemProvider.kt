package ai.koog.rag.base.files

import ai.koog.rag.base.files.FileMetadata.FileContentType
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
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.isHidden
import kotlin.io.path.isRegularFile
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.notExists
import kotlin.io.path.pathString
import kotlin.io.path.readBytes
import kotlin.io.path.relativeToOrNull
import kotlin.io.path.writeBytes
import kotlin.use

/**
 * Provides access to the JVM-specific file system functionality. This object includes implementations
 * for operations like serialization of `Path` objects, file selection, read, write, and combined read-write operations.
 */
public object JVMFileSystemProvider {
    /**
     * Provides operations for path serialization, structure navigation, and content reading using [Path] objects
     * in a read-only manner without modifying the filesystem.
     */
    public object ReadOnly : FileSystemProvider.ReadOnly<Path> {

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
         * @throws IllegalArgumentException if the resolved path is not absolute.
         */
        override fun fromAbsoluteString(path: String): Path {
            val resolvedPath = Path.of(toSystemDependentName(path)).normalize()
            require(resolvedPath.isAbsolute) { "Resolved path must be absolute" }
            return resolvedPath
        }

        /**
         * Converts a relative string representation of a path into a normalized Path object
         * based on the provided base Path.
         *
         * @param base The base path against which the relative path will be resolved.
         * @param path The relative path as a string to be resolved.
         * @return A normalized Path object representing the resolved path.
         * @throws IllegalArgumentException if [path] is absolute.
         */
        override fun fromRelativeString(base: Path, path: String): Path {
            val resolvedPath = Path.of(path)
            require(!resolvedPath.isAbsolute) { "Path must be relative, but was absolute: $path" }
            return base.resolve(path).normalize()
        }

        /**
         * Retrieves the name of the given file path.
         *
         * @param path the file path from which to extract the name
         * @return the name of the file or directory represented by the provided path
         */
        override fun name(path: Path): String = path.name

        /**
         * Retrieves the extension of the specified path.
         *
         * @param path the path from which to extract the extension.
         * @return the extension of [path] as a string.
         */
        override fun extension(path: Path): String = path.extension

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
         * @throws IOException if an I/O error occurs while retrieving metadata.
         */
        override suspend fun metadata(path: Path): FileMetadata? {
            return if (path.isRegularFile()) {
                FileMetadata(FileType.File, path.isHidden())
            } else if (path.isDirectory()) {
                FileMetadata(FileType.Directory, path.isHidden())
            } else {
                null
            }
        }

        /**
         * Retrieves a sorted list of paths within the specified directory.
         * The listing is not recursive.
         *
         * @param directory The directory path whose contents are to be listed.
         * @return A list of paths within the specified directory, sorted by name.
         *         Returns an empty list if an error occurs or the directory is empty.
         * @throws IllegalArgumentException if [directory] is not a directory or doesn't exist.
         * @throws IOException if an I/O error occurs during listing.
         */
        override suspend fun list(directory: Path): List<Path> {
            require(directory.exists()) { "Path must exist" }
            require(directory.isDirectory()) { "Path must be a directory" }

            return Files.list(directory).use {
                it.sorted { a, b -> a.name.compareTo(b.name) }.toList()
            }
        }

        /**
         * Retrieves the parent directory of the specified path, if it exists.
         *
         * @param path the path for which to retrieve the parent directory
         * @return the parent path if it exists, or null if the path does not have a parent
         */
        override fun parent(path: Path): Path? = path.parent

        /**
         * Computes the relative path from the given root to the specified path.
         *
         * @param root the root path to which the provided path will be relativized.
         * @param path the path for which the relative path needs to be determined.
         * @return the relative path from the root to the given path as a normalized string, or null if the paths have no common prefix.
         */
        override fun relativize(root: Path, path: Path): String? {
            return path.relativeToOrNull(root)?.normalize()?.pathString
        }

        /**
         * Checks if the specified file or directory exists at the given path.
         *
         * @param path The path to the file or directory to be checked.
         * @return `true` if the file or directory exists, `false` otherwise.
         */
        override suspend fun exists(path: Path): Boolean = path.exists()

        /**
         * Detects the type of content stored in a file using a [path].
         *
         * @param path The path to the file whose content type is to be detected.
         * @return [FileContentType.Text] for text files, [FileContentType.Binary] for binary files.
         * @throws IllegalArgumentException if the path doesn't exist or isn't a regular file.
         * @throws IOException if an I/O error occurs while detecting the file content type.
         */
        override suspend fun getFileContentType(path: Path): FileContentType {
            require(path.exists()) { "Path must exist" }
            require(path.isRegularFile()) { "Path must be a regular file" }
            return if (path.isFileHeadTextBased()) FileContentType.Text else FileContentType.Binary
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

        /**
         * Reads the contents of the file located at the specified path.
         *
         * @param path the path of the file to read, which must be a regular file and must exist.
         * @return a ByteArray containing the contents of the file.
         * @throws IllegalArgumentException if the specified path is not a regular file or does not exist.
         * @throws IOException if an I/O error occurs during reading.
         */
        override suspend fun read(path: Path): ByteArray {
            require(path.exists()) { "Path must exist" }
            require(path.isRegularFile()) { "Path must be a regular file" }

            return withContext(Dispatchers.IO) { path.readBytes() }
        }

        /**
         * Opens a source to read from the specified file path.
         *
         * @param path The file path from which the source will be opened.
         * @return A buffered source for reading from the file.
         * @throws IllegalArgumentException if [path] doesn't exist or isn't a regular file.
         * @throws IOException if an I/O error occurs during source creation.
         */
        override suspend fun source(path: Path): Source = withContext(Dispatchers.IO) {
            require(path.exists()) { "Path must exist" }
            require(path.isRegularFile()) { "Path must be a regular file" }
            SystemFileSystem.source(path = kotlinx.io.files.Path(path.pathString)).buffered()
        }

        /**
         * Returns the size of the regular file at the specified path.
         *
         * @param path the path to the file whose size is to be obtained.
         * Must be a regular file and exist.
         * @return the size of the file in bytes.
         * @throws IllegalArgumentException if [path] doesn't exist or isn't a regular file.
         * @throws IOException if an I/O error occurs while determining the file size.
         */
        override suspend fun size(path: Path): Long {
            require(path.exists()) { "Path must exist" }
            require(path.isRegularFile()) { "Path must be a regular file" }
            return withContext(Dispatchers.IO) { path.fileSize() }
        }
    }

    /**
     * This is the most comprehensive interface, offering complete filesystem operations using [Path] objects
     * including reading, writing, and path manipulation.
     */
    public object ReadWrite : FileSystemProvider.ReadWrite<Path>, FileSystemProvider.ReadOnly<Path> by ReadOnly {

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
         * @throws IOException or its inheritor if the [source] doesn't exist, isn't a file or directory,
         *   [target] already exists, or any I/O error occurs.
         */
        override suspend fun move(source: Path, target: Path) {
            withContext(Dispatchers.IO) {
                if (target.exists()) {
                    throw FileAlreadyExistsException("Target path already exists: $target")
                }
                if (source.notExists()) {
                    throw IOException("Source path doesn't exist: $source")
                }

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
                    target.createParentDirectories()
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
         * @throws NoSuchFileException if a file or directory doesn't exist.
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
}
