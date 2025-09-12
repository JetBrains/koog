package ai.koog.agents.mcp

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DefaultMcpToolDescriptorParserTest {

    private val parser = DefaultMcpToolDescriptorParser

    @Test
    fun `test basic tool parsing with name and description`() {
        // Create a simple SDK Tool with just a name and description
        val sdkTool = createSdkTool(
            name = "test-tool",
            description = "A test tool",
            properties = buildJsonObject { },
            required = emptyList()
        )

        // Parse the tool
        val toolDescriptor = parser.parse(sdkTool)

        // Verify the result
        val expectedToolDescriptor = ToolDescriptor(
            name = "test-tool",
            description = "A test tool",
            requiredParameters = emptyList(),
            optionalParameters = emptyList()
        )
        assertEquals(expectedToolDescriptor, toolDescriptor)
    }

    @Test
    fun `test parsing required and optional parameters`() {
        // Test with both required and optional parameters
        val sdkTool = createSdkTool(
            name = "test-tool",
            description = "A test tool",
            properties = buildJsonObject {
                putJsonObject("requiredParam") {
                    put("type", "string")
                    put("description", "Required parameter")
                }
                putJsonObject("optionalParam") {
                    put("type", "integer")
                    put("description", "Optional parameter")
                }
            },
            required = listOf("requiredParam") // Only requiredParam is required, optionalParam is optional
        )

        // Parse the tool
        val toolDescriptor = parser.parse(sdkTool)

        // Verify the result
        val expectedToolDescriptor = ToolDescriptor(
            name = "test-tool",
            description = "A test tool",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "requiredParam",
                    description = "Required parameter",
                    type = ToolParameterType.String
                )
            ),
            optionalParameters = listOf(
                ToolParameterDescriptor(
                    name = "optionalParam",
                    description = "Optional parameter",
                    type = ToolParameterType.Integer
                )
            )
        )
        assertEquals(expectedToolDescriptor, toolDescriptor)
    }

    @Test
    fun `test parsing all parameter types`() {
        // Create an SDK Tool with all parameter types
        val sdkTool = createSdkTool(
            name = "test-tool",
            description = "A test tool",
            properties = buildJsonObject {
                // Primitive types
                putJsonObject("stringParam") {
                    put("type", "string")
                    put("description", "String parameter")
                }
                putJsonObject("nullableStringParam") {
                    putJsonArray("anyOf") {
                        addJsonObject {
                            put("type", "null")
                        }
                        addJsonObject {
                            put("type", "string")
                        }
                    }
                    put("description", "Nullable string parameter")
                }
                putJsonObject("integerParam") {
                    put("type", "integer")
                    put("description", "Integer parameter")
                }
                putJsonObject("nullableIntegerParam") {
                    putJsonArray("anyOf") {
                        addJsonObject {
                            put("type", "null")
                        }
                        addJsonObject {
                            put("type", "integer")
                        }
                    }
                    put("description", "Nullable integer parameter")
                }
                putJsonObject("numberParam") {
                    put("type", "number")
                    put("description", "Number parameter")
                }
                putJsonObject("nullableNumberParam") {
                    putJsonArray("anyOf") {
                        addJsonObject {
                            put("type", "null")
                        }
                        addJsonObject {
                            put("type", "number")
                        }
                    }
                    put("description", "Nullable number parameter")
                }
                putJsonObject("booleanParam") {
                    put("type", "boolean")
                    put("description", "Boolean parameter")
                }
                putJsonObject("nullableBooleanParam") {
                    putJsonArray("anyOf") {
                        addJsonObject {
                            put("type", "null")
                        }
                        addJsonObject {
                            put("type", "boolean")
                        }
                    }
                    put("description", "Nullable boolean parameter")
                }

                // Array types
                putJsonObject("arrayParam") {
                    put("type", "array")
                    put("description", "Array parameter")
                    putJsonObject("items") {
                        put("type", "string")
                    }
                }

                putJsonObject("nullableArrayParam") {
                    putJsonArray("anyOf") {
                        addJsonObject {
                            put("type", "null")
                        }
                        addJsonObject {
                            put("type", "array")
                            put("description", "Array parameter")
                            putJsonObject("items") {
                                put("type", "string")
                            }
                        }
                    }
                    put("description", "Nullable array parameter")
                }

                // Object type
                putJsonObject("objectParam") {
                    put("type", "object")
                    put("description", "Object parameter")
                    putJsonObject("properties") {
                        putJsonObject("nestedString") {
                            put("type", "string")
                            put("description", "Nested string parameter")
                        }
                        putJsonObject("nestedInteger") {
                            put("type", "integer")
                            put("description", "Nested integer parameter")
                        }
                    }
                }
            },
            required = emptyList()
        )

        // Parse the tool
        val toolDescriptor = parser.parse(sdkTool)

        // Verify the result
        val expectedToolDescriptor = ToolDescriptor(
            name = "test-tool",
            description = "A test tool",
            requiredParameters = emptyList(),
            optionalParameters = listOf(
                // Primitive types
                ToolParameterDescriptor(
                    name = "stringParam",
                    description = "String parameter",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "nullableStringParam",
                    description = "Nullable string parameter",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "integerParam",
                    description = "Integer parameter",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "nullableIntegerParam",
                    description = "Nullable integer parameter",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "numberParam",
                    description = "Number parameter",
                    type = ToolParameterType.Float
                ),
                ToolParameterDescriptor(
                    name = "nullableNumberParam",
                    description = "Nullable number parameter",
                    type = ToolParameterType.Float
                ),
                ToolParameterDescriptor(
                    name = "booleanParam",
                    description = "Boolean parameter",
                    type = ToolParameterType.Boolean
                ),
                ToolParameterDescriptor(
                    name = "nullableBooleanParam",
                    description = "Nullable boolean parameter",
                    type = ToolParameterType.Boolean
                ),

                // Array type
                ToolParameterDescriptor(
                    name = "arrayParam",
                    description = "Array parameter",
                    type = ToolParameterType.List(ToolParameterType.String)
                ),
                ToolParameterDescriptor(
                    name = "nullableArrayParam",
                    description = "Nullable array parameter",
                    type = ToolParameterType.List(ToolParameterType.String)
                ),

                // Object type
                ToolParameterDescriptor(
                    name = "objectParam",
                    description = "Object parameter",
                    type = ToolParameterType.Object(
                        listOf(
                            ToolParameterDescriptor(
                                name = "nestedString",
                                description = "Object parameter",
                                type = ToolParameterType.String
                            ),
                            ToolParameterDescriptor(
                                name = "nestedInteger",
                                description = "Object parameter",
                                type = ToolParameterType.Integer
                            )
                        )
                    )
                )
            )
        )
        assertEquals(expectedToolDescriptor, toolDescriptor)
    }

    @Test
    fun `test parsing enum parameter type`() {
        // Create an SDK Tool with an enum parameter
        val sdkTool = createSdkTool(
            name = "test-tool",
            description = "A test tool with enum parameter",
            properties = buildJsonObject {
                // Enum with type
                putJsonObject("enumParam") {
                    put("type", "enum")
                    put("description", "Enum parameter")
                    putJsonArray("enum") {
                        add("option1")
                        add("option2")
                        add("option3")
                    }
                }

                // Enum with a default string type
                putJsonObject("stringEnumParam") {
                    put("description", "String enum parameter")
                    putJsonArray("enum") {
                        add("option4")
                        add("option5")
                        add("option6")
                    }
                }
            },
            required = listOf("enumParam", "stringEnumParam")
        )

        // Parse the tool
        val toolDescriptor = parser.parse(sdkTool)

        // Verify the basic properties
        assertEquals("test-tool", toolDescriptor.name)
        assertEquals("A test tool with enum parameter", toolDescriptor.description)
        assertEquals(2, toolDescriptor.requiredParameters.size)
        assertEquals(0, toolDescriptor.optionalParameters.size)

        // Verify the enum parameter
        val enumParam = toolDescriptor.requiredParameters.first()
        assertEquals("enumParam", enumParam.name)
        assertEquals("Enum parameter", enumParam.description)
        assertTrue(enumParam.type is ToolParameterType.Enum)

        // Verify the enum values
        val enumType = enumParam.type as ToolParameterType.Enum
        val expectedOptions = arrayOf("option1", "option2", "option3")
        assertEquals(expectedOptions.size, enumType.entries.size)
        expectedOptions.forEachIndexed { index, option ->
            assertEquals(option, enumType.entries[index])
        }

        // Verify the enum parameter
        val specialEnumParam = toolDescriptor.requiredParameters[1]
        assertEquals("stringEnumParam", specialEnumParam.name)
        assertEquals("String enum parameter", specialEnumParam.description)
        assertTrue(specialEnumParam.type is ToolParameterType.Enum)

        // Verify the enum values
        val specialEnumType = specialEnumParam.type as ToolParameterType.Enum
        val expectedSpecialOptions = arrayOf("option4", "option5", "option6")
        assertEquals(expectedSpecialOptions.size, specialEnumType.entries.size)
        expectedSpecialOptions.forEachIndexed { index, option ->
            assertEquals(option, specialEnumType.entries[index])
        }
    }

    // Ignore until https://github.com/JetBrains/koog/issues/307 is fixed
    @Ignore
    @Test
    fun `test parsing enum parameter type with complex values`() {
        // Create an SDK Tool with an enum parameter that has complex values (JsonArray)
        val sdkTool = createSdkTool(
            name = "test-tool-complex-enum",
            description = "A test tool with complex enum parameter",
            properties = buildJsonObject {
                putJsonObject("complexEnumParam") {
                    put("type", "enum")
                    put("description", "Complex enum parameter")
                    putJsonArray("enum") {
                        add("option1")
                        addJsonArray {
                            add("nested1")
                            add("nested2")
                        }
                        addJsonObject {
                            put("key", "value")
                        }
                    }
                }
            },
            required = listOf("complexEnumParam")
        )

        // Parse the tool
        val toolDescriptor = parser.parse(sdkTool)

        // Verify the basic properties
        assertEquals("test-tool-complex-enum", toolDescriptor.name)
        assertEquals("A test tool with complex enum parameter", toolDescriptor.description)
        assertEquals(1, toolDescriptor.requiredParameters.size)
        assertEquals(0, toolDescriptor.optionalParameters.size)

        // Verify the enum parameter
        val enumParam = toolDescriptor.requiredParameters.first()
        assertEquals("complexEnumParam", enumParam.name)
        assertEquals("Complex enum parameter", enumParam.description)
        assertTrue(enumParam.type is ToolParameterType.Enum)

        // Verify the enum values
        val enumType = enumParam.type as ToolParameterType.Enum
        assertEquals(3, enumType.entries.size)
        assertEquals("option1", enumType.entries[0])
        assertEquals("[\"nested1\",\"nested2\"]", enumType.entries[1])
        assertEquals("{\"key\":\"value\"}", enumType.entries[2])
    }

    @Test
    fun `test parsing object parameter with additional properties`() {
        // Create an SDK Tool with an object parameter that has additional properties
        val sdkTool = createSdkTool(
            name = "test-tool",
            description = "A test tool with object parameter",
            properties = buildJsonObject {
                putJsonObject("objectParam") {
                    put("type", "object")
                    put("description", "Object parameter")
                    putJsonObject("properties") {
                        putJsonObject("name") {
                            put("type", "string")
                            put("description", "Name property")
                        }
                        putJsonObject("age") {
                            put("type", "integer")
                            put("description", "Age property")
                        }
                    }
                    putJsonArray("required") {
                        add("name")
                    }
                    putJsonObject("additionalProperties") {
                        put("type", "string")
                    }
                }
            },
            required = listOf("objectParam")
        )

        // Parse the tool
        val toolDescriptor = parser.parse(sdkTool)

        // Verify the result
        val expectedToolDescriptor = ToolDescriptor(
            name = "test-tool",
            description = "A test tool with object parameter",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "objectParam",
                    description = "Object parameter",
                    type = ToolParameterType.Object(
                        properties = listOf(
                            ToolParameterDescriptor(
                                name = "name",
                                description = "Object parameter",
                                type = ToolParameterType.String
                            ),
                            ToolParameterDescriptor(
                                name = "age",
                                description = "Object parameter",
                                type = ToolParameterType.Integer
                            )
                        ),
                        requiredProperties = listOf("name"),
                        additionalPropertiesType = ToolParameterType.String,
                        additionalProperties = true
                    )
                )
            ),
            optionalParameters = emptyList()
        )
        assertEquals(expectedToolDescriptor, toolDescriptor)
    }

    @Test
    fun `test parameter type is missing`() {
        val missingTypeToolSdk = createSdkTool(
            name = "test-tool",
            description = "A test tool",
            properties = buildJsonObject {
                putJsonObject("invalidParam") {
                    put("description", "Invalid parameter")
                    // Missing type property
                }
            },
            required = emptyList()
        )

        assertFailsWith<IllegalArgumentException>("Should fail when parameter type is missing") {
            parser.parse(missingTypeToolSdk)
        }
    }

    @Test
    fun `test array items property is missing`() {
        val missingArrayItemsToolSdk = createSdkTool(
            name = "test-tool",
            description = "A test tool",
            properties = buildJsonObject {
                putJsonObject("invalidArrayParam") {
                    put("type", "array")
                    put("description", "Invalid array parameter")
                    // Missing items property
                }
            },
            required = emptyList()
        )

        assertFailsWith<IllegalArgumentException>("Should fail when array items property is missing") {
            parser.parse(missingArrayItemsToolSdk)
        }
    }

    @Test
    fun `test object without properties returns empty properties list`() {
        val missingObjectPropertiesToolSdk = createSdkTool(
            name = "test-tool",
            description = "A test tool",
            properties = buildJsonObject {
                putJsonObject("objectParam") {
                    put("type", "object")
                    put("description", "Object parameter without properties")
                    // Missing properties property
                }
            },
            required = emptyList()
        )

        val toolDescriptor = parser.parse(missingObjectPropertiesToolSdk)
        val objectParam = toolDescriptor.optionalParameters.first()
        val objectType = objectParam.type as ToolParameterType.Object
        assertEquals(emptyList(), objectType.properties, "Object without properties should have empty properties list")
    }

    @Test
    fun `test parameter type is unsupported`() {
        val unsupportedTypeToolSdk = createSdkTool(
            name = "test-tool",
            description = "A test tool",
            properties = buildJsonObject {
                putJsonObject("invalidTypeParam") {
                    put("type", "unsupported")
                    put("description", "Invalid type parameter")
                }
            },
            required = emptyList()
        )

        assertFailsWith<IllegalArgumentException>("Should fail when parameter type is unsupported") {
            parser.parse(unsupportedTypeToolSdk)
        }
    }

    // Helper function to create an SDK Tool for testing
    private fun createSdkTool(
        name: String,
        description: String,
        properties: JsonObject,
        required: List<String>
    ): Tool {
        return Tool(
            name = name,
            description = description,
            inputSchema = Tool.Input(
                properties = properties,
                required = required
            ),
            outputSchema = null,
            annotations = null,
            title = null,
        )
    }
}
