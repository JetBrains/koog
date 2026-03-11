package ai.koog.spring.ai.chat

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.MessageType
import org.springframework.ai.chat.messages.ToolResponseMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.metadata.Usage
import org.springframework.ai.chat.model.Generation
import kotlin.time.Clock

class ConvertersTest {

    private val requestMeta = RequestMetaInfo.create(Clock.System)
    private val responseMeta = ResponseMetaInfo.create(Clock.System)

    // ---- koogMessageToSpringMessage ----

    @Test
    fun `converts Koog System message to Spring AI SystemMessage`() {
        val koogMsg = Message.System("You are a helpful assistant", requestMeta)
        val springMsg = koogMessageToSpringMessage(koogMsg)
        assertEquals(MessageType.SYSTEM, springMsg.messageType)
        assertEquals("You are a helpful assistant", springMsg.text)
    }

    @Test
    fun `converts Koog User message to Spring AI UserMessage`() {
        val koogMsg = Message.User("Hello", requestMeta)
        val springMsg = koogMessageToSpringMessage(koogMsg)
        assertEquals(MessageType.USER, springMsg.messageType)
        assertEquals("Hello", springMsg.text)
    }

    @Test
    fun `converts Koog Assistant message to Spring AI AssistantMessage`() {
        val koogMsg = Message.Assistant("Hi there", responseMeta)
        val springMsg = koogMessageToSpringMessage(koogMsg)
        assertEquals(MessageType.ASSISTANT, springMsg.messageType)
        assertEquals("Hi there", springMsg.text)
    }

    @Test
    fun `converts Koog Tool Call to Spring AI AssistantMessage with tool calls`() {
        val koogMsg = Message.Tool.Call(
            id = "call-1",
            tool = "get_weather",
            content = """{"city": "London"}""",
            metaInfo = responseMeta
        )
        val springMsg = koogMessageToSpringMessage(koogMsg)
        assertEquals(MessageType.ASSISTANT, springMsg.messageType)
        assertTrue(springMsg is AssistantMessage)
        val toolCalls = (springMsg as AssistantMessage).toolCalls
        assertEquals(1, toolCalls.size)
        assertEquals("get_weather", toolCalls[0].name())
        assertEquals("call-1", toolCalls[0].id())
        assertEquals("""{"city": "London"}""", toolCalls[0].arguments())
    }

    @Test
    fun `converts Koog Tool Result to Spring AI ToolResponseMessage`() {
        val koogMsg = Message.Tool.Result(
            id = "call-1",
            tool = "get_weather",
            content = "Sunny, 22C",
            metaInfo = requestMeta
        )
        val springMsg = koogMessageToSpringMessage(koogMsg)
        assertTrue(springMsg is ToolResponseMessage)
        val responses = (springMsg as ToolResponseMessage).responses
        assertEquals(1, responses.size)
        assertEquals("call-1", responses[0].id())
        assertEquals("get_weather", responses[0].name())
        assertEquals("Sunny, 22C", responses[0].responseData())
    }

    @Test
    fun `converts Koog Reasoning message to Spring AI AssistantMessage with reasoningContent metadata`() {
        val koogMsg = Message.Reasoning(content = "step by step", metaInfo = responseMeta)
        val springMsg = koogMessageToSpringMessage(koogMsg)
        assertTrue(springMsg is AssistantMessage)
        assertEquals(MessageType.ASSISTANT, springMsg.messageType)
        assertEquals("", springMsg.text)
        assertEquals("step by step", springMsg.metadata["reasoningContent"])
    }

    // ---- springGenerationToKoogResponses ----

    @Test
    fun `converts Spring AI Generation with text to Koog Assistant message`() {
        val generation = Generation(AssistantMessage("Hello from LLM"))
        val responses = springGenerationToKoogResponses(generation)
        assertEquals(1, responses.size)
        assertTrue(responses[0] is Message.Assistant)
        assertEquals("Hello from LLM", responses[0].content)
    }

    @Test
    fun `converts Spring AI Generation with tool calls to Koog Tool Call messages`() {
        val toolCall = AssistantMessage.ToolCall("tc-1", "function", "search", """{"q":"test"}""")
        val generation = Generation(AssistantMessage.builder().toolCalls(listOf(toolCall)).build())
        val responses = springGenerationToKoogResponses(generation)
        assertEquals(1, responses.size)
        assertTrue(responses[0] is Message.Tool.Call)
        val call = responses[0] as Message.Tool.Call
        assertEquals("tc-1", call.id)
        assertEquals("search", call.tool)
        assertEquals("""{"q":"test"}""", call.content)
    }

