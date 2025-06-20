package ai.koog.prompt.executor.clients.bedrock

import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMCapability
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.params.LLMParams
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertContains
import kotlin.test.assertFails

class BedrockLLMClientTest {
    @Test
    fun `can create BedrockLLMClient`() {
        val client = createBedrockLLMClient(
            awsAccessKeyId = "test-key",
            awsSecretAccessKey = "test-secret",
            settings = BedrockClientSettings(region = "us-east-1"),
            clock = Clock.System
        )

        assertNotNull(client)
    }

    @Test
    fun `verify bedrock provider exists`() {
        // Verify that Bedrock is a valid LLMProvider
        val provider = LLMProvider.Bedrock
        assertEquals("bedrock", provider.id)
        assertEquals("AWS Bedrock", provider.display)
    }

    @Test
    fun `verify all BedrockModels are properly configured`() {
        // Test Claude 3 models with full capabilities
        val claude3Models = listOf(
            BedrockModels.AnthropicClaude3Opus,
            BedrockModels.AnthropicClaude3Sonnet,
            BedrockModels.AnthropicClaude3Haiku
        )

        claude3Models.forEach { model ->
            assertEquals(LLMProvider.Bedrock, model.provider)
            assertTrue(model.capabilities.contains(LLMCapability.Completion))
            assertTrue(model.capabilities.contains(LLMCapability.Temperature))
            assertTrue(model.capabilities.contains(LLMCapability.Tools))
            assertTrue(model.capabilities.contains(LLMCapability.ToolChoice))
            assertTrue(model.capabilities.contains(LLMCapability.Vision.Image))
            assertTrue(model.capabilities.contains(LLMCapability.Schema.JSON.Full))
        }

        // Test Claude 3.5 models with full capabilities
        val claude35Models = listOf(
            BedrockModels.AnthropicClaude35SonnetV2,
            BedrockModels.AnthropicClaude35Haiku
        )

        claude35Models.forEach { model ->
            assertEquals(LLMProvider.Bedrock, model.provider)
            assertTrue(model.capabilities.contains(LLMCapability.Completion))
            assertTrue(model.capabilities.contains(LLMCapability.Temperature))
            assertTrue(model.capabilities.contains(LLMCapability.Tools))
            assertTrue(model.capabilities.contains(LLMCapability.ToolChoice))
            assertTrue(model.capabilities.contains(LLMCapability.Vision.Image))
            assertTrue(model.capabilities.contains(LLMCapability.Schema.JSON.Full))
        }

        // Test Claude 4 models with full capabilities
        val claude4Models = listOf(
            BedrockModels.AnthropicClaude4Opus,
            BedrockModels.AnthropicClaude4Sonnet
        )

        claude4Models.forEach { model ->
            assertEquals(LLMProvider.Bedrock, model.provider)
            assertTrue(model.capabilities.contains(LLMCapability.Completion))
            assertTrue(model.capabilities.contains(LLMCapability.Temperature))
            assertTrue(model.capabilities.contains(LLMCapability.Tools))
            assertTrue(model.capabilities.contains(LLMCapability.ToolChoice))
            assertTrue(model.capabilities.contains(LLMCapability.Vision.Image))
            assertTrue(model.capabilities.contains(LLMCapability.Schema.JSON.Full))
        }

        // Test older Claude models with standard capabilities
        val olderClaudeModels = listOf(
            BedrockModels.AnthropicClaude21,
            BedrockModels.AnthropicClaude2,
            BedrockModels.AnthropicClaudeInstant
        )

        olderClaudeModels.forEach { model ->
            assertEquals(LLMProvider.Bedrock, model.provider)
            assertTrue(model.id.startsWith("anthropic.claude"))
            assertTrue(model.capabilities.contains(LLMCapability.Completion))
            assertTrue(model.capabilities.contains(LLMCapability.Temperature))
        }

        // Test Titan models
        val titanModels = listOf(
            BedrockModels.AmazonTitanTextExpressV1,
            BedrockModels.AmazonTitanTextLiteV1,
            BedrockModels.AmazonTitanTextPremierV1
        )

        titanModels.forEach { model ->
            assertEquals(LLMProvider.Bedrock, model.provider)
            assertTrue(model.id.startsWith("amazon.titan"))
            assertTrue(model.capabilities.contains(LLMCapability.Completion))
            assertTrue(model.capabilities.contains(LLMCapability.Temperature))
        }

        // Test Mistral Large with tool support
        val mistralLarge = BedrockModels.MistralLarge
        assertEquals(LLMProvider.Bedrock, mistralLarge.provider)
        assertTrue(mistralLarge.capabilities.contains(LLMCapability.Tools))
        assertTrue(mistralLarge.capabilities.contains(LLMCapability.ToolChoice))

        // Test other Mistral models without tool support
        val otherMistralModels = listOf(
            BedrockModels.MistralMixtral8x7BInstruct,
            BedrockModels.Mistral7BInstruct,
            BedrockModels.MistralSmall
        )

        otherMistralModels.forEach { model ->
            assertEquals(LLMProvider.Bedrock, model.provider)
            assertTrue(model.id.startsWith("mistral."))
            assertTrue(!model.capabilities.contains(LLMCapability.Tools))
        }
    }

