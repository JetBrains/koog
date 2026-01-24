package ai.koog.prompt.executor.ollama.client

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.executor.ollama.tools.json.OllamaToolDescriptorSchemaGenerator
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class OllamaToolDescriptorConverterAdditionalTest {

    private val json = Json { prettyPrint = true }

    @Test
    fun `primitives mapping and required list`() {
        val descriptor = ToolDescriptor(
            name = "primitive_tool",
            description = "Tool with primitive params",
            requiredParameters = listOf(
                ToolParameterDescriptor("name", "User name", ToolParameterType.String),
                ToolParameterDescriptor("age", "User age", ToolParameterType.Integer),
                ToolParameterDescriptor("active", "Is active", ToolParameterType.Boolean),
            ),
            optionalParameters = listOf(
                ToolParameterDescriptor("rating", "Optional rating", ToolParameterType.Float),
            )
        )

        val actual = json.encodeToString(OllamaToolDescriptorSchemaGenerator().generate(descriptor))

        val expected = """
        {
            "title": "primitive_tool",
            "description": "Tool with primitive params",
            "type": "object",
            "properties": {
                "name": {
                    "type": "string",
                    "description": "User name"
                },
                "age": {
                    "type": "integer",
                    "description": "User age"
                },
                "active": {
                    "type": "boolean",
                    "description": "Is active"
                },
                "rating": {
                    "type": "number",
                    "description": "Optional rating"
                }
            },
            "required": [
                "name",
                "age",
                "active"
            ]
        }
        """.trimIndent()

        assertEquals(expected, actual)
    }

    @Test
    fun `enum mapping`() {
        val descriptor = ToolDescriptor(
            name = "enum_tool",
            description = "Tool with enum",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "color",
                    description = "Pick a color",
                    type = ToolParameterType.Enum(arrayOf("RED", "GREEN", "BLUE"))
                )
            )
        )

        val actual = json.encodeToString(OllamaToolDescriptorSchemaGenerator().generate(descriptor))

        val expected = """
        {
            "title": "enum_tool",
            "description": "Tool with enum",
            "type": "object",
            "properties": {
                "color": {
                    "type": "string",
                    "enum": [
                        "RED",
                        "GREEN",
                        "BLUE"
                    ],
                    "description": "Pick a color"
                }
            },
            "required": [
                "color"
            ]
        }
        """.trimIndent()

        assertEquals(expected, actual)
    }

    @Test
    fun `list with item schema`() {
        val descriptor = ToolDescriptor(
            name = "list_tool",
            description = "Tool with list",
            requiredParameters = emptyList(),
            optionalParameters = listOf(
                ToolParameterDescriptor(
                    name = "tags",
                    description = "List of tags",
                    type = ToolParameterType.List(ToolParameterType.String)
                )
            )
        )

        val actual = json.encodeToString(OllamaToolDescriptorSchemaGenerator().generate(descriptor))

        val expected = """
        {
            "title": "list_tool",
            "description": "Tool with list",
            "type": "object",
            "properties": {
                "tags": {
                    "type": "array",
                    "items": {
                        "type": "string"
                    },
                    "description": "List of tags"
                }
            },
            "required": []
        }
        """.trimIndent()

        assertEquals(expected, actual)
    }
}
