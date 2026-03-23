package ai.koog.prompt.executor.clients.google.genai

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.google.GoogleParams
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.params.LLMParams
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class GoogleGenaiToolConversionTest {

    private val delegate = mockk<com.google.genai.Client>(relaxed = true)
    private val subject = CustomizedGoogleGenaiLLMClient(delegate)

    // region Scenario: prompt with tools produces correct config

    @Test
    fun `prompt with single tool produces config with function declaration`() {
        val tools = listOf(
            ToolDescriptor(
                name = "get_weather",
                description = "Get current weather for a city",
                requiredParameters = listOf(
                    ToolParameterDescriptor("city", "City name", ToolParameterType.String)
                ),
                optionalParameters = listOf(
                    ToolParameterDescriptor(
                        "unit",
                        "Temperature unit",
                        ToolParameterType.Enum(arrayOf("celsius", "fahrenheit"))
                    )
                )
            )
        )

        val prompt = Prompt(
            messages = listOf(Message.User("Weather in Paris?", RequestMetaInfo.Empty)),
            id = "tool-test",
            params = GoogleParams(toolChoice = LLMParams.ToolChoice.Auto)
        )

        val (_, systemInstruction) = subject.buildSdkContents(prompt, GoogleModels.Gemini2_5Flash)
        val config = subject.buildConfig(prompt.params, GoogleModels.Gemini2_5Flash, tools, systemInstruction).build()

        // Verify tool conversion overrides were invoked
        subject.toolsCustomized shouldBe true
        subject.toolConfigCustomized shouldBe true

        val sdkTools = config.tools().orElse(null)
        sdkTools.shouldNotBeNull()
        sdkTools shouldHaveSize 1
        val decls = sdkTools[0].functionDeclarations().orElse(emptyList())
        decls shouldHaveSize 1
        decls[0].name().orElse(null) shouldBe "get_weather"
        decls[0].description().orElse(null) shouldBe "Get current weather for a city"
        decls[0].parametersJsonSchema().orElse(null).shouldNotBeNull()

        val toolConfig = config.toolConfig().orElse(null)
        toolConfig.shouldNotBeNull()
        toolConfig.functionCallingConfig().orElse(null)?.mode()?.orElse(null)?.toString() shouldBe "AUTO"
    }

    @Test
    fun `prompt with multiple tools produces config with all declarations`() {
        val tools = listOf(
            ToolDescriptor(
                name = "search",
                description = "Search the web",
                requiredParameters = listOf(ToolParameterDescriptor("query", "Search query", ToolParameterType.String))
            ),
            ToolDescriptor(
                name = "calculate",
                description = "Do math",
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        "expression",
                        "Math expression",
                        ToolParameterType.String
                    )
                )
            )
        )

        val (_, si) = subject.buildSdkContents(
            Prompt(messages = listOf(Message.User("q", RequestMetaInfo.Empty)), id = "t"),
            GoogleModels.Gemini2_5Flash
        )
        val config = subject.buildConfig(LLMParams(), GoogleModels.Gemini2_5Flash, tools, si).build()

        val decls = config.tools().orElse(null)!![0].functionDeclarations().orElse(emptyList())
        decls shouldHaveSize 2
        decls[0].name().orElse(null) shouldBe "search"
        decls[1].name().orElse(null) shouldBe "calculate"
    }

    @Test
    fun `empty tool list produces config with no tools`() {
        val config = subject.buildConfig(LLMParams(), GoogleModels.Gemini2_5Flash, emptyList(), null).build()
        config.tools().orElse(null).shouldBeNull()
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
    ) {
        val tools = listOf(
            ToolDescriptor(
                name = "test_tool",
                description = "test",
                requiredParameters = listOf(ToolParameterDescriptor("param", "desc", paramType))
            )
        )

        val sdkTools = subject.buildSdkTools(tools)
        sdkTools.shouldNotBeNull()
        val schema = sdkTools[0].build().functionDeclarations().orElse(emptyList())[0]
            .parametersJsonSchema().orElse(null) as Map<*, *>
        val properties = schema["properties"] as Map<*, *>
        val paramSchema = properties["param"] as Map<*, *>
        paramSchema["type"] shouldBe expectedSchemaType
    }

    @Test
    fun `Enum parameter produces string type with enum values`() {
        val tools = listOf(
            ToolDescriptor(
                name = "t",
                description = "d",
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        "color",
                        "Pick color",
                        ToolParameterType.Enum(arrayOf("red", "blue", "green"))
                    )
                )
            )
        )

        val sdkTools = subject.buildSdkTools(tools)!!
        val schema = sdkTools[0].build().functionDeclarations().orElse(emptyList())[0]
            .parametersJsonSchema().orElse(null) as Map<*, *>
        val paramSchema = (schema["properties"] as Map<*, *>)["color"] as Map<*, *>
        paramSchema["type"] shouldBe "string"
        paramSchema["enum"] shouldBe listOf("red", "blue", "green")
    }

    @Test
    fun `List parameter produces array type with items`() {
        val tools = listOf(
            ToolDescriptor(
                name = "t",
                description = "d",
                requiredParameters = listOf(
                    ToolParameterDescriptor("tags", "Tag list", ToolParameterType.List(ToolParameterType.String))
                )
            )
        )

        val sdkTools = subject.buildSdkTools(tools)!!
        val schema = sdkTools[0].build().functionDeclarations().orElse(emptyList())[0]
            .parametersJsonSchema().orElse(null) as Map<*, *>
        val paramSchema = (schema["properties"] as Map<*, *>)["tags"] as Map<*, *>
        paramSchema["type"] shouldBe "array"
        val items = paramSchema["items"] as Map<*, *>
        items["type"] shouldBe "string"
    }

    @Test
    fun `AnyOf parameter produces anyOf list`() {
        val tools = listOf(
            ToolDescriptor(
                name = "t",
                description = "d",
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        "value",
                        "Mixed type",
                        ToolParameterType.AnyOf(
                            arrayOf(
                                ToolParameterDescriptor("", "", ToolParameterType.String),
                                ToolParameterDescriptor("", "", ToolParameterType.Integer)
                            )
                        )
                    )
                )
            )
        )

        val sdkTools = subject.buildSdkTools(tools)!!
        val schema = sdkTools[0].build().functionDeclarations().orElse(emptyList())[0]
            .parametersJsonSchema().orElse(null) as Map<*, *>
        val paramSchema = (schema["properties"] as Map<*, *>)["value"] as Map<*, *>
        val anyOf = paramSchema["anyOf"] as List<*>
        anyOf shouldHaveSize 2
        (anyOf[0] as Map<*, *>)["type"] shouldBe "string"
        (anyOf[1] as Map<*, *>)["type"] shouldBe "integer"
    }

    @Test
    fun `Object parameter produces object type with properties`() {
        val tools = listOf(
            ToolDescriptor(
                name = "t",
                description = "d",
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        "addr",
                        "Address",
                        ToolParameterType.Object(
                            properties = listOf(
                                ToolParameterDescriptor("street", "Street name", ToolParameterType.String),
                                ToolParameterDescriptor("zip", "Zip code", ToolParameterType.Integer)
                            ),
                            requiredProperties = listOf("street")
                        )
                    )
                )
            )
        )

        val sdkTools = subject.buildSdkTools(tools)!!
        val schema = sdkTools[0].build().functionDeclarations().orElse(emptyList())[0]
            .parametersJsonSchema().orElse(null) as Map<*, *>
        val paramSchema = (schema["properties"] as Map<*, *>)["addr"] as Map<*, *>
        paramSchema["type"] shouldBe "object"
        val props = paramSchema["properties"] as Map<*, *>
        (props["street"] as Map<*, *>)["type"] shouldBe "string"
        (props["zip"] as Map<*, *>)["type"] shouldBe "integer"
    }

    // endregion

    // region Tool choice modes

    @Test
    fun `ToolChoice Required maps to ANY mode`() {
        val config = subject.buildConfig(
            GoogleParams(toolChoice = LLMParams.ToolChoice.Required),
            GoogleModels.Gemini2_5Flash,
            emptyList(),
            null
        ).build()
        config.toolConfig().orElse(null)?.functionCallingConfig()?.orElse(null)
            ?.mode()?.orElse(null)?.toString() shouldBe "ANY"
    }

    @Test
    fun `ToolChoice None maps to NONE mode`() {
        val config = subject.buildConfig(
            GoogleParams(toolChoice = LLMParams.ToolChoice.None),
            GoogleModels.Gemini2_5Flash,
            emptyList(),
            null
        ).build()
        config.toolConfig().orElse(null)?.functionCallingConfig()?.orElse(null)
            ?.mode()?.orElse(null)?.toString() shouldBe "NONE"
    }

    @Test
    fun `ToolChoice Named maps to ANY with allowedFunctionNames`() {
        val config = subject.buildConfig(
            GoogleParams(toolChoice = LLMParams.ToolChoice.Named("get_weather")),
            GoogleModels.Gemini2_5Flash,
            emptyList(),
            null
        ).build()
        val fc = config.toolConfig().orElse(null)?.functionCallingConfig()?.orElse(null)
        fc.shouldNotBeNull()
        fc.mode().orElse(null)?.toString() shouldBe "ANY"
        fc.allowedFunctionNames().orElse(emptyList()) shouldBe listOf("get_weather")
    }

    @Test
    fun `null toolChoice produces no toolConfig`() {
        val config = subject.buildConfig(LLMParams(), GoogleModels.Gemini2_5Flash, emptyList(), null).build()
        config.toolConfig().orElse(null).shouldBeNull()
    }

    // endregion
}
