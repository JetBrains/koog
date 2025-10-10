package ai.koog.prompt.executor.cached

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ToolUtilsTest {

    @Test
    fun `test toJSONSchema with null type`() {
        val toolDescriptor = ToolDescriptor(
            name = "test_tool",
            description = "A test tool with null parameter",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "nullParam",
                    description = "A null parameter",
                    type = ToolParameterType.Null
                )
            )
        )

        val schema = toolDescriptor.toJSONSchema()

        assertNotNull(schema)
        assertEquals("test_tool", schema["title"]?.jsonPrimitive?.content)
        assertEquals("A test tool with null parameter", schema["description"]?.jsonPrimitive?.content)
        assertEquals("object", schema["type"]?.jsonPrimitive?.content)

        val properties = schema["properties"]?.jsonObject
        assertNotNull(properties)

        val nullParam = properties["nullParam"]?.jsonObject
        assertNotNull(nullParam)
        assertEquals("null", nullParam["type"]?.jsonPrimitive?.content)
        assertEquals("A null parameter", nullParam["description"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test toJSONSchema with anyOf type`() {
        val toolDescriptor = ToolDescriptor(
            name = "test_tool",
            description = "A test tool with anyOf parameter",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "anyOfParam",
                    description = "String or number parameter",
                    type = ToolParameterType.AnyOf(
                        types = arrayOf(
                            ToolParameterDescriptor(name = "", description = "String option", type = ToolParameterType.String),
                            ToolParameterDescriptor(name = "", description = "Number option", type = ToolParameterType.Float)
                        )
                    )
                )
            )
        )

        val schema = toolDescriptor.toJSONSchema()

        assertNotNull(schema)
        assertEquals("test_tool", schema["title"]?.jsonPrimitive?.content)
        assertEquals("A test tool with anyOf parameter", schema["description"]?.jsonPrimitive?.content)
        assertEquals("object", schema["type"]?.jsonPrimitive?.content)

        val properties = schema["properties"]?.jsonObject
        assertNotNull(properties)

        val anyOfParam = properties["anyOfParam"]?.jsonObject
        assertNotNull(anyOfParam)
        assertEquals("String or number parameter", anyOfParam["description"]?.jsonPrimitive?.content)

        val anyOf = anyOfParam["anyOf"]?.jsonArray
        assertNotNull(anyOf, "anyOf array should exist")
        assertEquals(2, anyOf.size, "anyOf should have 2 options")

        // Verify first option (String)
        val stringOption = anyOf[0].jsonObject
        assertEquals("string", stringOption["type"]?.jsonPrimitive?.content)
        assertEquals("String option", stringOption["description"]?.jsonPrimitive?.content)

        // Verify second option (Number)
        val numberOption = anyOf[1].jsonObject
        assertEquals("number", numberOption["type"]?.jsonPrimitive?.content)
        assertEquals("Number option", numberOption["description"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test toJSONSchema with complex anyOf including null`() {
        val toolDescriptor = ToolDescriptor(
            name = "test_tool",
            description = "A test tool with complex anyOf",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "complexParam",
                    description = "String, number, or null",
                    type = ToolParameterType.AnyOf(
                        types = arrayOf(
                            ToolParameterDescriptor(name = "", description = "", type = ToolParameterType.String),
                            ToolParameterDescriptor(name = "", description = "", type = ToolParameterType.Float),
                            ToolParameterDescriptor(name = "", description = "", type = ToolParameterType.Null)
                        )
                    )
                )
            )
        )

        val schema = toolDescriptor.toJSONSchema()

        val properties = schema["properties"]?.jsonObject
        assertNotNull(properties)

        val complexParam = properties["complexParam"]?.jsonObject
        assertNotNull(complexParam)

        val anyOf = complexParam["anyOf"]?.jsonArray
        assertNotNull(anyOf)
        assertEquals(3, anyOf.size, "anyOf should have 3 options")

        // Verify the types
        val types = anyOf.map { it.jsonObject["type"]?.jsonPrimitive?.content }
        assertTrue(types.contains("string"), "Should contain string type")
        assertTrue(types.contains("number"), "Should contain number type")
        assertTrue(types.contains("null"), "Should contain null type")
    }
}
