package ai.koog.agents.core.tools.annotations

import kotlinx.serialization.SerialInfo


/**
 * Annotation used to specify the maximum allowable numeric value for properties, parameters,
 * return values, or other entities provided to LLMs.
 *
 * This annotation can be applied to properties, classes, types, value parameters, or functions
 * to enforce a constraint that the value must not exceed the specified maximum value.
 *
 * @property max The maximum allowable value.
 */
@SerialInfo
@Target(
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
)
public annotation class LLMMax(val max: Int)
