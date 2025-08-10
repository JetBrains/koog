package ai.koog.prompt.structure

import ai.koog.prompt.text.TextContentBuilderBase

/**
 * Adds a structured representation of the given value to the [TextContentBuilderBase].
 *
 * @param structure The structure definition
 * @param value The value to be serialized and added to the builder.
 */
public fun <T> TextContentBuilderBase<*>.structure(structure: StructuredData<T, *>, value: T) {
    +structure.pretty(value)
}
