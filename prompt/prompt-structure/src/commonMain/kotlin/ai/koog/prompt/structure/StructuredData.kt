package ai.koog.prompt.structure

import ai.koog.prompt.params.LLMParams

/**
 * Represents a generic structure for handling data with a specific schema.
 *
 * This abstract class provides a base for defining structured data entities
 * that are identified by a unique `id`, involve a list of examples of a
 * specific type `TStruct`, and adhere to a given `schema`.
 *
 * @param TStruct The type of the structured data that this class handles.
 * @property id A unique identifier for the structured data entity.
 * @property schema The schema that defines the structure and validation rules for the data.
 * @property examples A collection of example instances of the structured data type.
 */
public abstract class StructuredData<TStruct, TSchema : LLMParams.Schema>(
    public val id: String,
    public val schema: TSchema,
    public val examples: List<TStruct>,
) : StructuredDataDefinition {
    /**
     * Parses the given text into a structured data representation.
     *
     * @param text The raw input text to be parsed into a structured format.
     * @return The structured data representation of type TStruct derived from the input text.
     */
    public abstract fun parse(text: String): TStruct

    /**
     * Formats the given structured data into a human-readable string representation.
     *
     * @param value The structured data of type `TStruct` to be formatted.
     * @return A string representing the pretty-printed version of the input structured data.
     */
    public abstract fun pretty(value: TStruct): String
}
