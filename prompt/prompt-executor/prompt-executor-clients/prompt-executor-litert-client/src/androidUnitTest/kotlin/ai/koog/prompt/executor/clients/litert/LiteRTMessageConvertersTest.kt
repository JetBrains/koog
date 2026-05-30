package ai.koog.prompt.executor.clients.litert

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.utils.time.KoogClock
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Role
import com.google.ai.edge.litertlm.ToolCall
import org.junit.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import com.google.ai.edge.litertlm.Message as LitertMessage

/**
 * Unit tests for [toLitertMessage], focused on the tool-call/tool-result distinction
 * that previously regressed by being collapsed into plain user text.
 *
 * TODO: Re-enable once CI runs androidUnitTest on JDK 21+.
 *  litertlm-android 0.11.x is compiled with Java 21 (class file 65.0), while CI currently uses
 *  JDK 17 (61.0), causing UnsupportedClassVersionError on com.google.ai.edge.litertlm.Message.
 */
@Ignore("Requires JDK 21+ on the test runtime; litertlm-android is built with Java 21.")
class LiteRTMessageConvertersTest {

    @Test
    fun testSystemMessageMapsToSystemRole() {
        val koog = Message.System("you are helpful", RequestMetaInfo.Empty)
        val litert = koog.toLitertMessage()

        assertEquals(Role.SYSTEM, litert.role)
        val content = litert.contents.contents.single()
        assertTrue(content is Content.Text)
        assertEquals("you are helpful", content.text)
        assertTrue(litert.toolCalls.isEmpty())
    }

    @Test
    fun testUserMessageMapsToUserRole() {
        val koog = Message.User("hi", RequestMetaInfo.Empty)
        val litert = koog.toLitertMessage()

        assertEquals(Role.USER, litert.role)
        assertEquals("hi", (litert.contents.contents.single() as Content.Text).text)
    }

    @Test
    fun testAssistantMessageMapsToModelRole() {
        val koog = Message.Assistant("hello!", ResponseMetaInfo.Empty)
        val litert = koog.toLitertMessage()

        assertEquals(Role.MODEL, litert.role)
        assertEquals("hello!", (litert.contents.contents.single() as Content.Text).text)
        assertTrue(litert.toolCalls.isEmpty())
    }

    @Test
    fun testToolCallMapsToModelMessageWithToolCalls() {
        val koog = Message.Assistant(
            MessagePart.Tool.Call(
                id = "c1",
                tool = "search",
                args = """{"query":"weather","limit":3}""",
            ),
            metaInfo = ResponseMetaInfo.Empty,
        )

        val litert = koog.toLitertMessage()

        assertEquals(Role.MODEL, litert.role)
        assertTrue(
            litert.contents.contents.isEmpty(),
            "Tool.Call must not be sent as user/model text content",
        )
        val toolCall = litert.toolCalls.single()
        assertEquals("search", toolCall.name)
        assertEquals("weather", toolCall.arguments["query"])
        assertEquals(3L, toolCall.arguments["limit"])
    }

    @Test
    fun testToolCallWithBlankContentProducesEmptyArguments() {
        val koog = Message.Assistant(
            MessagePart.Tool.Call(
                id = null,
                tool = "noop",
                args = "",
            ),
            metaInfo = ResponseMetaInfo.Empty,
        )

        val litert = koog.toLitertMessage()

        val toolCall = litert.toolCalls.single()
        assertEquals("noop", toolCall.name)
        assertTrue(toolCall.arguments.isEmpty())
    }

    @Test
    fun testToolCallWithUnparseableContentThrowsException() {
        val koog = Message.Assistant(
            MessagePart.Tool.Call(
                id = null,
                tool = "broken",
                args = "<<not json>>",
            ),
            metaInfo = ResponseMetaInfo.Empty,
        )

        assertFailsWith<IllegalArgumentException> {
            koog.toLitertMessage()
        }
    }

    @Test
    fun testToolResultWithJsonObjectMapsToToolResponseContent() {
        val koog = Message.User(
            MessagePart.Tool.Result(
                id = "c1",
                tool = "search",
                output = """{"found":2,"items":["a","b"]}""",
            ),
            metaInfo = RequestMetaInfo.Empty,
        )

        val litert = koog.toLitertMessage()

        assertEquals(Role.TOOL, litert.role)
        assertTrue(litert.toolCalls.isEmpty())
        val toolResponse = litert.contents.contents.single() as? Content.ToolResponse
            ?: fail("Tool.Result must be wrapped in Content.ToolResponse, not Content.Text")
        assertEquals("search", toolResponse.name)
        val response = toolResponse.response as Map<*, *>
        assertEquals(2L, response["found"])
        assertEquals(listOf("a", "b"), response["items"])
    }

