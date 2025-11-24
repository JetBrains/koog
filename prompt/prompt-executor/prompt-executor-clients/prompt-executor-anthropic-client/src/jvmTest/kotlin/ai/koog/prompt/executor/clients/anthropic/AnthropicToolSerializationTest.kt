package ai.koog.prompt.executor.clients.anthropic

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClientException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AnthropicToolSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `createAnthropicRequest should handle Null parameter type`() {
        val client = AnthropicLLMClient(apiKey = "test-key")
        val model = AnthropicModels.Sonnet_3_7

        val tool = ToolDescriptor(
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

        val requestJson = client.createAnthropicRequest(
            prompt = Prompt(
                messages = emptyList(),
                id = "id"
            ),
            tools = listOf(tool),
            model = model,
            stream = false
        )

        val request = json.parseToJsonElement(requestJson).jsonObject
        val tools = request["tools"]?.jsonArray
        assertNotNull(tools)
        assertEquals(1, tools.size)

        val toolObj = tools[0].jsonObject
        assertEquals("test_tool", toolObj["name"]?.jsonPrimitive?.content)

        val inputSchema = toolObj["input_schema"]?.jsonObject
        assertNotNull(inputSchema)

        val properties = inputSchema["properties"]?.jsonObject
        assertNotNull(properties)

        val nullParam = properties["nullParam"]?.jsonObject
        assertNotNull(nullParam)
        assertEquals("null", nullParam["type"]?.jsonPrimitive?.content)
        assertEquals("A null parameter", nullParam["description"]?.jsonPrimitive?.content)
    }

    @Test
    fun `createAnthropicRequest should throw exception for AnyOf parameter type`() {
        val client = AnthropicLLMClient(apiKey = "test-key")
        val model = AnthropicModels.Sonnet_3_7

        val tool = ToolDescriptor(
            name = "test_tool",
            description = "A test tool with anyOf parameter",
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

        val exception = assertFailsWith<LLMClientException> {
            client.createAnthropicRequest(
                prompt = Prompt(
                    messages = emptyList(),
                    id = "id"
                ),
                tools = listOf(tool),
                model = model,
                stream = false
            )
        }

        val message = exception.message

        assertNotNull(message)
        assertContains(message, "AnyOf type is not supported")
    }

    @Test
    fun `createAnthropicRequest should handle multiple parameter types including Null`() {
        val client = AnthropicLLMClient(apiKey = "test-key")
        val model = AnthropicModels.Sonnet_3_7

        val tool = ToolDescriptor(
            name = "test_tool",
            description = "A test tool with multiple parameter types",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "stringParam",
                    description = "A string parameter",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "nullParam",
                    description = "A null parameter",
                    type = ToolParameterType.Null
                ),
                ToolParameterDescriptor(
                    name = "numberParam",
                    description = "A number parameter",
                    type = ToolParameterType.Float
                )
            )
        )

        val requestJson = client.createAnthropicRequest(
            prompt = Prompt(
                messages = emptyList(),
                id = "id"
            ),
            tools = listOf(tool),
            model = model,
            stream = false
        )

        val request = json.parseToJsonElement(requestJson).jsonObject
        val tools = request["tools"]?.jsonArray
        assertNotNull(tools)
        assertEquals(1, tools.size)

        val toolObj = tools[0].jsonObject
        val inputSchema = toolObj["input_schema"]?.jsonObject
        assertNotNull(inputSchema)

        val properties = inputSchema["properties"]?.jsonObject
        assertNotNull(properties)

        // Verify string parameter
        val stringParam = properties["stringParam"]?.jsonObject
        assertNotNull(stringParam)
        assertEquals("string", stringParam["type"]?.jsonPrimitive?.content)

        // Verify null parameter
        val nullParam = properties["nullParam"]?.jsonObject
        assertNotNull(nullParam)
        assertEquals("null", nullParam["type"]?.jsonPrimitive?.content)

        // Verify number parameter
        val numberParam = properties["numberParam"]?.jsonObject
        assertNotNull(numberParam)
        assertEquals("number", numberParam["type"]?.jsonPrimitive?.content)

        // Verify all three are in required array
        val required = inputSchema["required"]?.jsonArray
        assertNotNull(required)
        assertEquals(3, required.size)
        val requiredNames = required.map { it.jsonPrimitive.content }
        assertTrue(requiredNames.contains("stringParam"))
        assertTrue(requiredNames.contains("nullParam"))
        assertTrue(requiredNames.contains("numberParam"))
    }
}
