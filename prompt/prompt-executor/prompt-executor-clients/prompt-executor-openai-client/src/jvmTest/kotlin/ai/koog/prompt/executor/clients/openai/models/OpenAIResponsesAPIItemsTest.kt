package ai.koog.prompt.executor.clients.openai.models

import ai.koog.test.utils.runWithBothJsonConfigurations
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test

class OpenAIResponsesAPIItemsTest {

    @Test
    fun `test Item_Text serialization`() = runWithBothJsonConfigurations("Item.Text serialization") { json ->
        json.encodeToString<Item>(ItemPolymorphicSerializer, Item.Text("Hello world")) shouldEqualJson "\"Hello world\""
    }

    @Test
    fun `test Item_Text deserialization`() = runWithBothJsonConfigurations("Item.Text deserialization") { json ->
        json.decodeFromJsonElement(
            ItemPolymorphicSerializer,
            JsonPrimitive("Hello world")
        ) shouldBe Item.Text("Hello world")
    }

    @Test
    fun `test Item_InputMessage serialization`() =
        runWithBothJsonConfigurations("Item.InputMessage serialization") { json ->
            json.encodeToString(
                ItemPolymorphicSerializer,
                Item.InputMessage(
                    content = listOf(InputContent.Text("Test message")),
                    role = "user",
                    status = OpenAIInputStatus.COMPLETED
                )
            ) shouldEqualJson """
            {
                "type": "message",
                "content": [{"type": "input_text", "text": "Test message"}],
                "role": "user",
                "status": "completed"
            }
            """.trimIndent()
        }

