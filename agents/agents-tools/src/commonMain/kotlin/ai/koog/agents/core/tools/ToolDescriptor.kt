package ai.koog.agents.core.tools

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
public open class ToolDescriptor(
    public val name: String,
    public val description: String,
    public val requiredParameters: List<ToolParameterDescriptor> = emptyList(),
    public val optionalParameters: List<ToolParameterDescriptor> = emptyList(),
) {
    /**
     * Creates a copy of the current ToolDescriptor with the option to modify specific attributes.
     *
     * @param name The name of the tool. Defaults to the current tool's name if not provided.
     * @param description The description of the tool. Defaults to the current tool's description if not provided.
     * @param requiredParameters A list of ToolParameterDescriptor representing the required parameters for the tool.
     * Defaults to the current required parameters if not provided.
     * @param optionalParameters A list of ToolParameterDescriptor representing the optional parameters for the tool.
     * Defaults to the current optional parameters if not provided.
     * @return A new instance of ToolDescriptor with the updated attributes.
     */
    public fun copy(
        name: String = this.name,
        description: String = this.description,
        requiredParameters: List<ToolParameterDescriptor> = this.requiredParameters.toList(),
        optionalParameters: List<ToolParameterDescriptor> = this.optionalParameters.toList(),
    ): ToolDescriptor {
        return ToolDescriptor(
            name = name,
            description = description,
            requiredParameters = requiredParameters,
            optionalParameters = optionalParameters,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ToolDescriptor) return false

        if (name != other.name) return false
        if (description != other.description) return false
        if (requiredParameters != other.requiredParameters) return false
        if (optionalParameters != other.optionalParameters) return false

        return true
    }

    override fun toString(): String {
        return "ToolDescriptor(name=$name, description=$description, requiredParameters=$requiredParameters, optionalParameters=$optionalParameters)"
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + requiredParameters.hashCode()
        result = 31 * result + optionalParameters.hashCode()
        return result
    }
}
