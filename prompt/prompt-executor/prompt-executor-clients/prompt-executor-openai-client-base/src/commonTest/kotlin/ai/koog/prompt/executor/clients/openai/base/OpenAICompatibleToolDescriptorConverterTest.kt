package ai.koog.prompt.executor.clients.openai.base

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenAICompatibleToolDescriptorConverterTest {

    private val json = Json { prettyPrint = true }

    @Test
    fun primitives_mapping_and_required_list() {
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

        val actual = json.encodeToString(OpenAICompatibleToolDescriptorSchemaGenerator().generate(descriptor))

        val expected = """
        {
            "type": "object",
            "properties": {
                "name": {
                    "description": "User name",
                    "type": "string"
                },
                "age": {
                    "description": "User age",
                    "type": "integer"
                },
                "active": {
                    "description": "Is active",
                    "type": "boolean"
                },
                "rating": {
                    "description": "Optional rating",
                    "type": "number"
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
    fun enum_mapping() {
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

        val actual = json.encodeToString(OpenAICompatibleToolDescriptorSchemaGenerator().generate(descriptor))

        val expected = """
        {
            "type": "object",
            "properties": {
                "color": {
                    "description": "Pick a color",
                    "type": "string",
                    "enum": [
                        "RED",
                        "GREEN",
                        "BLUE"
                    ]
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
    fun list_with_item_schema() {
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

        val actual = json.encodeToString(OpenAICompatibleToolDescriptorSchemaGenerator().generate(descriptor))

        val expected = """
        {
            "type": "object",
            "properties": {
                "tags": {
                    "description": "List of tags",
                    "type": "array",
                    "items": {
                        "type": "string"
                    }
                }
            },
            "required": []
        }
        """.trimIndent()

        assertEquals(expected, actual)
    }

    @Test
    fun object_with_properties_and_additionalProperties() {
        val descriptor = ToolDescriptor(
            name = "object_tool",
            description = "Tool with object param",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "config",
                    description = "Configuration",
                    type = ToolParameterType.Object(
                        properties = listOf(
                            ToolParameterDescriptor("enabled", "Enable flag", ToolParameterType.Boolean),
                            ToolParameterDescriptor("threshold", "Threshold value", ToolParameterType.Float),
                        ),
                        requiredProperties = listOf("enabled"),
                        additionalProperties = true,
                    )
                )
            )
        )

        val actual = json.encodeToString(OpenAICompatibleToolDescriptorSchemaGenerator().generate(descriptor))

        // Note: OpenAI converter does not emit "required" inside nested object; it preserves order: type, additionalProperties, properties
        val expected = """
        {
            "type": "object",
            "properties": {
                "config": {
                    "description": "Configuration",
                    "type": "object",
                    "additionalProperties": true,
                    "properties": {
                        "enabled": {
                            "type": "boolean",
                            "description": "Enable flag"
                        },
                        "threshold": {
                            "type": "number",
                            "description": "Threshold value"
                        }
                    }
                }
            },
            "required": [
                "config"
            ]
        }
        """.trimIndent()

        assertEquals(expected, actual)
    }

    @Test
    fun anyOf_mapping() {
        val descriptor = ToolDescriptor(
            name = "anyof_tool",
            description = "Tool with anyOf param",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "value",
                    description = "A value that can be string or number",
                    type = ToolParameterType.AnyOf(
                        types = arrayOf(
                            ToolParameterDescriptor(name = "", description = "String option", type = ToolParameterType.String),
                            ToolParameterDescriptor(name = "", description = "Number option", type = ToolParameterType.Float)
                        )
                    )
                )
            )
        )

        val actual = json.encodeToString(OpenAICompatibleToolDescriptorSchemaGenerator().generate(descriptor))

        val expected = """
        {
            "type": "object",
            "properties": {
                "value": {
                    "description": "A value that can be string or number",
                    "anyOf": [
                        {
                            "description": "String option",
                            "type": "string"
                        },
                        {
                            "description": "Number option",
                            "type": "number"
                        }
                    ]
                }
            },
            "required": [
                "value"
            ]
        }
        """.trimIndent()

        assertEquals(expected, actual)
    }
}
