package ai.koog.prompt.executor.clients.google

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.google.models.GoogleFunctionCallingMode
import ai.koog.prompt.executor.clients.google.models.GoogleThinkingConfig
import ai.koog.prompt.params.LLMParams
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GoogleLLMClientTest {

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
        val tools = request.tools
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
                            ToolParameterDescriptor(
                                name = "",
                                description = "String option",
                                type = ToolParameterType.String
                            ),
                            ToolParameterDescriptor(
                                name = "",
                                description = "Number option",
                                type = ToolParameterType.Float
                            )
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
        val tools = request.tools
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
        val tools = request.tools
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

    @Test
    fun `createGoogleRequest should map GoogleParams to generationConfig`() {
        val client = GoogleLLMClient(apiKey = "apiKey")
        val model = GoogleModels.Gemini2_5Pro

        val params = GoogleParams(
            temperature = 0.4,
            maxTokens = 1024,
            numberOfChoices = 2,
            topP = 0.8,
            topK = 10,
            thinkingConfig = GoogleThinkingConfig(
                includeThoughts = true,
                thinkingBudget = 99
            ),
            additionalProperties = mapOf("custom" to JsonPrimitive("v"))
        )

        val request = client.createGoogleRequest(
            prompt = Prompt(messages = emptyList(), id = "id", params = params),
            model = model,
            tools = emptyList()
        )

        val gen = request.generationConfig!!
        assertEquals(1024, gen.maxOutputTokens)
        assertEquals(0.4, gen.temperature)
        assertEquals(2, gen.candidateCount)
        assertEquals(0.8, gen.topP)
        assertEquals(10, gen.topK)
        assertEquals(true, gen.thinkingConfig?.includeThoughts)
        assertEquals(99, gen.thinkingConfig?.thinkingBudget)
        assertNotNull(gen.additionalProperties)
        assertEquals("v", gen.additionalProperties["custom"]?.jsonPrimitive?.content)
    }

    @Test
    fun `createGoogleRequest should map JSON Basic schema to responseSchema`() {
        val client = GoogleLLMClient(apiKey = "apiKey")
        val model = GoogleModels.Gemini2_5Pro

        val schema = LLMParams.Schema.JSON.Basic(
            name = "out",
            schema = JsonObject(mapOf("type" to JsonPrimitive("object")))
        )

        val request = client.createGoogleRequest(
            prompt = Prompt(messages = emptyList(), id = "id", params = GoogleParams(schema = schema)),
            model = model,
            tools = emptyList()
        )

        val gen = request.generationConfig!!
        assertEquals("application/json", gen.responseMimeType)
        assertNotNull(gen.responseSchema)
        assertEquals(null, gen.responseJsonSchema)
    }

    @Test
    fun `createGoogleRequest should map JSON Standard schema to responseJsonSchema`() {
        val client = GoogleLLMClient(apiKey = "apiKey")
        val model = GoogleModels.Gemini2_5Pro

        val schema = LLMParams.Schema.JSON.Standard(
            name = "out",
            schema = JsonObject(mapOf("type" to JsonPrimitive("object")))
        )

        val request = client.createGoogleRequest(
            prompt = Prompt(messages = emptyList(), id = "id", params = GoogleParams(schema = schema)),
            model = model,
            tools = emptyList()
        )

        val gen = request.generationConfig!!
        assertEquals("application/json", gen.responseMimeType)
        assertNotNull(gen.responseJsonSchema)
        assertEquals(null, gen.responseSchema)
    }

    @Test
    fun `toolChoice Auto None Required should map to Google function calling modes`() {
        val client = GoogleLLMClient(apiKey = "apiKey")
        val model = GoogleModels.Gemini2_5Pro

        fun getMode(tc: LLMParams.ToolChoice): GoogleFunctionCallingMode? {
            val req = client.createGoogleRequest(
                prompt = Prompt(messages = emptyList(), id = "id", params = GoogleParams(toolChoice = tc)),
                model = model,
                tools = emptyList()
            )
            return req.toolConfig?.functionCallingConfig?.mode
        }

        assertEquals(
            GoogleFunctionCallingMode.AUTO,
            getMode(LLMParams.ToolChoice.Auto)
        )
        assertEquals(
            GoogleFunctionCallingMode.NONE,
            getMode(LLMParams.ToolChoice.None)
        )
        assertEquals(
            GoogleFunctionCallingMode.ANY,
            getMode(LLMParams.ToolChoice.Required)
        )
    }

    @Test
    fun `toolChoice Named should set ANY with allowedFunctionNames`() {
        val client = GoogleLLMClient(apiKey = "apiKey")
        val model = GoogleModels.Gemini2_5Pro
        val req = client.createGoogleRequest(
            prompt = Prompt(
                messages = emptyList(),
                id = "id",
                params = GoogleParams(toolChoice = LLMParams.ToolChoice.Named("weather"))
            ),
            model = model,
            tools = emptyList()
        )
        val fc = req.toolConfig?.functionCallingConfig
        assertNotNull(fc)
        assertEquals(GoogleFunctionCallingMode.ANY, fc.mode)
        assertEquals(listOf("weather"), fc.allowedFunctionNames)
    }
}
