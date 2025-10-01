package ai.koog.a2a.annotations

/**
 * Marks an API as internal to the a2a module. This annotation indicates that the
 * marked API is not intended for public use and is subject to change or removal
 * without prior notice. It should be used with caution and only within the intended
 * internal scope.
 */
@RequiresOptIn("This API is internal in a2a and should not be used. It could be removed or changed without notice.")
public annotation class InternalA2AApi
