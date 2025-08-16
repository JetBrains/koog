package ai.koog.agents.core.tools.annotations

import kotlinx.serialization.SerialInfo

/**
 * Annotation used to specify the minimum allowable numeric value for properties, parameters,
 * return values, or other entities provided to LLMs.
 *
 * This annotation can be applied to properties, classes, types, value parameters, or functions
 * to enforce a constraint that the value must not fall below the specified minimum value.
 *
 * @property min The minimum allowable value.
 */
@SerialInfo
@Target(
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPE,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FUNCTION
)
public annotation class LLMMin(val min: Int)
