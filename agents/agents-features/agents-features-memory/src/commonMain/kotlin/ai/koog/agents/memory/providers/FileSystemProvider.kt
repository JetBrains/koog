package ai.koog.agents.memory.providers

import kotlinx.io.Sink
import kotlinx.io.Source

public object FileSystemProvider {

    public interface Serialization<Path> {
        /**
         * Performs path to string conversion.
         *
         * Note that conversion depends on [path] state, there is no
         * guarantee whether the result will be relative, absolute or any other
         * type of path.
         */
        @Deprecated("Use toAbsolutePathString instead", ReplaceWith("toAbsolutePathString(path)"))
        public fun toPathString(path: Path): String
        public fun toAbsolutePathString(path: Path): String
        public fun fromAbsoluteString(path: String): Path
        public fun fromRelativeString(base: Path, path: String): Path

        public suspend fun name(path: Path): String
        public suspend fun extension(path: Path): String
    }

    public interface Select<Path> : Serialization<Path> {
        public suspend fun metadata(path: Path): FileMetadata?
        public suspend fun list(path: Path): List<Path>
        public suspend fun parent(path: Path): Path?
        @Deprecated("Use relativize instead", ReplaceWith("relativize(root, path)"))
        public suspend fun relative(root: Path, path: Path): String? = relativize(root, path)
        public suspend fun relativize(root: Path, path: Path): String?
        public suspend fun exists(path: Path): Boolean
    }

    public interface Read<Path> : Serialization<Path> {
        public suspend fun read(path: Path): ByteArray
        public suspend fun source(path: Path): Source
        public suspend fun size(path: Path): Long
    }

    public interface ReadOnly<Path>: Serialization<Path>, Select<Path>, Read<Path>  {}

    public interface Write<Path> : Serialization<Path> {
        public suspend fun create(parent: Path, name: String, type: FileMetadata.FileType)
        public suspend fun move(source: Path, target: Path)
        public suspend fun write(path: Path, content: ByteArray)
        public suspend fun sink(path: Path, append: Boolean = false): Sink
        public suspend fun delete(parent: Path, name: String)
    }

    public interface ReadWrite<Path> : ReadOnly<Path>, Write<Path>
}
