package ai.koog.agents.mcp

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
                putJsonObject("integerParam") {
                    put("type", "integer")
                    put("description", "Integer parameter")
                }
                putJsonObject("numberParam") {
                    put("type", "number")
                    put("description", "Number parameter")
                }
                putJsonObject("booleanParam") {
                    put("type", "boolean")
                    put("description", "Boolean parameter")
                }

                // Array types
                putJsonObject("arrayParam") {
                    put("type", "array")
                    put("description", "Array parameter")
                    putJsonObject("items") {
                        put("type", "string")
                    }
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
                    name = "integerParam",
                    description = "Integer parameter",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "numberParam",
                    description = "Number parameter",
                    type = ToolParameterType.Float
                ),
                ToolParameterDescriptor(
                    name = "booleanParam",
                    description = "Boolean parameter",
                    type = ToolParameterType.Boolean
                ),

                // Array type
                ToolParameterDescriptor(
                    name = "arrayParam",
                    description = "Array parameter",
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
    fun `test error cases`() {
        // Test case 1: Parameter type is missing
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

        // Test case 2: Array items property is missing
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

        // Test case 3: Object properties property is missing
        val missingObjectPropertiesToolSdk = createSdkTool(
            name = "test-tool",
            description = "A test tool",
            properties = buildJsonObject {
                putJsonObject("invalidObjectParam") {
                    put("type", "object")
                    put("description", "Invalid object parameter")
                    // Missing properties property
                }
            },
            required = emptyList()
        )

        assertFailsWith<IllegalArgumentException>("Should fail when object properties property is missing") {
            parser.parse(missingObjectPropertiesToolSdk)
        }

        // Test case 4: Parameter type is unsupported
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
            )
        )
    }
}
