package ai.grazie.code.agents.core.tools

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.enums.EnumEntries

/**
 * Represents a descriptor for a tool that contains information about the tool's name, description, required parameters,
 * and optional parameters.
 *
 * This class is annotated with @Serializable to support serialization/deserialization using kotlinx.serialization.
 *
 * @property name The name of the tool.
 * @property description The description of the tool.
 * @property requiredParameters A list of ToolParameterDescriptor representing the required parameters for the tool.
 * @property optionalParameters A list of ToolParameterDescriptor representing the optional parameters for the tool.
 */
data class ToolDescriptor(
    val name: String,
    val description: String,
    val requiredParameters: List<ToolParameterDescriptor<*>> = emptyList(),
    val optionalParameters: List<ToolParameterDescriptor<*>> = emptyList(),
)

/**
 * Represents a descriptor for a tool parameter.
 * A tool parameter descriptor contains information about a specific tool parameter, such as its name, description,
 * data type, and default value.
 *
 * Note that parameters are deserialized using CamelCase to snake_case conversion, so use snake_case names
 *
 * This class is annotated with @Serializable to support serialization/deserialization using kotlinx.serialization.
 *
 * @property name The name of the tool parameter in snake_case
 * @property description The description of the tool parameter.
 * @property type The data type of the tool parameter.
 * @property defaultValue The default value of the tool parameter (nullable).
 */
data class ToolParameterDescriptor<T>(
    val name: String,
    val description: String,
    val type: ToolParameterType<T>,
    val defaultValue: T? = null
)

/**
 * Sealed class representing different types of tool parameters.
 *
 * Each subclass of ToolParameterType denotes a specific data type that a tool parameter can have.
 *
 * @param T The type of data that the tool parameter represents.
 * @property name The name associated with the type of tool parameter.
 */
sealed class ToolParameterType<T>(val name: kotlin.String) {
    /**
     * Describes how to serialize default value for a given type
     */
    internal abstract val serializer: KSerializer<T>

    /**
     * Represents a string type parameter.
     */
    data object String : ToolParameterType<kotlin.String>("STRING") {
        override val serializer = kotlin.String.serializer()
    }

    /**
     * Represents an integer type parameter.
     */
    data object Integer : ToolParameterType<Int>("INT") {
        override val serializer = Int.serializer()
    }

    /**
     * Represents a float type parameter.
     */
    data object Float : ToolParameterType<kotlin.Float>("FLOAT") {
        override val serializer = kotlin.Float.serializer()
    }

    /**
     * Represents a boolean type parameter.
     */
    data object Boolean : ToolParameterType<kotlin.Boolean>("BOOLEAN") {
        override val serializer = kotlin.Boolean.serializer()
    }

    /**
     * Represents an enum type parameter.
     *
     * @param E The specific enumeration type handled by this parameter type.
     * @property entries The entries for the enumeration, allowing the parameter to be one of these values.
     */
    data class Enum<E : kotlin.Enum<E>>(
        val entries: EnumEntries<E>,
        override val serializer: KSerializer<E>
    ) : ToolParameterType<E>("ENUM")

    /**
     * Represents an array type parameter.
     *
     * @param T The type of each item within the array.
     * @property itemsType The type definition for the items within the array.
     */
    data class List<T>(val itemsType: ToolParameterType<T>) : ToolParameterType<kotlin.collections.List<T>>("ARRAY") {
        override val serializer = ListSerializer(itemsType.serializer)
    }
}
