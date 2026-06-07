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
import kotlin.test.assertFalse
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

    private class SecretLeakingTool : SimpleTool<RequiredArgs>(
        argsType = typeToken<RequiredArgs>(),
        name = "secret_leaking_tool",
        description = "Tool that throws an exception carrying sensitive text in its message.",
    ) {
        override suspend fun execute(args: RequiredArgs): String {
            throw IllegalStateException("secret=hunter2")
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
    fun testDefaultPresenterForwardsRawExceptionMessage() = runTest {
        val environment = GenericAgentEnvironment(
            agentId = "test_agent",
            logger = KotlinLogging.logger { },
            toolRegistry = ToolRegistry { tool(SecretLeakingTool()) },
            serializer = serializer,
        )

        val result = environment.executeTool(
            MessagePart.Tool.Call(
                id = "1",
                tool = "secret_leaking_tool",
                args = """{"required":"value"}""",
            )
        )

        // Backward-compatible default: the raw exception message is re-injected verbatim.
        assertTrue(result.resultKind is ToolResultKind.Failure)
        assertTrue(result.output.contains("secret=hunter2"))
    }

    @Test
    fun testCustomPresenterReplacesRawExceptionMessage() = runTest {
        val environment = GenericAgentEnvironment(
            agentId = "test_agent",
            logger = KotlinLogging.logger { },
            toolRegistry = ToolRegistry { tool(SecretLeakingTool()) },
            serializer = serializer,
            toolFailurePresenter = ToolFailurePresenter { failure ->
                "The tool '${failure.toolName}' failed at stage ${failure.stage}."
            },
        )

        val result = environment.executeTool(
            MessagePart.Tool.Call(
                id = "1",
                tool = "secret_leaking_tool",
                args = """{"required":"value"}""",
            )
        )

        val failure = result.resultKind
        assertTrue(failure is ToolResultKind.Failure)

        // The model only sees the sanitized text...
        assertEquals(
            "The tool 'secret_leaking_tool' failed at stage ${ToolFailureStage.Execution}.",
            result.output,
        )
        assertFalse(result.output.contains("secret=hunter2"))

        // ...while the host still observes the original throwable through the result kind (and, in a full
        // agent run, through the event-handler feature's onToolCallFailed event).
        assertEquals("secret=hunter2", failure.error?.message)
    }

    @Test
    fun testCustomPresenterCanReuseDefaultMessage() = runTest {
        val environment = GenericAgentEnvironment(
            agentId = "test_agent",
            logger = KotlinLogging.logger { },
            toolRegistry = ToolRegistry { tool(SecretLeakingTool()) },
            serializer = serializer,
            toolFailurePresenter = ToolFailurePresenter { it.defaultMessage },
        )

        val result = environment.executeTool(
            MessagePart.Tool.Call(
                id = "1",
                tool = "secret_leaking_tool",
                args = """{"required":"value"}""",
            )
        )

        assertTrue(result.resultKind is ToolResultKind.Failure)
        assertEquals(
            "Tool with name 'secret_leaking_tool' failed to execute due to the error: secret=hunter2!",
            result.output,
        )
    }

    @Test
    fun testCustomPresenterIsNotAppliedToToolValidationErrors() = runTest {
        val environment = GenericAgentEnvironment(
            agentId = "test_agent",
            logger = KotlinLogging.logger { },
            toolRegistry = ToolRegistry { tool(ValidationTool()) },
            serializer = serializer,
            toolFailurePresenter = ToolFailurePresenter { "redacted" },
        )

        val result = environment.executeTool(
            MessagePart.Tool.Call(
                id = "1",
                tool = "validation_tool",
                args = """{"required":"value"}""",
            )
        )

        // ToolException messages are author-controlled validation guidance and bypass the presenter.
        assertTrue(result.resultKind is ToolResultKind.ValidationError)
        assertEquals("Invalid arguments", result.output)
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
