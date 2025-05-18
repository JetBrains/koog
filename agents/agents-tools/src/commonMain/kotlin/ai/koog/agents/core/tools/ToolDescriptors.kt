package ai.koog.agents.core.tools


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
public data class ToolDescriptor(
    val name: String,
    val description: String,
    val requiredParameters: List<ToolParameterDescriptor> = emptyList(),
    val optionalParameters: List<ToolParameterDescriptor> = emptyList(),
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
 */
public data class ToolParameterDescriptor(
    val name: String, val description: String, val type: ToolParameterType
)

/**
 * Sealed class representing different types of tool parameters.
 *
 * Each subclass of ToolParameterType denotes a specific data type that a tool parameter can have.
 *
 * @param T The type of data that the tool parameter represents.
 * @property name The name associated with the type of tool parameter.
 */
public sealed class ToolParameterType(public val name: kotlin.String) {

    /**
     * Represents a string type parameter.
     */
    public data object String : ToolParameterType("STRING")

    /**
     * Represents an integer type parameter.
     */
    public data object Integer : ToolParameterType("INT")

    /**
     * Represents a float type parameter.
     */
    public data object Float : ToolParameterType("FLOAT")

    /**
     * Represents a boolean type parameter.
     */
    public data object Boolean : ToolParameterType("BOOLEAN")

    /**
     * Represents an enum type parameter.
     *
     * @property entries The entries for the enumeration, allowing the parameter to be one of these values.
     */
    public data class Enum(
        val entries: Array<kotlin.String>,
    ) : ToolParameterType("ENUM")

    /**
     * Represents an array type parameter.
     *
     * @property itemsType The type definition for the items within the array.
     */
    public data class List(val itemsType: ToolParameterType) : ToolParameterType("ARRAY")

    /**
     * Represents an array type parameter.
     *
     * @property properties The properties of the object type.
     */
    public data class Object(val properties: kotlin.collections.List<ToolParameterDescriptor>) : ToolParameterType("OBJECT")

    public companion object {
        public fun Enum(entries: EnumEntries<*>): Enum = Enum(entries.map { it.name }.toTypedArray())
        public fun Enum(entries: Array<kotlin.Enum<*>>): Enum = Enum(entries.map { it.name }.toTypedArray())
    }
}

