package ai.koog.rag.base.files.filter

import ai.koog.rag.base.files.FileSystemProvider
import ai.koog.rag.base.files.contains
import kotlin.jvm.JvmStatic

/**
 * A class that is used for path filtering while traversing a file system.
 */
public fun interface TraversalFilter<Path> {
    /**
     * Returns `true` if the filter accepts the path, `false` otherwise.
     */
    public suspend fun show(path: Path, fs: FileSystemProvider.ReadOnly<Path>): Boolean

    /**
     * Returns `true` if the path is not accepted, `false` otherwise.
     */
    public suspend fun hide(path: Path, fs: FileSystemProvider.ReadOnly<Path>): Boolean = !show(path, fs)

    /**
     * Returns a conjunction of this filter and the [other] filter.
     */
    public infix fun and(other: TraversalFilter<Path>): TraversalFilter<Path> = TraversalFilter { path, fs ->
        this@TraversalFilter.show(path, fs) && other.show(path, fs)
    }

    /**
     * Returns a disjunction of this filter and the [other] filter.
     */
    public infix fun or(other: TraversalFilter<Path>): TraversalFilter<Path> = TraversalFilter { path, fs ->
        this@TraversalFilter.show(path, fs) || other.show(path, fs)
    }

    /**
     * Contains static methods for [TraversalFilter].
     */
    public companion object {
        /**
         * A filter that always returns `true`.
         */
        @JvmStatic
        public fun <Path> any(): TraversalFilter<Path> = TraversalFilter { _, _ -> true }

        /**
         * Returns a reversed version of the [filter].
         */
        @JvmStatic
        public fun <Path> not(filter: TraversalFilter<Path>): TraversalFilter<Path> =
            TraversalFilter { path, fs -> !filter.show(path, fs) }
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
    public fun <Path> byRoot(root: Path): TraversalFilter<Path> = TraversalFilter { path, fs ->
        root.contains(path, fs) || path.contains(root, fs)
    }
}