    @Test
    fun `springGenerationToKoogResponses maps usage token counts correctly`() {
        val generation = Generation(AssistantMessage("response"))
        val usage = object : Usage {
            override fun getPromptTokens(): Int = 10
            override fun getCompletionTokens(): Int = 20
            override fun getNativeUsage(): Any = emptyMap<String, Any>()
        }
        val responses = springGenerationToKoogResponses(generation, usage = usage)
        assertEquals(1, responses.size)
        val metaInfo = (responses[0] as Message.Assistant).metaInfo
        assertEquals(10, metaInfo.inputTokensCount)
        assertEquals(20, metaInfo.outputTokensCount)
        assertEquals(30, metaInfo.totalTokensCount)
    }

    @Test
    fun `converts Spring AI Generation with reasoningContent metadata to Koog Reasoning and Assistant messages`() {
        val generation = Generation(
            AssistantMessage.builder()
                .content("Final answer")
                .properties(mapOf("reasoningContent" to "hidden chain of thought"))
                .build()
        )

        val responses = springGenerationToKoogResponses(generation)

        assertEquals(2, responses.size)
        assertTrue(responses[0] is Message.Reasoning)
        assertEquals("hidden chain of thought", responses[0].content)
        assertTrue(responses[1] is Message.Assistant)
        assertEquals("Final answer", responses[1].content)
    }

    // ---- koogToolDescriptorToToolCallback ----

    @Test
    fun `converts ToolDescriptor to ToolCallback with correct definition`() {
        val descriptor = ToolDescriptor(
            name = "get_weather",
            description = "Get weather for a city",
            requiredParameters = listOf(
                ToolParameterDescriptor("city", "The city name", ToolParameterType.String)
            )
        )
        val callback = koogToolDescriptorToToolCallback(descriptor)
        val definition = callback.toolDefinition
        assertEquals("get_weather", definition.name())
        assertEquals("Get weather for a city", definition.description())
        assertTrue(definition.inputSchema().contains("\"city\""))
        assertTrue(definition.inputSchema().contains("\"string\""))
    }

    @Test
    fun `ToolCallback call throws IllegalStateException with configuration guidance`() {
        val descriptor = ToolDescriptor("test_tool", "A test tool")
        val callback = koogToolDescriptorToToolCallback(descriptor)
        val ex = assertThrows<IllegalStateException> {
            callback.call("{}")
        }
        assertTrue(ex.message!!.contains("Koog agent framework"))
        assertTrue(ex.message!!.contains("internalToolExecutionEnabled"))
    }

    // ---- toolDescriptorToJsonSchema ----

    @Test
    fun `toolDescriptorToJsonSchema produces valid JSON`() {
        val descriptor = ToolDescriptor(
            name = "search",
            description = "Search tool",
            requiredParameters = listOf(
                ToolParameterDescriptor("query", "Search query", ToolParameterType.String)
            ),
            optionalParameters = listOf(
                ToolParameterDescriptor("limit", "Max results", ToolParameterType.Integer)
            )
        )
        val json = toolDescriptorToJsonSchema(descriptor)
        assertEquals("object", json["type"]?.jsonPrimitive?.content)
        assertTrue(json["properties"]?.jsonObject?.containsKey("query") == true)
        assertTrue(json["properties"]?.jsonObject?.containsKey("limit") == true)
        assertEquals("query", json["required"]?.jsonArray?.get(0)?.jsonPrimitive?.content)
    }

    @Test
    fun `toolDescriptorToJsonSchema includes parameter descriptions`() {
        val descriptor = ToolDescriptor(
            name = "tool",
            description = "A tool",
            requiredParameters = listOf(
                ToolParameterDescriptor("param1", "First parameter", ToolParameterType.String)
            )
        )
        val json = toolDescriptorToJsonSchema(descriptor)
        val param1 = json["properties"]?.jsonObject?.get("param1")?.jsonObject
        assertEquals("First parameter", param1?.get("description")?.jsonPrimitive?.content)
    }

    @Test
    fun `toolDescriptorToJsonSchema handles enum parameters`() {
        val descriptor = ToolDescriptor(
            name = "set_mode",
            description = "Set mode",
            requiredParameters = listOf(
                ToolParameterDescriptor("mode", "The mode", ToolParameterType.Enum(arrayOf("fast", "slow")))
            )
        )
        val json = toolDescriptorToJsonSchema(descriptor)
        val modeSchema = json["properties"]?.jsonObject?.get("mode")?.jsonObject
        assertEquals("string", modeSchema?.get("type")?.jsonPrimitive?.content)
        val enumValues = modeSchema?.get("enum")?.jsonArray?.map { it.jsonPrimitive.content }
        assertEquals(listOf("fast", "slow"), enumValues)
    }

