package ai.koog.rag.base.files

import ai.koog.rag.base.files.filter.TraversalFilter
import kotlinx.io.IOException
import kotlinx.io.Sink
import kotlinx.io.Source

/**
 * Filters the current read-only file system implementation such that
 * only paths that are accepted by [filter] are visible and accessible.
 */
public fun <Path> FileSystemProvider.ReadOnly<Path>.filter(
    filter: TraversalFilter<Path>
): FileSystemProvider.ReadOnly<Path> {
    return FilteredReadOnly(this, filter)
}

/**
 * Filters the current read-write file system implementation such that
 * only paths that are accepted by [filter] are visible and accessible.
 */
public fun <Path> FileSystemProvider.ReadWrite<Path>.filter(
    filter: TraversalFilter<Path>
): FileSystemProvider.ReadWrite<Path> {
    return FilteredReadWrite(this, filter)
}

internal open class FilteredReadOnly<P>(
    private val fs: FileSystemProvider.ReadOnly<P>,
    private val filter: TraversalFilter<P>
) : FileSystemProvider.ReadOnly<P> {
    private suspend fun requireAllowed(path: P) {
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

    override suspend fun getFileContentType(path: P): FileMetadata.FileContentType {
        requireAllowed(path)
        return fs.getFileContentType(path)
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
    private val filter: TraversalFilter<P>
) : FileSystemProvider.ReadWrite<P>, FilteredReadOnly<P>(fs, filter) {
    private suspend fun ensureAllowed(path: P) {
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
