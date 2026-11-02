package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.agent.tools.AgentContextAwareTool
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.builder.subgraph
import ai.koog.agents.core.dsl.extension.ToolCalls
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResults
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.mock.MockSpanExporter
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.message.MessagePart
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.typeToken
import ai.koog.utils.io.use
import io.opentelemetry.kotlin.tracing.export.simpleSpanProcessor
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenTelemetryParallelToolSubgraphTest {
    @Test
    fun testParallelToolSubgraphsCompleteWithoutOpenTelemetry() = runTest {
        runParallelToolSubgraphs(withOpenTelemetry = false)
    }

    // Regression for https://github.com/JetBrains/koog/issues/2236.
    @Test
    fun testParallelToolSubgraphsHaveSiblingSpans() = runTest {
        runParallelToolSubgraphs(withOpenTelemetry = true)
    }

    @Test
    fun testModelCallsInsideParallelToolsUseTheirOwnNodeSpans() = runTest {
        runParallelToolSubgraphs(withOpenTelemetry = true, callModelInSubgraph = true)
    }

    private suspend fun runParallelToolSubgraphs(
        withOpenTelemetry: Boolean,
        callModelInSubgraph: Boolean = false,
    ) {
        val completedTopics = mutableListOf<String>()
        val tool = ResearchTool(completedTopics, callModelInSubgraph)
        val userPrompt = "Research first and second"
        val serializer = KotlinxSerializer()
        val executor = getMockExecutor(serializer) {
            mockLLMAnswer("Done").asDefaultResponse
        }
        val strategy = strategy<String, String>("research") {
            // Supply the two calls directly: model behavior is not under test here.
            val requestTools by node<String, ToolCalls> {
                ToolCalls(
                    listOf("first", "second").map { topic ->
                        MessagePart.Tool.Call(
                            id = topic,
                            tool = tool.name,
                            args = tool.encodeArgsToString(ResearchArgs(topic), serializer),
                        )
                    }
                )
            }
            val executeTools by nodeExecuteTools(parallel = true)
            val sendToolResults by nodeLLMSendToolResults()

            edge(nodeStart forwardTo requestTools)
            edge(requestTools forwardTo executeTools)
            edge(executeTools forwardTo sendToolResults)
            edge(sendToolResults forwardTo nodeFinish onTextMessage { true })
        }

        MockSpanExporter().use { exporter ->
            executor.use {
                val agent = AIAgent(
                    promptExecutor = executor,
                    strategy = strategy,
                    agentConfig = AIAgentConfig(
                        prompt = prompt("parallel-tool-subgraphs") { system("Research the requested topics.") },
                        model = OpenAIModels.Chat.GPT4o,
                        maxAgentIterations = 20,
                    ),
                    toolRegistry = ToolRegistry { tool(tool) },
                ) {
                    if (withOpenTelemetry) {
                        install(OpenTelemetry) {
                            addSpanProcessor { simpleSpanProcessor(exporter) }
                        }
                    }
                }

                agent.use {
                    assertEquals("Done", it.run(userPrompt))
                }
            }

            // A canned final answer alone would not prove that both tools succeeded.
            assertEquals(listOf("first", "second"), completedTopics)

            if (withOpenTelemetry) {
                val spans = exporter.collectedSpans
                val executeToolsSpan = spans.single { it.name == "node executeTools" }
                val toolSpans = spans.filter { it.name == "execute_tool research_topic" }
                val subgraphSpans = spans.filter { it.name == "subgraph research-subgraph" }

                assertEquals(2, toolSpans.size)
                assertEquals(2, subgraphSpans.size)
                for (span in toolSpans) {
                    assertEquals(
                        executeToolsSpan.spanContext.spanId,
                        span.parent.spanId,
                        "${span.name} should belong to executeTools, not another tool's subgraph",
                    )
                    assertEquals(
                        1,
                        subgraphSpans.count { it.parent.spanId == span.spanContext.spanId },
                        "Each tool call should own exactly one research subgraph",
                    )
                }
                if (callModelInSubgraph) {
                    val answerSpans = spans.filter { it.name == "node answer" }
                    val inferenceSpans = spans.filter { it.name.startsWith("chat ") }
                    assertEquals(2, answerSpans.size)
                    assertEquals(3, inferenceSpans.size)
                    answerSpans.forEach { answerSpan ->
                        assertEquals(1, inferenceSpans.count { it.parent.spanId == answerSpan.spanContext.spanId })
                    }
                }
            }
        }
    }

    @Serializable
    private data class ResearchArgs(val topic: String)

    private class ResearchTool(
        private val completedTopics: MutableList<String>,
        private val callModelInSubgraph: Boolean,
    ) :
        AgentContextAwareTool<ResearchArgs, String>(
            argsType = typeToken<ResearchArgs>(),
            resultType = typeToken<String>(),
            name = "research_topic",
            description = "Research a topic in a subgraph",
        ) {
        private val research by subgraph<ResearchArgs, String>(
            name = "research-subgraph",
            toolSelectionStrategy = ToolSelectionStrategy.NONE,
            freshHistory = true,
        ) {
            val answer by node<ResearchArgs, String> { args ->
                // runTest uses virtual time: both calls enter this node before either finishes.
                // The first finishes while the second is still inside its subgraph.
                delay(if (args.topic == "first") 100L else 200L)
                if (callModelInSubgraph) {
                    llm.writeSession {
                        appendPrompt { user("Research ${args.topic}") }
                        requestLLM()
                    }
                }
                "Notes on ${args.topic}"
            }
            edge(nodeStart forwardTo answer)
            edge(answer forwardTo nodeFinish)
        }

        override suspend fun execute(args: ResearchArgs, context: AIAgentContext): String {
            val result = research.execute(context as AIAgentGraphContextBase, args).orEmpty()
            completedTopics += args.topic
            return result
        }
    }
}
