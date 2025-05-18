package ai.koog.prompt.structure

/**
 * Represents a metadata definition for annotated descriptions of a class and its fields.
 * This interface facilitates managing descriptions for classes and their corresponding fields.
 */
public interface DescriptionMetadata {
    /**
     * Represents the name of a class associated with a description or metadata entry.
     *
     * This property is intended to uniquely identify a class in a descriptive metadata context.
     * It can be used to associate additional information, such as a description or field details,
     * with the class it represents.
     */
    public val className: String
    /**
     * Represents a detailed description of a class provided in a metadata context.
     *
     * This property may contain additional information or insights related to the class it belongs to,
     * offering explanations, context, or clarifications about its purpose or behavior.
     *
     * Can be `null` if no description is provided or applicable.
     */
    public val classDescription: String?
    /**
     * A map containing metadata descriptions for fields associated with a specific class.
     *
     * The keys in the map represent the names of fields, and the corresponding values provide human-readable
     * descriptions or details about each field. This is commonly used for dynamically managing and accessing
     * metadata about object fields.
     */
    public val fieldDescriptions: Map<String, String>

    /**
     * Aggregates all the available descriptions into a single map. This includes field descriptions
     * and, if available, the class description associated with the class name.
     *
     * @return A map where the keys are field or class names and the values are their corresponding descriptions.
     */
    public fun allDescriptions(): Map<String, String> = buildMap {
        putAll(fieldDescriptions)
        classDescription?.let { put(className, it) }
    }
}