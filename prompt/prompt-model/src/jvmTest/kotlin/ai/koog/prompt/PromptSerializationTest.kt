package ai.koog.prompt

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.json.shouldBeValidJson
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class PromptSerializationTest : AbstractPromptTest() {

    @Test
    fun testBasicSerialization() {
        val json = Json.encodeToString(basicPrompt)
        json.shouldBeValidJson()
        val decoded = Json.decodeFromString<Prompt>(json)

        decoded shouldBeEqualToComparingFields basicPrompt
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
        encodedPrompt.shouldBeValidJson()
        val decodedPrompt = Json.decodeFromString<Prompt>(encodedPrompt)

        decodedPrompt shouldBeEqualToComparingFields prompt
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("schemaSerializationProvider")
    fun testSchemaSerialization(name: String, schema: LLMParams.Schema, schemaName: String, schemaClass: Class<*>) {
        val prompt = basicPrompt.withUpdatedParams {
            this.schema = schema
        }

        val schemaJson = Json.encodeToString(prompt)
        schemaJson.shouldBeValidJson()

        val decodedSchema = Json.decodeFromString<Prompt>(schemaJson)

        decodedSchema shouldBeEqualToComparingFields prompt

        decodedSchema.params.schema shouldNotBeNull {
            javaClass shouldBe schemaClass
            this.name shouldBe schemaName
        }
    }

    @ParameterizedTest
    @MethodSource("toolChoiceSerializationProvider")
    fun testToolChoiceSerialization(name: String, toolChoiceOption: LLMParams.ToolChoice) {
        val prompt = basicPrompt.withUpdatedParams {
            toolChoice = toolChoiceOption
        }
        val toolChoiceJson = Json.encodeToString(prompt)
        toolChoiceJson.shouldBeValidJson()
        val decodedToolChoice = Json.decodeFromString<Prompt>(toolChoiceJson)

        decodedToolChoice shouldBeEqualToComparingFields prompt
    }

    @Test
    fun testEmptyPrompt() {
        val emptyPrompt = Prompt.Empty

        assertSoftly(emptyPrompt) {
            messages.isEmpty().shouldBeTrue()
            id shouldBe ""
            params shouldBe LLMParams()
        }

        val json = Json.encodeToString(emptyPrompt)
        json.shouldBeValidJson()
        val decoded = Json.decodeFromString<Prompt>(json)

        decoded shouldBeEqualToComparingFields emptyPrompt
    }

    @Test
    fun testPromptWithEmptyMessages() {
        val prompt = Prompt(emptyList(), promptId)

        prompt.messages.shouldBeEmpty()
        prompt.id shouldBe promptId

        val json = Json.encodeToString(prompt)
        val decoded = Json.decodeFromString<Prompt>(json)

        decoded shouldBe prompt
        decoded.messages.isEmpty().shouldBeTrue()
    }

    @Test
    fun testMessageWithEmptyContent() {
        val emptySystemMessage = Message.System("", testReqMetaInfo)
        val emptyUserMessage = Message.User("", testReqMetaInfo)
        val emptyAssistantMessage = Message.Assistant("", testRespMetaInfo)
        val emptyToolCallMessage = Message.Tool.Call(toolCallId, toolName, "", testRespMetaInfo)
        val emptyToolResultMessage = Message.Tool.Result(toolCallId, toolName, "", testReqMetaInfo)

        emptySystemMessage.content shouldBe ""
        emptyUserMessage.content shouldBe ""
        emptyAssistantMessage.content shouldBe ""
        emptyToolCallMessage.content shouldBe ""
        emptyToolResultMessage.content shouldBe ""

        val prompt = Prompt.build(promptId) {
            system("")
            user("")
            assistant("")
            tool {
                call(toolCallId, toolName, "")
                result(toolCallId, toolName, "")
            }
        }

        prompt.messages.size shouldBe 5
        prompt.messages.forEach { message ->
            message.content shouldBe ""
        }

        val json = Json.encodeToString(prompt)
        json.shouldBeValidJson()
        val decoded = Json.decodeFromString<Prompt>(json)

        decoded shouldBeEqualToComparingFields prompt
    }

    @Test
    fun testToolMessagesWithNullId() {
        val toolCallWithNullId = Message.Tool.Call(null, toolName, toolCallContent, testRespMetaInfo)
        val toolResultWithNullId = Message.Tool.Result(null, toolName, toolCallContent, testReqMetaInfo)

        toolCallWithNullId.id.shouldBeNull()
        toolResultWithNullId.id.shouldBeNull()

        val prompt = Prompt.build(promptId) {
            tool {
                call(null, toolName, toolCallContent)
                result(null, toolName, toolCallContent)
            }
        }

        assertSoftly(prompt) {
            messages.size shouldBe 2
            messages[0].shouldBeInstanceOf<Message.Tool.Call>()
            messages[1].shouldBeInstanceOf<Message.Tool.Result>()
            (messages[0] as Message.Tool.Call).id.shouldBeNull()
            (messages[1] as Message.Tool.Result).id.shouldBeNull()
        }

        val json = Json.encodeToString(prompt)
        json.shouldBeValidJson()
        val decoded = Json.decodeFromString<Prompt>(json)

        decoded shouldBeEqualToComparingFields prompt

        assertSoftly(decoded.messages) {
            (get(0) as Message.Tool.Call).id shouldBe null
            (get(1) as Message.Tool.Result).id shouldBe null
        }
    }

    @Test
    fun testAssistantMessageWithNullFinishReason() {
        val prompt = Prompt.build(promptId) {
            message(Message.Assistant(assistantMessage, testRespMetaInfo, null))
        }

        assertSoftly(prompt) {
            messages.size shouldBe 1
            messages[0].shouldBeInstanceOf<Message.Assistant>()
            (messages[0] as Message.Assistant).finishReason.shouldBeNull()
        }

        val json = Json.encodeToString(prompt)
        val decoded = Json.decodeFromString<Prompt>(json)

        decoded shouldBe prompt
        (decoded.messages[0] as Message.Assistant).finishReason.shouldBeNull()
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

        assertSoftly(prompt.params) {
            temperature.shouldBeNull()
            speculation.shouldBeNull()
            schema.shouldBeNull()
            toolChoice.shouldBeNull()
            user.shouldBeNull()
        }

        val json = Json.encodeToString(prompt)
        val decoded = Json.decodeFromString<Prompt>(json)

        decoded shouldBe prompt

        assertSoftly(decoded.params) {
            temperature.shouldBeNull()
            speculation.shouldBeNull()
            schema.shouldBeNull()
            toolChoice.shouldBeNull()
            user.shouldBeNull()
        }
    }

    @Test
    fun testToolChoiceNamedWithEmptyName() {
        val toolChoiceWithEmptyName = LLMParams.ToolChoice.Named(emptyName)
        val prompt = basicPrompt.withUpdatedParams {
            toolChoice = toolChoiceWithEmptyName
        }

        withClue("Prompt's toolChoice should be of type Named with empty name") {
            assertSoftly(prompt.params.toolChoice) {
                this.shouldBeInstanceOf<LLMParams.ToolChoice.Named>()
                this.name shouldBe emptyName
            }
        }

        val json = Json.encodeToString(prompt)
        val decoded = Json.decodeFromString<Prompt>(json)

        decoded shouldBe prompt

        withClue("Decoded prompt's toolChoice should be of type Named with empty name") {
            assertSoftly(decoded.params.toolChoice) {
                this.shouldBeInstanceOf<LLMParams.ToolChoice.Named>()
                this.name shouldBe emptyName
            }
        }
    }

    @Test
    fun testSchemaWithEmptyName() {
        val schemaWithEmptyName = LLMParams.Schema.JSON.Simple(
            emptyName,
            buildJsonObject { put("type", "string") }
        )

        val prompt = basicPrompt.withUpdatedParams {
            schema = schemaWithEmptyName
        }

        assertSoftly(prompt.params.schema) {
            this.shouldBeInstanceOf<LLMParams.Schema.JSON.Simple>()
            this.name shouldBe emptyName
        }

        val json = Json.encodeToString(prompt)
        val decoded = Json.decodeFromString<Prompt>(json)

        decoded shouldBe prompt
        assertSoftly(decoded.params.schema) {
            this.shouldBeInstanceOf<LLMParams.Schema.JSON.Simple>()
            this.name shouldBe emptyName
        }
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

        assertSoftly(prompt) {
            messages.size shouldBe 2
            messages[0].shouldBeInstanceOf<Message.Tool.Call>()
            messages[1].shouldBeInstanceOf<Message.Tool.Result>()
            (messages[0] as Message.Tool.Call).tool shouldBe toolCallWithEmptyName.tool
            (messages[1] as Message.Tool.Result).tool shouldBe toolResultWithEmptyName.tool
        }

        val json = Json.encodeToString(prompt)
        val decoded = Json.decodeFromString<Prompt>(json)

        decoded shouldBe prompt

        // Individual assertions for different subjects
        (decoded.messages[0] as Message.Tool.Call).tool shouldBe (prompt.messages[0] as Message.Tool.Call).tool
        (decoded.messages[1] as Message.Tool.Result).tool shouldBe (prompt.messages[1] as Message.Tool.Result).tool
    }

    @Test
    fun testLLMParamsWithExtremeTemperatureValues() {
        val lowTemp = -1.0
        val highTemp = 100.0

        val promptWithNegativeTemp = basicPrompt.withUpdatedParams {
            temperature = lowTemp
        }
        val promptWithHighTemp = basicPrompt.withUpdatedParams {
            temperature = highTemp
        }

        promptWithNegativeTemp.params.temperature shouldBe lowTemp
        promptWithHighTemp.params.temperature shouldBe highTemp

        val jsonNegative = Json.encodeToString(promptWithNegativeTemp)
        val jsonHigh = Json.encodeToString(promptWithHighTemp)

        val decodedNegative = Json.decodeFromString<Prompt>(jsonNegative)
        val decodedHigh = Json.decodeFromString<Prompt>(jsonHigh)

        decodedNegative.params.temperature shouldBe promptWithNegativeTemp.params.temperature
        decodedHigh.params.temperature shouldBe promptWithHighTemp.params.temperature
    }

    @Test
    fun testSchemaWithEmptyJsonObject() {
        val emptySchemaName = "empty-schema"
        val emptyJsonSchema = buildJsonObject { }

        val schemaWithEmptyJson = LLMParams.Schema.JSON.Simple(emptySchemaName, emptyJsonSchema)

        schemaWithEmptyJson.schema.entries.isEmpty().shouldBeTrue()

        val prompt = basicPrompt.withUpdatedParams {
            schema = schemaWithEmptyJson
        }

        assertSoftly(prompt.params.schema) {
            this.shouldBeInstanceOf<LLMParams.Schema.JSON.Simple>()
            name shouldBe emptySchemaName
            schema.entries.isEmpty().shouldBeTrue()
        }

        val json = Json.encodeToString(prompt)
        val decoded = Json.decodeFromString<Prompt>(json)

        decoded shouldBe prompt
        assertSoftly(decoded.params.schema) {
            this.shouldBeInstanceOf<LLMParams.Schema.JSON.Simple>()
            name shouldBe emptySchemaName
            schema.entries.isEmpty().shouldBeTrue()
        }
    }
}
