package ai.koog.serialization.annotations

/**
 * Indicates that the annotated API is internal to serialization implementations and is not intended for public use.
 * Symbols marked with this annotation are not guaranteed to maintain backwards compatibility.
 */
@RequiresOptIn(
    message = "This API is internal in koog serialization and should not be used. It could be removed or changed without notice.",
)
public annotation class InternalKoogSerializationApi
