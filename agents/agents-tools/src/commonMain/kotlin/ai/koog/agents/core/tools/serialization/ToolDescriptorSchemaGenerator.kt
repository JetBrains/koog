package ai.koog.agents.core.tools.serialization

import ai.koog.agents.core.tools.ToolDescriptor
import kotlinx.serialization.json.JsonObject

/**
 * Interface for converting a list of ToolDescriptor objects to a JSON schema representation.
 */
public interface ToolDescriptorSchemaGenerator {
    /**
     * Converts a list of ToolDescriptor objects into a JSON object representation.
     *
     * @param toolDescriptor The ToolDescriptor to convert.
     * @return A JsonObject containing the JSON representation of the provided list of ToolDescriptor objects.
     */
    public fun generate(toolDescriptor: ToolDescriptor): JsonObject
}
