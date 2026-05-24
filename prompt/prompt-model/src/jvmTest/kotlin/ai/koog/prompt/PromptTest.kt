package ai.koog.prompt

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.utils.time.KoogClock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.time.Instant

class PromptTest {
    companion object {
        val ts: Instant = Instant.parse("2023-01-01T00:00:00Z")

        val testClock: KoogClock = KoogClock { ts }

        val testRespMetaInfo = ResponseMetaInfo.create(testClock)
        val testReqMetaInfo = RequestMetaInfo.create(testClock)

        val promptId = "test-id"
        val systemMessageText = "You are a helpful assistant with many capabilities"
        val assistantMessageText = "I'm here to help!"
        val userMessageText = "Can you help me calculate 5 + 3?"
        val speculationMessageText = "The result is 8"
        val toolCallId = "tool_call_123"
        val toolName = "calculator"
        val toolCallContent = buildJsonObject {
            put("operation", "add")
            put("a", 5)
            put("b", 3)
        }
        val toolResultContent = "8"
        val finishReason = "stop"
        val schemaName = "test_schema"

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
            system(systemMessageText)
            user(userMessageText)
            message(
                Message.Assistant(
                    content = assistantMessageText,
                    metaInfo = testRespMetaInfo,
                    finishReason = finishReason
                )
            )
            assistant {
                toolCall(id = toolCallId, tool = toolName, args = toolCallContent)
            }
            user {
                toolResult(id = toolCallId, tool = toolName, output = toolResultContent)
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
                LLMParams.Schema.JSON.Basic(simpleSchemaName, simpleSchema),
                simpleSchemaName,
                LLMParams.Schema.JSON.Basic::class.java
            ),
            arrayOf(
                LLMParams.Schema.JSON.Standard(fullSchemaName, fullSchema),
                fullSchemaName,
                LLMParams.Schema.JSON.Standard::class.java
            )
        )
    }

    @Test
    fun testPromptBuilding() {
        val assistantMessage = "Hi! How can I help you?"
        val toolCallId = "tool_call_dummy_123"
        val toolName = "search"
        val toolArgs = Json.parseToJsonElement("""{"query": "Searching for information..."}""").jsonObject
        val toolOutput = "Found some results"

        val prompt = Prompt.build("test") {
            system(systemMessageText)
            user(userMessageText)
            assistant(assistantMessage)
            assistant { toolCall(id = toolCallId, tool = toolName, args = toolArgs) }
            user { toolResult(id = toolCallId, tool = toolName, output = toolOutput) }
        }

        assertEquals(5, prompt.messages.size)
        val sysMsg = assertIs<Message.System>(prompt.messages[0])
        val userMsg = assertIs<Message.User>(prompt.messages[1])
        val assistantMsg = assertIs<Message.Assistant>(prompt.messages[2])

        assertEquals(systemMessageText, assertIs<MessagePart.Text>(sysMsg.parts[0]).text)
        assertEquals(userMessageText, assertIs<MessagePart.Text>(userMsg.parts[0]).text)
        assertEquals(assistantMessage, assertIs<MessagePart.Text>(assistantMsg.parts[0]).text)

        val toolCallMsg = assertIs<Message.Assistant>(prompt.messages[3])
        assertEquals(1, toolCallMsg.parts.size)
        val toolCallPart = assertIs<MessagePart.Tool.Call>(toolCallMsg.parts[0])
        assertEquals(toolCallId, toolCallPart.id)
        assertEquals(toolName, toolCallPart.tool)

        val toolResultMsg = assertIs<Message.User>(prompt.messages[4])
        assertEquals(1, toolResultMsg.parts.size)
        val toolResultPart = assertIs<MessagePart.Tool.Result>(toolResultMsg.parts[0])
        assertEquals(toolCallId, toolResultPart.id)
        assertEquals(toolName, toolResultPart.tool)
        assertEquals(toolOutput, toolResultPart.output)
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
            speculation = speculationMessageText
            schema = LLMParams.Schema.JSON.Basic(simpleSchemaName, simpleSchema)
            toolChoice = LLMParams.ToolChoice.Auto
            user = "test_user"
        }

        val encodedPrompt = Json.encodeToString(prompt)
        val decodedPrompt = Json.decodeFromString<Prompt>(encodedPrompt)

        assertEquals(prompt, decodedPrompt)
        assertEquals(prompt.messages.size, decodedPrompt.messages.size)
        assertEquals(0.7, decodedPrompt.params.temperature)
        assertEquals(speculationMessageText, decodedPrompt.params.speculation)
        assertIs<LLMParams.Schema.JSON>(decodedPrompt.params.schema)
        assertEquals(simpleSchemaName, decodedPrompt.params.schema?.name)
        assertIs<LLMParams.ToolChoice.Auto>(decodedPrompt.params.toolChoice)
        assertEquals("test_user", decodedPrompt.params.user)

        decodedPrompt.messages.forEachIndexed { index, decodedMessage ->
            assertEquals(decodedMessage, prompt.messages[index])
            if (decodedMessage is Message.Assistant) {
                assertEquals(
                    decodedMessage.finishReason,
                    assertIs<Message.Assistant>(prompt.messages[index]).finishReason
                )
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
            assertEquals(decodedMessage, prompt.messages[index])
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
            assertEquals(toolName, assertIs<LLMParams.ToolChoice.Named>(decodedToolChoice.params.toolChoice).name)
        }

        decodedToolChoice.messages.forEachIndexed { index, decodedMessage ->
            assertEquals(decodedMessage, prompt.messages[index])
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
        assertEquals(systemMessage, assertIs<MessagePart.Text>(updatedPrompt.messages[0].parts[0]).text)
        assertEquals(userMessage, assertIs<MessagePart.Text>(updatedPrompt.messages[1].parts[0]).text)
        assertEquals(assistantMessage, assertIs<MessagePart.Text>(updatedPrompt.messages[2].parts[0]).text)
    }

    @Test
    fun testUpdatePromptWithNewParams() {
        val speculation = "test speculation"
        val schemaName = "test-schema"
        val newParams = LLMParams(
            temperature = 0.7,
            speculation = speculation,
            schema = LLMParams.Schema.JSON.Basic(
                schemaName,
                buildJsonObject { put("type", "string") }
            ),
            toolChoice = LLMParams.ToolChoice.Auto,
            user = "test_user"
        )

        val updatedPrompt = basicPrompt.withParams(newParams)

        assertEquals(0.7, updatedPrompt.params.temperature)
        assertEquals(speculation, updatedPrompt.params.speculation)
        val schema = assertIs<LLMParams.Schema.JSON>(updatedPrompt.params.schema)
        assertEquals(schemaName, schema.name)
        assertIs<LLMParams.ToolChoice.Auto>(updatedPrompt.params.toolChoice)
        assertEquals("test_user", updatedPrompt.params.user)
    }

    @Test
    fun testUpdatePromptWithUpdatedParams() {
        val newSpeculation = "improved speculation"
        val schemaName = "full-schema"
        val updatedPrompt = basicPrompt.withUpdatedParams {
            temperature = 0.8
            speculation = newSpeculation
            schema = LLMParams.Schema.JSON.Standard(
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
        val schema = assertIs<LLMParams.Schema.JSON>(updatedPrompt.params.schema)
        assertEquals(schemaName, schema.name)
        assertIs<LLMParams.ToolChoice.Required>(updatedPrompt.params.toolChoice)
        assertEquals("updated_user", updatedPrompt.params.user)
    }

    @Test
    fun testEmptyPrompt() {
        val emptyPrompt = Prompt.Empty

        assertTrue(emptyPrompt.messages.isEmpty())
        assertEquals("default", emptyPrompt.id)
        assertEquals(LLMParams(), emptyPrompt.params)

        val json = Json.encodeToString(emptyPrompt)
        val decoded = Json.decodeFromString<Prompt>(json)

        assertEquals(emptyPrompt, decoded)
        assertTrue(decoded.messages.isEmpty())
        assertEquals("default", decoded.id)
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

        assertEquals("", assertIs<MessagePart.Text>(emptySystemMessage.parts[0]).text)
        assertEquals("", assertIs<MessagePart.Text>(emptyUserMessage.parts[0]).text)
        assertEquals("", assertIs<MessagePart.Text>(emptyAssistantMessage.parts[0]).text)

        val emptyToolCallArgs = buildJsonObject { }
        val emptyToolOutput = ""

        val prompt = Prompt.build(promptId) {
            system("")
            user("")
            assistant("")
            assistant { toolCall(id = toolCallId, tool = toolName, args = emptyToolCallArgs) }
            user { toolResult(id = toolCallId, tool = toolName, output = emptyToolOutput) }
        }

        assertEquals(5, prompt.messages.size)
        assertEquals("", assertIs<MessagePart.Text>(prompt.messages[0].parts[0]).text)
        assertEquals("", assertIs<MessagePart.Text>(prompt.messages[1].parts[0]).text)
        assertEquals("", assertIs<MessagePart.Text>(prompt.messages[2].parts[0]).text)
        assertIs<MessagePart.Tool.Call>(assertIs<Message.Assistant>(prompt.messages[3]).parts[0])
        assertIs<MessagePart.Tool.Result>(assertIs<Message.User>(prompt.messages[4]).parts[0])

        val json = Json.encodeToString(prompt)
        val decoded = Json.decodeFromString<Prompt>(json)

        assertEquals(prompt, decoded)
    }

    @Test
    fun testToolMessagesWithNullId() {
        val toolArgs = toolCallContent
        val toolOutput = toolResultContent

        val toolCallPart = MessagePart.Tool.Call(tool = toolName, args = toolArgs)
        val toolResultPart = MessagePart.Tool.Result(tool = toolName, output = toolOutput)
        assertNull(toolCallPart.id)
        assertNull(toolResultPart.id)

        val prompt = Prompt.build(promptId) {
            assistant { toolCall(id = null, tool = toolName, args = toolArgs) }
            user { toolResult(id = null, tool = toolName, output = toolOutput) }
        }

        assertEquals(2, prompt.messages.size)

        val callMsg = assertIs<Message.Assistant>(prompt.messages[0])
        val resultMsg = assertIs<Message.User>(prompt.messages[1])
        assertNull(assertIs<MessagePart.Tool.Call>(callMsg.parts[0]).id)
        assertNull(assertIs<MessagePart.Tool.Result>(resultMsg.parts[0]).id)

        val json = Json.encodeToString(prompt)
        val decoded = Json.decodeFromString<Prompt>(json)

        assertEquals(prompt, decoded)
        assertNull(assertIs<MessagePart.Tool.Call>(assertIs<Message.Assistant>(decoded.messages[0]).parts[0]).id)
        assertNull(assertIs<MessagePart.Tool.Result>(assertIs<Message.User>(decoded.messages[1]).parts[0]).id)
    }

    @Test
    fun testAssistantMessageWithNullFinishReason() {
        val prompt = Prompt.build(promptId) {
            message(Message.Assistant(assistantMessageText, testRespMetaInfo))
        }

        assertEquals(1, prompt.messages.size)
        val assistantMsg = assertIs<Message.Assistant>(prompt.messages[0])
        assertNull(assistantMsg.finishReason)

        val json = Json.encodeToString(prompt)
        val decoded = Json.decodeFromString<Prompt>(json)

        assertEquals(prompt, decoded)
        assertNull(assertIs<Message.Assistant>(decoded.messages[0]).finishReason)
    }

    @Test
    fun testToolCallPartWithValidJson() {
        val toolArgs = toolCallContent
        val toolCallPart = MessagePart.Tool.Call(id = toolCallId, tool = toolName, args = toolArgs)

        assertEquals(toolCallId, toolCallPart.id)
        assertEquals(toolName, toolCallPart.tool)
        assertEquals(toolArgs, toolCallPart.argsJson)
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
    fun testToolChoiceNamedWithEmptyName() {
        val toolChoiceWithEmptyName = LLMParams.ToolChoice.Named(schemaName)
        val prompt = basicPrompt.withUpdatedParams {
            toolChoice = toolChoiceWithEmptyName
        }

        val toolChoice = assertIs<LLMParams.ToolChoice.Named>(prompt.params.toolChoice)
        assertEquals(schemaName, toolChoice.name)

        val json = Json.encodeToString(prompt)
        val decoded = Json.decodeFromString<Prompt>(json)

        assertEquals(prompt, decoded)
        val decodedToolChoice = assertIs<LLMParams.ToolChoice.Named>(decoded.params.toolChoice)
        assertEquals(schemaName, decodedToolChoice.name)
    }

    @Test
    fun testSchemaWithEmptyName() {
        val schemaWithEmptyName = LLMParams.Schema.JSON.Basic(
            schemaName,
            buildJsonObject { put("type", "string") }
        )

        val prompt = basicPrompt.withUpdatedParams {
            schema = schemaWithEmptyName
        }

        val schema = assertIs<LLMParams.Schema.JSON>(prompt.params.schema)
        assertEquals(schemaName, schema.name)

        val json = Json.encodeToString(prompt)
        val decoded = Json.decodeFromString<Prompt>(json)

        assertEquals(prompt, decoded)
        val decodedSchema = assertIs<LLMParams.Schema.JSON>(decoded.params.schema)
        assertEquals(schemaName, decodedSchema.name)
    }

    @Test
    fun testToolMessagesWithSpecificToolName() {
        val toolArgs = toolCallContent
        val toolOutput = toolResultContent

        val prompt = Prompt.build(promptId) {
            assistant { toolCall(id = toolCallId, tool = schemaName, args = toolArgs) }
            user { toolResult(id = toolCallId, tool = schemaName, output = toolOutput) }
        }

        assertEquals(2, prompt.messages.size)
        val callMsg = assertIs<Message.Assistant>(prompt.messages[0])
        val resultMsg = assertIs<Message.User>(prompt.messages[1])
        val callPart = assertIs<MessagePart.Tool.Call>(callMsg.parts[0])
        val resultPart = assertIs<MessagePart.Tool.Result>(resultMsg.parts[0])
        assertEquals(schemaName, callPart.tool)
        assertEquals(schemaName, resultPart.tool)

        val json = Json.encodeToString(prompt)
        val decoded = Json.decodeFromString<Prompt>(json)

        assertEquals(prompt, decoded)
        assertEquals(callPart.tool, assertIs<MessagePart.Tool.Call>(assertIs<Message.Assistant>(decoded.messages[0]).parts[0]).tool)
        assertEquals(resultPart.tool, assertIs<MessagePart.Tool.Result>(assertIs<Message.User>(decoded.messages[1]).parts[0]).tool)
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

        val schemaWithEmptyJson = LLMParams.Schema.JSON.Basic(emptySchemaName, emptyJsonSchema)

        assertTrue(schemaWithEmptyJson.schema.entries.isEmpty())

        val prompt = basicPrompt.withUpdatedParams {
            schema = schemaWithEmptyJson
        }

        val schema = assertIs<LLMParams.Schema.JSON>(prompt.params.schema)
        assertEquals(emptySchemaName, schema.name)
        assertTrue(schema.schema.entries.isEmpty())

        val json = Json.encodeToString(prompt)
        val decoded = Json.decodeFromString<Prompt>(json)

        assertEquals(prompt, decoded)
        val decodedSchema = assertIs<LLMParams.Schema.JSON>(decoded.params.schema)
        assertEquals(emptySchemaName, decodedSchema.name)
        assertTrue(decodedSchema.schema.entries.isEmpty())
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
        val originalParams = LLMParams(
            temperature = 0.7,
            speculation = "test speculation",
            user = "test_user",
            additionalProperties = mapOf("test_property_name" to JsonPrimitive("test_property_value")),
        )
        val originalPrompt = Prompt.build("test", originalParams) {
            system("You are a helpful assistant")
        }

        // Test updating temperature only
        val tempUpdatedPrompt = originalPrompt.withUpdatedParams {
            temperature = 0.8
        }

        assertNotEquals(originalPrompt, tempUpdatedPrompt)
        assertEquals(0.8, tempUpdatedPrompt.params.temperature)
        assertEquals(originalParams.speculation, tempUpdatedPrompt.params.speculation)
        assertEquals(originalParams.user, tempUpdatedPrompt.params.user)
        assertEquals(originalParams.additionalProperties, tempUpdatedPrompt.params.additionalProperties)

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

    @Test
    fun testWithUpdatedParamsPreservesLLMParamsSubtype() {
        class CustomProviderParams(
            temperature: Double? = null,
            toolChoice: ToolChoice? = null,
            val providerField: String? = null,
        ) : LLMParams(temperature = temperature, toolChoice = toolChoice) {
            override fun copy(
                temperature: Double?,
                maxTokens: Int?,
                numberOfChoices: Int?,
                speculation: String?,
                schema: Schema?,
                toolChoice: ToolChoice?,
                user: String?,
                additionalProperties: Map<String, kotlinx.serialization.json.JsonElement>?,
            ): CustomProviderParams = CustomProviderParams(
                temperature = temperature,
                toolChoice = toolChoice,
                providerField = providerField,
            )
        }

        val originalParams = CustomProviderParams(temperature = 0.7, providerField = "provider-specific")
        val prompt = Prompt.build("test", originalParams) { system("system") }

        val updated = prompt.withUpdatedParams {
            toolChoice = LLMParams.ToolChoice.Named("myTool")
        }

        assertIs<CustomProviderParams>(updated.params)
        assertEquals(originalParams.providerField, updated.params.providerField)
        assertEquals(LLMParams.ToolChoice.Named("myTool"), updated.params.toolChoice)
        assertEquals(originalParams.temperature, updated.params.temperature)
    }
}
