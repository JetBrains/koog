package ai.koog.prompt

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class PromptTest {
    companion object {
        val systemMessage = "You are a helpful assistant with many capabilities"
        val assistantMessage = "I'm here to help!"
        val userMessage = "Can you help me calculate 5 + 3?"
        val speculationMessage = "The result is 8"
        val toolCallId = "tool_call_123"
        val toolName = "calculator"
        val toolCallContent = """{"operation": "add", "a": 5, "b": 3}"""
        val toolResultContent = "8"
        val finishReason = "stop"

        val simpleSchemaName = "simple-schema"
        val simpleSchema = buildJsonObject {
            put("type", "string")
        }

        val fullSchemaName = "full-schema"
        val fullSchema = buildJsonObject {
            put("type", "object")
            put("required", true)
        }

        val basicPrompt = Prompt.Companion.build("test") {
            system(systemMessage)
            user(userMessage)
            message(Message.Assistant(assistantMessage, finishReason))
            tool {
                call(Message.Tool.Call(toolCallId, toolName, toolCallContent))
                result(Message.Tool.Result(toolCallId, toolName, toolResultContent))
            }
        }

        @JvmStatic
        fun toolChoiceSerializationProvider(): Stream<Array<Any>> = Stream.of(
            arrayOf("Auto", LLMParams.ToolChoice.Auto),
            arrayOf("Required", LLMParams.ToolChoice.Required),
            arrayOf("Named", LLMParams.ToolChoice.Named(toolName)),
            arrayOf("None", LLMParams.ToolChoice.None)
        )

        @JvmStatic
        fun schemaSerializationProvider(): Stream<Array<Any>> = Stream.of(
            arrayOf(
                "Simple JSON Schema",
                LLMParams.Schema.JSON.Simple(simpleSchemaName, simpleSchema),
                simpleSchemaName,
                LLMParams.Schema.JSON.Simple::class.java
            ),
            arrayOf(
                "Full JSON Schema",
                LLMParams.Schema.JSON.Full(fullSchemaName, fullSchema),
                fullSchemaName,
                LLMParams.Schema.JSON.Full::class.java
            )
        )
    }

    @Test
    fun testPromptBuilding() {
        val assistantMessage = "Hi! How can I help you?"
        val toolCallId = "tool_call_dummy_123"
        val toolName = "search"
        val toolContent = "Searching for information..."
        val toolResult = "Found some results"

        val prompt = Prompt.Companion.build("test") {
            system(systemMessage)
            user(userMessage)
            assistant(assistantMessage)
            tool {
                call(Message.Tool.Call(toolCallId, toolName, toolContent))
                result(Message.Tool.Result(toolCallId, toolName, toolResult))
            }
        }

        assertEquals(5, prompt.messages.size)
        assertTrue(prompt.messages[0] is Message.System)
        assertTrue(prompt.messages[1] is Message.User)
        assertTrue(prompt.messages[2] is Message.Assistant)
        assertTrue(prompt.messages[3] is Message.Tool.Call)
        assertTrue(prompt.messages[4] is Message.Tool.Result)

        assertEquals(systemMessage, prompt.messages[0].content)
        assertEquals(userMessage, prompt.messages[1].content)
        assertEquals(assistantMessage, prompt.messages[2].content)
        assertEquals(toolContent, prompt.messages[3].content)
        assertEquals(toolResult, prompt.messages[4].content)
        assertEquals(toolName, (prompt.messages[3] as Message.Tool.Call).tool)
        assertEquals(toolName, (prompt.messages[4] as Message.Tool.Result).tool)
    }

    @Test
    fun testBasicSerialization() {
        val json = Json.encodeToString(basicPrompt)
        val decoded = Json.decodeFromString<Prompt>(json)

        assertEquals(basicPrompt, decoded)
        assertEquals(basicPrompt.messages.size, decoded.messages.size)
        for (i in basicPrompt.messages.indices) {
            assertTrue(decoded.messages[i].role == basicPrompt.messages[i].role)
        }
    }

    @Test
    fun testPromptSerialization() {
        val prompt = basicPrompt.withUpdatedParams {
            temperature = 0.7
            speculation = speculationMessage
            schema = LLMParams.Schema.JSON.Simple(simpleSchemaName, simpleSchema)
            toolChoice = LLMParams.ToolChoice.Auto
        }

        val encodedPrompt = Json.encodeToString(prompt)
        val decodedPrompt = Json.decodeFromString<Prompt>(encodedPrompt)

        assertEquals(prompt, decodedPrompt)
        assertEquals(prompt.messages.size, decodedPrompt.messages.size)
        assertEquals(0.7, decodedPrompt.params.temperature)
        assertEquals(speculationMessage, decodedPrompt.params.speculation)
        assertTrue(decodedPrompt.params.schema is LLMParams.Schema.JSON.Simple)
        assertEquals(simpleSchemaName, decodedPrompt.params.schema?.name)
        assertTrue(decodedPrompt.params.toolChoice is LLMParams.ToolChoice.Auto)

        decodedPrompt.messages.forEachIndexed { index, decodedMessage ->
            assertTrue(decodedMessage.role == prompt.messages[index].role)
            assertTrue(decodedMessage.content == prompt.messages[index].content)
            if (decodedMessage.role == Message.Role.Assistant) {
                assertTrue(
                    (decodedMessage as Message.Assistant).finishReason ==
                            (prompt.messages[index] as Message.Assistant).finishReason
                )
            }

            if (decodedMessage.role == Message.Role.Tool) {
                if (decodedMessage is Message.Tool.Call) {
                    val originalToolMessage = prompt.messages[index] as Message.Tool.Call
                    val decodedToolMessage = decodedMessage as Message.Tool.Call
                    assertTrue(decodedToolMessage.id == originalToolMessage.id)
                    assertTrue(decodedToolMessage.tool == originalToolMessage.tool)
                    assertTrue(decodedToolMessage.content == originalToolMessage.content)
                } else if (decodedMessage is Message.Tool.Result) {
                    val originalToolMessage = prompt.messages[index] as Message.Tool.Result
                    val decodedToolMessage = decodedMessage as Message.Tool.Result
                    assertTrue(decodedToolMessage.id == originalToolMessage.id)
                    assertTrue(decodedToolMessage.tool == originalToolMessage.tool)
                    assertTrue(decodedToolMessage.content == originalToolMessage.content)
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("schemaSerializationProvider")
    fun testSchemaSerialization(name: String, schema: LLMParams.Schema, schemaName: String, schemaClass: Class<*>) {
        val prompt = basicPrompt.withUpdatedParams {
            this.schema = schema
        }

        val schemaJson = Json.encodeToString(prompt)
        val decodedSchema = Json.decodeFromString<Prompt>(schemaJson)

        assertEquals(prompt, decodedSchema)
        assertEquals(prompt.messages.size, decodedSchema.messages.size)
        assertTrue(schemaClass.isInstance(decodedSchema.params.schema))
        assertEquals(schemaName, decodedSchema.params.schema?.name)

        decodedSchema.messages.forEachIndexed { index, decodedMessage ->
            assertTrue(decodedMessage.role == prompt.messages[index].role)
            assertTrue(decodedMessage.content == prompt.messages[index].content)
        }
    }

    @ParameterizedTest
    @MethodSource("toolChoiceSerializationProvider")
    fun testToolChoiceSerialization(name: String, toolChoiceOption: LLMParams.ToolChoice) {
        val prompt = basicPrompt.withUpdatedParams {
            toolChoice = toolChoiceOption
        }
        val toolChoiceJson = Json.encodeToString(prompt)
        val decodedToolChoice = Json.decodeFromString<Prompt>(toolChoiceJson)

        assertEquals(prompt, decodedToolChoice)
        assertEquals(prompt.messages.size, decodedToolChoice.messages.size)
        assertTrue(decodedToolChoice.params.toolChoice == toolChoiceOption)
        if (toolChoiceOption is LLMParams.ToolChoice.Named) {
            assertEquals(toolName, (decodedToolChoice.params.toolChoice as LLMParams.ToolChoice.Named).name)
        }

        decodedToolChoice.messages.forEachIndexed { index, decodedMessage ->
            assertTrue(decodedMessage.role == prompt.messages[index].role)
            assertTrue(decodedMessage.content == prompt.messages[index].content)
        }
    }

    @Test
    fun testUpdatePromptWithNewMessages() {
        val systemMessage = "You are a coding assistant"
        val userMessage = "Help me with Kotlin"
        val assistantMessage = "I'll help you with Kotlin programming"

        val newMessages = listOf(
            Message.System(systemMessage),
            Message.User(userMessage),
            Message.Assistant(assistantMessage)
        )

        val updatedPrompt = basicPrompt.withMessages(newMessages)

        assertEquals(3, updatedPrompt.messages.size)
        assertEquals(systemMessage, updatedPrompt.messages[0].content)
        assertEquals(userMessage, updatedPrompt.messages[1].content)
        assertEquals(assistantMessage, updatedPrompt.messages[2].content)
    }

    @Test
    fun testUpdatePromptWithNewParams() {
        val speculation = "test speculation"
        val schemaName = "test-schema"
        val newParams = LLMParams(
            temperature = 0.7,
            speculation = speculation,
            schema = LLMParams.Schema.JSON.Simple(
                schemaName,
                buildJsonObject { put("type", "string") }
            ),
            toolChoice = LLMParams.ToolChoice.Auto
        )

        val updatedPrompt = basicPrompt.withParams(newParams)

        assertEquals(0.7, updatedPrompt.params.temperature)
        assertEquals(speculation, updatedPrompt.params.speculation)
        assertTrue(updatedPrompt.params.schema is LLMParams.Schema.JSON.Simple)
        assertEquals(schemaName, updatedPrompt.params.schema?.name)
        assertTrue(updatedPrompt.params.toolChoice is LLMParams.ToolChoice.Auto)
    }

    @Test
    fun testUpdatePromptWithUpdatedMessages() {
        val assistantMessage = "Hi there! How can I assist you today?"
        val userMessage = "I need help with coding"

        val updatedPrompt = basicPrompt.withUpdatedMessages {
            add(Message.Assistant(assistantMessage))
            add(Message.User(userMessage))
        }

        assertEquals(7, updatedPrompt.messages.size)
        assertTrue(updatedPrompt.messages[0] is Message.System)
        assertTrue(updatedPrompt.messages[1] is Message.User)
        assertTrue(updatedPrompt.messages[2] is Message.Assistant)
        assertTrue(updatedPrompt.messages[3] is Message.Tool.Call)
        assertTrue(updatedPrompt.messages[4] is Message.Tool.Result)
        assertTrue(updatedPrompt.messages[5] is Message.Assistant)
        assertTrue(updatedPrompt.messages[6] is Message.User)
        assertEquals(assistantMessage, updatedPrompt.messages[5].content)
        assertEquals(userMessage, updatedPrompt.messages[6].content)
    }

    @Test
    fun testUpdatePromptWithUpdatedParams() {
        val newSpeculation = "improved speculation"
        val schemaName = "full-schema"
        val updatedPrompt = basicPrompt.withUpdatedParams {
            temperature = 0.8
            speculation = newSpeculation
            schema = LLMParams.Schema.JSON.Full(
                schemaName,
                buildJsonObject {
                    put("type", "object")
                    put("required", true)
                }
            )
            toolChoice = LLMParams.ToolChoice.Required
        }

        assertEquals(0.8, updatedPrompt.params.temperature)
        assertEquals(newSpeculation, updatedPrompt.params.speculation)
        assertTrue(updatedPrompt.params.schema is LLMParams.Schema.JSON.Full)
        assertEquals(schemaName, updatedPrompt.params.schema?.name)
        assertTrue(updatedPrompt.params.toolChoice is LLMParams.ToolChoice.Required)
    }
}
