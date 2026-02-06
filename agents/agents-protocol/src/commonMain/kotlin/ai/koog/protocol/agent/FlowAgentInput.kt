package ai.koog.protocol.agent

import ai.koog.protocol.parser.FlowAgentInputSerializer
import kotlinx.serialization.Serializable

/**
 *
 */
@Serializable(with = FlowAgentInputSerializer::class)
public interface FlowAgentInput {

    /**
     *
     */
    public val type: String

    /**
     * Determines if the input is a primitive type.
     */
    public val isPrimitive: Boolean
        get() = this is Primitive
}

/**
 *
 */
public interface Primitive : FlowAgentInput

//region Entities

/**
 *
 */
@Serializable
public data class InputInt(public val data: Int) : Primitive {
    override val type: String = "int"
}

/**
 *
 */
@Serializable
public data class InputDouble(public val data: Double) : Primitive {
    override val type: String = "double"
}

/**
 *
 */
@Serializable
public data class InputString(public val data: String) : Primitive {
    override val type: String = "string"
}

/**
 *
 */
@Serializable
public data class InputBoolean(public val data: Boolean) : Primitive {
    override val type: String = "boolean"
}

/**
 *
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
 *
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
 *
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
 *
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
 *
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

//endregion Arrays