    @Test
    fun `client configuration options work correctly`() {
        val customSettings = BedrockClientSettings(
            region = "eu-west-1",
            endpointUrl = "https://custom.endpoint.com",
            maxRetries = 5,
            enableLogging = true,
            timeoutConfig = ConnectionTimeoutConfig(
                requestTimeoutMillis = 120_000,
                connectTimeoutMillis = 10_000,
                socketTimeoutMillis = 120_000
            )
        )

        val client = createBedrockLLMClient(
            awsAccessKeyId = "test-key",
            awsSecretAccessKey = "test-secret",
            settings = customSettings,
            clock = Clock.System
        )

        assertNotNull(client)
        assertEquals("eu-west-1", customSettings.region)
        assertEquals("https://custom.endpoint.com", customSettings.endpointUrl)
        assertEquals(5, customSettings.maxRetries)
        assertEquals(true, customSettings.enableLogging)
    }

    @Test
    fun `model IDs follow expected patterns`() {
        // Verify Anthropic Claude 4 model IDs
        assertTrue(BedrockModels.AnthropicClaude4Opus.id == "anthropic.claude-opus-4-20250514-v1:0")
        assertTrue(BedrockModels.AnthropicClaude4Sonnet.id == "anthropic.claude-sonnet-4-20250514-v1:0")

        // Verify Anthropic Claude 3.5 model IDs
        assertTrue(BedrockModels.AnthropicClaude35SonnetV2.id == "anthropic.claude-3-5-sonnet-20241022-v2:0")
        assertTrue(BedrockModels.AnthropicClaude35Haiku.id == "anthropic.claude-3-5-haiku-20241022-v1:0")

        // Verify Anthropic model IDs
        assertTrue(BedrockModels.AnthropicClaude3Opus.id.startsWith("anthropic.claude-3-opus"))
        assertTrue(BedrockModels.AnthropicClaude3Sonnet.id.startsWith("anthropic.claude-3-sonnet"))
        assertTrue(BedrockModels.AnthropicClaude3Haiku.id.startsWith("anthropic.claude-3-haiku"))
        assertTrue(BedrockModels.AnthropicClaude21.id == "anthropic.claude-v2:1")
        assertTrue(BedrockModels.AnthropicClaude2.id == "anthropic.claude-v2")
        assertTrue(BedrockModels.AnthropicClaudeInstant.id == "anthropic.claude-instant-v1")

        // Verify Amazon Titan model IDs
        assertTrue(BedrockModels.AmazonTitanTextExpressV1.id == "amazon.titan-text-express-v1")
        assertTrue(BedrockModels.AmazonTitanTextLiteV1.id == "amazon.titan-text-lite-v1")
        assertTrue(BedrockModels.AmazonTitanTextPremierV1.id == "amazon.titan-text-premier-v1:0")

        // Verify AI21 model IDs
        assertTrue(BedrockModels.AI21Jurassic2Ultra.id == "ai21.j2-ultra-v1")
        assertTrue(BedrockModels.AI21Jurassic2Mid.id == "ai21.j2-mid-v1")

        // Verify Cohere model IDs
        assertTrue(BedrockModels.CohereCommand.id == "cohere.command-text-v14")
        assertTrue(BedrockModels.CohereCommandLight.id == "cohere.command-light-text-v14")

        // Verify Meta Llama model IDs
        assertTrue(BedrockModels.MetaLlama2_70BChat.id == "meta.llama2-70b-chat-v1")
        assertTrue(BedrockModels.MetaLlama2_13BChat.id == "meta.llama2-13b-chat-v1")
        assertTrue(BedrockModels.MetaLlama3_8BInstruct.id == "meta.llama3-8b-instruct-v1:0")
        assertTrue(BedrockModels.MetaLlama3_70BInstruct.id == "meta.llama3-70b-instruct-v1:0")

        // Verify Mistral model IDs
        assertTrue(BedrockModels.Mistral7BInstruct.id == "mistral.mistral-7b-instruct-v0:2")
        assertTrue(BedrockModels.MistralMixtral8x7BInstruct.id == "mistral.mixtral-8x7b-instruct-v0:1")
        assertTrue(BedrockModels.MistralLarge.id == "mistral.mistral-large-2402-v1:0")
        assertTrue(BedrockModels.MistralSmall.id == "mistral.mistral-small-2402-v1:0")
    }

