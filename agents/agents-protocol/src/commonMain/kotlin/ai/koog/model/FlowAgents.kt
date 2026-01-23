package ai.koog.model

import kotlinx.serialization.Serializable

/**
 * Represents a transformation operation.
 */
@Serializable
public data class Transformation(
    val input: String,
    val to: String
)
