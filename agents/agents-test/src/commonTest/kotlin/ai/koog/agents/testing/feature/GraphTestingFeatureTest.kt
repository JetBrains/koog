package ai.koog.agents.testing.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.builder.subgraph
import ai.koog.agents.core.dsl.extension.nodeExecuteSingleTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequestWithUserText
import ai.koog.agents.core.dsl.extension.onToolCalls
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.serialization.kotlinx.KotlinxSerializer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the Testing feature.
 */
class GraphTestingFeatureTest {
    private val serializer = KotlinxSerializer()

    @Test
    fun testMultiSubgraphAgentStructure() = runTest {
        val strategy = strategy<String, String>("test") {
            val firstSubgraph by subgraph<Unit, String>(
                name = "first",
                tools = listOf(DummyTool, CreateTool, SolveTool)
            ) {
                val callLLM by nodeLLMRequestWithUserText(name = "callLLM")
                val executeTool by nodeExecuteSingleTool(name = "executeTool")
                val giveFeedback by node<Any?, Any?>("giveFeedback") { input ->
                    llm.writeSession {
                        appendPrompt {
                            user("Call tools! Don't chat!")
                        }
                    }
                    input
                }

                edge(nodeStart forwardTo callLLM transformed { "Hello" })
                edge(
                    callLLM forwardTo executeTool
                        onToolCalls { true }
                        transformed { it.toolCalls.first() }
                )
                edge(
                    callLLM forwardTo giveFeedback
                        onCondition { msg -> msg.parts.none { it is MessagePart.Tool.Call } }
                )
                edge(
                    giveFeedback forwardTo executeTool
                        onCondition { it is Message.Assistant && it.parts.any { p -> p is MessagePart.Tool.Call } }
                        transformed { (it as Message.Assistant).parts.filterIsInstance<MessagePart.Tool.Call>().first() }
                )
                edge(executeTool forwardTo nodeFinish transformed { it.output })
            }

            val secondSubgraph by subgraph<Unit, String>(name = "second") {
                edge(nodeStart forwardTo nodeFinish transformed { "" })
            }

            edge(nodeStart forwardTo firstSubgraph transformed { })
            edge(firstSubgraph forwardTo secondSubgraph transformed { })
            edge(secondSubgraph forwardTo nodeFinish)
        }

        val toolRegistry = ToolRegistry {
            tool(DummyTool)
            tool(CreateTool)
            tool(SolveTool)
        }

        val mockLLMApi = getMockExecutor(serializer) {
            mockLLMAnswer("Hello!") onRequestContains "Hello"
            mockLLMToolCall(CreateTool, CreateTool.Args("solve")) onRequestEquals "Solve task"
        }

        val basePrompt = prompt("test") {}

        AIAgent(
            promptExecutor = mockLLMApi,
            strategy = strategy,
            agentConfig = AIAgentConfig(prompt = basePrompt, model = OpenAIModels.Chat.GPT4o, maxAgentIterations = 100),
            toolRegistry = toolRegistry
        ) {
            testGraph<String, String>("test") {
                val firstSubgraph = assertSubgraphByName<Unit, String>("first")
                val secondSubgraph = assertSubgraphByName<Unit, String>("second")

                assertEdges {
                    startNode() alwaysGoesTo firstSubgraph
                    firstSubgraph alwaysGoesTo secondSubgraph
                    secondSubgraph alwaysGoesTo finishNode()
                }

                verifySubgraph(firstSubgraph) {
                    val start = startNode()
                    val finish = finishNode()

                    val askLLM = assertNodeByName<String, Message.Assistant>("callLLM")
                    val callTool = assertNodeByName<MessagePart.Tool.Call, ReceivedToolResult>("executeTool")
                    val giveFeedback = assertNodeByName<Any?, Any?>("giveFeedback")

                    assertReachable(start, askLLM)
                    assertReachable(askLLM, callTool)

                    assertNodes {
                        askLLM withInput "Hello" outputs assistantMessage("Hello!")
                        askLLM withInput "Solve task" outputs assistantMessage(CreateTool, CreateTool.Args("solve"))

                        val createToolArgs = SolveTool.Args("solve")
                        callTool withInput toolCallMessagePart(
                            SolveTool,
                            createToolArgs,
                        ) outputs toolResult(SolveTool, createToolArgs, result = "solved")

                        val solveToolArgs = CreateTool.Args("solve")
                        callTool withInput toolCallMessagePart(
                            CreateTool,
                            solveToolArgs,
                        ) outputs toolResult(CreateTool, solveToolArgs, result = "created")
                    }

                    assertEdges {
                        askLLM withOutput assistantMessage("Hello!") goesTo giveFeedback
                        askLLM withOutput assistantMessage(CreateTool, CreateTool.Args("solve")) goesTo callTool
                    }
                }
            }
        }
    }

    @Test
    fun testTestingFeatureAPI() {
        // This test demonstrates the API of Testing feature
        // In a real test, you would use an actual AIAgent

        // Create a Config instance directly to test the API
        val config = Testing.Config(serializer).apply {
            verifyStrategy<String, String>("test") {
                val first = assertSubgraphByName<String, String>("first")
                val second = assertSubgraphByName<String, String>("second")
                verifySubgraph(first) {
                    val start = startNode()
                    val finish = finishNode()

                    val askLLM = assertNodeByName<String, Message.Assistant>("callLLM")
                    val callTool = assertNodeByName<MessagePart.Tool.Call, MessagePart.Tool.Result>("executeTool")
                    val giveFeedback = assertNodeByName<Any?, Any?>("giveFeedback")

                    assertReachable(start, askLLM)
                    assertReachable(askLLM, callTool)
                    assertReachable(callTool, giveFeedback)
                    assertReachable(giveFeedback, finish)
                }

                verifySubgraph(second) {
                    val start = startNode()
                    val finish = finishNode()

                    assertReachable(start, finish)
                }
            }
        }

        // Verify that the config was created correctly
        assertEquals(2, config.getAssertions().subgraphAssertions.size)

        // Verify the first stage
        val firstSubgraphAssertion = config.getAssertions().subgraphAssertions.find { it.subgraphRef.name == "first" }
        assertEquals("first", firstSubgraphAssertion?.subgraphRef?.name)
        assertEquals(3, firstSubgraphAssertion?.graphAssertions?.nodes?.size)
        assertEquals(4, firstSubgraphAssertion?.graphAssertions?.reachabilityAssertions?.size)

        // Verify the second stage
        val secondSubgraphAssertion = config.getAssertions().subgraphAssertions.find { it.subgraphRef.name == "second" }
        assertEquals("second", secondSubgraphAssertion?.subgraphRef?.name)
        assertEquals(0, secondSubgraphAssertion?.graphAssertions?.nodes?.size)
        assertEquals(1, secondSubgraphAssertion?.graphAssertions?.reachabilityAssertions?.size)
    }
}
