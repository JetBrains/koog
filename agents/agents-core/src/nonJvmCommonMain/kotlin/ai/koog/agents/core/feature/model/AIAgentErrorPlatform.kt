package ai.koog.agents.core.feature.model

/**
 * Provides the simple class name of the current [Throwable] instance.
 *
 * This property retrieves the simple name of the class corresponding to the `Throwable`
 * instance. On platforms where the fully qualified name is unavailable or impractical
 * to use, this property ensures that at least the simplified class name is accessible.
 *
 * @return The simple class name of the [Throwable], or `null` if it cannot be determined.
 */
internal actual val Throwable.typeName: String?
    get() = this::class.simpleName
