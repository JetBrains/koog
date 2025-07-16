package ai.koog.prompt.executor.clients

/**
 * Marks APIs as internal to the koog library's LLM clients and not intended for public use.
 *
 * APIs annotated with this annotation are liable to removal or modification without prior notice and
 * should not be used in external code. Such APIs are designed solely for internal purposes.
 *
 * Utilization of this annotation will result in a compiler error unless explicitly opted-in.
 *
 * Use cases include:
 * - Restricting certain experimental or implementation-specific features from unintended usage.
 * - Marking APIs intended exclusively for internal consumption within the koog library.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This API is internal in koog and should not be used. It could be removed or changed without notice."
)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPEALIAS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FIELD,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.PROPERTY_SETTER
)
public annotation class InternalLLMClientApi
