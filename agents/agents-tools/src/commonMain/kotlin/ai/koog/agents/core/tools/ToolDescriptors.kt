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
    val name: String,
    val description: String,
    val type: ToolParameterType
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
    public data class Enum(val entries: Array<kotlin.String>) : ToolParameterType("ENUM") {
        override fun equals(other: Any?): kotlin.Boolean = other is Enum && this.entries.contentEquals(other.entries)
    }

    /**
     * Represents an array type parameter.
     *
     * @property itemsType The type definition for the items within the array.
     */
    public data class List(val itemsType: ToolParameterType) : ToolParameterType("ARRAY")

    /**
     * Represents an object-type parameter used in the tool's parameter schema.
     * This class is a specialization of [ToolParameterType] with the parameter type set to "OBJECT".
     *
     * @property properties A list of descriptors for the properties of the object.
     * Each descriptor defines the name, description, and type for an individual property.
     * @property requiredProperties A list of property names that are required.
     * Defaults to an empty list if no properties are considered mandatory.
     * @property additionalProperties Indicates whether additional properties are allowed in the object.
     * If `null`, the allowance for additional properties is unspecified.
     * @property additionalPropertiesType The type of any additional properties allowed, if additional
     * properties are permitted. If not applicable, defaults to `null`.
     */
    public data class Object(
        val properties: kotlin.collections.List<ToolParameterDescriptor>,
        val requiredProperties: kotlin.collections.List<kotlin.String> = listOf(),
        val additionalProperties: kotlin.Boolean? = null,
        val additionalPropertiesType: ToolParameterType? = null,
    ) : ToolParameterType("OBJECT")

    /**
     * Companion object for the enclosing class. Provides utility functions for creating instances
     * of the `Enum` type parameter from different kinds of inputs.
     */
    public companion object {
        /**
         * Creates an Enum instance using the provided EnumEntries.
         *
         * @param entries The EnumEntries to initialize the Enum instance. The names of the entries will be used.
         * @return A new Enum instance populated with the names of the provided entries.
         */
        public fun Enum(entries: EnumEntries<*>): Enum = Enum(entries.map { it.name }.toTypedArray())

        /**
         * Constructs an Enum parameter type from an array of Enum entries.
         *
         * @param entries An array of Enum entries from which the parameter type is constructed.
         *                Each entry's name is extracted to define the enumeration values.
         * @return An instance of Enum containing the names of the provided Enum entries.
         */
        public fun Enum(entries: Array<kotlin.Enum<*>>): Enum = Enum(entries.map { it.name }.toTypedArray())
    }
}
