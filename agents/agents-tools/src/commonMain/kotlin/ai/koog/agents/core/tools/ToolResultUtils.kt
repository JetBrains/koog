package ai.koog.agents.core.tools

import ai.koog.agents.core.tools.ToolResult.AsTextSerializer
import ai.koog.agents.core.tools.ToolResult.TextSerializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * Utility class for handling results related to tools and their serialization mechanisms.
 */
public class ToolResultUtils {
    /**
     * Companion object containing utility methods for handling specialized serialization mechanisms.
     */
    public companion object {
        /**
         * Creates an instance of `AsTextSerializer` for the specified type `T` that extends `TextSerializable`.
         * The resulting serializer facilitates the conversion of objects implementing the `TextSerializable` interface
         * into a textual format, utilizing their predefined serialization logic.
         *
         * @return An `AsTextSerializer` instance for the type `T`, providing serialization and deserialization support.
         */
        public inline fun <reified T : TextSerializable> toTextSerializer(): KSerializer<T> =
            AsTextSerializer(serializer())
    }
}