    @Test
    fun `toolDescriptorToJsonSchema handles list parameters`() {
        val descriptor = ToolDescriptor(
            name = "process",
            description = "Process items",
            requiredParameters = listOf(
                ToolParameterDescriptor("items", "List of items", ToolParameterType.List(ToolParameterType.String))
            )
        )
        val json = toolDescriptorToJsonSchema(descriptor)
        val itemsSchema = json["properties"]?.jsonObject?.get("items")?.jsonObject
        assertEquals("array", itemsSchema?.get("type")?.jsonPrimitive?.content)
    }

    @Test
    fun `toolDescriptorToJsonSchema handles nested object parameters`() {
        val descriptor = ToolDescriptor(
            name = "create_user",
            description = "Create a user",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    "address",
                    "User address",
                    ToolParameterType.Object(
                        properties = listOf(
                            ToolParameterDescriptor("street", "Street", ToolParameterType.String),
                            ToolParameterDescriptor("zip", "Zip code", ToolParameterType.String)
                        ),
                        requiredProperties = listOf("street")
                    )
                )
            )
        )
        val json = toolDescriptorToJsonSchema(descriptor)
        val addressSchema = json["properties"]?.jsonObject?.get("address")?.jsonObject
        assertEquals("object", addressSchema?.get("type")?.jsonPrimitive?.content)
        assertTrue(addressSchema?.get("properties")?.jsonObject?.containsKey("street") == true)
        assertEquals("street", addressSchema?.get("required")?.jsonArray?.get(0)?.jsonPrimitive?.content)
    }

    @Test
    fun `toolDescriptorToJsonSchema handles anyOf parameters`() {
        val descriptor = ToolDescriptor(
            name = "flexible_tool",
            description = "Accepts string or integer",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    "value",
                    "A value",
                    ToolParameterType.AnyOf(
                        arrayOf(
                            ToolParameterDescriptor("", "", ToolParameterType.String),
                            ToolParameterDescriptor("", "", ToolParameterType.Integer)
                        )
                    )
                )
            )
        )
        val json = toolDescriptorToJsonSchema(descriptor)
        val valueSchema = json["properties"]?.jsonObject?.get("value")?.jsonObject
        assertTrue(valueSchema?.containsKey("anyOf") == true)
        assertEquals(2, valueSchema?.get("anyOf")?.jsonArray?.size)
    }

    // ---- Schema escaping and special characters ----

    @Test
    fun `toolDescriptorToJsonSchema properly handles unicode in descriptions`() {
        val descriptor = ToolDescriptor(
            name = "unicode_tool",
            description = "Outil avec des caractères spéciaux: é, ñ, ü, 日本語",
            requiredParameters = listOf(
                ToolParameterDescriptor("名前", "名前を入力してください", ToolParameterType.String)
            )
        )
        val json = toolDescriptorToJsonSchema(descriptor)
        val paramSchema = json["properties"]?.jsonObject?.get("名前")?.jsonObject
        assertEquals("名前を入力してください", paramSchema?.get("description")?.jsonPrimitive?.content)
    }

    @Test
    fun `toolDescriptorToJsonSchema handles enum with special characters`() {
        val descriptor = ToolDescriptor(
            name = "special_enum",
            description = "Enum with special values",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    "status",
                    "Status",
                    ToolParameterType.Enum(arrayOf("in-progress", "done & dusted", "status\"quoted\""))
                )
            )
        )
        val json = toolDescriptorToJsonSchema(descriptor)
        val enumValues = json["properties"]?.jsonObject?.get("status")?.jsonObject
            ?.get("enum")?.jsonArray?.map { it.jsonPrimitive.content }
        assertEquals(3, enumValues?.size)
        assertTrue(enumValues?.contains("in-progress") == true)
        assertTrue(enumValues?.contains("done & dusted") == true)
        assertTrue(enumValues?.contains("status\"quoted\"") == true)
    }

    @Test
    fun `toolDescriptorToJsonSchema handles empty parameters`() {
        val descriptor = ToolDescriptor(
            name = "no_params",
            description = "Tool with no parameters"
        )
        val json = toolDescriptorToJsonSchema(descriptor)
        assertEquals("object", json["type"]?.jsonPrimitive?.content)
        assertTrue(json["properties"]?.jsonObject?.isEmpty() == true)
        assertTrue(json["required"] == null)
    }

    // ---- parameterTypeToJsonSchema ----

    @Test
    fun `parameterTypeToJsonSchema returns correct type for all primitives`() {
        // Parse as JSON to verify validity rather than exact string match
        fun assertType(expected: String, type: ToolParameterType) {
            val json = Json.parseToJsonElement(parameterTypeToJsonElement(type).toString()).jsonObject
            assertEquals(expected, json["type"]?.jsonPrimitive?.content)
        }
        assertType("string", ToolParameterType.String)
        assertType("integer", ToolParameterType.Integer)
        assertType("number", ToolParameterType.Float)
        assertType("boolean", ToolParameterType.Boolean)
        assertType("null", ToolParameterType.Null)
    }

    // ---- Multimodal content conversion ----

    @Test
    fun `converts User message with image URL attachment to UserMessage with media`() {
        val parts = listOf(
            ContentPart.Text("Look at this image"),
            ContentPart.Image(
                content = AttachmentContent.URL("https://example.com/image.png"),
                format = "png",
                mimeType = "image/png"
            )
        )
        val koogMsg = Message.User(parts, requestMeta)
        val springMsg = koogMessageToSpringMessage(koogMsg)
        assertEquals(MessageType.USER, springMsg.messageType)
        assertTrue(springMsg is UserMessage)
        val userMsg = springMsg as UserMessage
        assertEquals("Look at this image", userMsg.text)
        assertEquals(1, userMsg.media.size)
        assertEquals("image/png", userMsg.media[0].mimeType.toString())
    }

    @Test
    fun `converts User message with binary image attachment to UserMessage with media`() {
        val imageBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47) // PNG magic bytes
        val parts = listOf(
            ContentPart.Text("Describe this"),
            ContentPart.Image(
                content = AttachmentContent.Binary.Bytes(imageBytes),
                format = "png",
                mimeType = "image/png"
            )
        )
        val koogMsg = Message.User(parts, requestMeta)
        val springMsg = koogMessageToSpringMessage(koogMsg)
        assertTrue(springMsg is UserMessage)
        val userMsg = springMsg as UserMessage
        assertEquals(1, userMsg.media.size)
        assertEquals("image/png", userMsg.media[0].mimeType.toString())
    }

    @Test
    fun `converts User message with base64 image attachment to UserMessage with media`() {
        val base64Content = "iVBORw0KGgo="
        val parts = listOf(
            ContentPart.Text("What is this?"),
            ContentPart.Image(
                content = AttachmentContent.Binary.Base64(base64Content),
                format = "jpeg",
                mimeType = "image/jpeg"
            )
        )
        val koogMsg = Message.User(parts, requestMeta)
        val springMsg = koogMessageToSpringMessage(koogMsg)
        assertTrue(springMsg is UserMessage)
        val userMsg = springMsg as UserMessage
        assertEquals(1, userMsg.media.size)
        assertEquals("image/jpeg", userMsg.media[0].mimeType.toString())
    }

    @Test
    fun `converts User message with multiple attachments to UserMessage with multiple media`() {
        val parts = listOf(
            ContentPart.Text("Compare these"),
            ContentPart.Image(
                content = AttachmentContent.URL("https://example.com/a.png"),
                format = "png",
                mimeType = "image/png"
            ),
            ContentPart.Image(
                content = AttachmentContent.URL("https://example.com/b.jpg"),
                format = "jpg",
                mimeType = "image/jpeg"
            )
        )
        val koogMsg = Message.User(parts, requestMeta)
        val springMsg = koogMessageToSpringMessage(koogMsg)
        assertTrue(springMsg is UserMessage)
        val userMsg = springMsg as UserMessage
        assertEquals(2, userMsg.media.size)
    }

    @Test
    fun `converts User message with audio attachment to UserMessage with media`() {
        val parts = listOf(
            ContentPart.Text("Transcribe this"),
            ContentPart.Audio(
                content = AttachmentContent.Binary.Bytes(byteArrayOf(0x00, 0x01, 0x02)),
                format = "mp3",
                mimeType = "audio/mp3"
            )
        )
        val koogMsg = Message.User(parts, requestMeta)
        val springMsg = koogMessageToSpringMessage(koogMsg)
        assertTrue(springMsg is UserMessage)
        val userMsg = springMsg as UserMessage
        assertEquals(1, userMsg.media.size)
        assertEquals("audio/mp3", userMsg.media[0].mimeType.toString())
    }

    @Test
    fun `converts User message with file attachment with plain text content`() {
        val parts = listOf(
            ContentPart.Text("Summarize this document"),
            ContentPart.File(
                content = AttachmentContent.PlainText("This is the document content"),
                format = "txt",
                mimeType = "text/plain"
            )
        )
        val koogMsg = Message.User(parts, requestMeta)
        val springMsg = koogMessageToSpringMessage(koogMsg)
        assertTrue(springMsg is UserMessage)
        val userMsg = springMsg as UserMessage
        assertEquals(1, userMsg.media.size)
        assertEquals("text/plain", userMsg.media[0].mimeType.toString())
    }

    @Test
    fun `converts User message with text only has no media`() {
        val koogMsg = Message.User("Just text", requestMeta)
        val springMsg = koogMessageToSpringMessage(koogMsg)
        assertTrue(springMsg is UserMessage)
        val userMsg = springMsg as UserMessage
        assertTrue(userMsg.media.isEmpty())
    }
}
