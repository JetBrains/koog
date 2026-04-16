package ai.koog.agents.tools.test

import kotlinx.serialization.Serializable

@Serializable
data class Payload(
    val id: Int,
    val name: String
)

@Serializable
data class Complex(
    val payload: Payload,
    val meta: String
)

@Serializable
enum class InnerEnum {
    A,
    B,
    C
}

@Serializable
enum class OuterEnum {
    X,
    Y,
    Z
}

@Serializable
data class NestedEnumPayload(
    val outer: OuterEnum,
    val inner: InnerEnum
)

@Serializable
data class EnumListPayload(
    val enums: List<InnerEnum>
)
