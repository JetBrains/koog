package ai.koog.prompt.executor.clients.bedrock.modelfamilies

import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.test.assertEquals

class BedrockNullableToolSerializationTest {
    companion object {
        private fun nullable(type: ToolParameterType, nullFirst: Boolean = true): ToolParameterType.AnyOf {
            val types = listOf(ToolParameterType.Null, type).let { if (nullFirst) it else it.reversed() }
            return ToolParameterType.AnyOf(types.map { ToolParameterDescriptor("", "", it) }.toTypedArray())
        }

        @JvmStatic
        fun nullableTypes(): Stream<Arguments> = Stream.of(
            Arguments.of(ToolParameterType.String, "string"),
            Arguments.of(ToolParameterType.Integer, "integer"),
            Arguments.of(ToolParameterType.Float, "number"),
            Arguments.of(ToolParameterType.Boolean, "boolean")
        )

        @JvmStatic
        fun objectConstraints(): Stream<Arguments> = Stream.of(
            Arguments.of(null, null, ""),
            Arguments.of(false, null, """, "additionalProperties": false"""),
            Arguments.of(true, null, """, "additionalProperties": true"""),
            Arguments.of(true, ToolParameterType.Integer, """, "additionalProperties": {"type": "integer"}"""),
            Arguments.of(
                true,
                nullable(ToolParameterType.String),
                """, "additionalProperties": {"type": ["string", "null"]}"""
            ),
            Arguments.of(false, ToolParameterType.Integer, """, "additionalProperties": false""")
        )
    }

    @ParameterizedTest
    @MethodSource("nullableTypes")
    fun testNullablePrimitiveRetainsType(type: ToolParameterType, expectedType: String) {
        for (nullFirst in listOf(true, false)) {
            assertSchema(
                nullable(type, nullFirst),
                """{"description":"Parameter", "type":["$expectedType", "null"]}"""
            )
        }
    }

    @Test
    fun testNullableEnumAllowsNullAndPreservesStringValues() {
        val enum = ToolParameterType.Enum(arrayOf("setup-scan", "1h", "123", "true", "null", "\"encoded\""))
        assertSchema(
            nullable(enum),
            """{"description":"Parameter", "type":["string","null"],
                "enum":["setup-scan","1h","123","true","null","encoded",null]}"""
        )
        assertSchema(
            enum,
            """{"description":"Parameter", "type":"string",
                "enum":["setup-scan","1h","123","true","null","encoded"]}"""
        )
    }

    @Test
    fun testNullableArrayAndNullableItemsAreIndependent() {
        assertSchema(
            nullable(ToolParameterType.List(ToolParameterType.String)),
            """{"description":"Parameter", "type":["array","null"], "items":{"type":"string"}}"""
        )
        assertSchema(
            ToolParameterType.List(nullable(ToolParameterType.String)),
            """{"description":"Parameter", "type":"array", "items":{"type":["string","null"]}}"""
        )
        assertSchema(
            nullable(ToolParameterType.List(nullable(ToolParameterType.String))),
            """{"description":"Parameter", "type":["array","null"], "items":{"type":["string","null"]}}"""
        )
    }

    @ParameterizedTest
    @MethodSource("objectConstraints")
    fun testNullableObjectRetainsConstraints(
        additionalProperties: Boolean?,
        additionalPropertiesType: ToolParameterType?,
        expectedConstraint: String
    ) {
        val objectType = ToolParameterType.Object(
            properties = listOf(
                ToolParameterDescriptor("requiredMode", "Required mode", nullable(ToolParameterType.String)),
                ToolParameterDescriptor("optionalMode", "Optional mode", nullable(ToolParameterType.String))
            ),
            requiredProperties = listOf("requiredMode"),
            additionalProperties = additionalProperties,
            additionalPropertiesType = additionalPropertiesType
        )
        assertSchema(
            nullable(objectType),
            """{"description":"Parameter", "type":["object","null"], "properties":{
                "requiredMode":{"description":"Required mode", "type":["string","null"]},
                "optionalMode":{"description":"Optional mode", "type":["string","null"]}
            }, "required":["requiredMode"] $expectedConstraint}"""
        )
    }

    @Test
    fun testObjectWithoutRequiredPropertiesKeepsThemOptional() {
        assertSchema(
            ToolParameterType.Object(emptyList()),
            """{"description":"Parameter", "type":"object", "properties":{}}"""
        )
    }

    @Test
    fun testGeneralAndNestedUnionsRetainAnyOf() {
        val union = ToolParameterType.AnyOf(
            arrayOf(
                ToolParameterDescriptor("", "String option", ToolParameterType.String),
                ToolParameterDescriptor("", "Number option", ToolParameterType.Float)
            )
        )
        assertSchema(
            union,
            """{"description":"Parameter", "anyOf":[
                {"description":"String option","type":"string"},
                {"description":"Number option","type":"number"}
            ]}"""
        )
        assertSchema(
            nullable(union),
            """{"description":"Parameter", "anyOf":[
                {"description":"","type":"null"},
                {"description":"","anyOf":[
                    {"description":"String option","type":"string"},
                    {"description":"Number option","type":"number"}
                ]}
            ]}"""
        )
    }

    private fun assertSchema(type: ToolParameterType, expected: String) {
        assertEquals(
            Json.parseToJsonElement(expected) as JsonObject,
            BedrockToolSerialization.buildToolParameterSchema(ToolParameterDescriptor("value", "Parameter", type))
        )
    }
}
