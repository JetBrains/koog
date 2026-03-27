package ai.koog.prompt.executor.clients.google.genai

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.google.GoogleParams
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.params.LLMParams
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * Black-box tests for tool conversion and tool-choice config in [GoogleGenaiLLMClient].
 */
class GoogleGenaiToolConversionTest {

    private val asyncModels: com.google.genai.AsyncModels
    private val subject: CustomizedGoogleGenaiLLMClient

    private val toolCapableModel get() = TestModels.toolCapable

    init {
        val (d, am) = mockGoogleGenaiClient()
        asyncModels = am
        subject = CustomizedGoogleGenaiLLMClient(d, models = TestModels.all)
    }

    private fun mockGenerateContent() = asyncModels.stubGenerateContent(textResponse("ok"))

    private fun userPrompt(params: LLMParams = LLMParams()) = Prompt(
        messages = listOf(Message.User("q", RequestMetaInfo.Empty)),
        id = "t",
        params = params
    )

    // region Scenario: prompt with tools produces correct config

    @Test
    fun `prompt with single tool produces config with function declaration`() = runTest {
        val tools = listOf(
            ToolDescriptor(
                name = "get_weather",
                description = "Get current weather for a city",
                requiredParameters = listOf(ToolParameterDescriptor("city", "City name", ToolParameterType.String)),
                optionalParameters = listOf(
                    ToolParameterDescriptor("unit", "Temperature unit", ToolParameterType.Enum(arrayOf("celsius", "fahrenheit")))
                )
            )
        )
        val captured = mockGenerateContent()

        subject.execute(userPrompt(GoogleParams(toolChoice = LLMParams.ToolChoice.Auto)), toolCapableModel, tools)

        subject.toolsCustomized shouldBe true
        subject.toolConfigCustomized shouldBe true

        val sdkTools = captured.config.tools().get()
        sdkTools shouldHaveSize 1
        val decls = sdkTools[0].functionDeclarations().get()
        decls shouldHaveSize 1
        decls[0].name().get() shouldBe "get_weather"
        decls[0].description().get() shouldBe "Get current weather for a city"
        decls[0].parametersJsonSchema().get().shouldNotBeNull()

        val toolConfig = captured.config.toolConfig().get()
        toolConfig.shouldNotBeNull()
        toolConfig.functionCallingConfig().get().mode().get().toString() shouldBe "AUTO"
    }

    @Test
    fun `prompt with multiple tools produces config with all declarations`() = runTest {
        val tools = listOf(
            ToolDescriptor(name = "search", description = "Search the web", requiredParameters = listOf(ToolParameterDescriptor("query", "Search query", ToolParameterType.String))),
            ToolDescriptor(name = "calculate", description = "Do math", requiredParameters = listOf(ToolParameterDescriptor("expression", "Math expression", ToolParameterType.String)))
        )
        val captured = mockGenerateContent()

        subject.execute(userPrompt(), toolCapableModel, tools)

        val decls = captured.config.tools().get()[0].functionDeclarations().get()
        decls shouldHaveSize 2
        decls[0].name().get() shouldBe "search"
        decls[1].name().get() shouldBe "calculate"
    }

    @Test
    fun `empty tool list produces config with no tools`() = runTest {
        val captured = mockGenerateContent()

        subject.execute(userPrompt(), toolCapableModel, emptyList())

        captured.config.tools().isPresent shouldBe false
    }

    // endregion

    // region Parameterized: all ToolParameterType variants produce correct schema

    companion object {
        @JvmStatic
        fun parameterTypeTestCases(): Stream<Arguments> = Stream.of(
            Arguments.of("String type", ToolParameterType.String, "string"),
            Arguments.of("Integer type", ToolParameterType.Integer, "integer"),
            Arguments.of("Float type", ToolParameterType.Float, "number"),
            Arguments.of("Boolean type", ToolParameterType.Boolean, "boolean"),
            Arguments.of("Null type", ToolParameterType.Null, "null"),
        )
    }

    @ParameterizedTest(name = "{0} maps to schema type {2}")
    @MethodSource("parameterTypeTestCases")
    fun `primitive parameter type maps to correct schema type`(
        @Suppress("UNUSED_PARAMETER") displayName: String,
        paramType: ToolParameterType,
        expectedSchemaType: String
    ) = runTest {
        val tools = listOf(ToolDescriptor(name = "test_tool", description = "test", requiredParameters = listOf(ToolParameterDescriptor("param", "desc", paramType))))
        val captured = mockGenerateContent()

        subject.execute(userPrompt(), toolCapableModel, tools)

        val schema = captured.config.tools().get()[0].functionDeclarations().get()[0]
            .parametersJsonSchema().get() as Map<*, *>
        val paramSchema = (schema["properties"] as Map<*, *>)["param"] as Map<*, *>
        paramSchema["type"] shouldBe expectedSchemaType
    }

