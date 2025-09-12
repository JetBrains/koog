package ai.koog.prompt.executor.clients.anthropic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class DataModelTest {

    @Test
    fun `AnthropicTool serializes inputSchema as input_schema`() {
        val tool = AnthropicTool(
            name = "get_weather",
            description = "Get current weather for a city",
            inputSchema = AnthropicToolSchema(
                properties = buildJsonObject {
                    put("city", JsonObject(mapOf("type" to JsonPrimitive("string"))))
                    put("units", JsonObject(mapOf("type" to JsonPrimitive("string"))))
                },
                required = listOf("city")
            )
        )
        // Serialize the tool to JSON
        val json = Json { encodeDefaults = true }
        val jsonString = json.encodeToString(AnthropicTool.serializer(), tool)
        // Verify that the serialized JSON contains "input_schema" instead of "inputSchema"
        assertTrue(jsonString.contains("\"input_schema\""))
        assertFalse(jsonString.contains("\"inputSchema\""))
    }

    @Test
    fun `AnthropicMessageRequest serializes correctly`() {
        val request = AnthropicMessageRequest(
            model = "test-model",
            messages = listOf(
                AnthropicMessage(
                    role = "user",
                    content = listOf(AnthropicContent.Text("Hello"))
                )
            ),
            maxTokens = 100,
            temperature = 0.5,
            system = null,
            tools = null,
            stream = false,
            toolChoice = null
        )
        // Serialize the request to JSON
        val json = Json { encodeDefaults = true }
        val jsonString = json.encodeToString(AnthropicMessageRequest.serializer(), request)
        // Verify the JSON structure
        assertTrue(jsonString.contains("\"model\":\"test-model\""))
        assertTrue(jsonString.contains("\"maxTokens\":100"))
        assertTrue(jsonString.contains("\"temperature\":0.5"))
    }
}
