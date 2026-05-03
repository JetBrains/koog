package ai.koog.utils.annotations

/**
 * Annotation to mark APIs that are internal to the koog utils and is not intended for public use.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This API is internal in Koog utils and should not be used outside of the library."
)
public annotation class InternalKoogUtils
