package ai.koog.agents.core.tools.annotations

import kotlinx.serialization.SerialInfo

/**
 * Annotation used to specify the acceptable range of numeric values for properties, parameters,
 * return values, or other entities provided to LLMs.
 *
 * This annotation can be applied to properties, classes, types, value parameters, or functions
 * to enforce a constraint that the value must fall within the specified minimum and maximum range.
 *
 * @property min The minimum allowable value.
 * @property max The maximum allowable value.
 */
@SerialInfo
@Target(
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPE,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FUNCTION
)
public annotation class LLMRange(val min: Int, val max: Int)
