package ai.koog.agents.core.environment

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolException
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.AttachmentSource
import ai.koog.prompt.message.MessagePart
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.typeToken
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GenericAgentEnvironmentTest {
    private val serializer = KotlinxSerializer()

    @Serializable
    private data class RequiredArgs(val required: String)

    private class RequiredArgsTool : SimpleTool<RequiredArgs>(
        argsType = typeToken<RequiredArgs>(),
        name = "required_args",
        description = "Tool that requires a single argument.",
    ) {
        override suspend fun execute(args: RequiredArgs): String = "Ok"
    }

    private class ValidationTool : SimpleTool<RequiredArgs>(
        argsType = typeToken<RequiredArgs>(),
        name = "validation_tool",
        description = "Tool that fails with validation error.",
    ) {
        override suspend fun execute(args: RequiredArgs): String {
            throw ToolException.ValidationFailure("Invalid arguments")
        }
    }

    private class FailingTool : SimpleTool<RequiredArgs>(
        argsType = typeToken<RequiredArgs>(),
        name = "failing_tool",
        description = "Tool that fails with runtime exception.",
    ) {
        override suspend fun execute(args: RequiredArgs): String {
            error("boom")
        }
    }

    private class SuccessTool : SimpleTool<RequiredArgs>(
        argsType = typeToken<RequiredArgs>(),
        name = "success_tool",
        description = "Tool that succeeds.",
    ) {
        override suspend fun execute(args: RequiredArgs): String = "ok:${args.required}"
    }

    private class CancellableTool : SimpleTool<RequiredArgs>(
        argsType = typeToken<RequiredArgs>(),
        name = "cancellable_tool",
        description = "Tool that throws cancellation.",
    ) {
        override suspend fun execute(args: RequiredArgs): String {
            throw CancellationException("cancelled")
        }
    }

    @Test
    fun testInvalidJsonArgsReturnsFailure() = runTest {
        val environment = GenericAgentEnvironment(
            agentId = "test_agent",
            logger = KotlinLogging.logger { },
            toolRegistry = ToolRegistry { tool(RequiredArgsTool()) },
            serializer = serializer,
        )

        val toolCall = MessagePart.Tool.Call(
            id = "1",
            tool = "required_args",
            args = "not-json",
        )

        val result = environment.executeTool(toolCall)
        assertEquals("required_args", result.tool)
        assertTrue(result.resultKind is ToolResultKind.Failure)
    }

    @Test
    fun testMissingFieldReturnsFailure() = runTest {
        val environment = GenericAgentEnvironment(
            agentId = "test_agent",
            logger = KotlinLogging.logger { },
            toolRegistry = ToolRegistry { tool(RequiredArgsTool()) },
            serializer = serializer,
        )

        val toolCall = MessagePart.Tool.Call(
            id = "1",
            tool = "required_args",
            args = "{}",
        )

        val result = environment.executeTool(toolCall)
        assertEquals("required_args", result.tool)
        assertTrue(result.resultKind is ToolResultKind.Failure)
    }

    @Test
    fun testUnknownToolReturnsFailure() = runTest {
        val environment = GenericAgentEnvironment(
            agentId = "test_agent",
            logger = KotlinLogging.logger { },
            toolRegistry = ToolRegistry {},
            serializer = serializer,
        )

        val result = environment.executeTool(
            MessagePart.Tool.Call(
                id = "1",
                tool = "missing_tool",
                args = """{"required":"value"}""",
            )
        )

        assertEquals("missing_tool", result.tool)
        assertTrue(result.resultKind is ToolResultKind.Failure)
        assertTrue(result.output.contains("not found in the tool registry"))
    }

    @Test
    fun testToolExceptionReturnsValidationError() = runTest {
        val environment = GenericAgentEnvironment(
            agentId = "test_agent",
            logger = KotlinLogging.logger { },
            toolRegistry = ToolRegistry { tool(ValidationTool()) },
            serializer = serializer,
        )

        val result = environment.executeTool(
            MessagePart.Tool.Call(
                id = "1",
                tool = "validation_tool",
                args = """{"required":"value"}""",
            )
        )

        assertTrue(result.resultKind is ToolResultKind.ValidationError)
        assertEquals("Invalid arguments", result.output)
    }

    @Test
    fun testRuntimeFailureReturnsFailure() = runTest {
        val environment = GenericAgentEnvironment(
            agentId = "test_agent",
            logger = KotlinLogging.logger { },
            toolRegistry = ToolRegistry { tool(FailingTool()) },
            serializer = serializer,
        )

        val result = environment.executeTool(
            MessagePart.Tool.Call(
                id = "1",
                tool = "failing_tool",
                args = """{"required":"value"}""",
            )
        )

        assertTrue(result.resultKind is ToolResultKind.Failure)
        assertTrue(result.output.contains("failed to execute"))
    }

    @Test
    fun testSuccessfulExecutionReturnsSuccess() = runTest {
        val environment = GenericAgentEnvironment(
            agentId = "test_agent",
            logger = KotlinLogging.logger { },
            toolRegistry = ToolRegistry { tool(SuccessTool()) },
            serializer = serializer,
        )

        val result = environment.executeTool(
            MessagePart.Tool.Call(
                id = "1",
                tool = "success_tool",
                args = """{"required":"value"}""",
            )
        )

        assertEquals(ToolResultKind.Success, result.resultKind)
        assertEquals("ok:value", result.output)
    }

    private class ImageTool : SimpleTool<RequiredArgs>(
        argsType = typeToken<RequiredArgs>(),
        name = "image_tool",
        description = "Tool that returns an image alongside its text result.",
    ) {
        val fakeImageBytes = byteArrayOf(1, 2, 3, 4)

        override suspend fun execute(args: RequiredArgs): String = "image data"

        override fun encodeResultToParts(result: String, serializer: JSONSerializer): List<MessagePart.ContentPart> =
            listOf(MessagePart.Attachment(AttachmentSource.Image(AttachmentContent.Binary.Bytes(fakeImageBytes), format = "png")))
    }

    @Test
    fun testMultimodalToolPartsFlowThroughToMessage() = runTest {
        val tool = ImageTool()
        val environment = GenericAgentEnvironment(
            agentId = "test_agent",
            logger = KotlinLogging.logger { },
            toolRegistry = ToolRegistry { tool(tool) },
            serializer = serializer,
        )

        val result = environment.executeTool(
            MessagePart.Tool.Call(
                id = "1",
                tool = "image_tool",
                args = """{"required":"value"}""",
            )
        )

        assertEquals(ToolResultKind.Success, result.resultKind)
        val parts = assertNotNull(result.parts)
        assertEquals(1, parts.size)
        val attachmentPart = parts.single() as MessagePart.Attachment
        val imageSource = attachmentPart.source as AttachmentSource.Image
        assertEquals("png", imageSource.format)
        assertTrue((imageSource.content as AttachmentContent.Binary.Bytes).data.contentEquals(tool.fakeImageBytes))

        val messagePart = result.toMessagePart()
        assertEquals(1, messagePart.parts.size)
        assertTrue((messagePart.parts.single() as MessagePart.Attachment).source is AttachmentSource.Image)
    }

    @Test
    fun testNonMultimodalToolDefaultsToTextPart() = runTest {
        val environment = GenericAgentEnvironment(
            agentId = "test_agent",
            logger = KotlinLogging.logger { },
            toolRegistry = ToolRegistry { tool(SuccessTool()) },
            serializer = serializer,
        )

        val result = environment.executeTool(
            MessagePart.Tool.Call(
                id = "1",
                tool = "success_tool",
                args = """{"required":"value"}""",
            )
        )

        assertEquals(ToolResultKind.Success, result.resultKind)
        val textPart = result.parts?.single() as? MessagePart.Text
        assertEquals("ok:value", textPart?.text)

        val messagePart = result.toMessagePart()
        assertEquals("ok:value", (messagePart.parts.single() as MessagePart.Text).text)
    }

    @Test
    fun testCancellationIsRethrown() = runTest {
        val environment = GenericAgentEnvironment(
            agentId = "test_agent",
            logger = KotlinLogging.logger { },
            toolRegistry = ToolRegistry { tool(CancellableTool()) },
            serializer = serializer,
        )

        assertFailsWith<CancellationException> {
            environment.executeTool(
                MessagePart.Tool.Call(
                    id = "1",
                    tool = "cancellable_tool",
                    args = """{"required":"value"}""",
                )
            )
        }
    }
}