    @Test
    fun testToolCallGeneration() = runTest {
        val tools = listOf(
            ToolDescriptor(
                name = "get_weather",
                description = "Get current weather for a city",
                requiredParameters = listOf(
                    ToolParameterDescriptor("city", "The city name", ToolParameterType.String)
                ),
                optionalParameters = listOf(
                    ToolParameterDescriptor("units", "Temperature units", ToolParameterType.String)
                )
            )
        )

        val prompt = Prompt.build("test") {
            user("What's the weather in Paris?")
        }

        // Test that Claude 3 models support tools
        val claudeModel = BedrockModels.AnthropicClaude3Sonnet
        assertTrue(claudeModel.capabilities.contains(LLMCapability.Tools))

        // Test that Claude 3.5 models support tools (with advanced capabilities)
        val claude35Sonnet = BedrockModels.AnthropicClaude35SonnetV2
        val claude35Haiku = BedrockModels.AnthropicClaude35Haiku
        assertTrue(claude35Sonnet.capabilities.contains(LLMCapability.Tools))
        assertTrue(claude35Sonnet.capabilities.contains(LLMCapability.ToolChoice))
        assertTrue(claude35Haiku.capabilities.contains(LLMCapability.Tools))
        assertTrue(claude35Haiku.capabilities.contains(LLMCapability.ToolChoice))

        // Test that Claude 4 models support tools (with advanced capabilities)
        val claude4Opus = BedrockModels.AnthropicClaude4Opus
        val claude4Sonnet = BedrockModels.AnthropicClaude4Sonnet
        assertTrue(claude4Opus.capabilities.contains(LLMCapability.Tools))
        assertTrue(claude4Opus.capabilities.contains(LLMCapability.ToolChoice))
        assertTrue(claude4Sonnet.capabilities.contains(LLMCapability.Tools))
        assertTrue(claude4Sonnet.capabilities.contains(LLMCapability.ToolChoice))

        // Mock client for testing tool call request generation
        val client = createBedrockLLMClient(
            awsAccessKeyId = "test-key",
            awsSecretAccessKey = "test-secret",
            settings = BedrockClientSettings(region = "us-east-1"),
            clock = Clock.System
        )

        // Verify that older Claude models don't support tools
        val olderClaudeModel = BedrockModels.AnthropicClaude2
        assertFails {
            client.execute(prompt, olderClaudeModel, tools)
        }
    }

    @Test
    fun testAnthropicToolCallResponseParsing() {
        // Simulate Anthropic Claude response with tool calls
        val mockResponse = buildJsonObject {
            putJsonArray("content") {
                add(buildJsonObject {
                    put("type", "tool_use")
                    put("id", "toolu_012345")
                    put("name", "get_weather")
                    putJsonObject("input") {
                        put("city", "Paris")
                        put("units", "celsius")
                    }
                })
            }
            putJsonObject("usage") {
                put("input_tokens", 100)
                put("output_tokens", 50)
            }
            put("stop_reason", "tool_use")
        }

        // Test parsing logic (this would normally be done inside the client)
        val json = Json { ignoreUnknownKeys = true }
        val content = mockResponse["content"]?.jsonArray?.firstOrNull()?.jsonObject

        assertNotNull(content)
        assertEquals("tool_use", content["type"]?.jsonPrimitive?.content)
        assertEquals("toolu_012345", content["id"]?.jsonPrimitive?.content)
        assertEquals("get_weather", content["name"]?.jsonPrimitive?.content)

        val input = content["input"]?.jsonObject
        assertNotNull(input)
        assertEquals("Paris", input["city"]?.jsonPrimitive?.content)
        assertEquals("celsius", input["units"]?.jsonPrimitive?.content)
    }

