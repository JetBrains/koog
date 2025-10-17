package ai.koog.prompt.executor.clients.google

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.params.LLMParams
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GoogleLLMClientTest {

    private val json = Json { prettyPrint = true }

    @Test
    fun `createGoogleRequest should use null maxTokens if unspecified`() {
        val client = GoogleLLMClient(apiKey = "apiKey")
        val model = GoogleModels.Gemini2_5Pro
        val request = client.createGoogleRequest(
            prompt = Prompt(
                messages = emptyList(),
                id = "id"
            ),
            model = model,
            tools = emptyList()
        )
        assertEquals(null, request.generationConfig!!.maxOutputTokens)
    }

    @Test
    fun `createGoogleRequest should use maxTokens from user specified parameters when available`() {
        val client = GoogleLLMClient(apiKey = "apiKey")
        val model = GoogleModels.Gemini2_5Pro
        val request = client.createGoogleRequest(
            prompt = Prompt(
                messages = emptyList(),
                id = "id",
                params = LLMParams(maxTokens = 100)
            ),
            model = model,
            tools = emptyList()
        )
        assertEquals(100, request.generationConfig!!.maxOutputTokens)
    }

    @Test
    fun `createGoogleRequest should handle Null parameter type`() {
        val client = GoogleLLMClient(apiKey = "apiKey")
        val model = GoogleModels.Gemini2_5Pro

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

        val request = client.createGoogleRequest(
            prompt = Prompt(
                messages = emptyList(),
                id = "id"
            ),
            model = model,
            tools = listOf(tool)
        )

        assertNotNull(request.tools)
        val tools = request.tools!!
        assertEquals(1, tools.size)
        val functionDeclarations = tools.first().functionDeclarations!!
        val functionDeclaration = functionDeclarations.first()
        assertEquals("test_tool", functionDeclaration.name)

        val parameters = functionDeclaration.parameters!!
        val properties = parameters["properties"]?.jsonObject!!
        assertNotNull(properties)

        val nullParam = properties["nullParam"]?.jsonObject!!
        assertNotNull(nullParam)
        assertEquals("null", nullParam["type"]?.jsonPrimitive?.content)
        assertEquals("A null parameter", nullParam["description"]?.jsonPrimitive?.content)
    }

    @Test
    fun `createGoogleRequest should handle AnyOf parameter type`() {
        val client = GoogleLLMClient(apiKey = "apiKey")
        val model = GoogleModels.Gemini2_5Pro

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

        val request = client.createGoogleRequest(
            prompt = Prompt(
                messages = emptyList(),
                id = "id"
            ),
            model = model,
            tools = listOf(tool)
        )

        assertNotNull(request.tools)
        val tools = request.tools!!
        assertEquals(1, tools.size)
        val functionDeclarations = tools.first().functionDeclarations!!
        val functionDeclaration = functionDeclarations.first()
        assertEquals("test_tool", functionDeclaration.name)

        val parameters = functionDeclaration.parameters!!
        val properties = parameters["properties"]?.jsonObject!!
        assertNotNull(properties)

        val valueParam = properties["value"]?.jsonObject!!
        assertNotNull(valueParam)
        assertEquals("A value that can be string or number", valueParam["description"]?.jsonPrimitive?.content)

        val anyOf = valueParam["anyOf"]?.jsonArray
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
    fun `createGoogleRequest should handle complex AnyOf with Null`() {
        val client = GoogleLLMClient(apiKey = "apiKey")
        val model = GoogleModels.Gemini2_5Pro

        val tool = ToolDescriptor(
            name = "test_tool",
            description = "A test tool with complex anyOf",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "complexValue",
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

        val request = client.createGoogleRequest(
            prompt = Prompt(
                messages = emptyList(),
                id = "id"
            ),
            model = model,
            tools = listOf(tool)
        )

        assertNotNull(request.tools)
        val tools = request.tools!!
        val functionDeclarations = tools.first().functionDeclarations!!
        val parameters = functionDeclarations.first().parameters!!
        val properties = parameters["properties"]?.jsonObject!!
        assertNotNull(properties)
        val complexValue = properties["complexValue"]?.jsonObject!!
        assertNotNull(complexValue)

        val anyOf = complexValue["anyOf"]?.jsonArray
        assertNotNull(anyOf)
        assertEquals(3, anyOf.size, "anyOf should have 3 options")

        // Verify the types
        val types = anyOf.map { it.jsonObject["type"]?.jsonPrimitive?.content }
        assertTrue(types.contains("string"), "Should contain string type")
        assertTrue(types.contains("number"), "Should contain number type")
        assertTrue(types.contains("null"), "Should contain null type")
    }
}
