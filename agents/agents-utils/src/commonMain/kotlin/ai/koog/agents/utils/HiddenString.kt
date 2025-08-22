package ai.koog.agents.utils

/**
 * Represents a string whose actual value is hidden when converted to a string representation.
 *
 * This class is useful for scenarios where sensitive or confidential information needs to be obscured
 * in logs or other outputs. The value is only accessible programmatically and will not be displayed
 * in plain text when the [toString] method is called.
 * The original value is accessible through a public [value] field.
 *
 * @param value The original string value to be hidden.
 * @param placeholder The placeholder text to display when the instance is converted to a string.
 * Defaults to "HIDDEN:non-empty".
 */
public data class HiddenString(val value: String, private val placeholder: String = HIDDEN_STRING_PLACEHOLDER) {

    /**
     * A companion object for holding constant values and shared logic associated with the enclosing class.
     *
     * Objects and properties defined in this companion can be accessed without an instance of the enclosing class.
     *
     * @property HIDDEN_STRING_PLACEHOLDER A constant string placeholder with the value "HIDDEN:non-empty".
     */
    public companion object {

        /**
         * A constant string used as a placeholder to represent hidden or sensitive string values.
         *
         * Typically used in scenarios where the actual value of a string needs to be obscured,
         * such as in logs or debug outputs, to protect sensitive or confidential information.
         *
         * The placeholder value "HIDDEN:non-empty" indicates that the actual string is not empty,
         * but its content is intentionally hidden.
         */
        public const val HIDDEN_STRING_PLACEHOLDER: String = "HIDDEN:non-empty"
    }

    override fun toString(): String {
        return if (value.isEmpty()) value else placeholder
    }
}
