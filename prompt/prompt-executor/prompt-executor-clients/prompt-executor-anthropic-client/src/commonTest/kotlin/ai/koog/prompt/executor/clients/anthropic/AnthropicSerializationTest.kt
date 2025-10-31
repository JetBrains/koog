package ai.koog.prompt.executor.clients.anthropic

import ai.koog.prompt.executor.clients.anthropic.models.AnthropicContent
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicMCPServerURLDefinition
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicMessage
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicMessageRequest
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicMessageRequestSerializer
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicServiceTier
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicThinking
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicTool
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicToolChoice
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicToolConfiguration
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicToolSchema
import ai.koog.prompt.executor.clients.anthropic.models.SystemAnthropicMessage
import ai.koog.test.utils.verifyDeserialization
import io.kotest.assertions.json.shouldEqualJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnthropicSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true // Ensure default values are included in serialization
        explicitNulls = false
    }

    @Test
    fun `test serialization without additionalProperties`() {
        val request = AnthropicMessageRequest(
            model = "claude-3",
            messages = listOf(
                AnthropicMessage.User(
                    content = listOf(AnthropicContent.Text("Hello, Claude"))
                )
            ),
            maxTokens = 1000,
            temperature = 0.7
        )

        val jsonString = json.encodeToString(AnthropicMessageRequestSerializer, request)

        jsonString shouldEqualJson
            // language=json
            """
            {
                "model": "claude-3",
                "max_tokens": 1000,
                "messages": [
                    {"role": "user", "content": [{ "type": "text", "text": "Hello, Claude"}]}
                ],
                "temperature": 0.7,
                "stream": false
            }
            """.trimIndent()
    }

    @Test
    fun `test serialization with additionalProperties`() {
        val additionalProperties = mapOf<String, JsonElement>(
            "customProperty" to JsonPrimitive("customValue"),
            "customNumber" to JsonPrimitive(42),
            "customBoolean" to JsonPrimitive(true)
        )

        val request = AnthropicMessageRequest(
            model = "claude-3",
            messages = listOf(
                AnthropicMessage.User(
                    content = listOf(AnthropicContent.Text("Hello"))
                )
            ),
            maxTokens = 1000,
            additionalProperties = additionalProperties
        )

        val jsonElement = json.encodeToJsonElement(AnthropicMessageRequestSerializer, request)
        val jsonObject = jsonElement.jsonObject

        // Standard properties should be present
        assertEquals("claude-3", jsonObject["model"]?.jsonPrimitive?.contentOrNull)
        assertEquals(1000, jsonObject["max_tokens"]?.jsonPrimitive?.intOrNull)

        // Additional properties should be flattened to root level
        assertEquals("customValue", jsonObject["customProperty"]?.jsonPrimitive?.contentOrNull)
        assertEquals(42, jsonObject["customNumber"]?.jsonPrimitive?.intOrNull)
        assertEquals(true, jsonObject["customBoolean"]?.jsonPrimitive?.booleanOrNull)

        // additionalProperties field itself should not be present in serialized JSON
        assertNull(jsonObject["additionalProperties"])
    }

    @Test
    fun `test deserialization without additional properties`() {
        val jsonString =
            // language=json
            """
            {
                "model": "claude-3",
                "max_tokens": 1000,
                "messages": [
                    {"role": "user", "content": [{ "type": "text", "text": "Hello, Claude"}]}
                ],
                "temperature": 0.7,
                "stream": false
            }
            """.trimIndent()

        val request: AnthropicMessageRequest = verifyDeserialization(
            payload = jsonString,
            serializer = AnthropicMessageRequestSerializer,
            json = json
        )

        assertEquals("claude-3", request.model)
        assertEquals(1000, request.maxTokens)
        assertEquals(0.7, request.temperature)
        assertNull(request.additionalProperties)
    }

    @Test
    fun `test deserialization with additional properties`() {
        val jsonString =
            // language=json
            """
            {
                "model": "claude-3",
                "max_tokens": 1000,
                "messages": [
                    {"role": "user", "content": [{ "type": "text", "text": "Hello, Claude"}]}
                ],
                "temperature": 0.7,
                "stream": false,
                "customProperty": "customValue",
                "customNumber": 42,
                "customBoolean": true
            }
            """.trimIndent()

        val request: AnthropicMessageRequest = verifyDeserialization(
            payload = jsonString,
            serializer = AnthropicMessageRequestSerializer,
            json = json
        )

        assertEquals("claude-3", request.model)
        assertEquals(1000, request.maxTokens)

        assertNotNull(request.additionalProperties)
        val additionalProps = request.additionalProperties
        assertEquals(3, additionalProps.size)
        assertEquals("customValue", additionalProps["customProperty"]?.jsonPrimitive?.contentOrNull)
        assertEquals(42, additionalProps["customNumber"]?.jsonPrimitive?.intOrNull)
        assertEquals(true, additionalProps["customBoolean"]?.jsonPrimitive?.booleanOrNull)
    }

    @Test
    fun `test round trip serialization with additionalProperties`() {
        val originalAdditionalProperties = mapOf<String, JsonElement>(
            "customProperty" to JsonPrimitive("customValue"),
            "customNumber" to JsonPrimitive(42)
        )

        val originalRequest = AnthropicMessageRequest(
            model = "claude-3",
            messages = listOf(
                AnthropicMessage.User(
                    content = listOf(AnthropicContent.Text("Hello"))
                )
            ),
            maxTokens = 1000,
            additionalProperties = originalAdditionalProperties
        )

        // Serialize to JSON string
        val jsonString = json.encodeToString(AnthropicMessageRequestSerializer, originalRequest)

        // Deserialize back to object
        val deserializedRequest = json.decodeFromString(AnthropicMessageRequestSerializer, jsonString)

        // Verify standard properties
        assertEquals(originalRequest.model, deserializedRequest.model)
        assertEquals(originalRequest.maxTokens, deserializedRequest.maxTokens)
        assertEquals(originalRequest.messages.size, deserializedRequest.messages.size)

        // Verify additional properties were preserved
        assertNotNull(deserializedRequest.additionalProperties)
        val deserializedAdditionalProps = deserializedRequest.additionalProperties
        assertEquals(originalAdditionalProperties.size, deserializedAdditionalProps.size)
        assertEquals(
            (originalAdditionalProperties["customProperty"] as JsonPrimitive).content,
            (deserializedAdditionalProps["customProperty"] as JsonPrimitive).content
        )
        assertEquals(
            originalAdditionalProperties["customNumber"]?.jsonPrimitive?.intOrNull,
            deserializedAdditionalProps["customNumber"]?.jsonPrimitive?.intOrNull
        )
    }

    @Test
    fun `test serialization of extended parameters`() {
        val request = AnthropicMessageRequest(
            model = "claude-3",
            messages = listOf(
                AnthropicMessage(
                    role = "user",
                    content = listOf(AnthropicContent.Text("Hello"))
                )
            ),
            maxTokens = 1000,
            container = "container-123",
            mcpServers = listOf(
                AnthropicMCPServerURLDefinition(
                    name = "mcp-one",
                    url = "https://mcp.example",
                    authorizationToken = "token-abc",
                    toolConfiguration = AnthropicToolConfiguration(
                        allowedTools = listOf("weather", "news"),
                        enabled = true
                    )
                )
            ),
            serviceTier = AnthropicServiceTier.AUTO,
            stopSequence = listOf("STOP", "END"),
            stream = true,
            system = listOf(
                SystemAnthropicMessage("sys-msg")
            ),
            temperature = 0.5,
            thinking = AnthropicThinking.Enabled(budgetTokens = 1024),
            toolChoice = AnthropicToolChoice.Tool(name = "weather"),
            tools = listOf(
                AnthropicTool(
                    name = "weather",
                    description = "Get weather",
                    inputSchema = AnthropicToolSchema(
                        properties = JsonObject(emptyMap()),
                        required = emptyList()
                    )
                )
            ),
            topK = 1,
            topP = 0.9,
        )

        val element = json.encodeToJsonElement(AnthropicMessageRequestSerializer, request).jsonObject

        // Simple primitives and lists
        assertEquals("claude-3", element["model"]?.jsonPrimitive?.contentOrNull)
        assertEquals(1000, element["max_tokens"]?.jsonPrimitive?.intOrNull)
        assertEquals("container-123", element["container"]?.jsonPrimitive?.contentOrNull)
        assertEquals("0.5", element["temperature"]?.jsonPrimitive?.contentOrNull)
        assertEquals(true, element["stream"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(1, element["topK"]?.jsonPrimitive?.intOrNull)
        assertEquals("0.9", element["topP"]?.jsonPrimitive?.contentOrNull)

        // stop_sequence
        val stopSeq = element["stop_sequence"]?.let { it as? JsonArray }
        assertNotNull(stopSeq)
        assertEquals(listOf("STOP", "END"), stopSeq.map { it.jsonPrimitive.content })

        // system messages
        val systemArr = element["system"]?.let { it as? JsonArray }
        assertNotNull(systemArr)
        val sys0 = systemArr[0].jsonObject
        assertEquals("text", sys0["type"]?.jsonPrimitive?.contentOrNull)
        assertEquals("sys-msg", sys0["text"]?.jsonPrimitive?.contentOrNull)

        // service_tier
        assertEquals("auto", element["service_tier"]?.jsonPrimitive?.contentOrNull)

        // thinking
        val thinking = element["thinking"]?.jsonObject
        assertNotNull(thinking)
        assertEquals("enabled", thinking["type"]?.jsonPrimitive?.contentOrNull)
        assertEquals(1024, thinking["budget_tokens"]?.jsonPrimitive?.intOrNull)

        // tool_choice
        val toolChoice = element["tool_choice"]?.jsonObject
        assertNotNull(toolChoice)
        assertEquals("tool", toolChoice["type"]?.jsonPrimitive?.contentOrNull)
        assertEquals("weather", toolChoice["name"]?.jsonPrimitive?.contentOrNull)

        // tools
        val toolsArr = element["tools"]?.let { it as? JsonArray }
        assertNotNull(toolsArr)
        val tool0 = toolsArr[0].jsonObject
        assertEquals("weather", tool0["name"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Get weather", tool0["description"]?.jsonPrimitive?.contentOrNull)
        val schema = (tool0["input_schema"] ?: tool0["inputSchema"])?.jsonObject
        assertNotNull(schema)
        assertEquals("object", schema["type"]?.jsonPrimitive?.contentOrNull)

        // mcp_servers
        val mcp = element["mcp_servers"]?.let { it as? JsonArray }
        assertNotNull(mcp)
        val mcp0 = mcp[0].jsonObject
        assertEquals("mcp-one", mcp0["name"]?.jsonPrimitive?.contentOrNull)
        assertEquals("https://mcp.example", mcp0["url"]?.jsonPrimitive?.contentOrNull)
        assertEquals("url", mcp0["type"]?.jsonPrimitive?.contentOrNull)
        assertEquals("token-abc", mcp0["authorization_token"]?.jsonPrimitive?.contentOrNull)
        val toolCfg = mcp0["tool_configuration"]?.jsonObject
        assertNotNull(toolCfg)
        val allowed = toolCfg["allowed_tools"] as JsonArray
        assertEquals(listOf("weather", "news"), allowed.map { it.jsonPrimitive.content })
        assertEquals(true, toolCfg["enabled"]?.jsonPrimitive?.booleanOrNull)
    }

    @Test
    fun `test deserialization serialization of extended parameters`() {
        val original = AnthropicMessageRequest(
            model = "claude-3",
            messages = listOf(
                AnthropicMessage(
                    role = "user",
                    content = listOf(AnthropicContent.Text("Hello"))
                )
            ),
            maxTokens = 1000,
            container = "container-xyz",
            mcpServers = listOf(
                AnthropicMCPServerURLDefinition(
                    name = "mcp-two",
                    url = "https://mcp2.example",
                    authorizationToken = null,
                    toolConfiguration = AnthropicToolConfiguration(
                        allowedTools = null,
                        enabled = false
                    )
                )
            ),
            serviceTier = AnthropicServiceTier.STANDARD_ONLY,
            stopSequence = listOf("X"),
            stream = false,
            system = listOf(
                SystemAnthropicMessage("sys2")
            ),
            temperature = 0.3,
            thinking = AnthropicThinking.Disabled(),
            toolChoice = AnthropicToolChoice.Auto,
            tools = listOf(
                AnthropicTool(
                    name = "calc",
                    description = "Simple calc",
                    inputSchema = AnthropicToolSchema(
                        properties = JsonObject(
                            mapOf(
                                "a" to JsonObject(mapOf("type" to JsonPrimitive("number")))
                            )
                        ),
                        required = listOf("a")
                    )
                )
            ),
            topK = 7,
            topP = 0.75,
        )

        val jsonStr = json.encodeToString(AnthropicMessageRequestSerializer, original)
        val deserialized = json.decodeFromString(AnthropicMessageRequestSerializer, jsonStr)

        assertEquals(original.model, deserialized.model)
        assertEquals(original.maxTokens, deserialized.maxTokens)
        assertEquals(original.container, deserialized.container)
        assertEquals(original.serviceTier, deserialized.serviceTier)
        assertEquals(original.stopSequence, deserialized.stopSequence)
        assertEquals(original.stream, deserialized.stream)
        assertEquals(original.temperature, deserialized.temperature)
        assertEquals(original.topK, deserialized.topK)
        assertEquals(original.topP, deserialized.topP)

        // thinking
        assertTrue(deserialized.thinking is AnthropicThinking.Disabled)

        // tool choice
        assertTrue(deserialized.toolChoice is AnthropicToolChoice.Auto)

        // system
        assertEquals(1, deserialized.system?.size)
        assertEquals("sys2", deserialized.system?.get(0)?.text)

        // tools
        assertEquals(1, deserialized.tools?.size)
        val t0 = deserialized.tools!![0]
        assertEquals("calc", t0.name)
        assertEquals("Simple calc", t0.description)
        assertEquals(listOf("a"), t0.inputSchema.required)
        assertEquals("object", t0.inputSchema.type)

        // mcp servers
        assertEquals(1, deserialized.mcpServers?.size)
        val s0 = deserialized.mcpServers!![0]
        assertEquals("mcp-two", s0.name)
        assertEquals("https://mcp2.example", s0.url)
        assertEquals(null, s0.authorizationToken)
        assertEquals(false, s0.toolConfiguration?.enabled)
    }
}
