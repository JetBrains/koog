package ai.grazie.code.agents.core.tools.annotations

/**
 * Description for an entity that can be provided to LLMs.
 * You may use it to annotate properties, functions, parameters, classes, return types, etc.
 *
 * @property description The description of the entity.
 */
@Target(
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPE,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FUNCTION
)
annotation class LLMDescription(val description: String)