    @Test
    fun `Enum parameter produces string type with enum values`() = runTest {
        val tools = listOf(ToolDescriptor(name = "t", description = "d", requiredParameters = listOf(
            ToolParameterDescriptor("color", "Pick color", ToolParameterType.Enum(arrayOf("red", "blue", "green")))
        )))
        val captured = mockGenerateContent()

        subject.execute(userPrompt(), toolCapableModel, tools)

        val schema = captured.config.tools().get()[0].functionDeclarations().get()[0]
            .parametersJsonSchema().get() as Map<*, *>
        val paramSchema = (schema["properties"] as Map<*, *>)["color"] as Map<*, *>
        paramSchema["type"] shouldBe "string"
        paramSchema["enum"] shouldBe listOf("red", "blue", "green")
    }

    @Test
    fun `List parameter produces array type with items`() = runTest {
        val tools = listOf(ToolDescriptor(name = "t", description = "d", requiredParameters = listOf(
            ToolParameterDescriptor("tags", "Tag list", ToolParameterType.List(ToolParameterType.String))
        )))
        val captured = mockGenerateContent()

        subject.execute(userPrompt(), toolCapableModel, tools)

        val schema = captured.config.tools().get()[0].functionDeclarations().get()[0]
            .parametersJsonSchema().get() as Map<*, *>
        val paramSchema = (schema["properties"] as Map<*, *>)["tags"] as Map<*, *>
        paramSchema["type"] shouldBe "array"
        (paramSchema["items"] as Map<*, *>)["type"] shouldBe "string"
    }

    @Test
    fun `AnyOf parameter produces anyOf list`() = runTest {
        val tools = listOf(ToolDescriptor(name = "t", description = "d", requiredParameters = listOf(
            ToolParameterDescriptor("value", "Mixed", ToolParameterType.AnyOf(arrayOf(
                ToolParameterDescriptor("", "", ToolParameterType.String),
                ToolParameterDescriptor("", "", ToolParameterType.Integer)
            )))
        )))
        val captured = mockGenerateContent()

        subject.execute(userPrompt(), toolCapableModel, tools)

        val schema = captured.config.tools().get()[0].functionDeclarations().get()[0]
            .parametersJsonSchema().get() as Map<*, *>
        val anyOf = (schema["properties"] as Map<*, *>)["value"] as Map<*, *>
        val anyOfList = anyOf["anyOf"] as List<*>
        anyOfList shouldHaveSize 2
        (anyOfList[0] as Map<*, *>)["type"] shouldBe "string"
        (anyOfList[1] as Map<*, *>)["type"] shouldBe "integer"
    }

    @Test
    fun `Object parameter produces object type with properties`() = runTest {
        val tools = listOf(ToolDescriptor(name = "t", description = "d", requiredParameters = listOf(
            ToolParameterDescriptor("addr", "Address", ToolParameterType.Object(
                properties = listOf(
                    ToolParameterDescriptor("street", "Street name", ToolParameterType.String),
                    ToolParameterDescriptor("zip", "Zip code", ToolParameterType.Integer)
                ),
                requiredProperties = listOf("street")
            ))
        )))
        val captured = mockGenerateContent()

        subject.execute(userPrompt(), toolCapableModel, tools)

        val schema = captured.config.tools().get()[0].functionDeclarations().get()[0]
            .parametersJsonSchema().get() as Map<*, *>
        val paramSchema = (schema["properties"] as Map<*, *>)["addr"] as Map<*, *>
        paramSchema["type"] shouldBe "object"
        val props = paramSchema["properties"] as Map<*, *>
        (props["street"] as Map<*, *>)["type"] shouldBe "string"
        (props["zip"] as Map<*, *>)["type"] shouldBe "integer"
    }

    // endregion

    // region Tool choice modes

    @Test
    fun `ToolChoice Required maps to ANY mode`() = runTest {
        val captured = mockGenerateContent()
        subject.execute(userPrompt(GoogleParams(toolChoice = LLMParams.ToolChoice.Required)), toolCapableModel)
        captured.config.toolConfig().get().functionCallingConfig().get().mode().get().toString() shouldBe "ANY"
    }

    @Test
    fun `ToolChoice None maps to NONE mode`() = runTest {
        val captured = mockGenerateContent()
        subject.execute(userPrompt(GoogleParams(toolChoice = LLMParams.ToolChoice.None)), toolCapableModel)
        captured.config.toolConfig().get().functionCallingConfig().get().mode().get().toString() shouldBe "NONE"
    }

    @Test
    fun `ToolChoice Named maps to ANY with allowedFunctionNames`() = runTest {
        val captured = mockGenerateContent()
        subject.execute(userPrompt(GoogleParams(toolChoice = LLMParams.ToolChoice.Named("get_weather"))), toolCapableModel)
        val fc = captured.config.toolConfig().get().functionCallingConfig().get()
        fc.mode().get().toString() shouldBe "ANY"
        fc.allowedFunctionNames().get() shouldBe listOf("get_weather")
    }

    @Test
    fun `null toolChoice produces no toolConfig`() = runTest {
        val captured = mockGenerateContent()
        subject.execute(userPrompt(), toolCapableModel)
        captured.config.toolConfig().isPresent shouldBe false
    }

    // endregion
}
