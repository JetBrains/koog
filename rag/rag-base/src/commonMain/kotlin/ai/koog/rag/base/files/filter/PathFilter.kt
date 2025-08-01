package ai.koog.rag.base.files.filter

import ai.koog.rag.base.files.FileSystemProvider
import ai.koog.rag.base.files.contains
import kotlin.jvm.JvmStatic

/**
 * A class that is used for path filtering.
 * Methods are not suspendable intentionally because they should not use IO operations.
 * In most cases, non-suspendable methods from [FileSystemProvider] should be enough.
 */
public fun interface PathFilter<Path> {
    /**
     * Returns `true` if the filter accepts the path, `false` otherwise.
     */
    public fun show(path: Path, fs: FileSystemProvider.ReadOnly<Path>): Boolean

    /**
     * Returns `true` if the path is not accepted, `false` otherwise.
     */
    public fun hide(path: Path, fs: FileSystemProvider.ReadOnly<Path>): Boolean = !show(path, fs)

    /**
     * Returns a conjunction of this filter and the [other] filter.
     */
    public infix fun and(other: PathFilter<Path>): PathFilter<Path> = PathFilter { path, fs ->
        this@PathFilter.show(path, fs) && other.show(path, fs)
    }

    /**
     * Returns a disjunction of this filter and the [other] filter.
     */
    public infix fun or(other: PathFilter<Path>): PathFilter<Path> = PathFilter { path, fs ->
        this@PathFilter.show(path, fs) || other.show(path, fs)
    }

    /**
     * Contains static methods for [PathFilter].
     */
    public companion object {
        /**
         * A filter that always returns `true`.
         */
        @JvmStatic
        public fun <Path> any(): PathFilter<Path> = PathFilter { _, _ -> true }

        /**
         * Returns a reversed version of the [filter].
         */
        @JvmStatic
        public fun <Path> not(filter: PathFilter<Path>): PathFilter<Path> =
            PathFilter { path, fs -> !filter.show(path, fs) }
    }
}

/**
 * Contains factory methods with predefined path filters.
 */
public object PathFilters {
    /**
     * Accepts only paths that are contained within the given [root] or paths that contain the [root].
     */
    @JvmStatic
    public fun <Path> byRoot(root: Path): PathFilter<Path> = PathFilter { path, fs ->
        root.contains(path, fs) || path.contains(root, fs)
    }
}
