package ai.koog.agents.mcp

import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class McpOneOfToolDescriptorParserTest {
    @Test
    fun testObjectOrEncodedStringRequiredAndOptional() {
        val schema = """
            {
              "description": "JSON object or JSON-encoded string",
              "oneOf": [
                {"type": "object", "description": "Object", "properties": {
                  "message": {"type": "string"}
                }, "required": ["message"], "additionalProperties": false},
                {"type": "string", "description": "Encoded string"}
              ]
            }
        """.trimIndent()

        for (required in listOf(true, false)) {
            val descriptor = parse(schema, required)
            val parameter = if (required) {
                assertTrue(descriptor.optionalParameters.isEmpty())
                descriptor.requiredParameters.single()
            } else {
                assertTrue(descriptor.requiredParameters.isEmpty())
                descriptor.optionalParameters.single()
            }
            assertEquals("body", parameter.name)
            assertEquals("JSON object or JSON-encoded string", parameter.description)
            val union = assertIs<ToolParameterType.AnyOf>(parameter.type)
            assertEquals(
                listOf("Object", "Encoded string"),
                union.types.map { it.description }
            )
            assertEquals(
                ToolParameterType.Object(
                    properties = listOf(ToolParameterDescriptor("message", "", ToolParameterType.String)),
                    requiredProperties = listOf("message"),
                    additionalProperties = false
                ),
                union.types[0].type
            )
            assertEquals(ToolParameterType.String, union.types[1].type)
        }
    }

    @Test
    fun testNestedNullableUnionInArrayAndObject() {
        val descriptor = parse(
            """
                {"type": "object", "properties": {
                  "values": {"type": "array", "items": {
                    "oneOf": [
                      {"anyOf": [{"type": "string"}, {"type": "null"}]},
                      {"oneOf": [{"type": "integer"}, {"type": "boolean"}]}
                    ]
                  }}
                }, "required": ["values"]}
            """.trimIndent()
        )
        val objectType = assertIs<ToolParameterType.Object>(descriptor.requiredParameters.single().type)
        assertEquals(listOf("values"), objectType.requiredProperties)
        val listType = assertIs<ToolParameterType.List>(objectType.properties.single().type)
        val union = assertIs<ToolParameterType.AnyOf>(listType.itemsType)
        assertEquals(
            listOf(ToolParameterType.String, ToolParameterType.Null),
            assertIs<ToolParameterType.AnyOf>(union.types[0].type).types.map { it.type }
        )
        assertEquals(
            listOf(ToolParameterType.Integer, ToolParameterType.Boolean),
            assertIs<ToolParameterType.AnyOf>(union.types[1].type).types.map { it.type }
        )
    }

    @Test
    fun testNullableTypeArrayAlternative() {
        val union = assertIs<ToolParameterType.AnyOf>(
            parse("""{"oneOf": [{"type": ["string", "null"]}, {"type": "object"}]}""")
                .requiredParameters.single().type
        )
        assertEquals(
            listOf(ToolParameterType.Null, ToolParameterType.String),
            assertIs<ToolParameterType.AnyOf>(union.types[0].type).types.map { it.type }
        )
        assertIs<ToolParameterType.Object>(union.types[1].type)
    }

    @Test
    fun testReferencedAlternatives() {
        val descriptor = parse(
            """{"oneOf": [{"${'$'}ref": "#/${'$'}defs/Body"}, {"type": "string"}]}""",
            defs = Json.parseToJsonElement("""{"Body": {"type": "object"}}""").jsonObject
        )
        val union = assertIs<ToolParameterType.AnyOf>(descriptor.requiredParameters.single().type)
        assertIs<ToolParameterType.Object>(union.types[0].type)
        assertEquals(ToolParameterType.String, union.types[1].type)
    }

    @Test
    fun testOverlappingAlternativesAreRejected() {
        for (schema in listOf(
            """{"oneOf": [{"type": "string"}, {"type": "string"}]}""",
            """{"oneOf": [{"type": "integer"}, {"type": "number"}]}""",
            """{"oneOf": [{"type": "object"}, {"type": "object"}]}""",
            """{"oneOf": [{"type": ["string", "null"]}, {"type": "null"}]}""",
            """{"oneOf": [{"anyOf": [{"type": "string"}, {"type": "number"}]}, {"type": "integer"}]}""",
            """{"oneOf": [{"enum": ["a"]}, {"type": "number"}]}"""
        )) {
            val error = assertFailsWith<IllegalArgumentException> { parse(schema) }
            assertTrue(error.message.orEmpty().contains("disjoint"), error.message)
        }
    }

    @Test
    fun testSiblingConstraintsAreRejectedRatherThanIgnored() {
        for (sibling in listOf(
            """"type": "string"""",
            """"anyOf": [{"type": "string"}]""",
            """"allOf": [{"type": "string"}]""",
            """"enum": ["a"]""",
            """"const": "a"""",
            """"minLength": 1""",
            """"${'$'}ref": "#/${'$'}defs/Body""""
        )) {
            val error = assertFailsWith<IllegalArgumentException> {
                parse("""{$sibling, "oneOf": [{"type": "object"}, {"type": "string"}]}""")
            }
            assertTrue(error.message.orEmpty().contains("sibling"), error.message)
        }
    }

    @Test
    fun testAllDisjointJsonTypesAndAnnotations() {
        val union = assertIs<ToolParameterType.AnyOf>(
            parse(
                """
                    {
                      "title": "Body", "${'$'}comment": "Generic fixture", "default": null,
                      "examples": [true], "deprecated": false, "readOnly": false, "writeOnly": true,
                      "oneOf": [
                        {"type": "string"}, {"type": "null"}, {"type": "number"}, {"type": "boolean"},
                        {"type": "array", "items": {"type": "string"}}, {"type": "object"}
                      ]
                    }
                """.trimIndent()
            ).requiredParameters.single().type
        )
        assertEquals(
            listOf(
                ToolParameterType.String,
                ToolParameterType.Null,
                ToolParameterType.Float,
                ToolParameterType.Boolean,
                ToolParameterType.List(ToolParameterType.String),
                ToolParameterType.Object(emptyList())
            ),
            union.types.map { it.type }
        )
    }

    @Test
    fun testSingleAlternativeAndAdditionalPropertiesUnion() {
        val objectType = assertIs<ToolParameterType.Object>(
            parse(
                """
                    {"oneOf": [{
                      "type": "object", "additionalProperties": {
                        "oneOf": [{"type": "string"}, {"type": "boolean"}]
                      }
                    }]}
                """.trimIndent()
            ).requiredParameters.single().type.let {
                assertIs<ToolParameterType.AnyOf>(it).types.single().type
            }
        )
        assertEquals(true, objectType.additionalProperties)
        assertEquals(
            listOf(ToolParameterType.String, ToolParameterType.Boolean),
            assertIs<ToolParameterType.AnyOf>(objectType.additionalPropertiesType).types.map { it.type }
        )
    }

    @Test
    fun testMalformedAlternativesAreRejected() {
        for (schema in listOf(
            """{"oneOf": []}""",
            """{"oneOf": {}}""",
            """{"oneOf": [true]}""",
            """{"oneOf": [{}]}""",
            """{"oneOf": [{"type": "unsupported"}]}""",
            """{"oneOf": [{"type": []}]}""",
            """{"oneOf": [{"type": [null]}]}""",
            """{"oneOf": [{"type": ["null", "null"]}]}""",
            """{"oneOf": [{"anyOf": []}]}""",
            """{"oneOf": [{"type": ["string", "boolean"]}, {"type": "object"}]}"""
        )) {
            assertFailsWith<IllegalArgumentException> { parse(schema) }
        }
    }

    @Test
    fun testAnyOfStillAllowsOverlappingAlternatives() {
        val union = assertIs<ToolParameterType.AnyOf>(
            parse("""{"anyOf": [{"type": "integer"}, {"type": "number"}]}""")
                .requiredParameters.single().type
        )
        assertEquals(listOf(ToolParameterType.Integer, ToolParameterType.Float), union.types.map { it.type })
    }

    @Test
    fun testUnionReferenceCycleRespectsRecursionLimit() {
        for (keyword in listOf("oneOf", "anyOf")) {
            val error = assertFailsWith<IllegalArgumentException> {
                parse(
                    """{"${'$'}ref": "#/${'$'}defs/Body"}""",
                    defs = Json.parseToJsonElement(
                        """{"Body": {"$keyword": [{"${'$'}ref": "#/${'$'}defs/Body"}, {"type": "string"}]}}"""
                    ).jsonObject
                )
            }
            assertTrue(error.message.orEmpty().contains("Maximum recursion depth"), error.message)
        }
    }

    private fun parse(schema: String, required: Boolean = true, defs: JsonObject? = null) =
        DefaultMcpToolDescriptorParser.parse(
            Tool(
                name = "submit_body",
                description = "Submit a generic body",
                inputSchema = ToolSchema(
                    properties = JsonObject(mapOf("body" to Json.parseToJsonElement(schema))),
                    required = if (required) listOf("body") else emptyList(),
                    defs = defs
                )
            )
        )
}
