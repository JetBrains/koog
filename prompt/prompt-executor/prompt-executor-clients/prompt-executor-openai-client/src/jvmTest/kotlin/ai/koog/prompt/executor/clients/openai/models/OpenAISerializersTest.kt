package ai.koog.prompt.executor.clients.openai.models

import ai.koog.test.utils.runWithBothJsonConfigurations
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldInclude
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test

class OpenAISerializersTest {

    @Test
    fun `test ItemTextSerializer serialization`() =
        runWithBothJsonConfigurations("ItemTextSerializer serialization") { json ->
            json.encodeToString(ItemTextSerializer, Item.Text("Hello World")) shouldBe "\"Hello World\""
        }

    @Test
    fun `test ItemTextSerializer deserialization`() =
        runWithBothJsonConfigurations("ItemTextSerializer deserialization") { json ->
            json.decodeFromJsonElement(ItemTextSerializer, JsonPrimitive("Hello World")).shouldNotBeNull {
                shouldBeTypeOf<Item.Text>()
                value shouldBe "Hello World"
            }
        }

    @Test
    fun `test ItemPolymorphicSerializer with JsonPrimitive`() =
        runWithBothJsonConfigurations("ItemPolymorphicSerializer JsonPrimitive") { json ->
            json.decodeFromJsonElement(
                ItemPolymorphicSerializer,
                JsonPrimitive("Simple text")
            ).shouldNotBeNull {
                shouldBeInstanceOf<Item.Text>()
                value shouldBe "Simple text"
            }
        }

