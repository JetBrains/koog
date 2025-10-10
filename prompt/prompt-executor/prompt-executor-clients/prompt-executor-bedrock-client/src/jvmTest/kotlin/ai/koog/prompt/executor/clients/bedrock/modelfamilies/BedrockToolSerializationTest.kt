package ai.koog.prompt.executor.clients.bedrock.modelfamilies

import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BedrockToolSerializationTest {
    companion object {
        @JvmStatic
        fun toolParameterTestCases(): Stream<Arguments> {
            val stringParamDesc = "Search query"
            val intParamDesc = "Number of results"
            val floatParamDesc = "Temperature value"
            val boolParamDesc = "Feature toggle"
            val enumValues = listOf("json", "xml", "text")
            val listParamDesc = "List of users"
            val listIntParamDesc = "List of IDs"
            val objectParamDesc = "User information"
            val namePropertyName = "name"
            val namePropertyDesc = "User name"
            val agePropertyName = "age"
            val agePropertyDesc = "User age"
            val streetPropertyName = "street"
            val streetPropertyDesc = "Street address"
            val cityPropertyName = "city"
            val cityPropertyDesc = "City name"
            val addressPropertyName = "address"
            val addressPropertyDesc = "User address"

            return Stream.of(
                // String
                Arguments.of(
                    ToolParameterDescriptor(
                        name = "query",
                        description = stringParamDesc,
                        type = ToolParameterType.String
                    ),
                    mapOf(
                        "description" to stringParamDesc,
                        "type" to "string"
                    )
                ),

                // Integer
                Arguments.of(
                    ToolParameterDescriptor(
                        name = "count",
                        description = intParamDesc,
                        type = ToolParameterType.Integer
                    ),
                    mapOf(
                        "description" to intParamDesc,
                        "type" to "integer"
                    )
                ),

                // Float
                Arguments.of(
                    ToolParameterDescriptor(
                        name = "temperature",
                        description = floatParamDesc,
                        type = ToolParameterType.Float
                    ),
                    mapOf(
                        "description" to floatParamDesc,
                        "type" to "number"
                    )
                ),

                // Boolean
                Arguments.of(
                    ToolParameterDescriptor(
                        name = "enabled",
                        description = boolParamDesc,
                        type = ToolParameterType.Boolean
                    ),
                    mapOf(
                        "description" to boolParamDesc,
                        "type" to "boolean"
                    )
                ),

                // Null
                Arguments.of(
                    ToolParameterDescriptor(
                        name = "nullValue",
                        description = "Null parameter",
                        type = ToolParameterType.Null
                    ),
                    mapOf(
                        "description" to "Null parameter",
                        "type" to "null"
                    )
                ),

                // Enum
                Arguments.of(
                    ToolParameterDescriptor(
                        name = "format",
                        description = "Output format",
                        type = ToolParameterType.Enum(enumValues.toTypedArray())
                    ),
                    mapOf(
                        "description" to "Output format",
                        "type" to "string",
                        "enum" to enumValues
                    )
                ),

                // List of String
                Arguments.of(
                    ToolParameterDescriptor(
                        name = "user",
                        description = listParamDesc,
                        type = ToolParameterType.List(ToolParameterType.String)
                    ),
                    mapOf(
                        "description" to listParamDesc,
                        "type" to "array",
                        "items" to mapOf("type" to "string")
                    )
                ),

                // List of Integer
                Arguments.of(
                    ToolParameterDescriptor(
                        name = "List",
                        description = listIntParamDesc,
                        type = ToolParameterType.List(ToolParameterType.Integer)
                    ),
                    mapOf(
                        "description" to listIntParamDesc,
                        "type" to "array",
                        "items" to mapOf("type" to "integer")
                    )
                ),

                // Object
                Arguments.of(
                    {
                        val objectType = ToolParameterType.Object(
                            properties = listOf(
                                ToolParameterDescriptor(namePropertyName, namePropertyDesc, ToolParameterType.String),
                                ToolParameterDescriptor(agePropertyName, agePropertyDesc, ToolParameterType.Integer)
                            )
                        )

                        ToolParameterDescriptor(
                            name = "user",
                            description = objectParamDesc,
                            type = objectType
                        )
                    }(),
                    mapOf(
                        "description" to objectParamDesc,
                        "type" to "object"
                    )
                ),

                // Nested Object
                Arguments.of(
                    {
                        val addressType = ToolParameterType.Object(
                            properties = listOf(
                                ToolParameterDescriptor(
                                    streetPropertyName,
                                    streetPropertyDesc,
                                    ToolParameterType.String
                                ),
                                ToolParameterDescriptor(cityPropertyName, cityPropertyDesc, ToolParameterType.String)
                            )
                        )

                        val userType = ToolParameterType.Object(
                            properties = listOf(
                                ToolParameterDescriptor(namePropertyName, namePropertyDesc, ToolParameterType.String),
                                ToolParameterDescriptor(addressPropertyName, addressPropertyDesc, addressType)
                            )
                        )

                        ToolParameterDescriptor(
                            name = "user",
                            description = objectParamDesc,
                            type = userType
                        )
                    }(),
                    mapOf(
                        "description" to objectParamDesc,
                        "type" to "object"
                    )
                ),

                // AnyOf (String or Number)
                Arguments.of(
                    ToolParameterDescriptor(
                        name = "anyOfValue",
                        description = "String or number value",
                        type = ToolParameterType.AnyOf(
                            types = arrayOf(
                                ToolParameterDescriptor(name = "", description = "String option", type = ToolParameterType.String),
                                ToolParameterDescriptor(name = "", description = "Number option", type = ToolParameterType.Float)
                            )
                        )
                    ),
                    mapOf(
                        "description" to "String or number value",
                        "anyOf" to "expected" // We'll verify anyOf array exists in test
                    )
                )
            )
        }
    }

    @ParameterizedTest
    @MethodSource("toolParameterTestCases")
    fun testBuildToolParameterSchema(
        param: ToolParameterDescriptor,
        expectedValues: Map<String, Any>
    ) {
        val schema = BedrockToolSerialization.buildToolParameterSchema(param)

        assertNotNull(schema)

        expectedValues.forEach { (key, value) ->
            when (key) {
                "description" -> assertEquals(value as String, schema["description"]?.jsonPrimitive?.content)
                "type" -> assertEquals(value as String, schema["type"]?.jsonPrimitive?.content)
                "enum" -> {
                    val enumValues = schema["enum"]?.jsonArray
                    assertNotNull(enumValues)
                    assertEquals((value as List<*>).size, enumValues.size)
                    value.forEach { enumValue ->
                        assertTrue(enumValues.any { it.toString().contains(enumValue.toString()) })
                    }
                }

                "items" -> {
                    val items = schema["items"]?.jsonObject
                    assertNotNull(items)
                    (value as Map<*, *>).forEach { (itemKey, itemValue) ->
                        assertEquals(itemValue, items[itemKey.toString()]?.jsonPrimitive?.content)
                    }
                }

                "anyOf" -> {
                    val anyOf = schema["anyOf"]?.jsonArray
                    assertNotNull(anyOf, "anyOf array should exist in schema")
                    assertTrue(anyOf.size > 0, "anyOf array should not be empty")
                }
            }
        }
    }
}