    @Test
    fun testAnthropicMultipleToolCallsParsing() {
        val mockResponse = buildJsonObject {
            putJsonArray("content") {
                add(buildJsonObject {
                    put("type", "tool_use")
                    put("id", "toolu_001")
                    put("name", "get_weather")
                    putJsonObject("input") {
                        put("city", "London")
                    }
                })
                add(buildJsonObject {
                    put("type", "tool_use")
                    put("id", "toolu_002")
                    put("name", "calculate")
                    putJsonObject("input") {
                        put("expression", "2 + 2")
                    }
                })
            }
        }

        val json = Json { ignoreUnknownKeys = true }
        val content = mockResponse["content"]?.jsonArray

        assertNotNull(content)
        assertEquals(2, content.size)

        val firstTool = content[0].jsonObject
        assertEquals("get_weather", firstTool["name"]?.jsonPrimitive?.content)
        assertEquals("toolu_001", firstTool["id"]?.jsonPrimitive?.content)

        val secondTool = content[1].jsonObject
        assertEquals("calculate", secondTool["name"]?.jsonPrimitive?.content)
        assertEquals("toolu_002", secondTool["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun testAnthropicMixedTextAndToolResponse() {
        val mockResponse = buildJsonObject {
            putJsonArray("content") {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", "I'll help you with the weather. Let me check that for you.")
                })
                add(buildJsonObject {
                    put("type", "tool_use")
                    put("id", "toolu_123")
                    put("name", "get_weather")
                    putJsonObject("input") {
                        put("city", "Tokyo")
                    }
                })
            }
        }

        val json = Json { ignoreUnknownKeys = true }
        val content = mockResponse["content"]?.jsonArray

        assertNotNull(content)
        assertEquals(2, content.size)

        val textContent = content[0].jsonObject
        assertEquals("text", textContent["type"]?.jsonPrimitive?.content)
        assertContains(textContent["text"]?.jsonPrimitive?.content ?: "", "I'll help you")

        val toolContent = content[1].jsonObject
        assertEquals("tool_use", toolContent["type"]?.jsonPrimitive?.content)
        assertEquals("get_weather", toolContent["name"]?.jsonPrimitive?.content)
    }

    @Test
    fun testToolChoiceConfiguration() {
        val tools = listOf(
            ToolDescriptor(
                name = "search",
                description = "Search for information",
                requiredParameters = listOf(
                    ToolParameterDescriptor("query", "Search query", ToolParameterType.String)
                )
            )
        )

        // Test different tool choice configurations
        val autoPrompt = Prompt.build("test", params = LLMParams(toolChoice = LLMParams.ToolChoice.Auto)) {
            user("Search for something")
        }

        val nonePrompt = Prompt.build("test", params = LLMParams(toolChoice = LLMParams.ToolChoice.None)) {
            user("Just respond normally")
        }

        val requiredPrompt = Prompt.build("test", params = LLMParams(toolChoice = LLMParams.ToolChoice.Required)) {
            user("You must use a tool")
        }

        val namedPrompt = Prompt.build("test", params = LLMParams(toolChoice = LLMParams.ToolChoice.Named("search"))) {
            user("Use the search tool")
        }

        // Verify tool choice is properly set (this would be tested in integration)
        assertNotNull(autoPrompt.params.toolChoice)
        assertEquals(LLMParams.ToolChoice.Auto, autoPrompt.params.toolChoice)
        assertEquals(LLMParams.ToolChoice.None, nonePrompt.params.toolChoice)
        assertEquals(LLMParams.ToolChoice.Required, requiredPrompt.params.toolChoice)
        assertTrue(namedPrompt.params.toolChoice is LLMParams.ToolChoice.Named)
        assertEquals("search", (namedPrompt.params.toolChoice as LLMParams.ToolChoice.Named).name)
    }