    @Test
    fun `test Item_InputMessage deserialization`() =
        runWithBothJsonConfigurations("Item.InputMessage deserialization") { json ->
            val jsonInput = buildJsonObject {
                put("type", JsonPrimitive("message"))
                put(
                    "content",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("type", JsonPrimitive("input_text"))
                                put("text", JsonPrimitive("Test message"))
                            }
                        )
                    }
                )
                put("role", JsonPrimitive("user"))
                put("status", JsonPrimitive("completed"))
            }

            (json.decodeFromJsonElement(ItemPolymorphicSerializer, jsonInput) as Item.InputMessage).shouldNotBeNull {
                role shouldBe "user"
                status shouldBe OpenAIInputStatus.COMPLETED
                type shouldBe "message"
                content shouldHaveSize 1
                content.first() shouldBeEqualToComparingFields InputContent.Text("Test message")
            }
        }

    @Test
    fun `test Item_OutputMessage serialization`() =
        runWithBothJsonConfigurations("Item.OutputMessage serialization") { json ->
            json.encodeToString(
                ItemPolymorphicSerializer,
                Item.OutputMessage(
                    content = listOf(
                        OutputContent.Text(
                            annotations = emptyList(),
                            text = "Response text"
                        )
                    ),
                    id = "msg-123",
                    role = "assistant",
                    status = OpenAIInputStatus.COMPLETED
                )
            ) shouldEqualJson """
            {
                "type": "message",
                "content": [{"type": "output_text", "annotations": [], "text": "Response text"}],
                "id": "msg-123",
                "role": "assistant",
                "status": "completed"
            }
            """.trimIndent()
        }

    @Test
    fun `test Item_OutputMessage text() method`() {
        Item.OutputMessage(
            content = listOf(
                OutputContent.Text(annotations = emptyList(), text = "Hello"),
                OutputContent.Text(annotations = emptyList(), text = "World"),
                OutputContent.Refusal("Sorry, I can't help with that")
            )
        ).text() shouldBe "Hello World Sorry, I can't help with that"
    }

    @Test
    fun `test Item_FunctionToolCall serialization`() =
        runWithBothJsonConfigurations("Item.FunctionToolCall serialization") { json ->
            json.encodeToString(
                ItemPolymorphicSerializer,
                Item.FunctionToolCall(
                    arguments = """{"param": "value"}""",
                    callId = "call_123",
                    name = "test_function",
                    id = "func_456",
                    status = OpenAIInputStatus.IN_PROGRESS
                )
            ) shouldEqualJson """
            {
                "type": "function_call",
                "arguments": "{\"param\": \"value\"}",
                "callId": "call_123",
                "name": "test_function",
                "id": "func_456",
                "status": "in_progress"
            }
            """.trimIndent()
        }

    @Test
    fun `test Item_FunctionToolCallOutput serialization`() =
        runWithBothJsonConfigurations("Item.FunctionToolCallOutput serialization") { json ->
            json.encodeToString(
                ItemPolymorphicSerializer,
                Item.FunctionToolCallOutput(
                    callId = "call_123",
                    output = """{"result": "success"}""",
                    id = "output_456",
                    status = OpenAIInputStatus.COMPLETED
                )
            ) shouldEqualJson """
            {
                "type": "function_call_output",
                "callId": "call_123",
                "output": "{\"result\": \"success\"}",
                "id": "output_456",
                "status": "completed"
            }
            """.trimIndent()
        }

    @Test
    fun `test Item_FileSearchToolCall serialization`() =
        runWithBothJsonConfigurations("Item.FileSearchToolCall serialization") { json ->
            json.encodeToString(
                ItemPolymorphicSerializer,
                Item.FileSearchToolCall(
                    id = "search_123",
                    queries = listOf("test query", "another query"),
                    status = "searching",
                    results = listOf(
                        Item.FileSearchToolCall.FileSearchToolResult(
                            fileId = "file_123",
                            filename = "test.txt",
                            score = 0.95,
                            text = "Found text",
                            attributes = mapOf("category" to "document")
                        )
                    )
                )
            ) shouldEqualJson """
            {
                "type": "file_search_call",
                "id": "search_123",
                "queries": ["test query", "another query"],
                "status": "searching",
                "results": [{
                    "fileId": "file_123",
                    "filename": "test.txt",
                    "score": 0.95,
                    "text": "Found text",
                    "attributes": {"category": "document"}
                }]
            }
            """.trimIndent()
        }

    @Test
    fun `test Item_ComputerToolCall serialization`() =
        runWithBothJsonConfigurations("Item.ComputerToolCall serialization") { json ->
            json.encodeToString(
                ItemPolymorphicSerializer,
                Item.ComputerToolCall(
                    action = Item.ComputerToolCall.Action.Click("left", 100, 200),
                    callId = "comp_123",
                    id = "call_456",
                    pendingSafetyChecks = listOf(
                        Item.ComputerToolCall.PendingSafetyCheck("safety_001", "check_123", "Safety check message")
                    ),
                    status = "in_progress"
                )
            ) shouldEqualJson """
            {
                "type": "computer_call",
                "action": {"type": "click", "button": "left", "x": 100, "y": 200},
                "callId": "comp_123",
                "id": "call_456",
                "pendingSafetyChecks": [{
                    "code": "safety_001",
                    "id": "check_123",
                    "message": "Safety check message"
                }],
                "status": "in_progress"
            }
            """.trimIndent()
        }

    @Test
    fun `test ComputerToolCall Actions serialization`() =
        runWithBothJsonConfigurations("ComputerToolCall Actions") { json ->
            val actions = listOf(
                Item.ComputerToolCall.Action.Click("right", 50, 75),
                Item.ComputerToolCall.Action.DoubleClick(10, 20),
                Item.ComputerToolCall.Action.Drag(
                    listOf(
                        Item.ComputerToolCall.Action.Coordinates(0, 0),
                        Item.ComputerToolCall.Action.Coordinates(10, 10)
                    )
                ),
                Item.ComputerToolCall.Action.KeyPress(listOf("ctrl", "c")),
                Item.ComputerToolCall.Action.Move(30, 40),
                Item.ComputerToolCall.Action.Screenshot(),
                Item.ComputerToolCall.Action.Scroll(5, 10, 100, 200),
                Item.ComputerToolCall.Action.Type("Hello World"),
                Item.ComputerToolCall.Action.Wait()
            )

            actions.forEachIndexed { index, action ->
                val jsonString = json.encodeToString(action)
                when (index) {
                    0 -> jsonString shouldEqualJson """{"type":"click","button":"right","x":50,"y":75}"""
                    1 -> jsonString shouldEqualJson """{"type":"double_click","x":10,"y":20}"""
                    2 -> jsonString shouldEqualJson """{"type":"drag","path":[{"x":0,"y":0},{"x":10,"y":10}]}"""
                    3 -> jsonString shouldEqualJson """{"type":"keypress","keys":["ctrl","c"]}"""
                    4 -> jsonString shouldEqualJson """{"type":"move","x":30,"y":40}"""
                    5 -> jsonString shouldEqualJson """{"type":"screenshot"}"""
                    6 -> jsonString shouldEqualJson """{"type":"scroll","scrollX":5,"scrollY":10,"x":100,"y":200}"""
                    7 -> jsonString shouldEqualJson """{"type":"type","text":"Hello World"}"""
                    8 -> jsonString shouldEqualJson """{"type":"wait"}"""
                }
            }
        }

    @Test
    fun `test Item_WebSearchToolCall serialization`() =
        runWithBothJsonConfigurations("Item.WebSearchToolCall serialization") { json ->
            json.encodeToString(
                ItemPolymorphicSerializer,
                Item.WebSearchToolCall(
                    action = Item.WebSearchToolCall.Action.Search("test query"),
                    id = "web_123",
                    status = "in_progress"
                )
            ) shouldEqualJson """
            {
                "type": "web_search_call",
                "action": {"type": "search", "query": "test query"},
                "id": "web_123",
                "status": "in_progress"
            }
            """.trimIndent()
        }

    @Test
    fun `test WebSearchToolCall Actions serialization`() =
        runWithBothJsonConfigurations("WebSearchToolCall Actions") { json ->
            json.encodeToString(
                Item.WebSearchToolCall.Action.Search("machine learning")
            ) shouldEqualJson """{"query":"machine learning"}"""
            json.encodeToString(
                Item.WebSearchToolCall.Action.OpenPage("https://example.com")
            ) shouldEqualJson """{"url":"https://example.com"}"""
            json.encodeToString(
                Item.WebSearchToolCall.Action.Find(
                    "pattern",
                    "https://example.com"
                )
            ) shouldEqualJson """{"pattern":"pattern","url":"https://example.com"}"""
        }

    @Test
    fun `test Item_CodeInterpreterToolCall serialization`() =
        runWithBothJsonConfigurations("Item.CodeInterpreterToolCall serialization") { json ->
            json.encodeToString(
                ItemPolymorphicSerializer,
                Item.CodeInterpreterToolCall(
                    code = "print('Hello World')",
                    containerId = "container_123",
                    id = "code_456",
                    outputs = listOf(
                        Item.CodeInterpreterToolCall.Output.Logs("Hello World\n"),
                        Item.CodeInterpreterToolCall.Output.Image("https://example.com/image.png")
                    ),
                    status = OpenAIInputStatus.COMPLETED
                )
            ) shouldEqualJson """
            {
                "type": "code_interpreter_call",
                "code": "print('Hello World')",
                "containerId": "container_123",
                "id": "code_456",
                "outputs": [
                    {"type": "logs", "logs": "Hello World\n"},
                    {"type": "image", "url": "https://example.com/image.png"}
                ],
                "status": "completed"
            }
            """.trimIndent()
        }

    @Test
    fun `test Item_Reasoning serialization`() = runWithBothJsonConfigurations("Item.Reasoning serialization") { json ->
        json.encodeToString(
            ItemPolymorphicSerializer,
            Item.Reasoning(
                id = "reasoning_123",
                summary = listOf(
                    Item.Reasoning.Summary("This is a summary of the reasoning")
                ),
                content = listOf(
                    Item.Reasoning.Content("Detailed reasoning content")
                ),
                encryptedContent = "encrypted_content_here",
                status = OpenAIInputStatus.COMPLETED
            )
        ) shouldEqualJson """
            {
                "type": "reasoning",
                "id": "reasoning_123",
                "summary": [{"type": "summary_text", "text": "This is a summary of the reasoning"}],
                "content": [{"type": "reasoning_text", "text": "Detailed reasoning content"}],
                "encryptedContent": "encrypted_content_here",
                "status": "completed"
            }
        """.trimIndent()
    }

    @Test
    fun `test Item_LocalShellCall serialization`() =
        runWithBothJsonConfigurations("Item.LocalShellCall serialization") { json ->
            json.encodeToString(
                ItemPolymorphicSerializer,
                Item.LocalShellCall(
                    action = Item.LocalShellCall.Action(
                        command = listOf("echo", "hello"),
                        end = buildJsonObject { put("PATH", JsonPrimitive("/usr/bin")) },
                        timeoutMs = 5000,
                        user = "testuser",
                        workingDirectory = "/tmp"
                    ),
                    callId = "shell_123",
                    id = "call_456",
                    status = "in_progress"
                )
            ) shouldEqualJson """
            {
                "type": "local_shell_call",
                "action": {
                    "type": "exec",
                    "command": ["echo", "hello"],
                    "end": {"PATH": "/usr/bin"},
                    "timeoutMs": 5000,
                    "user": "testuser",
                    "workingDirectory": "/tmp"
                },
                "callId": "shell_123",
                "id": "call_456",
                "status": "in_progress"
            }
            """.trimIndent()
        }

    @Test
    fun `test Item_ItemReference serialization`() =
        runWithBothJsonConfigurations("Item.ItemReference serialization") { json ->
            json.encodeToString(
                ItemPolymorphicSerializer,
                Item.ItemReference("ref_123")
            ) shouldEqualJson """
            {
                "type": "item_reference",
                "id": "ref_123"
            }
            """.trimIndent()
        }

    @Test
    fun `test OpenAIInclude enum serialization`() =
        runWithBothJsonConfigurations("OpenAIInclude enum serialization") { json ->
            val includes = listOf(
                OpenAIInclude.WEB_SEARCH_CALL_ACTION_SOURCES,
                OpenAIInclude.CODE_INTERPRETER_CALL_OUTPUTS,
                OpenAIInclude.COMPUTER_CALL_OUTPUT_IMAGE_URL,
                OpenAIInclude.FILE_SEARCH_CALL_RESULTS,
                OpenAIInclude.INPUT_IMAGE_URL,
                OpenAIInclude.OUTPUT_TEXT_LOGPROBS,
                OpenAIInclude.REASONING_ENCRYPTED_CONTENT
            )

            val expectedSerializations = listOf(
                "\"web_search_call.action.sources\"",
                "\"code_interpreter_call.outputs\"",
                "\"computer_call_output.output.image_url\"",
                "\"file_search_call.results\"",
                "\"message.input_image.image_url\"",
                "\"message.output_text.logprobs\"",
                "\"reasoning.encrypted_content\""
            )

            includes.zip(expectedSerializations).forEach { (include, expected) ->
                json.encodeToString(include) shouldEqualJson expected
            }
        }

    @Test
    fun `test OpenAIInputStatus enum serialization`() =
        runWithBothJsonConfigurations("OpenAIInputStatus enum serialization") { json ->
            val statuses = OpenAIInputStatus.entries
            val expectedSerializations = listOf(
                "\"in_progress\"",
                "\"completed\"",
                "\"searching\"",
                "\"failed\"",
                "\"interpreting\"",
                "\"incomplete\"",
                "\"cancelled\"",
                "\"queued\""
            )

            statuses.zip(expectedSerializations).forEach { (status, expected) ->
                json.encodeToString(status) shouldEqualJson expected
            }
        }

    @Test
    fun `test Truncation enum serialization`() =
        runWithBothJsonConfigurations("Truncation enum serialization") { json ->
            json.encodeToString(Truncation.AUTO) shouldEqualJson "\"auto\""
            json.encodeToString(Truncation.DISABLED) shouldEqualJson "\"disabled\""
        }

    @Test
    fun `test ReasoningSummary enum serialization`() =
        runWithBothJsonConfigurations("ReasoningSummary enum serialization") { json ->
            json.encodeToString(ReasoningSummary.AUTO) shouldEqualJson "\"auto\""
            json.encodeToString(ReasoningSummary.CONCISE) shouldEqualJson "\"concise\""
            json.encodeToString(ReasoningSummary.DETAILED) shouldEqualJson "\"detailed\""
        }

    @Test
    fun `test TextVerbosity enum serialization`() =
        runWithBothJsonConfigurations("TextVerbosity enum serialization") { json ->
            json.encodeToString(TextVerbosity.LOW) shouldEqualJson "\"low\""
            json.encodeToString(TextVerbosity.MEDIUM) shouldEqualJson "\"medium\""
            json.encodeToString(TextVerbosity.HIGH) shouldEqualJson "\"high\""
        }
}
