package ai.koog.agents.core.feature.model

/**
 * Retrieves the fully qualified class name of the [Throwable], or null if unavailable.
 *
 * The `typeName` property provides information about the specific type of the [Throwable]
 * instance. On the JVM, this returns the fully qualified class name, giving precise details
 * about the exception's originating class. On other platforms, it defaults to the simple class name.
 *
 * This property is particularly useful for debugging and error handling, allowing developers to
 * programmatically access and log the type of exception without requiring additional reflection
 * or runtime logic.
 */
internal actual val Throwable.typeName: String?
    get() = this::class.qualifiedName
