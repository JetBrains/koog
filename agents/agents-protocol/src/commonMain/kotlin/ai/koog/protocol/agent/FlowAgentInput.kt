package ai.koog.protocol.agent

import ai.koog.protocol.parser.FlowAgentInputSerializer
import kotlinx.serialization.Serializable

/**
 *
 */
@Serializable(with = FlowAgentInputSerializer::class)
public sealed interface FlowAgentInput {

    /**
     *
     */
    public sealed interface Primitive : FlowAgentInput

    //region Entities

    /**
     *
     */
    @Serializable
    public data class InputInt(public val data: Int) : Primitive

    /**
     *
     */
    @Serializable
    public data class InputDouble(public val data: Double) : Primitive

    /**
     *
     */
    @Serializable
    public data class InputString(public val data: String) : Primitive

    /**
     *
     */
    @Serializable
    public data class InputBoolean(public val data: Boolean) : Primitive

    /**
     *
     */
    @Serializable
    public data class InputCritiqueResult(
        public val success: Boolean,
        public val feedback: String,
        public val input: FlowAgentInput
    ) : FlowAgentInput

    //endregion Entities

    //region Arrays

    /**
     *
     */
    @Serializable
    public data class InputArrayInt(public val data: Array<Int>) : FlowAgentInput {

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
    public data class InputArrayStrings(public val data: Array<String>) : FlowAgentInput {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            return data.contentEquals((other as InputArrayStrings).data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    /**
     *
     */
    @Serializable
    public data class InputArrayBooleans(public val data: Array<Boolean>) : FlowAgentInput {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            return data.contentEquals((other as InputArrayBooleans).data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    //endregion Arrays

    /**
     * Determines if the input is a primitive type.
     */
    public val isPrimitive: Boolean
        get() = this is Primitive
}
