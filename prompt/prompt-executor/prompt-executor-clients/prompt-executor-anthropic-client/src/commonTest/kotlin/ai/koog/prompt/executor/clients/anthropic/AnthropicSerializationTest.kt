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
import ai.koog.test.utils.runWithBothJsonConfigurations
import ai.koog.test.utils.verifyDeserialization
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

class AnthropicSerializationTest {

    @Test
    fun `test serialization without additionalProperties`() =
        runWithBothJsonConfigurations("serialization without additionalProperties") { json ->
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
    fun `test serialization with additionalProperties`() =
        runWithBothJsonConfigurations("serialization with additionalProperties") { json ->
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

            val jsonString = json.encodeToString(AnthropicMessageRequestSerializer, request)

            jsonString shouldEqualJson
                // language=json
                """
            {
              "model": "claude-3",
              "max_tokens": 1000,
              "messages": [
                {"role": "user", "content": [{"type": "text", "text": "Hello"}]}
              ],
              "stream": false,
              "customProperty": "customValue",
              "customNumber": 42,
              "customBoolean": true
            }
                """.trimIndent()
        }

    @Test
    fun `test deserialization without additional properties`() =
        runWithBothJsonConfigurations("deserialization without additional properties") { json ->
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

            request.model shouldBe "claude-3"
            request.maxTokens shouldBe 1000
            request.temperature shouldBe 0.7
            request.additionalProperties shouldBe null
        }

    @Test
    fun `test deserialization with additional properties`() =
        runWithBothJsonConfigurations("deserialization with additional properties") { json ->
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

            request.model shouldBe "claude-3"
            request.maxTokens shouldBe 1000
            request.temperature shouldBe 0.7

            request.additionalProperties shouldNotBe null
            val additionalProps = request.additionalProperties

            additionalProps!!.size shouldBe 3
            additionalProps["customProperty"]?.jsonPrimitive?.contentOrNull shouldBe "customValue"
            additionalProps["customNumber"]?.jsonPrimitive?.intOrNull shouldBe 42
            additionalProps["customBoolean"]?.jsonPrimitive?.booleanOrNull shouldBe true
        }

    @Test
    fun `test serialization of extended parameters`() =
        runWithBothJsonConfigurations("serialization of extended parameters") { json ->
            val request = AnthropicMessageRequest(
                model = "claude-3",
                messages = listOf(
                    AnthropicMessage.User(
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

            val jsonString = json.encodeToString(AnthropicMessageRequestSerializer, request)

            jsonString shouldEqualJson
                // language=json
                """
            {
              "model": "claude-3",
              "max_tokens": 1000,
              "messages": [
                {"role": "user", "content": [{"type": "text", "text": "Hello"}]}
              ],
              "container": "container-123",
              "mcp_servers": [
                {
                  "name": "mcp-one",
                  "url": "https://mcp.example",
                  "type": "url",
                  "authorization_token": "token-abc",
                  "tool_configuration": {
                    "allowed_tools": ["weather", "news"],
                    "enabled": true
                  }
                }
              ],
              "service_tier": "auto",
              "stop_sequence": ["STOP", "END"],
              "stream": true,
              "system": [
                {"type": "text", "text": "sys-msg"}
              ],
              "temperature": 0.5,
              "thinking": {
                "type": "enabled",
                "budget_tokens": 1024
              },
              "tool_choice": {
                "type": "tool",
                "name": "weather"
              },
              "tools": [
                {
                  "name": "weather",
                  "description": "Get weather",
                  "inputSchema": {
                    "type": "object",
                    "properties": {},
                    "required": []
                  }
                }
              ],
              "topK": 1,
              "topP": 0.9
            }
                """.trimIndent()
        }

    @Test
    fun `test deserialization of extended parameters`() =
        runWithBothJsonConfigurations("deserialization of extended parameters") { json ->
            val jsonString =
                // language=json
                """
            {
              "model": "claude-3",
              "max_tokens": 1000,
              "messages": [
                {"role": "user", "content": [{"type": "text", "text": "Hello"}]}
              ],
              "container": "container-xyz",
              "mcp_servers": [
                {
                  "name": "mcp-two",
                  "url": "https://mcp2.example",
                  "type": "url",
                  "tool_configuration": {
                    "enabled": false
                  }
                }
              ],
              "service_tier": "standard_only",
              "stop_sequence": ["X"],
              "stream": false,
              "system": [
                {"type": "text", "text": "sys2"}
              ],
              "temperature": 0.3,
              "thinking": {
                "type": "disabled"
              },
              "tool_choice": {
                "type": "auto"
              },
              "tools": [
                {
                  "name": "calc",
                  "description": "Simple calc",
                  "inputSchema": {
                    "type": "object",
                    "properties": {
                      "a": {"type": "number"}
                    },
                    "required": ["a"]
                  }
                }
              ],
              "topK": 7,
              "topP": 0.75
            }
                """.trimIndent()

            val deserialized: AnthropicMessageRequest = verifyDeserialization(
                payload = jsonString,
                serializer = AnthropicMessageRequestSerializer,
                json = json
            )

            deserialized.model shouldBe "claude-3"
            deserialized.maxTokens shouldBe 1000
            deserialized.container shouldBe "container-xyz"
            deserialized.serviceTier shouldBe AnthropicServiceTier.STANDARD_ONLY
            deserialized.stopSequence shouldBe listOf("X")
            deserialized.stream shouldBe false
            deserialized.temperature shouldBe 0.3
            deserialized.topK shouldBe 7
            deserialized.topP shouldBe 0.75

            // thinking
            deserialized.thinking.shouldBeTypeOf<AnthropicThinking.Disabled>()

            // tool choice
            deserialized.toolChoice shouldBe AnthropicToolChoice.Auto

            // system
            deserialized.system?.size shouldBe 1
            deserialized.system?.get(0)?.text shouldBe "sys2"

            // tools
            deserialized.tools?.size shouldBe 1
            val t0 = deserialized.tools!![0]
            t0.name shouldBe "calc"
            t0.description shouldBe "Simple calc"
            t0.inputSchema.required shouldBe listOf("a")
            t0.inputSchema.type shouldBe "object"

            // mcp servers
            deserialized.mcpServers?.size shouldBe 1
            val s0 = deserialized.mcpServers!![0]
            s0.name shouldBe "mcp-two"
            s0.url shouldBe "https://mcp2.example"
            s0.authorizationToken shouldBe null
            s0.toolConfiguration?.enabled shouldBe false
        }
}
