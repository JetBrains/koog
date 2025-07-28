package ai.koog.rag.base.files

import kotlinx.io.IOException
import kotlinx.io.Sink
import kotlinx.io.Source

/**
 * Filters the current read-only file system implementation based on the specified root path such that
 * only paths that are contained within the given root or paths that contain the root are visible and accessible.
 *
 * @param root The root path to use as the basis for filtering.
 * @return A new file system provider instance that is filtered based on the given root path.
 */
public fun <Path> FileSystemProvider.ReadOnly<Path>.filterByRoot(root: Path): FileSystemProvider.ReadOnly<Path> {
    val filter = PathFilter { path, fs ->
        root.contains(path, fs) || path.contains(root, fs)
    }
    return FilteredReadOnly(this, filter)
}

/**
 * Filters the current read-write file system implementation based on the specified root path such that
 * only paths that are contained within the given root or paths that contain the root are visible and accessible.
 *
 * @param root The root path to use as the basis for filtering.
 * @return A new file system provider instance that is filtered based on the given root path.
 */
public fun <Path> FileSystemProvider.ReadWrite<Path>.filterByRoot(root: Path): FileSystemProvider.ReadWrite<Path> {
    val filter = PathFilter { path, fs ->
        root.contains(path, fs) || path.contains(root, fs)
    }
    return FilteredReadWrite(this, filter)
}

internal fun interface PathFilter<Path> {
    fun show(path: Path, fs: FileSystemProvider.ReadOnly<Path>): Boolean
    fun hide(path: Path, fs: FileSystemProvider.ReadOnly<Path>): Boolean = !show(path, fs)
}

internal open class FilteredReadOnly<P>(
    private val fs: FileSystemProvider.ReadOnly<P>,
    private val filter: PathFilter<P>
) : FileSystemProvider.ReadOnly<P> {
    private fun requireAllowed(path: P) {
        require(filter.show(path, fs)) { "Path $path is hidden by filter" }
    }

    override suspend fun read(path: P): ByteArray {
        requireAllowed(path)
        return fs.read(path)
    }

    override suspend fun source(path: P): Source {
        requireAllowed(path)
        return fs.source(path)
    }

    override suspend fun size(path: P): Long {
        requireAllowed(path)
        return fs.size(path)
    }

    override suspend fun list(directory: P): List<P> {
        requireAllowed(directory)
        return fs.list(directory).filter { child ->
            filter.show(child, fs)
        }
    }

    override suspend fun metadata(path: P): FileMetadata? {
        return if (filter.show(path, fs)) {
            fs.metadata(path)
        } else {
            null
        }
    }

    override suspend fun exists(path: P): Boolean {
        return if (filter.show(path, fs)) {
            fs.exists(path)
        } else {
            false
        }
    }

    override fun parent(path: P): P? = fs.parent(path)

    override fun relativize(root: P, path: P): String? = fs.relativize(root, path)

    override fun toAbsolutePathString(path: P): String = fs.toAbsolutePathString(path)

    override fun fromAbsoluteString(path: String): P = fs.fromAbsoluteString(path)

    override fun fromRelativeString(base: P, path: String): P = fs.fromRelativeString(base, path)

    override fun name(path: P): String = fs.name(path)

    override fun extension(path: P): String = fs.extension(path)
}

internal class FilteredReadWrite<P>(
    private val fs: FileSystemProvider.ReadWrite<P>,
    private val filter: PathFilter<P>
) : FileSystemProvider.ReadWrite<P>, FilteredReadOnly<P>(fs, filter) {
    private fun ensureAllowed(path: P) {
        if (filter.hide(path, fs)) {
            throw IOException("Path $path is not allowed by filter")
        }
    }

    override suspend fun create(parent: P, name: String, type: FileMetadata.FileType) {
        ensureAllowed(parent)
        ensureAllowed(fs.fromRelativeString(parent, name))
        fs.create(parent, name, type)
    }

    override suspend fun write(path: P, content: ByteArray) {
        ensureAllowed(path)
        fs.write(path, content)
    }

    override suspend fun sink(path: P, append: Boolean): Sink {
        ensureAllowed(path)
        return fs.sink(path, append)
    }

    override suspend fun move(source: P, target: P) {
        ensureAllowed(source)
        ensureAllowed(target)
        fs.move(source, target)
    }

    override suspend fun delete(parent: P, name: String) {
        ensureAllowed(parent)
        ensureAllowed(fs.fromRelativeString(parent, name))
        fs.delete(parent, name)
    }


}