    @Test
    fun `test ItemPolymorphicSerializer with InputMessage`() =
        runWithBothJsonConfigurations("ItemPolymorphicSerializer InputMessage") { json ->
            val messageJson = buildJsonObject {
                put("type", JsonPrimitive("message"))
                put(
                    "content",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("type", JsonPrimitive("input_text"))
                                put("text", JsonPrimitive("User message"))
                            }
                        )
                    }
                )
                put("role", JsonPrimitive("user"))
            }

            json.decodeFromJsonElement(ItemPolymorphicSerializer, messageJson).shouldNotBeNull {
                shouldBeInstanceOf<Item.InputMessage>()
                role shouldBe "user"
            }
        }

    @Test
    fun `test ItemPolymorphicSerializer with OutputMessage`() =
        runWithBothJsonConfigurations("ItemPolymorphicSerializer OutputMessage") { json ->
            val messageJson = buildJsonObject {
                put("type", JsonPrimitive("message"))
                put("id", JsonPrimitive("msg_123")) // This makes it an OutputMessage
                put(
                    "content",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("type", JsonPrimitive("output_text"))
                                put("annotations", buildJsonArray { })
                                put("text", JsonPrimitive("Assistant message"))
                            }
                        )
                    }
                )
                put("role", JsonPrimitive("assistant"))
            }

            json.decodeFromJsonElement(ItemPolymorphicSerializer, messageJson).shouldNotBeNull {
                shouldBeInstanceOf<Item.OutputMessage>()
                id shouldBe "msg_123"
            }
        }

    @Test
    fun `test ItemPolymorphicSerializer with all item types`() =
        runWithBothJsonConfigurations("ItemPolymorphicSerializer all types") { json ->
            val itemTypes = mapOf(
                "file_search_call" to Item.FileSearchToolCall.serializer(),
                "computer_call" to Item.ComputerToolCall.serializer(),
                "computer_call_output" to Item.ComputerToolCallOutput.serializer(),
                "web_search_call" to Item.WebSearchToolCall.serializer(),
                "function_call" to Item.FunctionToolCall.serializer(),
                "function_call_output" to Item.FunctionToolCallOutput.serializer(),
                "reasoning" to Item.Reasoning.serializer(),
                "image_generation_call" to Item.ImageGenerationCall.serializer(),
                "code_interpreter_call" to Item.CodeInterpreterToolCall.serializer(),
                "local_shell_call" to Item.LocalShellCall.serializer(),
                "local_shell_call_output" to Item.LocalShellCallOutput.serializer(),
                "mcp_list_tools" to Item.McpListTools.serializer(),
                "mcp_approval_request" to Item.McpApprovalRequest.serializer(),
                "mcp_approval_response" to Item.McpApprovalResponse.serializer(),
                "mcp_call" to Item.McpToolCall.serializer(),
                "custom_tool_call_output" to Item.CustomToolCallOutput.serializer(),
                "custom_tool_call" to Item.CustomToolCall.serializer(),
                "item_reference" to Item.ItemReference.serializer()
            )

            itemTypes.forEach { (type, _) ->
                val itemJson = buildJsonObject {
                    put("type", JsonPrimitive(type))
                    // Add minimal required fields for each type
                    when (type) {
                        "file_search_call" -> {
                            put("id", JsonPrimitive("search_123"))
                            put("queries", buildJsonArray { add(JsonPrimitive("test")) })
                            put("status", JsonPrimitive("completed"))
                        }

                        "computer_call" -> {
                            put(
                                "action",
                                buildJsonObject {
                                    put("type", JsonPrimitive("click"))
                                    put("button", JsonPrimitive("left"))
                                    put("x", JsonPrimitive(10))
                                    put("y", JsonPrimitive(20))
                                }
                            )
                            put("callId", JsonPrimitive("call_123"))
                            put("id", JsonPrimitive("comp_123"))
                            put("pendingSafetyChecks", buildJsonArray { })
                            put("status", JsonPrimitive("completed"))
                        }

                        "computer_call_output" -> {
                            put("callId", JsonPrimitive("call_123"))
                            put(
                                "output",
                                buildJsonObject {
                                    put("type", JsonPrimitive("computer_screenshot"))
                                }
                            )
                        }

                        "web_search_call" -> {
                            put(
                                "action",
                                buildJsonObject {
                                    put("type", JsonPrimitive("search"))
                                    put("query", JsonPrimitive("test query"))
                                }
                            )
                            put("id", JsonPrimitive("web_123"))
                            put("status", JsonPrimitive("completed"))
                        }

                        "function_call" -> {
                            put("arguments", JsonPrimitive("{}"))
                            put("callId", JsonPrimitive("call_123"))
                            put("name", JsonPrimitive("test_func"))
                        }

                        "function_call_output" -> {
                            put("callId", JsonPrimitive("call_123"))
                            put("output", JsonPrimitive("{}"))
                        }

                        "reasoning" -> {
                            put("id", JsonPrimitive("reasoning_123"))
                            put(
                                "summary",
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put("type", JsonPrimitive("summary_text"))
                                            put("text", JsonPrimitive("Summary"))
                                        }
                                    )
                                }
                            )
                        }

                        "image_generation_call" -> {
                            put("id", JsonPrimitive("img_123"))
                            put("result", JsonPrimitive("base64_image"))
                            put("status", JsonPrimitive("completed"))
                        }

                        "code_interpreter_call" -> {
                            put("code", JsonPrimitive("print('hello')"))
                            put("containerId", JsonPrimitive("container_123"))
                            put("id", JsonPrimitive("code_123"))
                            put("outputs", buildJsonArray { })
                            put("status", JsonPrimitive("completed"))
                        }

                        "local_shell_call" -> {
                            put(
                                "action",
                                buildJsonObject {
                                    put("type", JsonPrimitive("exec"))
                                    put("command", buildJsonArray { add(JsonPrimitive("echo")) })
                                    put("end", buildJsonObject { })
                                    put("timeoutMs", JsonPrimitive(5000))
                                }
                            )
                            put("callId", JsonPrimitive("shell_123"))
                            put("id", JsonPrimitive("call_123"))
                            put("status", JsonPrimitive("completed"))
                        }

                        "local_shell_call_output" -> {
                            put("id", JsonPrimitive("output_123"))
                            put("output", JsonPrimitive("hello"))
                        }

                        "mcp_list_tools" -> {
                            put("id", JsonPrimitive("list_123"))
                            put("serverLabel", JsonPrimitive("server"))
                            put("tools", buildJsonArray { })
                        }

                        "mcp_approval_request" -> {
                            put("arguments", JsonPrimitive("{}"))
                            put("id", JsonPrimitive("approval_123"))
                            put("name", JsonPrimitive("tool"))
                            put("serverLabel", JsonPrimitive("server"))
                        }

                        "mcp_approval_response" -> {
                            put("approvalRequestId", JsonPrimitive("req_123"))
                            put("approve", JsonPrimitive(true))
                        }

                        "mcp_call" -> {
                            put("arguments", JsonPrimitive("{}"))
                            put("id", JsonPrimitive("mcp_123"))
                            put("name", JsonPrimitive("tool"))
                            put("serverLabel", JsonPrimitive("server"))
                        }

                        "custom_tool_call_output" -> {
                            put("callId", JsonPrimitive("call_123"))
                            put("output", JsonPrimitive("result"))
                        }

                        "custom_tool_call" -> {
                            put("callId", JsonPrimitive("call_123"))
                            put("input", JsonPrimitive("input"))
                            put("name", JsonPrimitive("custom"))
                        }

                        "item_reference" -> {
                            put("id", JsonPrimitive("ref_123"))
                        }
                    }
                }

                json.decodeFromJsonElement(ItemPolymorphicSerializer, itemJson).shouldNotBeNull()
            }
        }

    @Test
    fun `test ItemPolymorphicSerializer unknown type error`() =
        runWithBothJsonConfigurations("ItemPolymorphicSerializer unknown type") { json ->
            shouldThrow<SerializationException> {
                json.decodeFromJsonElement(
                    ItemPolymorphicSerializer,
                    buildJsonObject {
                        put("type", JsonPrimitive("unknown_type"))
                    }
                )
            }.message shouldBe "Unknown Item type: unknown_type"
        }

    @Test
    fun `test ItemPolymorphicSerializer invalid format error`() =
        runWithBothJsonConfigurations("ItemPolymorphicSerializer invalid format") { json ->
            val invalidJson = JsonPrimitive(123) // Neither primitive string nor object

            shouldThrow<SerializationException> {
                json.decodeFromJsonElement(ItemPolymorphicSerializer, invalidJson)
            }.message shouldInclude "String literal for key 'primitive' should be quoted at element"
        }

    @Test
    fun `test OpenAIResponsesToolChoiceSerializer Mode serialization`() =
        runWithBothJsonConfigurations("OpenAIResponsesToolChoiceSerializer Mode") { json ->
            json.encodeToString(OpenAIResponsesToolChoiceSerializer, OpenAIResponsesToolChoice.Mode("auto"))
                .shouldBe("\"auto\"")

            json.decodeFromJsonElement(OpenAIResponsesToolChoiceSerializer, JsonPrimitive("auto"))
                .shouldBe(OpenAIResponsesToolChoice.Mode("auto"))
        }

    @Test
    fun `test OpenAIResponsesToolChoiceSerializer AllowedTools serialization`() =
        runWithBothJsonConfigurations("OpenAIResponsesToolChoiceSerializer AllowedTools") { json ->
            json.decodeFromJsonElement(
                OpenAIResponsesToolChoiceSerializer,
                json.parseToJsonElement(
                    json.encodeToString(
                        OpenAIResponsesToolChoiceSerializer,
                        OpenAIResponsesToolChoice.AllowedTools(
                            mode = "required",
                            tools = listOf(buildJsonObject { put("type", JsonPrimitive("function")) })
                        )
                    )
                )
            ).shouldNotBeNull {
                shouldBeInstanceOf<OpenAIResponsesToolChoice.AllowedTools>()
                mode shouldBe "required"
                tools shouldHaveSize 1
            }
        }

    @Test
    fun `test OpenAIResponsesToolChoiceSerializer HostedTool serialization`() =
        runWithBothJsonConfigurations("OpenAIResponsesToolChoiceSerializer HostedTool") { json ->
            json.decodeFromJsonElement(
                OpenAIResponsesToolChoiceSerializer,
                json.parseToJsonElement(
                    json.encodeToString(
                        OpenAIResponsesToolChoiceSerializer,
                        OpenAIResponsesToolChoice.HostedTool("file_search")
                    )
                )
            ).shouldNotBeNull {
                shouldBeInstanceOf<OpenAIResponsesToolChoice.HostedTool>()
                type shouldBe "file_search"
            }
        }

    @Test
    fun `test OpenAIResponsesToolChoiceSerializer unknown object type error`() =
        runWithBothJsonConfigurations("OpenAIResponsesToolChoiceSerializer unknown type") { json ->
            shouldThrow<SerializationException> {
                json.decodeFromJsonElement(
                    OpenAIResponsesToolChoiceSerializer,
                    buildJsonObject {
                        put("type", JsonPrimitive("unknown_hosted_type"))
                    }
                )
            }.message shouldBe "Not recognize tool choice type: unknown_hosted_type"
        }

    @Test
    fun `test OpenAIResponsesToolChoiceSerializer invalid array type error`() =
        runWithBothJsonConfigurations("OpenAIResponsesToolChoiceSerializer array error") { json ->
            shouldThrow<SerializationException> {
                json.decodeFromJsonElement(OpenAIResponsesToolChoiceSerializer, buildJsonArray { })
            }.message shouldBe "Tool choice must be either a string or an object"
        }

    @Test
    fun `test OpenAIResponsesToolChoiceSerializer with number input`() =
        runWithBothJsonConfigurations("OpenAIResponsesToolChoiceSerializer number input") { json ->
            // Numbers are converted to string mode by the serializer
            json.decodeFromJsonElement(
                OpenAIResponsesToolChoiceSerializer,
                JsonPrimitive(123)
            ) shouldBe OpenAIResponsesToolChoice.Mode("123")
        }

    @Test
    fun `test ItemTextSerializer with empty string`() =
        runWithBothJsonConfigurations("ItemTextSerializer empty string") { json ->
            json.encodeToString(ItemTextSerializer, Item.Text("")) shouldBe "\"\""
            json.decodeFromJsonElement(ItemTextSerializer, JsonPrimitive("")).value shouldBe ""
        }

    @Test
    fun `test ItemPolymorphicSerializer with minimal message`() =
        runWithBothJsonConfigurations("ItemPolymorphicSerializer minimal message") { json ->
            val minimalMessageJson = buildJsonObject {
                put("type", JsonPrimitive("message"))
                put("content", buildJsonArray { })
                put("role", JsonPrimitive("user"))
            }

            val decodedMessage =
                json.decodeFromJsonElement(ItemPolymorphicSerializer, minimalMessageJson) as Item.InputMessage
            decodedMessage.role shouldBe "user"
            decodedMessage.content shouldBe emptyList()
            decodedMessage.status shouldBe null
        }

    @Test
    fun `test complex serialization round trip`() = runWithBothJsonConfigurations("complex round trip") { json ->
        val complexItem = Item.ComputerToolCall(
            action = Item.ComputerToolCall.Action.Drag(
                path = listOf(
                    Item.ComputerToolCall.Action.Coordinates(0, 0),
                    Item.ComputerToolCall.Action.Coordinates(100, 100)
                )
            ),
            callId = "drag_call_123",
            id = "drag_id_456",
            pendingSafetyChecks = listOf(
                Item.ComputerToolCall.PendingSafetyCheck("safety_001", "check_001", "Drag safety check")
            ),
            status = "in_progress"
        )

        json.decodeFromJsonElement(
            ItemPolymorphicSerializer,
            json.parseToJsonElement(json.encodeToString(ItemPolymorphicSerializer, complexItem))
        ).shouldNotBeNull {
            shouldBeInstanceOf<Item.ComputerToolCall>()
            callId shouldBe "drag_call_123"
            id shouldBe "drag_id_456"
            status shouldBe "in_progress"
            action.shouldNotBeNull {
                shouldBeTypeOf<Item.ComputerToolCall.Action.Drag>()
                path shouldHaveSize 2
                path[0].shouldBeEqualToComparingFields(Item.ComputerToolCall.Action.Coordinates(0, 0))
                path[1].shouldBeEqualToComparingFields(Item.ComputerToolCall.Action.Coordinates(100, 100))
            }
        }
    }
}
