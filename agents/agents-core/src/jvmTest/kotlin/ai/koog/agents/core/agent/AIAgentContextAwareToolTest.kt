package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.typeToken
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AIAgentContextAwareToolTest {
    private val serializer = KotlinxSerializer()

    @Serializable
    data class EchoArgs(val message: String)

    private val capturedContexts: MutableList<AIAgentContext> = mutableListOf()
    private val plainToolInvocations: MutableList<String> = mutableListOf()

    @AfterTest
    fun tearDown() {
        capturedContexts.clear()
        plainToolInvocations.clear()
    }

    /**
     * A tool that opts into receiving the [AIAgentContext]. Captures the context for assertions
     * and also writes a value into [AIAgentContext.storage] so we can read it back via the context.
     */
    inner class ContextEchoTool : SimpleContextAwareTool<EchoArgs>(
        argsType = typeToken<EchoArgs>(),
        name = "context_echo",
        description = "Echoes the message and records the calling context.",
    ) {
        override suspend fun execute(args: EchoArgs, context: AIAgentContext): String {
            capturedContexts += context
            context.storage.set(echoKey, args.message)
            return "echo:${args.message}@${context.agentId}"
        }
    }

    /**
     * A plain tool with no context support — must continue to work alongside context-aware tools.
     */
    inner class PlainEchoTool : SimpleTool<EchoArgs>(
        argsType = typeToken<EchoArgs>(),
        name = "plain_echo",
        description = "Echoes the message without context.",
    ) {
        override suspend fun execute(args: EchoArgs): String {
            plainToolInvocations += args.message
            return "plain:${args.message}"
        }
    }

    private val agentConfig = AIAgentConfig(
        prompt = prompt("test") { system("You are a test agent.") },
        model = OpenAIModels.Chat.GPT4o,
        maxAgentIterations = 10,
    )

    @Test
    fun testContextAwareToolReceivesAgentContext() = runTest {
        val contextEcho = ContextEchoTool()
        val toolRegistry = ToolRegistry { tool(contextEcho) }

        val executor = getMockExecutor(serializer) {
            mockLLMToolCall(contextEcho, EchoArgs("hello")) onRequestEquals "go"
            mockLLMAnswer("done").asDefaultResponse
        }

        val agent = AIAgent(
            promptExecutor = executor,
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
            id = "parent-agent",
        )

        agent.run("go")

        assertEquals(1, capturedContexts.size, "Context-aware overload must be invoked exactly once")
        val captured = capturedContexts.single()
        assertEquals("parent-agent", captured.agentId, "Tool must see the parent agent's id via context")
        assertNotNull(captured.runId)
        assertEquals("hello", captured.storage.get(echoKey), "Tool wrote into context.storage; we must read it back")
    }

    @Test
    fun testPlainToolStillWorksAlongsideContextAwareTool() = runTest {
        val plainEcho = PlainEchoTool()
        val contextEcho = ContextEchoTool()
        val toolRegistry = ToolRegistry {
            tool(plainEcho)
            tool(contextEcho)
        }

        val executor = getMockExecutor(serializer) {
            mockLLMToolCall(plainEcho, EchoArgs("first")) onRequestEquals "go"
            mockLLMAnswer("done").asDefaultResponse
        }

        val agent = AIAgent(
            promptExecutor = executor,
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
            id = "parent-agent-2",
        )

        agent.run("go")

        assertEquals(listOf("first"), plainToolInvocations)
        assertTrue(capturedContexts.isEmpty(), "Plain tool path must not flow through the context-aware overload")
    }

    private companion object {
        val echoKey = AIAgentStorageKey<String>("test-echo")
    }
}
