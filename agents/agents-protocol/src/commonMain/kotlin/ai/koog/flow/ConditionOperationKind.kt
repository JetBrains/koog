package ai.koog.flow

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 *
 */
@Serializable
public enum class ConditionOperationKind(public val text: String) {
    @SerialName("equals")
    EQUALS("equals"),

    @SerialName("not_equals")
    NOT_EQUALS("not equals"),

    @SerialName("more")
    MORE("more"),

    @SerialName("less")
    LESS("less"),

    @SerialName("more_or_equal")
    MORE_OR_EQUAL("more or equal"),

    @SerialName("less_or_equal")
    LESS_OR_EQUAL("less or equal"),

    @SerialName("not")
    NOT("not"),

    @SerialName("and")
    AND("and"),

    @SerialName("or")
    OR("or");
}
