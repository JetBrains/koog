package ai.koog.rag.base.files

import ai.koog.rag.base.files.FileMetadata.FileContentType
import ai.koog.rag.base.files.FileMetadata.FileType
import ai.koog.rag.base.files.JVMFileSystemProvider.ReadWrite.parent
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
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.copyToRecursively
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
        override fun fromAbsolutePathString(path: String): Path {
            val resolvedPath = Path.of(toSystemDependentName(path)).normalize()
            require(resolvedPath.isAbsolute) { "Resolved path must be absolute" }
            return resolvedPath
        }

        /**
         * Resolves strings from [parts] against a [base] path.
         * This method works with the path structure
         * and doesn't check if the path actually exists in the filesystem.
         *
         * @param base The base path for resolution.
         * @param parts The path strings to resolve.
         * @return A normalized [Path] object representing the resolved path.
         * @throws IllegalArgumentException if any of the [parts] is an absolute path.
         */
        override fun joinPath(base: Path, vararg parts: String): Path {
            return parts.fold(base) { acc, part ->
                val resolvedPath = Path.of(part)
                require(!resolvedPath.isAbsolute) { "Path must be relative, but was absolute: $part" }
                acc.resolve(part)
            }.normalize()
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
         * The operation is performed with [Dispatchers.IO] context.
         *
         * @param path The path for which the metadata is to be retrieved.
         * @return A [FileMetadata] instance containing information about the file or directory,
         * or null if the path does not represent a valid file or directory.
         * @throws IOException if an I/O error occurs while retrieving metadata.
         */
        override suspend fun metadata(path: Path): FileMetadata? = withContext(Dispatchers.IO) {
            if (path.isRegularFile()) {
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
         * The operation is performed with [Dispatchers.IO] context.
         *
         * @param directory The directory path whose contents are to be listed.
         * @return A list of paths within the specified directory, sorted by name.
         *         Returns an empty list if an error occurs or the directory is empty.
         * @throws IllegalArgumentException if [directory] is not a directory or doesn't exist.
         * @throws IOException if an I/O error occurs during listing.
         */
        override suspend fun list(directory: Path): List<Path> = withContext(Dispatchers.IO) {
            require(directory.exists()) { "Path must exist" }
            require(directory.isDirectory()) { "Path must be a directory" }

            Files.list(directory).use {
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
         * The operation is performed with [Dispatchers.IO] context.
         *
         * @param path The path to the file or directory to be checked.
         * @return `true` if the file or directory exists, `false` otherwise.
         */
        override suspend fun exists(path: Path): Boolean = withContext(Dispatchers.IO) { path.exists() }

        /**
         * Detects the type of content stored in a file using a [path].
         *
         * The operation is performed with [Dispatchers.IO] context.
         *
         * @param path The path to the file whose content type is to be detected.
         * @return [FileContentType.Text] for text files, [FileContentType.Binary] for binary files.
         * @throws IllegalArgumentException if the path doesn't exist or isn't a regular file.
         * @throws IOException if an I/O error occurs while detecting the file content type.
         */
        override suspend fun getFileContentType(path: Path): FileContentType = withContext(Dispatchers.IO) {
            require(path.exists()) { "Path must exist" }
            require(path.isRegularFile()) { "Path must be a regular file" }
            if (path.isFileHeadTextBased()) FileContentType.Text else FileContentType.Binary
        }

        /**
         * Determines if the beginning of a file's content is text-based, as opposed to binary.
         * This method reads a specified amount of data from the start of the file,
         * checks for null bytes, and attempts decoding with a list of provided character sets.
         *
         * @param headMaxSize The maximum number of bytes to read from the start of the file. Defaults to 8000 bytes.
         * @param charsetsToTry A list of character sets to attempt decoding the file's content. Defaults to a list containing UTF-8.
         * @return True if the file's head data contains no null bytes and is successfully decoded with one of the given character sets,
         * otherwise false.
         */
        private fun Path.isFileHeadTextBased(
            headMaxSize: Int = 8000,
            charsetsToTry: List<Charset> = listOf(
                Charsets.UTF_8,
            )
        ): Boolean {
            return runCatching {
                val bytes = inputStream().use { stream ->
                    val buffer = ByteArray(headMaxSize)
                    val bytesRead = stream.read(buffer, 0, headMaxSize)
                    if (bytesRead <= 0) return false
                    buffer.copyOf(bytesRead)
                }

                // check for null bytes
                if (bytes.any { it == 0.toByte() }) {
                    return false
                }

                val headData = ByteBuffer.wrap(bytes)
                charsetsToTry.any { charset ->
                    runCatching {
                        charset.newDecoder().decode(headData.duplicate())
                    }.isSuccess
                }
            }.getOrElse { false }
        }

        /**
         * Reads the content of a file at the specified [path].
         * Bytes are read with [Dispatchers.IO] context.
         *
         * @param path The path to read.
         * @return The file content as a byte array.
         * @throws IllegalArgumentException if the path doesn't exist or isn't a regular file.
         * @throws IOException if an I/O error occurs during reading.
         */
        override suspend fun readBytes(path: Path): ByteArray = withContext(Dispatchers.IO) {
            require(path.exists()) { "Path must exist" }
            require(path.isRegularFile()) { "Path must be a regular file" }

            path.readBytes()
        }

        /**
         * Creates a [Source] for reading from a file at the specified [path].
         * The returned [Source] is buffered. It is created with [Dispatchers.IO] context.
         *
         * @param path The path to read from.
         * @return A buffered [Source] object for reading.
         * @throws IllegalArgumentException if [path] doesn't exist or isn't a regular file.
         * @throws IOException if an I/O error occurs during [Source] creation.
         */
        override suspend fun inputStream(path: Path): Source = withContext(Dispatchers.IO) {
            require(path.exists()) { "Path must exist" }
            require(path.isRegularFile()) { "Path must be a regular file" }
            SystemFileSystem.source(path = kotlinx.io.files.Path(path.pathString)).buffered()
        }

        /**
         * Returns the size of the regular file at the specified path.
         *
         * The operation is performed with [Dispatchers.IO] context.
         *
         * @param path the path to the file whose size is to be obtained.
         * Must be a regular file and exist.
         * @return the size of the file in bytes.
         * @throws IllegalArgumentException if [path] doesn't exist or isn't a regular file.
         * @throws IOException if an I/O error occurs while determining the file size.
         */
        override suspend fun size(path: Path): Long = withContext(Dispatchers.IO) {
            require(path.exists()) { "Path must exist" }
            require(path.isRegularFile()) { "Path must be a regular file" }
            path.fileSize()
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
         * Creates a new file or directory denoted by the [path] using the specified [type].
         * It is created with [Dispatchers.IO] context.
         * Parent directories will be created if they don't exist.
         *
         * @param path The path of the new file or directory.
         * @param type The type (file or directory) to create.
         * @throws IOException or its inheritor if the [path] already exists,
         *   or [path] is invalid (e.g., contains reserved characters), or if any other I/O error occurs.
         */
        override suspend fun create(path: Path, type: FileType): Unit = withContext(Dispatchers.IO) {
            if (path.name in WINDOWS_RESERVED_NAMES && System.getProperty("os.name").lowercase().contains("win")) {
                throw IOException("Invalid file name: ${path.name}")
            }

            path.createParentDirectories()

            when (type) {
                FileType.File -> path.createFile()
                FileType.Directory -> path.createDirectory()
            }
        }

        /**
         * Writes content to a file.
         * If the file doesn't exist, it will be created.
         * If the file exists, its content will be overwritten.
         * Parent directories will be created if they don't exist.
         * The operation is performed with [Dispatchers.IO] context.
         *
         * @param path The path to write to.
         * @param data The data to write as a byte array.
         * @throws IOException if the path is a directory or any other I/O error occurs during writing.
         */
        override suspend fun writeBytes(path: Path, data: ByteArray): Unit = withContext(Dispatchers.IO) {
            path.createParentDirectories()
            path.writeBytes(data)
        }

        /**
         * Creates a [Sink] for writing to a file.
         * If the file doesn't exist, it will be created.
         * If the parent directories don't exist, they will be created.
         * The returned [Sink] is buffered.
         * It is created with [Dispatchers.IO] context.
         *
         * @param path The path where [Sink] will be created.
         * @param append Append to existing content (true) or overwrite (false). Default is false (overwrite).
         * @return A buffered [Sink] object for writing.
         * @throws IOException if the path is a directory or any other I/O error occurs during [Sink] creation.
         */
        override suspend fun outputStream(path: Path, append: Boolean): Sink = withContext(Dispatchers.IO) {
            path.createParentDirectories()
            SystemFileSystem.sink(path = kotlinx.io.files.Path(path.pathString), append = append).buffered()
        }

        /**
         * Moves a file or directory from [source] to [target].
         * If the [source] is a directory, all its contents are moved recursively.
         * Parent directories of the [target] will be created if they don't exist.
         * The operation is performed with [Dispatchers.IO] context.
         *
         * @param source The source path to move from.
         * @param target The target path to move to.
         * @throws IOException or its inheritor if the [source] doesn't exist, isn't a file or directory,
         *   [target] already exists, or any I/O error occurs.
         */
        override suspend fun move(source: Path, target: Path): Unit = withContext(Dispatchers.IO) {
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

        /**
         * Copies a file or directory from [source] to [target].
         * If the [source] is a directory, all its contents are copied recursively.
         * Parent directories of the [target] will be created if they don't exist.
         * The operation is performed with [Dispatchers.IO] context.
         *
         * @param source The source path to copy from.
         * @param target The target path to copy to.
         * @throws IOException or its inheritor if the [source] doesn't exist, isn't a file or directory,
         *   [target] already exists, or any I/O error occurs.
         */
        @OptIn(ExperimentalPathApi::class)
        override suspend fun copy(source: Path, target: Path): Unit = withContext(Dispatchers.IO) {
            if (target.exists()) {
                throw FileAlreadyExistsException("Destination path already exists: $target")
            }
            if (source.notExists()) {
                throw IOException("Source path doesn't exist: $source")
            }

            if (source.isDirectory()) {
                target.createDirectories()
                Files.list(source).use { stream ->
                    stream.forEach { child ->
                        val targetChild = target.resolve(child.name)

                        if (!child.isDirectory()) {
                            child.copyTo(targetChild)
                        } else {
                            child.copyToRecursively(target = targetChild, followLinks = false, overwrite = false)
                        }
                    }
                }
            } else if (source.isRegularFile()) {
                target.createParentDirectories()
                source.copyTo(target)
            } else {
                throw IOException("Source path is neither a file nor a directory: $source")
            }
        }

        /**
         * Deletes a file or directory denoted by the [path].
         * If the item is a directory, it will be deleted recursively with all its contents.
         * The operation is performed with [Dispatchers.IO] context.
         *
         * @param parent The path of the item to delete.
         * @throws IOException or its inheritor if the file or directory doesn't exist or can't be deleted for any other reason.
         */
        @OptIn(ExperimentalPathApi::class)
        override suspend fun delete(path: Path): Unit = withContext(Dispatchers.IO) {
            if (path.isDirectory()) {
                path.deleteRecursively()
            } else {
                path.deleteExisting()
            }
        }
    }
}
