package ai.koog.agents.core.tools.schema

import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.serialization.typeToken
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Platform-independent (serialization based) schema generation for kotlinx.serialization.json built-in types.
 */
@OptIn(InternalAgentToolsApi::class)
class JsonBuiltinTypesSerializableSchemaTest {
    @Serializable
    data class WrapArgs(
        val name: String,
        val arguments: JsonObject,
        val batch: List<JsonObject> = emptyList(),
    )

    @Serializable
    data class AnyArgs(val value: JsonElement, val items: JsonArray)

    @Serializable
    data class PlainArgs(val name: String, val tags: List<String>)

    private val freeFormObject = ToolParameterType.Object(
        properties = emptyList(),
        requiredProperties = emptyList(),
        additionalProperties = true,
        additionalPropertiesType = null,
    )

    @Test
    fun testJsonObjectArgsAreFreeFormObjects() {
        val descriptor = getToolDescriptor(typeToken<WrapArgs>(), "wrap")

        assertEquals(
            listOf(
                ToolParameterDescriptor("name", "", ToolParameterType.String),
                ToolParameterDescriptor("arguments", "", freeFormObject),
            ),
            descriptor.requiredParameters,
        )
        assertEquals(
            listOf(ToolParameterDescriptor("batch", "", ToolParameterType.List(freeFormObject))),
            descriptor.optionalParameters,
        )
    }

    @Test
    fun testAnyJsonValueArgsFailWithClearMessage() {
        val exception = assertFailsWith<IllegalArgumentException> {
            getToolDescriptor(typeToken<AnyArgs>(), "any")
        }

        assertTrue("any JSON value" in exception.message.orEmpty(), exception.message)
    }

    @Test
    fun testDescriptorWithoutJsonTypesIsReturnedAsIs() {
        val descriptor = serializer<PlainArgs>().descriptor

        assertSame(descriptor, descriptor.withSchemaFriendlyJsonTypes())
    }
}
