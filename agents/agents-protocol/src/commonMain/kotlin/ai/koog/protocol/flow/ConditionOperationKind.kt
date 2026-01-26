package ai.koog.protocol.flow

/**
 *
 */
public enum class ConditionOperationKind(public val id: String) {
    EQUALS("equals"),
    NOT_EQUALS("not_equals"),
    MORE("more"),
    LESS("less"),
    MORE_OR_EQUAL("more_or_equal"),
    LESS_OR_EQUAL("less_or_equal"),
    NOT("not"),
    AND("and"),
    OR("or")
}
