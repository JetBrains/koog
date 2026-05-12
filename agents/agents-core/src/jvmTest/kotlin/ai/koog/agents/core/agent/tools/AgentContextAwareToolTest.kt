@file:OptIn(InternalAgentToolsApi::class, InternalAgentsApi::class)

package ai.koog.agents.core.agent.tools

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AgentTestBase
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.ToolCallMetadata
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.serialization.typeToken
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AgentContextAwareToolTest : AgentTestBase() {

    @Serializable
    private data class EchoArgs(val value: String)

    private class CapturingContextTool : AgentContextAwareTool<EchoArgs, String>(
        argsType = typeToken<EchoArgs>(),
        resultType = typeToken<String>(),
        name = "capturing_context_tool",
        description = "Tool that captures the AIAgentContext it was invoked with.",
    ) {
        val observed: MutableList<AIAgentContext> = mutableListOf()

        override suspend fun execute(args: EchoArgs, context: AIAgentContext): String {
            observed += context
            return "echo:${args.value}"
        }
    }

    @Test
    fun testReceivesContextInjectedUnderReservedKey() = runTest {
        val tool = CapturingContextTool()
        val context = createTestContext()

        val result = tool.execute(
            EchoArgs("hello"),
            ToolCallMetadata.of(AgentContextAwareTool.AgentContextKey to context),
        )

        assertEquals("echo:hello", result)
        assertSame(context, tool.observed.single())
    }

    @Test
    fun testEmptyMetadataRaisesIllegalStateWithToolName() = runTest {
        val tool = CapturingContextTool()

        val error = assertFailsWith<IllegalStateException> {
            tool.execute(EchoArgs("hello"), ToolCallMetadata.EMPTY)
        }

        val message = error.message.orEmpty()
        assertTrue(
            "capturing_context_tool" in message,
            "Message must name the tool. Was: $message",
        )
        assertTrue(
            "ContextualAgentEnvironment" in message,
            "Message must point to ContextualAgentEnvironment. Was: $message",
        )
    }

    @Test
    fun testMetadataWithoutReservedKeyRaisesIllegalState() = runTest {
        val tool = CapturingContextTool()

        assertFailsWith<IllegalStateException> {
            tool.execute(EchoArgs("hello"), ToolCallMetadata.of("unrelated" to "value"))
        }
    }

    @Test
    fun testExecuteUnsafeWithoutMetadataRaisesIllegalState() = runTest {
        val tool = CapturingContextTool()

        assertFailsWith<IllegalStateException> {
            tool.executeUnsafe(EchoArgs("hello"))
        }
    }

    @Test
    fun testExecuteUnsafeWithEmptyMetadataRaisesIllegalState() = runTest {
        val tool = CapturingContextTool()

        assertFailsWith<IllegalStateException> {
            tool.executeUnsafe(EchoArgs("hello"), ToolCallMetadata.EMPTY)
        }
    }

    @Test
    fun testExecuteUnsafeWithReservedKeyRoutesToTypedOverload() = runTest {
        val tool = CapturingContextTool()
        val context = createTestContext()

        val result = tool.executeUnsafe(
            EchoArgs("unsafe"),
            ToolCallMetadata.of(AgentContextAwareTool.AgentContextKey to context),
        )

        assertEquals("echo:unsafe", result)
        assertSame(context, tool.observed.single())
    }

    @Test
    fun testAgentContextKeyMatchesPublishedValue() {
        // Pin the key string so an accidental rename surfaces here rather than silently breaking
        // feature integrations that read or merge entries under this namespace.
        assertEquals("ai.koog.agents.core.agentContext", AgentContextAwareTool.AgentContextKey)
    }
}
