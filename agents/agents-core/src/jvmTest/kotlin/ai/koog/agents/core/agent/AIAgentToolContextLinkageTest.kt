package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.AIAgentTool.AgentToolInput
import ai.koog.agents.core.agent.AIAgentTool.AgentToolResult
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.typeToken
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AIAgentToolContextLinkageTest {
    @Serializable
    data class LinkInput(val text: String)

    private fun mockAgent(): GraphAIAgent<LinkInput, LinkInput> = GraphAIAgent(
        id = "child_template",
        strategy = strategy("link") {
            edge(nodeStart forwardTo nodeFinish transformed { LinkInput("ok:${it.text}") })
        },
        promptExecutor = getMockExecutor(KotlinxSerializer()) { },
        agentConfig = AIAgentConfig(
            prompt = prompt("link-prompt") { system("test") },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 5,
        ),
        inputType = typeToken<LinkInput>(),
        outputType = typeToken<LinkInput>(),
    )

    @Suppress("UNCHECKED_CAST")
    private fun toolFor(
        service: AIAgentService<LinkInput, LinkInput, *>,
        parentAgentId: String?,
    ): AIAgentTool<LinkInput, LinkInput> {
        @OptIn(InternalAgentToolsApi::class)
        val tool: Tool<AgentToolInput<LinkInput>, AgentToolResult<LinkInput>> = service.createAgentTool(
            agentName = "linkAgent",
            agentDescription = "linkAgent",
            inputDescription = null,
            parentAgentId = parentAgentId,
        )
        return tool as AIAgentTool<LinkInput, LinkInput>
    }

    @Test
    fun testContextAwareExecuteUsesContextAgentIdAsParent() = runTest {
        val service = AIAgentService.fromAgent(mockAgent())
        val tool = toolFor(service, parentAgentId = "constructor-time-parent")

        val ctx = StubAIAgentContext(agentId = "runtime-parent", runId = "run-1")
        val args = AgentToolInput(LinkInput("payload"))

        val result = tool.execute(args, ctx)

        assertTrue(result.successful)
        assertEquals(LinkInput("ok:payload"), result.result)

        val expectedChildId = "runtime-parent.0"
        assertTrue(
            service.agentById(expectedChildId) != null,
            "Expected a managed sub-agent with id '$expectedChildId' but found none.",
        )
    }

    @Test
    fun testPlainExecuteFallsBackToConstructorParentAgentId() = runTest {
        val service = AIAgentService.fromAgent(mockAgent())
        val tool = toolFor(service, parentAgentId = "constructor-time-parent")

        val args = AgentToolInput(LinkInput("payload"))
        val result = tool.execute(args)

        assertTrue(result.successful)
        val expectedChildId = "constructor-time-parent.0"
        assertTrue(
            service.agentById(expectedChildId) != null,
            "Expected a managed sub-agent with id '$expectedChildId' but found none.",
        )
    }
}
