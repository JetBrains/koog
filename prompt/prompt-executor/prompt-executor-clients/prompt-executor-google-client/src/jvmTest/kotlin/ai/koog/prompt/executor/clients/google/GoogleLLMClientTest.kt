package ai.koog.prompt.executor.clients.google

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.google.models.GoogleFunctionCallingMode
import ai.koog.prompt.executor.clients.google.models.GoogleThinkingConfig
import ai.koog.prompt.params.LLMParams
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

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
        request.generationConfig!!.maxOutputTokens shouldBe null
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
        request.generationConfig!!.maxOutputTokens shouldBe 100
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

        request.tools.shouldNotBeNull()
        val tools = request.tools
        tools.size shouldBe 1
        val functionDeclarations = tools.first().functionDeclarations!!
        val functionDeclaration = functionDeclarations.first()
        functionDeclaration.name shouldBe "test_tool"

        val parameters = functionDeclaration.parameters!!
        val properties = parameters["properties"]?.jsonObject!!

        val nullParam = properties["nullParam"]?.jsonObject!!
        nullParam["type"]?.jsonPrimitive?.content shouldBe "null"
        nullParam["description"]?.jsonPrimitive?.content shouldBe "A null parameter"
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

        request.tools.shouldNotBeNull()
        val tools = request.tools
        tools.size shouldBe 1
        val functionDeclarations = tools.first().functionDeclarations!!
        val functionDeclaration = functionDeclarations.first()
        functionDeclaration.name shouldBe "test_tool"

        val parameters = functionDeclaration.parameters!!
        val properties = parameters["properties"]?.jsonObject!!

        val valueParam = properties["value"]?.jsonObject!!
        valueParam["description"]?.jsonPrimitive?.content shouldBe "A value that can be string or number"

        val anyOf = valueParam["anyOf"]?.jsonArray
        anyOf.shouldNotBeNull()
        anyOf.size shouldBe 2

        // Verify the first option (String)
        val stringOption = anyOf[0].jsonObject
        stringOption["type"]?.jsonPrimitive?.content shouldBe "string"
        stringOption["description"]?.jsonPrimitive?.content shouldBe "String option"

        // Verify the second option (Number)
        val numberOption = anyOf[1].jsonObject
        numberOption["type"]?.jsonPrimitive?.content shouldBe "number"
        numberOption["description"]?.jsonPrimitive?.content shouldBe "Number option"
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

        request.tools.shouldNotBeNull()
        val tools = request.tools
        val functionDeclarations = tools.first().functionDeclarations!!
        val parameters = functionDeclarations.first().parameters!!
        val properties = parameters["properties"]?.jsonObject!!
        val complexValue = properties["complexValue"]?.jsonObject!!

        val anyOf = complexValue["anyOf"]?.jsonArray
        anyOf.shouldNotBeNull()
        anyOf.size shouldBe 3

        // Verify the types
        val types = anyOf.map { it.jsonObject["type"]?.jsonPrimitive?.content }
        types shouldContain "string"
        types shouldContain "number"
        types shouldContain "null"
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
        gen.maxOutputTokens shouldBe 1024
        gen.temperature shouldBe 0.4
        gen.candidateCount shouldBe 2
        gen.topP shouldBe 0.8
        gen.topK shouldBe 10
        gen.thinkingConfig?.includeThoughts shouldBe true
        gen.thinkingConfig?.thinkingBudget shouldBe 99
        gen.additionalProperties.shouldNotBeNull()
        gen.additionalProperties["custom"]?.jsonPrimitive?.content shouldBe "v"
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
        gen.responseMimeType shouldBe "application/json"
        gen.responseSchema.shouldNotBeNull()
        gen.responseJsonSchema shouldBe null
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
        gen.responseMimeType shouldBe "application/json"
        gen.responseJsonSchema.shouldNotBeNull()
        gen.responseSchema shouldBe null
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

        getMode(LLMParams.ToolChoice.Auto) shouldBe GoogleFunctionCallingMode.AUTO
        getMode(LLMParams.ToolChoice.None) shouldBe GoogleFunctionCallingMode.NONE
        getMode(LLMParams.ToolChoice.Required) shouldBe GoogleFunctionCallingMode.ANY
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
        fc.shouldNotBeNull()
        fc.mode shouldBe GoogleFunctionCallingMode.ANY
        fc.allowedFunctionNames shouldBe listOf("weather")
    }
}