    @Test
    fun testToolResultWithNonJsonContentFallsBackToRawString() {
        val koog = Message.User(
            MessagePart.Tool.Result(
                id = null,
                tool = "echo",
                output = "plain text result",
            ),
            metaInfo = RequestMetaInfo.Empty,
        )

        val litert = koog.toLitertMessage()

        val toolResponse = litert.contents.contents.single() as Content.ToolResponse
        assertEquals("echo", toolResponse.name)
        assertEquals("plain text result", toolResponse.response)
    }

    @Test
    fun testToolResultWithBlankContentPreservesEmptyString() {
        val koog = Message.User(
            MessagePart.Tool.Result(
                id = null,
                tool = "noop",
                output = "",
            ),
            metaInfo = RequestMetaInfo.Empty,
        )

        val litert = koog.toLitertMessage()

        val toolResponse = litert.contents.contents.single() as Content.ToolResponse
        assertEquals("", toolResponse.response)
    }

    @Test
    fun testToolResultIsNeverSentAsUserText() {
        // Regression guard: previously Tool.Result was forwarded as plain user text.
        val koog = Message.User(
            MessagePart.Tool.Result(
                id = "c1",
                tool = "search",
                output = "{}",
            ),
            metaInfo = RequestMetaInfo.Empty,
        )

        val litert = koog.toLitertMessage()

        assertEquals(Role.TOOL, litert.role)
        val first = litert.contents.contents.single()
        assertTrue(
            first is Content.ToolResponse,
            "Tool.Result must produce Content.ToolResponse, got ${first::class.simpleName}",
        )
    }

    @Test
    fun testMixedTextAndToolResultProducesToolRoleWithBothContents() {
        val koog = Message.User(
            listOf(
                MessagePart.Tool.Result(id = "c1", tool = "search", output = """{"found":1}"""),
                MessagePart.Text("some context text"),
            ),
            metaInfo = RequestMetaInfo.Empty,
        )

        val litert = koog.toLitertMessage()

        assertEquals(Role.TOOL, litert.role)
        assertEquals(2, litert.contents.contents.size)
        assertTrue(litert.contents.contents[0] is Content.ToolResponse)
        assertTrue(litert.contents.contents[1] is Content.Text)
        assertEquals("some context text", (litert.contents.contents[1] as Content.Text).text)
    }

    @Test
    fun testToolCallAndToolResultProduceDifferentRoles() {
        // Regression guard for the converter previously collapsing both via role == Tool.
        val call = Message.Assistant(
            MessagePart.Tool.Call(
                id = "c1",
                tool = "search",
                args = "{}",
            ),
            metaInfo = ResponseMetaInfo.Empty,
        ).toLitertMessage()
        val result = Message.User(
            MessagePart.Tool.Result(
                id = "c1",
                tool = "search",
                output = "{}",
            ),
            metaInfo = RequestMetaInfo.Empty,
        ).toLitertMessage()

        assertEquals(Role.MODEL, call.role)
        assertEquals(Role.TOOL, result.role)
    }

    @Test
    fun multiToolCallsMustHaveStableIdsOrFail() {
        val litert = LitertMessage.model(
            contents = Contents.of(emptyList()),
            toolCalls = listOf(
                ToolCall("weather", mapOf("city" to "London")),
                ToolCall("weather", mapOf("city" to "Paris")),
            ),
        )

        val assistantMessage = litert.toKoogMessage(KoogClock.System)
        val toolCalls = assistantMessage
            .parts
            .filterIsInstance<MessagePart.Tool.Call>()

        assertEquals(2, toolCalls.size)
    }

    @Test
    fun testLitertToolCallArgumentsSerializeToValidJsonObject() {
        // Regression: previously Map<String, JsonElement>.toString() emitted Kotlin map syntax
        // like "{query=\"weather\", limit=3}" which is NOT valid JSON, breaking downstream
        // tool-argument parsing on the Koog side.
        val litert = LitertMessage.model(
            contents = Contents.of(emptyList()),
            toolCalls = listOf(ToolCall("search", mapOf("query" to "weather", "limit" to 3))),
        )

        val toolCall = litert.toKoogMessage(KoogClock.System)
            .parts
            .filterIsInstance<MessagePart.Tool.Call>()
            .single()
        // Must be parseable as a JSON object.
        val parsed = kotlinx.serialization.json.Json.parseToJsonElement(toolCall.args)
        assertTrue(parsed is kotlinx.serialization.json.JsonObject)
        assertEquals("weather", (parsed["query"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals("3", (parsed["limit"] as kotlinx.serialization.json.JsonPrimitive).content)
    }

    @Test
    fun testJsonNullInArgumentsBecomesNull() {
        val koog = Message.Assistant(
            MessagePart.Tool.Call(
                id = null,
                tool = "t",
                args = """{"x":null}""",
            ),
            metaInfo = ResponseMetaInfo.Empty,
        )

        val toolCall = koog.toLitertMessage().toolCalls.single()
        assertTrue(toolCall.arguments.containsKey("x"))
        assertNull(toolCall.arguments["x"])
    }
}
