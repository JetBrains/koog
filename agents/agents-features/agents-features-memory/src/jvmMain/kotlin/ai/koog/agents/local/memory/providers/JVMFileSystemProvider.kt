package ai.koog.agents.local.memory.providers

import ai.koog.agents.local.memory.providers.FileMetadata.FileContent
import ai.koog.agents.local.memory.providers.FileMetadata.FileType
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

public object JVMFileSystemProvider {
    public object Serialization : FileSystemProvider.Serialization<Path> {
        @Deprecated("Use toAbsolutePathString instead", replaceWith = ReplaceWith("toAbsolutePathString(path)"))
        override fun toPathString(path: Path): String = path.normalize().pathString
        override fun toAbsolutePathString(path: Path): String = path.normalize().absolutePathString()
        override fun fromAbsoluteString(path: String): Path = Path.of(toSystemDependentName(path)).normalize()
        override fun fromRelativeString(base: Path, path: String): Path = base.resolve(path).normalize()

        override suspend fun name(path: Path): String = path.name
        override suspend fun extension(path: Path): String = path.extension

        private fun toSystemDependentName(path: String): String {
            return path.replace("/", FileSystems.getDefault().separator).replace("\\", FileSystems.getDefault().separator)
        }
    }

    public object Select : FileSystemProvider.Select<Path>, FileSystemProvider.Serialization<Path> by Serialization {
        override suspend fun metadata(path: Path): FileMetadata? {
            return if (path.isRegularFile()) {
                FileMetadata(FileType.File, path.isHidden(), path.contentType())
            } else if (path.isDirectory()) {
                FileMetadata(FileType.Directory, path.isHidden(), path.contentType())
            } else {
                null
            }
        }

        override suspend fun list(path: Path): List<Path> = runCatching { Files.list(path).use {
            it.sorted { a, b -> a.name.compareTo(b.name) }.toList()
        }}.getOrElse { emptyList() }

        override suspend fun parent(path: Path): Path? = path.parent

        @Deprecated("Use relativize instead", replaceWith = ReplaceWith("relativize(root, path)"))
        override suspend fun relative(root: Path, path: Path): String? =
            path.relativeToOrNull(root)?.normalize()?.pathString

        override suspend fun relativize(root: Path, path: Path): String? {
            return path.relativeToOrNull(root)?.normalize()?.pathString
        }

        override suspend fun exists(path: Path): Boolean = path.exists()
    }

    public object Read : FileSystemProvider.Read<Path>, FileSystemProvider.Serialization<Path> by Serialization {
        override suspend fun read(path: Path): ByteArray {
            require(path.isRegularFile()) { "Path must be a regular file" }
            require(path.exists()) { "Path must exist" }

            return withContext(Dispatchers.IO) { path.readBytes() }
        }
        override suspend fun source(path: Path): Source = withContext(Dispatchers.IO) { SystemFileSystem.source(path = kotlinx.io.files.Path(path.pathString)).buffered() }
        override suspend fun size(path: Path): Long {
            require(path.isRegularFile()) { "Path must be a regular file" }
            require(path.exists()) { "Path must exist" }
            return withContext(Dispatchers.IO) { path.fileSize() }
        }
    }

    public object ReadOnly: FileSystemProvider.ReadOnly<Path>,
        FileSystemProvider.Select<Path> by Select,
        FileSystemProvider.Read<Path> by Read {

        @Deprecated("Use toAbsolutePathString instead", replaceWith = ReplaceWith("toAbsolutePathString(path)"))
        override fun toPathString(path: Path): String = Serialization.toPathString(path)
        override fun toAbsolutePathString(path: Path): String = Serialization.toAbsolutePathString(path)

        override fun fromAbsoluteString(path: String): Path = Serialization.fromAbsoluteString(path)
        override fun fromRelativeString(base: Path, path: String): Path = Serialization.fromRelativeString(base, path)

        override suspend fun name(path: Path): String = Serialization.name(path)
        override suspend fun extension(path: Path): String = Serialization.extension(path)

    }

    public object ReadWrite: FileSystemProvider.ReadWrite<Path>,
        FileSystemProvider.ReadOnly<Path> by ReadOnly,
        FileSystemProvider.Write<Path> by Write {

        @Deprecated("Use toAbsolutePathString instead", replaceWith = ReplaceWith("toAbsolutePathString(path)"))
        override fun toPathString(path: Path): String = Serialization.toPathString(path)
        override fun toAbsolutePathString(path: Path): String = Serialization.toAbsolutePathString(path)
        override fun fromAbsoluteString(path: String): Path = Serialization.fromAbsoluteString(path)
        override fun fromRelativeString(base: Path, path: String): Path = Serialization.fromRelativeString(base, path)
        override suspend fun name(path: Path): String = Serialization.name(path)
        override suspend fun extension(path: Path): String = Serialization.extension(path)
    }

    public object Write : FileSystemProvider.Write<Path>, FileSystemProvider.Serialization<Path> by Serialization {

        private val WINDOWS_RESERVED_NAMES = setOf(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
        )

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

        override suspend fun write(path: Path, content: ByteArray) {
            path.createParentDirectories()
            withContext(Dispatchers.IO) { path.writeBytes(content) }
        }

        override suspend fun sink(path: Path, append: Boolean): Sink {
            return withContext(Dispatchers.IO) {
                path.createParentDirectories()
                SystemFileSystem.sink(path = kotlinx.io.files.Path(path.pathString), append = append).buffered()
            }
        }

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

    private fun Path.contentType(): FileContent = when {
        isFileHeadTextBased() -> FileContent.Text
        isRegularFile() -> FileContent.Binary
        else -> FileContent.Inapplicable
    }

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
