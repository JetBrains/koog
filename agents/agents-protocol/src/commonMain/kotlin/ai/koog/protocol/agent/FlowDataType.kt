package ai.koog.protocol.agent

import ai.koog.protocol.parser.FlowDataTypeSerializer
import kotlinx.serialization.Serializable

/**
 * Type-safe input values for flow agents supporting primitives, arrays, and complex types.
 */
@Serializable(with = FlowDataTypeSerializer::class)
public sealed interface FlowDataType {

    /**
     * The type identifier for this input.
     */
    public val type: String

    /**
     * Determines if the input is a primitive type.
     */
    public val isPrimitive: Boolean
        get() = this is FlowPrimitiveType

    /**
     * Marker interface for primitive data types.
     */
    public interface FlowPrimitiveType : FlowDataType

    /**
     * Integer data value.
     */
    @Serializable
    public data class FlowInteger(public val data: Int) : FlowPrimitiveType {
        override val type: String = "int"
    }

    /**
     * Represent the double floating point data value for flow agent data type.
     */
    @Serializable
    public data class FlowDouble(public val data: Double) : FlowPrimitiveType {
        override val type: String = "double"
    }

    /**
     * Represent the string data value for the flow agent data type.
     */
    @Serializable
    public data class FlowString(public val data: String) : FlowPrimitiveType {
        override val type: String = "string"
    }

    /**
     * Represent the boolean data value for the flow agent data type.
     */
    @Serializable
    public data class FlowBoolean(public val data: Boolean) : FlowPrimitiveType {
        override val type: String = "boolean"
    }

    /**
     * Represent a result from a verification/critique agent containing success status and feedback.
     */
    @Serializable
    public data class FlowCritiqueResult(
        public val success: Boolean,
        public val feedback: String,
        public val input: FlowDataType
    ) : FlowDataType {
        override val type: String = "critique"
    }

    /**
     * Represents the result of a parallel execution in a flow processing context.
     *
     * This data type is used to encapsulate the outcome of parallel operations, including
     * the name of the operation, the input data type, and the resulting output data type.
     *
     * @property name The name of the parallel execution operation that produced this result.
     * @property input The input data type provided to the parallel execution operation.
     * @property output The output data type produced by the parallel execution operation.
     */
    @Serializable
    public data class ParallelExecutionResult(
        val name: String,
        val input: FlowDataType,
        val output: FlowDataType,
    ) : FlowDataType {
        override val type: String = "parallel_result"
    }

//endregion Entities

//region Arrays

    /**
     * Represents an array of integer values for the flow agent data type.
     */
    @Serializable
    public data class FlowArrayInteger(public val data: Array<Int>) : FlowDataType {

        override val type: String = "array_int"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            return data.contentEquals((other as FlowArrayInteger).data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    /**
     * Represents an array of double floating point values for the flow agent data type.
     */
    @Serializable
    public data class FlowArrayDouble(public val data: Array<Double>) : FlowDataType {

        override val type: String = "array_double"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            return data.contentEquals((other as FlowArrayDouble).data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    /**
     * Represents an array of string values for the flow agent data type.
     */
    @Serializable
    public data class FlowArrayString(public val data: Array<String>) : FlowDataType {

        override val type: String = "array_string"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            return data.contentEquals((other as FlowArrayString).data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    /**
     * Represents an array of boolean values for the flow agent data type.
     */
    @Serializable
    public data class FlowArrayBoolean(public val data: Array<Boolean>) : FlowDataType {

        override val type: String = "array_boolean"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            return data.contentEquals((other as FlowArrayBoolean).data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }
}