    @Test
    fun testToolParameterTypes() {
        val complexTool = ToolDescriptor(
            name = "complex_tool",
            description = "A tool with various parameter types",
            requiredParameters = listOf(
                ToolParameterDescriptor("string_param", "A string", ToolParameterType.String),
                ToolParameterDescriptor("int_param", "An integer", ToolParameterType.Integer),
                ToolParameterDescriptor("float_param", "A float", ToolParameterType.Float),
                ToolParameterDescriptor("bool_param", "A boolean", ToolParameterType.Boolean)
            ),
            optionalParameters = listOf(
                ToolParameterDescriptor(
                    "enum_param",
                    "An enum",
                    ToolParameterType.Enum(arrayOf("option1", "option2", "option3"))
                ),
                ToolParameterDescriptor(
                    "list_param",
                    "A list of strings",
                    ToolParameterType.List(ToolParameterType.String)
                )
            )
        )

        // Verify parameter types are correctly defined
        assertEquals(4, complexTool.requiredParameters.size)
        assertEquals(2, complexTool.optionalParameters.size)

        val enumParam = complexTool.optionalParameters.find { it.name == "enum_param" }
        assertNotNull(enumParam)
        assertTrue(enumParam.type is ToolParameterType.Enum)

        val listParam = complexTool.optionalParameters.find { it.name == "list_param" }
        assertNotNull(listParam)
        assertTrue(listParam.type is ToolParameterType.List)
    }

    @Test
    fun testToolResultHandling() {
        // Test tool result message creation
        val toolResult = Message.Tool.Result(
            id = "toolu_123",
            tool = "get_weather",
            content = "The weather in Paris is 22°C and sunny",
            metaInfo = RequestMetaInfo(timestamp = Instant.parse("2023-01-01T00:00:00Z"))
        )

        assertEquals("toolu_123", toolResult.id)
        assertEquals("get_weather", toolResult.tool)
        assertContains(toolResult.content, "Paris")
        assertContains(toolResult.content, "22°C")
    }

    @Test
    fun testModelToolCapabilities() {
        // Verify Claude 4 models have the most advanced capabilities
        val claude4Opus = BedrockModels.AnthropicClaude4Opus
        val claude4Sonnet = BedrockModels.AnthropicClaude4Sonnet
        assertTrue(claude4Opus.capabilities.contains(LLMCapability.Tools))
        assertTrue(claude4Opus.capabilities.contains(LLMCapability.ToolChoice))
        assertTrue(claude4Opus.capabilities.contains(LLMCapability.Vision.Image))
        assertTrue(claude4Opus.capabilities.contains(LLMCapability.Schema.JSON.Full))
        assertTrue(claude4Sonnet.capabilities.contains(LLMCapability.Tools))
        assertTrue(claude4Sonnet.capabilities.contains(LLMCapability.ToolChoice))
        assertTrue(claude4Sonnet.capabilities.contains(LLMCapability.Vision.Image))
        assertTrue(claude4Sonnet.capabilities.contains(LLMCapability.Schema.JSON.Full))

        // Verify Claude 3.5 models have the most comprehensive tool support
        val claude35Sonnet = BedrockModels.AnthropicClaude35SonnetV2
        val claude35Haiku = BedrockModels.AnthropicClaude35Haiku
        assertTrue(claude35Sonnet.capabilities.contains(LLMCapability.Tools))
        assertTrue(claude35Sonnet.capabilities.contains(LLMCapability.ToolChoice))
        assertTrue(claude35Haiku.capabilities.contains(LLMCapability.Tools))
        assertTrue(claude35Haiku.capabilities.contains(LLMCapability.ToolChoice))

        // Verify Mistral Large also supports tools
        val mistralLarge = BedrockModels.MistralLarge
        assertTrue(mistralLarge.capabilities.contains(LLMCapability.Tools))
        assertTrue(mistralLarge.capabilities.contains(LLMCapability.ToolChoice))

        // Verify other Mistral models don't support tools
        val mistral7B = BedrockModels.Mistral7BInstruct
        assertTrue(!mistral7B.capabilities.contains(LLMCapability.Tools))

        // Verify Titan models don't support tools
        val titanExpress = BedrockModels.AmazonTitanTextExpressV1
        assertTrue(!titanExpress.capabilities.contains(LLMCapability.Tools))
    }
}
