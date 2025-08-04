package ai.koog.prompt

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PromptTest {
    companion object {
        val ts: Instant = Instant.parse("2023-01-01T00:00:00Z")

        val testClock: Clock = object : Clock {
            override fun now(): Instant = ts
        }

        val testRespMetaInfo = ResponseMetaInfo.create(testClock)
        val testReqMetaInfo = RequestMetaInfo.create(testClock)

        val promptId = "test-id"
        val systemMessage = "You are a helpful assistant with many capabilities"
        val assistantMessage = "I'm here to help!"
        val userMessage = "Can you help me calculate 5 + 3?"
        val speculationMessage = "The result is 8"
        val toolCallId = "tool_call_123"
        val toolName = "calculator"
        val toolCallContent = """{"operation": "add", "a": 5, "b": 3}"""
        val toolResultContent = "8"
        val finishReason = "stop"
        val emptyName = ""

        val simpleSchemaName = "simple-schema"
        val simpleSchema = buildJsonObject {
            put("type", "string")
        }

        val fullSchemaName = "full-schema"
        val fullSchema = buildJsonObject {
            put("type", "object")
            put("required", true)
        }

        val basicPrompt = Prompt.build("test", clock = testClock) {
            system(systemMessage)
            user(userMessage)
            message(
                Message.Assistant(
                    content = assistantMessage,
                    metaInfo = testRespMetaInfo,
                    finishReason = finishReason
                )
            )
            tool {
                call(toolCallId, toolName, toolCallContent)
                result(toolCallId, toolName, toolResultContent)
            }
        }

        @JvmStatic
        fun toolChoiceSerializationProvider(): Stream<Array<Any>> = Stream.of(
            arrayOf(LLMParams.ToolChoice.Auto),
            arrayOf(LLMParams.ToolChoice.Required),
            arrayOf(LLMParams.ToolChoice.Named(toolName)),
            arrayOf(LLMParams.ToolChoice.None)
        )

        @JvmStatic
        fun schemaSerializationProvider(): Stream<Array<Any>> = Stream.of(
            arrayOf(
                LLMParams.Schema.JSON.Simple(simpleSchemaName, simpleSchema),
                simpleSchemaName,
                LLMParams.Schema.JSON.Simple::class.java
            ),
            arrayOf(
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

        val prompt = Prompt.build("test") {
            system(systemMessage)
            user(userMessage)
            assistant(assistantMessage)
            tool {
                call(toolCallId, toolName, toolContent)
                result(toolCallId, toolName, toolResult)
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
            user = "test_user"
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
        assertEquals("test_user", decodedPrompt.params.user)

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
                    val decodedToolMessage = decodedMessage
                    assertTrue(decodedToolMessage.id == originalToolMessage.id)
                    assertTrue(decodedToolMessage.tool == originalToolMessage.tool)
                    assertTrue(decodedToolMessage.content == originalToolMessage.content)
                } else if (decodedMessage is Message.Tool.Result) {
                    val originalToolMessage = prompt.messages[index] as Message.Tool.Result
                    val decodedToolMessage = decodedMessage
                    assertTrue(decodedToolMessage.id == originalToolMessage.id)
                    assertTrue(decodedToolMessage.tool == originalToolMessage.tool)
                    assertTrue(decodedToolMessage.content == originalToolMessage.content)
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("schemaSerializationProvider")
    fun testSchemaSerialization(schema: LLMParams.Schema, schemaName: String, schemaClass: Class<*>) {
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
    fun testToolChoiceSerialization(toolChoiceOption: LLMParams.ToolChoice) {
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
            Message.System(systemMessage, testReqMetaInfo),
            Message.User(userMessage, testReqMetaInfo),
            Message.Assistant(assistantMessage, testRespMetaInfo)
        )

        val updatedPrompt = basicPrompt.withMessages { newMessages }

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
            toolChoice = LLMParams.ToolChoice.Auto,
            user = "test_user"
        )

        val updatedPrompt = basicPrompt.withParams(newParams)

        assertEquals(0.7, updatedPrompt.params.temperature)
        assertEquals(speculation, updatedPrompt.params.speculation)
        assertTrue(updatedPrompt.params.schema is LLMParams.Schema.JSON.Simple)
        assertEquals(schemaName, updatedPrompt.params.schema?.name)
        assertTrue(updatedPrompt.params.toolChoice is LLMParams.ToolChoice.Auto)
        assertEquals("test_user", updatedPrompt.params.user)
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
            user = "updated_user"
        }

        assertEquals(0.8, updatedPrompt.params.temperature)
        assertEquals(newSpeculation, updatedPrompt.params.speculation)
        assertTrue(updatedPrompt.params.schema is LLMParams.Schema.JSON.Full)
        assertEquals(schemaName, updatedPrompt.params.schema?.name)
        assertTrue(updatedPrompt.params.toolChoice is LLMParams.ToolChoice.Required)
        assertEquals("updated_user", updatedPrompt.params.user)
    }

    @Test
    fun testEmptyPrompt() {
        val emptyPrompt = Prompt.Empty

        assertTrue(emptyPrompt.messages.isEmpty())
        assertEquals("", emptyPrompt.id)
        assertEquals(LLMParams(), emptyPrompt.params)

        val json = Json.encodeToString(emptyPrompt)
        val decoded = Json.decodeFromString<Prompt>(json)

        assertEquals(emptyPrompt, decoded)
        assertTrue(decoded.messages.isEmpty())
        assertEquals("", decoded.id)
    }

    @Test
    fun testPromptWithEmptyMessages() {
        val prompt = Prompt(emptyList(), promptId)

        assertTrue(prompt.messages.isEmpty())
        assertEquals(promptId, prompt.id)

        val json = Json.encodeToString(prompt)
        val decoded = Json.decodeFromString<Prompt>(json)

        assertEquals(prompt, decoded)
        assertTrue(decoded.messages.isEmpty())
    }

    @Test
    fun testMessageWithEmptyContent() {
        val emptySystemMessage = Message.System("", testReqMetaInfo)
        val emptyUserMessage = Message.User("", testReqMetaInfo)
        val emptyAssistantMessage = Message.Assistant("", testRespMetaInfo)
        val emptyToolCallMessage = Message.Tool.Call(toolCallId, toolName, "", testRespMetaInfo)
        val emptyToolResultMessage = Message.Tool.Result(toolCallId, toolName, "", testReqMetaInfo)

        assertEquals("", emptySystemMessage.content)
        assertEquals("", emptyUserMessage.content)
        assertEquals("", emptyAssistantMessage.content)
        assertEquals("", emptyToolCallMessage.content)
        assertEquals("", emptyToolResultMessage.content)

        val prompt = Prompt.build(promptId) {
            system("")
            user("")
            assistant("")
            tool {
                call(toolCallId, toolName, "")
                result(toolCallId, toolName, "")
            }
        }

        assertEquals(5, prompt.messages.size)
        prompt.messages.forEach { message ->
            assertEquals("", message.content)
        }

        val json = Json.encodeToString(prompt)
        val decoded = Json.decodeFromString<Prompt>(json)

        assertEquals(prompt, decoded)
        decoded.messages.forEach { message ->
            assertEquals("", message.content)
        }
    }

    @Test
    fun testToolMessagesWithNullId() {
        val toolCallWithNullId = Message.Tool.Call(null, toolName, toolCallContent, testRespMetaInfo)
        val toolResultWithNullId = Message.Tool.Result(null, toolName, toolCallContent, testReqMetaInfo)

        assertNull(toolCallWithNullId.id)
        assertNull(toolResultWithNullId.id)

        val prompt = Prompt.build(promptId) {
            tool {
                call(null, toolName, toolCallContent)
                result(null, toolName, toolCallContent)
            }
        }

        assertEquals(2, prompt.messages.size)

        assertTrue(prompt.messages[0] is Message.Tool.Call)
        assertTrue(prompt.messages[1] is Message.Tool.Result)
        assertNull((prompt.messages[0] as Message.Tool.Call).id)
        assertNull((prompt.messages[1] as Message.Tool.Result).id)

        val json = Json.encodeToString(prompt)
        val decoded = Json.decodeFromString<Prompt>(json)

        assertEquals(prompt, decoded)
        assertNull((decoded.messages[0] as Message.Tool.Call).id)
        assertNull((decoded.messages[1] as Message.Tool.Result).id)
    }

    @Test
    fun testAssistantMessageWithNullFinishReason() {
        val prompt = Prompt.build(promptId) {
            message(Message.Assistant(assistantMessage, testRespMetaInfo))
        }

        assertEquals(1, prompt.messages.size)
        assertTrue(prompt.messages[0] is Message.Assistant)
        assertNull((prompt.messages[0] as Message.Assistant).finishReason)

        val json = Json.encodeToString(prompt)
        val decoded = Json.decodeFromString<Prompt>(json)

        assertEquals(prompt, decoded)
        assertNull((decoded.messages[0] as Message.Assistant).finishReason)
    }

    @Test
    fun testInvalidToolCallJsonContent() {
        val toolCallWithInvalidJson = Message.Tool.Call(toolCallId, toolName, "invalid json", testRespMetaInfo)

        assertThrows<SerializationException> {
            toolCallWithInvalidJson.contentJson
        }
    }

    @Test
    fun testLLMParamsWithNullValues() {
        val params = LLMParams(
            temperature = null,
            speculation = null,
            schema = null,
            toolChoice = null,
            user = null
        )

        val prompt = Prompt(emptyList(), promptId, params)

        assertNull(prompt.params.temperature)
        assertNull(prompt.params.speculation)
        assertNull(prompt.params.schema)
        assertNull(prompt.params.toolChoice)
        assertNull(prompt.params.user)

        val json = Json.encodeToString(prompt)
        val decoded = Json.decodeFromString<Prompt>(json)

        assertEquals(prompt, decoded)
        assertNull(decoded.params.temperature)
        assertNull(decoded.params.speculation)
        assertNull(decoded.params.schema)
        assertNull(decoded.params.toolChoice)
        assertNull(decoded.params.user)
    }

    @Test
    fun testToolMessagesWithEmptyToolName() {
        val toolCallWithEmptyName = Message.Tool.Call(toolCallId, emptyName, toolCallContent, testRespMetaInfo)
        val toolResultWithEmptyName = Message.Tool.Result(toolCallId, emptyName, toolCallContent, testReqMetaInfo)

        val prompt = Prompt.build(promptId) {
            tool {
                call(toolCallWithEmptyName)
                result(toolResultWithEmptyName)
            }
        }

        assertEquals(2, prompt.messages.size)
        assertTrue(prompt.messages[0] is Message.Tool.Call)
        assertTrue(prompt.messages[1] is Message.Tool.Result)
        assertEquals(toolCallWithEmptyName.tool, (prompt.messages[0] as Message.Tool.Call).tool)
        assertEquals(toolResultWithEmptyName.tool, (prompt.messages[1] as Message.Tool.Result).tool)

        val json = Json.encodeToString(prompt)
        val decoded = Json.decodeFromString<Prompt>(json)

        assertEquals(prompt, decoded)
        assertEquals(
            (prompt.messages[0] as Message.Tool.Call).tool,
            (decoded.messages[0] as Message.Tool.Call).tool
        )
        assertEquals(
            (prompt.messages[1] as Message.Tool.Result).tool,
            (decoded.messages[1] as Message.Tool.Result).tool
        )
    }

    @Test
    fun testLLMParamsWithValidTemperatureValues() {
        val lowTemp = 0.1
        val highTemp = 1.9

        val promptWithLowTemp = basicPrompt.withUpdatedParams {
            temperature = lowTemp
        }
        val promptWithHighTemp = basicPrompt.withUpdatedParams {
            temperature = highTemp
        }

        assertEquals(lowTemp, promptWithLowTemp.params.temperature)
        assertEquals(highTemp, promptWithHighTemp.params.temperature)

        val jsonLow = Json.encodeToString(promptWithLowTemp)
        val jsonHigh = Json.encodeToString(promptWithHighTemp)

        val decodedLow = Json.decodeFromString<Prompt>(jsonLow)
        val decodedHigh = Json.decodeFromString<Prompt>(jsonHigh)

        assertEquals(promptWithLowTemp.params.temperature, decodedLow.params.temperature)
        assertEquals(promptWithHighTemp.params.temperature, decodedHigh.params.temperature)
    }

    @Test
    fun testSchemaWithEmptyJsonObject() {
        val emptySchemaName = "empty-schema"
        val emptyJsonSchema = buildJsonObject { }

        val schemaWithEmptyJson = LLMParams.Schema.JSON.Simple(emptySchemaName, emptyJsonSchema)

        assertTrue(schemaWithEmptyJson.schema.entries.isEmpty())

        val prompt = basicPrompt.withUpdatedParams {
            schema = schemaWithEmptyJson
        }

        assertTrue(prompt.params.schema is LLMParams.Schema.JSON.Simple)
        assertEquals(emptySchemaName, prompt.params.schema?.name)
        assertTrue((prompt.params.schema as LLMParams.Schema.JSON.Simple).schema.entries.isEmpty())

        val json = Json.encodeToString(prompt)
        val decoded = Json.decodeFromString<Prompt>(json)

        assertEquals(prompt, decoded)
        assertTrue(decoded.params.schema is LLMParams.Schema.JSON.Simple)
        assertEquals(emptySchemaName, decoded.params.schema?.name)
        assertTrue((decoded.params.schema as LLMParams.Schema.JSON.Simple).schema.entries.isEmpty())
    }

    @Test
    fun testWithMessagesFunctions() {
        val originalPrompt = Prompt.build("test") {
            system("You are a helpful assistant")
            user("Hello")
        }

        // Test adding a message
        val updatedPrompt = originalPrompt.withMessages { messages ->
            messages + Message.Assistant("How can I help you?", testRespMetaInfo)
        }

        assertNotEquals(originalPrompt, updatedPrompt)
        assertEquals(3, updatedPrompt.messages.size)
        assertEquals(Message.Assistant("How can I help you?", testRespMetaInfo), updatedPrompt.messages[2])

        // Test replacing messages
        val replacedPrompt = originalPrompt.withMessages {
            listOf(Message.System("You are a coding assistant", testReqMetaInfo))
        }

        assertEquals(1, replacedPrompt.messages.size)
        assertEquals(Message.System("You are a coding assistant", testReqMetaInfo), replacedPrompt.messages[0])
    }

    @Test
    fun testWithParamsFunction() {
        val originalPrompt = Prompt.build("test") {
            system("You are a helpful assistant")
        }

        val newParams = LLMParams(
            temperature = 0.7,
            speculation = "test speculation",
            user = "test_user",
        )

        val updatedPrompt = originalPrompt.withParams(newParams)

        assertNotEquals(originalPrompt, updatedPrompt)
        assertEquals(newParams, updatedPrompt.params)
        assertEquals(0.7, updatedPrompt.params.temperature)
        assertEquals("test speculation", updatedPrompt.params.speculation)
        assertEquals("test_user", updatedPrompt.params.user)
    }

    @Test
    fun testWithUpdatedParamsFunction() {
        val originalPrompt = Prompt.build("test") {
            system("You are a helpful assistant")
        }

        // Test updating temperature only
        val tempUpdatedPrompt = originalPrompt.withUpdatedParams {
            temperature = 0.8
        }

        assertNotEquals(originalPrompt, tempUpdatedPrompt)
        assertEquals(0.8, tempUpdatedPrompt.params.temperature)

        // Test updating multiple parameters
        val multiUpdatedPrompt = originalPrompt.withUpdatedParams {
            temperature = 0.5
            speculation = "new speculation"
            toolChoice = LLMParams.ToolChoice.Auto
            user = "new_user"
        }

        assertEquals(0.5, multiUpdatedPrompt.params.temperature)
        assertEquals("new speculation", multiUpdatedPrompt.params.speculation)
        assertEquals(LLMParams.ToolChoice.Auto, multiUpdatedPrompt.params.toolChoice)
        assertEquals("new_user", multiUpdatedPrompt.params.user)
    }
}
