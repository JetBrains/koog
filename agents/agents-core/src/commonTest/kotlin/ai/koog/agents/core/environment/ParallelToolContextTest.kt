@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.environment

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AgentTestBase
import ai.koog.agents.core.agent.context.with
import ai.koog.agents.core.agent.tools.agentContext
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.ToolCallMetadata
import ai.koog.prompt.message.MessagePart
import ai.koog.serialization.JSONPrimitive
import ai.koog.serialization.kotlinx.toKoogJSONObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ParallelToolContextTest : AgentTestBase() {
    @Test
    fun testParallelCallsIsolateContextsAndPreserveMetadata() = runTest {
        val contexts = mutableListOf<AIAgentContext>()
        val paths = mutableListOf<String>()
        val metadataValues = mutableListOf<Any?>()
        val nestedContexts = mutableListOf<AIAgentContext>()
        val environment = object : AIAgentEnvironment {
            override suspend fun executeTool(toolCall: MessagePart.Tool.Call): ReceivedToolResult =
                error("Metadata overload must be used")

            override suspend fun executeTool(
                toolCall: MessagePart.Tool.Call,
                metadata: ToolCallMetadata,
            ): ReceivedToolResult {
                val context = checkNotNull(metadata.agentContext)
                if (toolCall.tool == "nested") {
                    nestedContexts += context
                } else {
                    contexts += context
                    paths += context.executionInfo.path()
                    metadataValues += metadata["caller"]
                    context.with(toolCall.id!!) { _, _ ->
                        context.llm.writeSession { appendPrompt { user(toolCall.id!!) } }
                        delay(if (toolCall.id == "first") 100L else 200L)
                        assertEquals(toolCall.id, context.llm.prompt.messages.last().textContent())
                        context.environment.executeTool(call("nested", "nested"))
                    }
                }
                return ReceivedToolResult(
                    id = toolCall.id,
                    tool = toolCall.tool,
                    toolArgs = toolCall.argsJson.toKoogJSONObject(),
                    toolDescription = null,
                    output = "ok",
                    resultKind = ToolResultKind.Success,
                    result = JSONPrimitive("ok"),
                )
            }

            override suspend fun reportProblem(exception: Throwable) = throw exception
        }
        val parent = createTestContext(environment = environment)
        val originalPrompt = parent.llm.prompt
        val originalPath = parent.executionInfo.path()
        val wrapper = ContextualAgentEnvironment(environment, parent)

        val results = wrapper.executeTools(
            listOf(call("first"), call("second")),
            ToolCallMetadata.of("caller" to "value"),
        )

        assertEquals(listOf("first", "second"), results.map { it.id })
        assertEquals(2, paths.distinct().size)
        assertTrue(paths.all { it.substringBeforeLast('/') == originalPath })
        assertEquals(listOf<Any?>("value", "value"), metadataValues)
        assertNotSame(contexts[0], contexts[1])
        contexts.forEach { child ->
            assertNotSame(parent, child)
            assertNotSame(parent.llm, child.llm)
            assertSame(parent.storage, child.storage)
            assertSame(parent.stateManager, child.stateManager)
            assertSame(parent, child.parentContext)
        }
        assertSame(contexts[0], nestedContexts[0])
        assertSame(contexts[1], nestedContexts[1])
        assertEquals(originalPath, parent.executionInfo.path())
        assertEquals(originalPrompt, parent.llm.prompt)
    }

    @Test
    fun testEmptyParallelBatchReturnsNoResults() = runTest {
        val parent = createTestContext()
        val wrapper = ContextualAgentEnvironment(parent.environment, parent)

        assertEquals(emptyList(), wrapper.executeTools(emptyList()))
    }

    @Test
    fun testNonGraphParallelBatchRetainsDefaultBehavior() = runTest {
        val graphContext = createTestContext()
        val context = object : AIAgentContext by graphContext {}
        val wrapper = ContextualAgentEnvironment(context.environment, context)

        val results = wrapper.executeTools(listOf(call("first"), call("second")))

        assertEquals(2, results.size)
        assertEquals(listOf("Test tool result", "Test tool result"), results.map { it.output })
    }

    private fun call(id: String, tool: String = "test-tool") = MessagePart.Tool.Call(id, tool, "{}")
}
