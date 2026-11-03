package ai.koog.prompt.processor

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.utils.time.KoogClock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class ToolCallArgumentCoercionProcessorTest {
    private companion object {
        private val serializer = KotlinxSerializer()

        private val testClock: KoogClock = KoogClock { Instant.parse("2023-01-01T00:00:00Z") }

        private val testMetaInfo = ResponseMetaInfo.create(testClock)

        private val executor = getMockExecutor(serializer) { }
        private val prompt = prompt("test-prompt") { }
        private val model = OpenAIModels.Chat.GPT4o

        private val processor = ToolCallArgumentCoercionProcessor()

        private val innerObject = buildJsonObject { put("k", JsonPrimitive("v")) }
        private val innerObjectString = innerObject.toString()

        private val innerObjectType = ToolParameterType.Object(
            properties = listOf(
                ToolParameterDescriptor("k", "Some property", ToolParameterType.String)
            )
        )

        private val objectTool = ToolDescriptor(
            name = "object_tool",
            description = "A tool with an object parameter",
            requiredParameters = listOf(
                ToolParameterDescriptor("payload", "Structured payload", innerObjectType)
            ),
            optionalParameters = listOf(
                ToolParameterDescriptor("optional_payload", "Optional structured payload", innerObjectType)
            )
        )

        private val listTool = ToolDescriptor(
            name = "list_tool",
            description = "A tool with a list parameter",
            requiredParameters = listOf(
                ToolParameterDescriptor("items", "List of strings", ToolParameterType.List(ToolParameterType.String))
            )
        )

        private val nestedObjectTool = ToolDescriptor(
            name = "nested_object_tool",
            description = "A tool with a nested object parameter",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    "outer",
                    "Outer object",
                    ToolParameterType.Object(
                        properties = listOf(
                            ToolParameterDescriptor("inner", "Inner object", innerObjectType)
                        )
                    )
                )
            )
        )

        private val anyOfTool = ToolDescriptor(
            name = "any_of_tool",
            description = "A tool with an anyOf parameter",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    "target",
                    "Write target",
                    ToolParameterType.AnyOf(
                        arrayOf(
                            ToolParameterDescriptor(
                                "path_target",
                                "Path target",
                                ToolParameterType.Object(
                                    properties = listOf(
                                        ToolParameterDescriptor(
                                            "type",
                                            "Target type",
                                            ToolParameterType.Enum(arrayOf("path"))
                                        ),
                                        ToolParameterDescriptor("path", "Note path", ToolParameterType.String)
                                    )
                                )
                            ),
                            ToolParameterDescriptor(
                                "active_target",
                                "Active target",
                                ToolParameterType.Object(
                                    properties = listOf(
                                        ToolParameterDescriptor(
                                            "type",
                                            "Target type",
                                            ToolParameterType.Enum(arrayOf("active"))
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        private val anyOfWithStringTool = ToolDescriptor(
            name = "any_of_with_string_tool",
            description = "A tool with an anyOf parameter that accepts strings",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    "value",
                    "String or object value",
                    ToolParameterType.AnyOf(
                        arrayOf(
                            ToolParameterDescriptor("string_value", "String value", ToolParameterType.String),
                            ToolParameterDescriptor("object_value", "Object value", innerObjectType)
                        )
                    )
                )
            )
        )

        private val stringTool = ToolDescriptor(
            name = "string_tool",
            description = "A tool with a string parameter",
            requiredParameters = listOf(
                ToolParameterDescriptor("text", "Plain text", ToolParameterType.String)
            )
        )

        private val openMapTool = ToolDescriptor(
            name = "open_map_tool",
            description = "A tool with an open object parameter",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    "map",
                    "Open map",
                    ToolParameterType.Object(
                        properties = emptyList(),
                        additionalProperties = true,
                        additionalPropertiesType = innerObjectType
                    )
                )
            )
        )

        private val closedMapTool = ToolDescriptor(
            name = "closed_map_tool",
            description = "A tool with a closed object parameter",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    "map",
                    "Closed map",
                    ToolParameterType.Object(
                        properties = emptyList(),
                        additionalProperties = false
                    )
                )
            )
        )
    }

    @Test
    fun test_shouldCoerceStringifiedObjectArgument() = runTest {
        val message = toolCallMessage("object_tool") {
            put("payload", JsonPrimitive(innerObjectString))
        }

        val result = process(listOf(objectTool), message)

        val toolCall = result.parts.filterIsInstance<MessagePart.Tool.Call>().single()
        assertEquals(buildJsonObject { put("payload", innerObject) }, toolCall.argsJson)
        assertEquals("1", toolCall.id)
        assertEquals(testMetaInfo, result.metaInfo)
    }

    @Test
    fun test_shouldCoerceStringifiedOptionalParameter() = runTest {
        val message = toolCallMessage("object_tool") {
            put("payload", JsonPrimitive(innerObjectString))
            put("optional_payload", JsonPrimitive(innerObjectString))
        }

        val result = process(listOf(objectTool), message)

        val expected = buildJsonObject {
            put("payload", innerObject)
            put("optional_payload", innerObject)
        }
        assertEquals(expected, singleToolCall(result).argsJson)
    }

    @Test
    fun test_shouldCoerceStringifiedListArgument() = runTest {
        val stringifiedArray = buildJsonArray {
            add(JsonPrimitive("a"))
            add(JsonPrimitive("b"))
        }

        val message = toolCallMessage("list_tool") {
            put("items", JsonPrimitive(stringifiedArray.toString()))
        }

        val result = process(listOf(listTool), message)

        assertEquals(buildJsonObject { put("items", stringifiedArray) }, singleToolCall(result).argsJson)
    }

    @Test
    fun test_shouldCoerceNestedStringifiedObjectProperty() = runTest {
        val outerWithStringifiedInner = buildJsonObject { put("inner", JsonPrimitive(innerObjectString)) }

        val message = toolCallMessage("nested_object_tool") {
            put("outer", JsonPrimitive(outerWithStringifiedInner.toString()))
        }

        val result = process(listOf(nestedObjectTool), message)

        val expected = buildJsonObject {
            put("outer", buildJsonObject { put("inner", innerObject) })
        }
        assertEquals(expected, singleToolCall(result).argsJson)
    }

    @Test
    fun test_shouldCoerceStringifiedAnyOfObjectCandidate() = runTest {
        val target = buildJsonObject {
            put("type", JsonPrimitive("path"))
            put("path", JsonPrimitive("folder/note.md"))
        }

        val message = toolCallMessage("any_of_tool") {
            put("target", JsonPrimitive(target.toString()))
        }

        val result = process(listOf(anyOfTool), message)

        assertEquals(buildJsonObject { put("target", target) }, singleToolCall(result).argsJson)
    }

    @Test
    fun test_shouldPreserveStringWhenAnyOfHasStringCandidate() = runTest {
        val message = toolCallMessage("any_of_with_string_tool") {
            put("value", JsonPrimitive(innerObjectString))
        }

        val result = process(listOf(anyOfWithStringTool), message)

        assertEquals(message, result)
    }

    @Test
    fun test_shouldPreserveStringParameter() = runTest {
        val message = toolCallMessage("string_tool") {
            put("text", JsonPrimitive(innerObjectString))
        }

        val result = process(listOf(stringTool), message)

        assertEquals(message, result)
    }

    @Test
    fun test_shouldPreserveValueOnParseFailure() = runTest {
        val message = toolCallMessage("object_tool") {
            put("payload", JsonPrimitive("not json"))
        }

        val result = process(listOf(objectTool), message)

        assertEquals(message, result)
    }

    @Test
    fun test_shouldPreserveValueOnShapeMismatch() = runTest {
        val stringifiedArray = buildJsonArray { add(JsonPrimitive("a")) }.toString()

        val message = toolCallMessage("object_tool") {
            put("payload", JsonPrimitive(stringifiedArray))
        }

        val result = process(listOf(objectTool), message)

        assertEquals(message, result)
    }

    @Test
    fun test_shouldPreserveUnknownTool() = runTest {
        val message = toolCallMessage("missing_tool") {
            put("payload", JsonPrimitive(innerObjectString))
        }

        val result = process(listOf(objectTool), message)

        assertEquals(message, result)
    }

    @Test
    fun test_shouldPreserveUnknownArgumentKey() = runTest {
        val message = toolCallMessage("object_tool") {
            put("payload", JsonPrimitive(innerObjectString))
            put("unknown", JsonPrimitive(innerObjectString))
        }

        val result = process(listOf(objectTool), message)

        val expected = buildJsonObject {
            put("payload", innerObject)
            put("unknown", JsonPrimitive(innerObjectString))
        }
        assertEquals(expected, singleToolCall(result).argsJson)
    }

    @Test
    fun test_shouldCoerceAdditionalPropertiesWithDeclaredType() = runTest {
        val message = toolCallMessage("open_map_tool") {
            put("map", buildJsonObject { put("extra", JsonPrimitive(innerObjectString)) })
        }

        val result = process(listOf(openMapTool), message)

        val expected = buildJsonObject {
            put("map", buildJsonObject { put("extra", innerObject) })
        }
        assertEquals(expected, singleToolCall(result).argsJson)
    }

    @Test
    fun test_shouldPreserveAdditionalPropertiesWhenNotAllowed() = runTest {
        val message = toolCallMessage("closed_map_tool") {
            put("map", buildJsonObject { put("extra", JsonPrimitive(innerObjectString)) })
        }

        val result = process(listOf(closedMapTool), message)

        assertEquals(message, result)
    }

    @Test
    fun test_shouldStopCoercionPastDepthBoundary() = runTest {
        // Descriptor side: o1 -> o2 -> ... -> o8 -> o9 -> {k: String}, each level a single object property.
        var levelType: ToolParameterType = innerObjectType
        for (i in 9 downTo 2) {
            levelType = ToolParameterType.Object(
                properties = listOf(ToolParameterDescriptor("o$i", "Level $i", levelType))
            )
        }
        val deepTool = ToolDescriptor(
            name = "deep_tool",
            description = "A tool with a deeply nested object parameter",
            requiredParameters = listOf(ToolParameterDescriptor("o1", "Level 1", levelType))
        )

        // Value side: real objects down to depth 7; the value at depth 8 is a stringified object whose
        // "o9" property (depth 9) is itself a stringified object.
        val depth8Object = buildJsonObject { put("o9", JsonPrimitive(innerObjectString)) }
        var argValue: JsonElement = JsonPrimitive(depth8Object.toString())
        for (i in 8 downTo 2) {
            argValue = buildJsonObject { put("o$i", argValue) }
        }

        val message = toolCallMessage("deep_tool") { put("o1", argValue) }

        val result = process(listOf(deepTool), message)

        var expected: JsonElement = depth8Object
        for (i in 8 downTo 2) {
            expected = buildJsonObject { put("o$i", expected) }
        }
        val argsJson = singleToolCall(result).argsJson
        assertEquals(buildJsonObject { put("o1", expected) }, argsJson)

        var cursor: JsonElement = argsJson.getValue("o1")
        for (i in 2..7) {
            cursor = assertIs<JsonObject>(cursor).getValue("o$i")
        }
        val depth8Value = assertIs<JsonObject>(
            assertIs<JsonObject>(cursor).getValue("o8"),
            "value at depth 8 should be coerced into an object"
        )
        val depth9Value = depth8Value.getValue("o9")
        assertTrue(
            depth9Value is JsonPrimitive && depth9Value.isString,
            "value at depth 9 should stay a stringified object"
        )
    }

    @Test
    fun test_shouldCoerceAfterManualToolCallFixProcessorInChain() = runTest {
        val chainedProcessor = ManualToolCallFixProcessor(Tools.toolRegistry) + ToolCallArgumentCoercionProcessor()

        val textToolCall = """
            {
                "tool": "object_tool",
                "args": {"payload": "{\"k\":\"v\"}"}
            }
        """.trimIndent()
        val message = Message.Assistant(textToolCall, metaInfo = testMetaInfo)

        val result = chainedProcessor.process(executor, prompt, model, listOf(objectTool), message, serializer)

        val toolCall = singleToolCall(result)
        assertEquals("object_tool", toolCall.tool)
        assertEquals(buildJsonObject { put("payload", innerObject) }, toolCall.argsJson)
    }

    private fun toolCallMessage(tool: String, buildArgs: JsonObjectBuilder.() -> Unit) =
        Message.Assistant(
            parts = listOf(
                MessagePart.Tool.Call(id = "1", tool = tool, args = buildJsonObject(buildArgs))
            ),
            metaInfo = testMetaInfo
        )

    private fun singleToolCall(message: Message.Assistant) =
        message.parts.filterIsInstance<MessagePart.Tool.Call>().single()

    private suspend fun process(tools: List<ToolDescriptor>, response: Message.Assistant) =
        processor.process(executor, prompt, model, tools, response, serializer)
}
