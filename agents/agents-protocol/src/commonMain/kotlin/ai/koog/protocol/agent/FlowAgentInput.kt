package ai.koog.protocol.agent

import ai.koog.protocol.parser.FlowAgentInputSerializer
import kotlinx.serialization.Serializable

/**
 * Type-safe input values for flow agents supporting primitives, arrays, and complex types.
 */
@Serializable(with = FlowAgentInputSerializer::class)
public sealed interface FlowAgentInput {

    /**
     * The type identifier for this input.
     */
    public val type: String

    /**
     * Determines if the input is a primitive type.
     */
    public val isPrimitive: Boolean
        get() = this is Primitive

    /**
     * Marker interface for primitive input types.
     */
    public interface Primitive : FlowAgentInput

    /**
     * Integer input value.
     */
    @Serializable
    public data class InputInt(public val data: Int) : Primitive {
        override val type: String = "int"
    }

    /**
     * Double-precision floating point input value.
     */
    @Serializable
    public data class InputDouble(public val data: Double) : Primitive {
        override val type: String = "double"
    }

    /**
     * String input value.
     */
    @Serializable
    public data class InputString(public val data: String) : Primitive {
        override val type: String = "string"
    }

    /**
     * Boolean input value.
     */
    @Serializable
    public data class InputBoolean(public val data: Boolean) : Primitive {
        override val type: String = "boolean"
    }

    /**
     * Result from a verification/critique agent containing success status and feedback.
     */
    @Serializable
    public data class InputCritiqueResult(
        public val success: Boolean,
        public val feedback: String,
        public val input: FlowAgentInput
    ) : FlowAgentInput {
        override val type: String = "critique"
    }

//endregion Entities

//region Arrays

    /**
     * Array of integer values.
     */
    @Serializable
    public data class InputArrayInt(public val data: Array<Int>) : FlowAgentInput {

        override val type: String = "array_int"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            return data.contentEquals((other as InputArrayInt).data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    /**
     * Array of double-precision floating point values.
     */
    @Serializable
    public data class InputArrayDouble(public val data: Array<Double>) : FlowAgentInput {

        override val type: String = "array_double"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            return data.contentEquals((other as InputArrayDouble).data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    /**
     * Array of string values.
     */
    @Serializable
    public data class InputArrayString(public val data: Array<String>) : FlowAgentInput {

        override val type: String = "array_string"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            return data.contentEquals((other as InputArrayString).data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    /**
     * Array of boolean values.
     */
    @Serializable
    public data class InputArrayBoolean(public val data: Array<Boolean>) : FlowAgentInput {

        override val type: String = "array_boolean"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            return data.contentEquals((other as InputArrayBoolean).data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }
}
