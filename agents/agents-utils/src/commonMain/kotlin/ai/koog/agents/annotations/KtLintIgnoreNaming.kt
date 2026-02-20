package ai.koog.agents.annotations

/**
 * Indicates that the annotated class or function should be excluded from KtLint naming rule checks.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
public annotation class KtLintIgnoreNaming
