package ai.koog.agents.core.environment

import ai.koog.agents.core.CalculatorChatExecutor.testClock
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolCallMetadata
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.prompt.message.Message
import ai.koog.serialization.JSONPrimitive
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.kotlinx.toKoogJSONObject
import ai.koog.serialization.typeToken
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SafeToolMetadataTest {
    private val serializer = KotlinxSerializer()

    @Serializable
    private data class EchoArgs(val value: String)

    private object EchoTool : SimpleTool<EchoArgs>(
        argsType = typeToken<EchoArgs>(),
        name = "echo",
        description = "Echo tool used by the metadata tests"
    ) {
        override suspend fun execute(args: EchoArgs): String = args.value
    }

    @OptIn(InternalAgentToolsApi::class)
    private class CapturingEnvironment : AIAgentEnvironment {
        var lastMetadata: ToolCallMetadata? = null
            private set

        override suspend fun executeTool(toolCall: Message.Tool.Call): ReceivedToolResult {
            lastMetadata = ToolCallMetadata.EMPTY
            return buildResult(toolCall)
        }

        override suspend fun executeTool(
            toolCall: Message.Tool.Call,
            metadata: ToolCallMetadata,
        ): ReceivedToolResult {
            lastMetadata = metadata
            return buildResult(toolCall)
        }

        override suspend fun reportProblem(exception: Throwable) {
            throw exception
        }

        private fun buildResult(toolCall: Message.Tool.Call): ReceivedToolResult = ReceivedToolResult(
            id = toolCall.id,
            tool = toolCall.tool,
            toolArgs = toolCall.contentJson.toKoogJSONObject(),
            toolDescription = null,
            content = "Ok",
            resultKind = ToolResultKind.Success,
            result = JSONPrimitive("Ok"),
        )
    }

    @Test
    fun testExecuteWithMetadataForwardsToEnvironment() = runTest {
        val environment = CapturingEnvironment()
        val safeTool = SafeTool(EchoTool, environment, testClock)
        val metadata = ToolCallMetadata.of("trace.span.id" to "span-1")

        val result = safeTool.execute(EchoArgs("hello"), serializer, metadata)

        assertTrue(result.isSuccessful())
        assertEquals(metadata, environment.lastMetadata)
    }

    @Test
    fun testExecuteWithoutMetadataUsesEmpty() = runTest {
        val environment = CapturingEnvironment()
        val safeTool = SafeTool(EchoTool, environment, testClock)

        val result = safeTool.execute(EchoArgs("hello"), serializer)

        assertTrue(result.isSuccessful())
        assertSame(ToolCallMetadata.EMPTY, environment.lastMetadata)
    }

    @Test
    fun testExecuteUnsafeWithMetadataForwardsToEnvironment() = runTest {
        val environment = CapturingEnvironment()
        val safeTool = SafeTool(EchoTool, environment, testClock)
        val metadata = ToolCallMetadata.of("trace.span.id" to "span-7")

        val result = safeTool.executeUnsafe(EchoArgs("unsafe"), serializer, metadata)

        assertTrue(result.isSuccessful())
        assertEquals(metadata, environment.lastMetadata)
    }

    @Test
    fun testEnvironmentThatOnlyOverridesLegacyStillWorks() = runTest {
        // Ensures SafeTool remains source-compatible with environments that haven't adopted the
        // metadata overload. The default interface method drops metadata silently and routes through
        // the legacy single-arg path.
        @OptIn(InternalAgentToolsApi::class)
        val legacy = object : AIAgentEnvironment {
            var legacyInvocations: Int = 0
                private set

            override suspend fun executeTool(toolCall: Message.Tool.Call): ReceivedToolResult {
                legacyInvocations++
                return ReceivedToolResult(
                    id = toolCall.id,
                    tool = toolCall.tool,
                    toolArgs = toolCall.contentJson.toKoogJSONObject(),
                    toolDescription = null,
                    content = "legacy-ok",
                    resultKind = ToolResultKind.Success,
                    result = JSONPrimitive("legacy-ok"),
                )
            }

            override suspend fun reportProblem(exception: Throwable) {
                throw exception
            }
        }
        val safeTool = SafeTool(EchoTool, legacy, testClock)

        val result = safeTool.execute(EchoArgs("v"), serializer, ToolCallMetadata.of("x" to "y"))

        assertTrue(result.isSuccessful())
        assertEquals("legacy-ok", result.content)
        assertEquals(1, legacy.legacyInvocations, "Default interface method must route through legacy executeTool")
    }
}
