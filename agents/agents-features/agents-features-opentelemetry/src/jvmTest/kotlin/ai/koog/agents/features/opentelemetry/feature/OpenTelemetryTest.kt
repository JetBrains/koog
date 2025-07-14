package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.*
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.createAgent
import ai.koog.agents.features.opentelemetry.mock.MockSpanExporter
import ai.koog.agents.features.opentelemetry.mock.TestGetWeatherTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the OpenTelemetry feature.
 *
 * These tests verify that spans are created correctly during agent execution
 * and that the structure of spans matches the expected hierarchy.
 */
class OpenTelemetryTest {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    @Test
    fun `test Open Telemetry feature default configuration`() = runBlocking {
        val testClock = Clock.System

        val strategy = strategy("test-strategy") {
            val nodeSendInput by nodeLLMRequest("test-llm-call")

            edge(nodeStart forwardTo nodeSendInput)
            edge(nodeSendInput forwardTo nodeFinish onAssistantMessage { true })
        }

        var actualServiceName: String? = null
        var actualServiceVersion: String? = null
        var actualIsVerbose: Boolean? = null

        createAgent(
            strategy = strategy,
            clock = testClock,
        ) {
            install(OpenTelemetry) {
                actualServiceName = serviceName
                actualServiceVersion = serviceVersion
                actualIsVerbose = isVerbose
            }
        }

        val props = Properties()
        this::class.java.classLoader.getResourceAsStream("product.properties")?.use { stream -> props.load(stream) }

        assertEquals(props["name"], actualServiceName)
        assertEquals(props["version"], actualServiceVersion)
        assertEquals(false, actualIsVerbose)
    }

    @Test
    fun `test Open Telemetry feature custom configuration`() = runBlocking {
        val testClock = Clock.System

        val strategy = strategy("test-strategy") {
            val nodeSendInput by nodeLLMRequest("test-llm-call")

            edge(nodeStart forwardTo nodeSendInput)
            edge(nodeSendInput forwardTo nodeFinish onAssistantMessage { true })
        }

        val expectedServiceName = "test-service-name"
        val expectedServiceVersion = "test-service-version"
        val expectedIsVerbose = true

        var actualServiceName: String? = null
        var actualServiceVersion: String? = null
        var actualIsVerbose: Boolean? = null

        createAgent(
            strategy = strategy,
            clock = testClock,
        ) {
            install(OpenTelemetry) {
                setServiceInfo(expectedServiceName, expectedServiceVersion)
                setVerbose(expectedIsVerbose)

                actualServiceName = serviceName
                actualServiceVersion = serviceVersion
                actualIsVerbose = isVerbose
            }
        }

        assertEquals(expectedServiceName, actualServiceName)
        assertEquals(expectedServiceVersion, actualServiceVersion)
        assertEquals(expectedIsVerbose, actualIsVerbose)
    }

    @Test
    fun `test spans are created for agent with one llm call`() = runBlocking {
        MockSpanExporter().use { mockExporter ->

            val userPrompt = "What's the weather in Paris?"
            val agentId = "test-agent-id"
            val promptId = "test-prompt-id"
            val testClock = Clock.System
            val model = OpenAIModels.Chat.GPT4o
            val temperature = 0.4

            val strategy = strategy("test-strategy") {
                val nodeSendInput by nodeLLMRequest("test-llm-call")

                edge(nodeStart forwardTo nodeSendInput)
                edge(nodeSendInput forwardTo nodeFinish onAssistantMessage { true })
            }

            val mockResponse = "The weather in Paris is rainy and overcast, with temperatures around 57°F"

            val mockExecutor = getMockExecutor(clock = testClock) {
                mockLLMAnswer(mockResponse) onRequestEquals userPrompt
            }

            val agent = createAgent(
                agentId = agentId,
                strategy = strategy,
                promptId = promptId,
                promptExecutor = mockExecutor,
                model = model,
                clock = testClock,
                temperature = temperature
            ) {
                install(OpenTelemetry) {
                    addSpanExporter(mockExporter)
                    setVerbose(true)
                }
            }

            agent.run(userPrompt)

            val collectedSpans = mockExporter.collectedSpans
            assertTrue(collectedSpans.isNotEmpty(), "Spans should be created during agent execution")

            agent.close()

            // Check each span

            val expectedSpans = listOf(
                mapOf(
                    "agent.$agentId" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.operation.name" to "create_agent",
                            "gen_ai.system" to model.provider.id,
                            "gen_ai.agent.id" to agentId,
                            "gen_ai.request.model" to model.id
                        ),
                        "events" to emptyMap()
                    )
                ),

                mapOf(
                    "run.${mockExporter.lastRunId}" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.operation.name" to "invoke_agent",
                            "koog.agent.strategy.name" to "test-strategy",
                            "gen_ai.system" to model.provider.id,
                            "gen_ai.agent.id" to agentId,
                            "gen_ai.conversation.id" to mockExporter.lastRunId
                        ),
                        "events" to emptyMap()
                    )
                ),

                mapOf(
                    "node.test-llm-call" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.lastRunId,
                            "koog.node.name" to "test-llm-call",
                        ),
                        "events" to emptyMap()
                    )
                ),

                mapOf(
                    "llm.$promptId" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.operation.name" to "chat",
                            "gen_ai.system" to model.provider.id,
                            "gen_ai.conversation.id" to mockExporter.lastRunId,
                            "gen_ai.request.temperature" to temperature,
                            "gen_ai.request.model" to model.id,
                        ),
                        "events" to mapOf(
                            "gen_ai.user.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "body" to "{\"content\":\"${userPrompt}\"}"
                            )
                        ),

                        "events" to mapOf(
                            "gen_ai.user.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "body" to "{\"content\":\"${userPrompt}\"}"
                            ),
                            "gen_ai.choice" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "body" to "{\"index\":0,\"message\":{\"content\":\"${mockResponse}\"}}"
                            )
                        )
                    )
                ),

                mapOf(
                    "node.__start__" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.lastRunId,
                            "koog.node.name" to "__start__"
                        ),
                        "events" to emptyMap()
                    )
                )
            )

            assertSpans(expectedSpans, collectedSpans)
        }
    }

    @Test
    fun `test spans for same agent run multiple times`() = runBlocking {
        MockSpanExporter().use { mockExporter ->

            val userPrompt0 = "What's the weather in Paris?"
            val mockResponse0 = "The weather in Paris is rainy and overcast, with temperatures around 57°F"

            val userPrompt1 = "What's the weather in London?"
            val mockResponse1 = "The weather in London is sunny, with temperatures around 65°F"

            val agentId = "test-agent-id"
            val promptId = "test-prompt-id"
            val testClock = Clock.System
            val model = OpenAIModels.Chat.GPT4o
            val temperature = 0.4

            val strategy = strategy("test-strategy") {
                val nodeSendInput by nodeLLMRequest("test-llm-call")

                edge(nodeStart forwardTo nodeSendInput)
                edge(nodeSendInput forwardTo nodeFinish onAssistantMessage { true })
            }

            val mockExecutor = getMockExecutor(clock = testClock) {
                mockLLMAnswer(mockResponse0) onRequestEquals userPrompt0
                mockLLMAnswer(mockResponse1) onRequestEquals userPrompt1
            }

            val agent = createAgent(
                agentId = agentId,
                strategy = strategy,
                promptId = promptId,
                promptExecutor = mockExecutor,
                model = model,
                clock = testClock,
                temperature = temperature
            ) {
                install(OpenTelemetry) {
                    addSpanExporter(mockExporter)
                    setVerbose(true)
                }
            }

            agent.run(userPrompt0)
            agent.run(userPrompt1)

            val collectedSpans = mockExporter.collectedSpans
            assertTrue(collectedSpans.isNotEmpty(), "Spans should be created during agent execution")

            agent.close()

            // Check each span

            val expectedSpans = listOf(
                mapOf(
                    "agent.$agentId" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.operation.name" to "create_agent",
                            "gen_ai.system" to model.provider.id,
                            "gen_ai.agent.id" to agentId,
                            "gen_ai.request.model" to model.id
                        ),
                        "events" to emptyMap()
                    )
                ),

                // First run
                mapOf(
                    "run.${mockExporter.runIds[1]}" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.operation.name" to "invoke_agent",
                            "koog.agent.strategy.name" to "test-strategy",
                            "gen_ai.system" to model.provider.id,
                            "gen_ai.agent.id" to agentId,
                            "gen_ai.conversation.id" to mockExporter.runIds[1]
                        ),
                        "events" to emptyMap()
                    )
                ),

                mapOf(
                    "node.test-llm-call" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.runIds[1],
                            "koog.node.name" to "test-llm-call",
                        ),
                        "events" to emptyMap()
                    )
                ),

                mapOf(
                    "llm.$promptId" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.operation.name" to "chat",
                            "gen_ai.system" to model.provider.id,
                            "gen_ai.conversation.id" to mockExporter.runIds[1],
                            "gen_ai.request.temperature" to temperature,
                            "gen_ai.request.model" to model.id,
                        ),
                        "events" to mapOf(
                            "gen_ai.user.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "body" to "{\"content\":\"${userPrompt1}\"}"
                            ),
                            "gen_ai.choice" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "body" to "{\"index\":0,\"message\":{\"content\":\"${mockResponse1}\"}}"
                            )
                        )
                    )
                ),

                mapOf(
                    "node.__start__" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.runIds[1],
                            "koog.node.name" to "__start__"
                        ),
                        "events" to emptyMap()
                    )
                ),

                // Second run
                mapOf(
                    "run.${mockExporter.runIds[0]}" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.operation.name" to "invoke_agent",
                            "koog.agent.strategy.name" to "test-strategy",
                            "gen_ai.system" to model.provider.id,
                            "gen_ai.agent.id" to agentId,
                            "gen_ai.conversation.id" to mockExporter.runIds[0]
                        ),
                        "events" to emptyMap()
                    )
                ),

                mapOf(
                    "node.test-llm-call" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.runIds[0],
                            "koog.node.name" to "test-llm-call",
                        ),
                        "events" to emptyMap()
                    )
                ),

                mapOf(
                    "llm.$promptId" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.operation.name" to "chat",
                            "gen_ai.system" to model.provider.id,
                            "gen_ai.conversation.id" to mockExporter.runIds[0],
                            "gen_ai.request.temperature" to temperature,
                            "gen_ai.request.model" to model.id,
                        ),
                        "events" to mapOf(
                            "gen_ai.user.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "body" to "{\"content\":\"${userPrompt0}\"}"
                            ),
                            "gen_ai.choice" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "body" to "{\"index\":0,\"message\":{\"content\":\"${mockResponse0}\"}}"
                            )
                        )
                    )
                ),

                mapOf(
                    "node.__start__" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.runIds[0],
                            "koog.node.name" to "__start__"
                        ),
                        "events" to emptyMap()
                    )
                )
            )

            assertSpans(expectedSpans, collectedSpans)
        }
    }

    @Test
    fun `test spans are created for agent with tool call`() = runBlocking {
        MockSpanExporter().use { mockExporter ->

            val userPrompt = "What's the weather in Paris?"
            val mockResponse = "The weather in Paris is rainy and overcast, with temperatures around 57°F"

            val agentId = "test-agent-id"
            val promptId = "test-prompt-id"
            val testClock = Clock.System
            val model = OpenAIModels.Chat.GPT4o
            val temperature = 0.4

            val strategy = strategy("test-strategy") {
                val nodeSendInput by nodeLLMRequest("test-llm-call")
                val nodeExecuteTool by nodeExecuteTool("test-tool-call")
                val nodeSendToolResult by nodeLLMSendToolResult("test-node-llm-send-tool-result")

                edge(nodeStart forwardTo nodeSendInput)
                edge(nodeSendInput forwardTo nodeExecuteTool onToolCall { true })
                edge(nodeSendInput forwardTo nodeFinish onAssistantMessage { true })
                edge(nodeExecuteTool forwardTo nodeSendToolResult)
                edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
                edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
            }

            val toolRegistry = ToolRegistry {
                tool(TestGetWeatherTool)
            }

            val mockExecutor = getMockExecutor(clock = testClock) {
                mockLLMToolCall(TestGetWeatherTool, TestGetWeatherTool.Args("Paris")) onRequestEquals userPrompt
                mockLLMAnswer(mockResponse) onRequestContains "57°F"
            }

            val agent = createAgent(
                agentId = agentId,
                strategy = strategy,
                promptId = promptId,
                toolRegistry = toolRegistry,
                promptExecutor = mockExecutor,
                model = model,
                clock = testClock,
                temperature = temperature
            ) {
                install(OpenTelemetry) {
                    addSpanExporter(mockExporter)
                    setVerbose(true)
                }
            }

            agent.run(userPrompt)

            val collectedSpans = mockExporter.collectedSpans
            assertTrue(collectedSpans.isNotEmpty(), "Spans should be created during agent execution")

            agent.close()

            // Check Spans

            val expectedSpans = listOf(
                mapOf(
                    "agent.$agentId" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.system" to model.provider.id,
                            "gen_ai.agent.id" to agentId,
                            "gen_ai.request.model" to model.id,
                            "gen_ai.operation.name" to "create_agent",
                        ),
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    "run.${mockExporter.lastRunId}" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.system" to model.provider.id,
                            "gen_ai.agent.id" to agentId,
                            "gen_ai.conversation.id" to mockExporter.lastRunId,
                            "gen_ai.operation.name" to "invoke_agent",
                            "koog.agent.strategy.name" to "test-strategy",
                        ),
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    "node.test-node-llm-send-tool-result" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.lastRunId,
                            "koog.node.name" to "test-node-llm-send-tool-result",
                        ),
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    "llm.$promptId" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.system" to model.provider.id,
                            "gen_ai.request.model" to model.id,
                            "gen_ai.conversation.id" to mockExporter.lastRunId,
                            "gen_ai.operation.name" to "chat",
                            "gen_ai.request.temperature" to temperature,
                        ),
                        "events" to mapOf(
                            "gen_ai.choice" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "body" to "{\"index\":0,\"message\":{\"content\":\"${mockResponse}\"}}"
                            )
                        )
                    )
                ),
                mapOf(
                    "node.test-tool-call" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.lastRunId,
                            "koog.node.name" to "test-tool-call",
                        ),
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    "tool.Get whether" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.tool.description" to "The test tool to get a whether based on provided location.",
                            "gen_ai.tool.name" to "Get whether",
                        ),
                        "events" to mapOf(
                            "gen_ai.tool.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "body" to "{\"content\":\"rainy, 57°F\"}" // Mocked return result defined in the Tool
                            ),
                        )
                    )
                ),
                mapOf(
                    "node.test-llm-call" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.lastRunId,
                            "koog.node.name" to "test-llm-call",
                        ),
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    "llm.$promptId" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.system" to model.provider.id,
                            "gen_ai.request.model" to model.id,
                            "gen_ai.conversation.id" to mockExporter.lastRunId,
                            "gen_ai.operation.name" to "chat",
                            "gen_ai.request.temperature" to temperature,
                        ),
                        "events" to mapOf(
                            "gen_ai.user.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "body" to "{\"content\":\"${userPrompt}\"}"
                            ),
                        )
                    )
                ),
                mapOf(
                    "node.__start__" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.lastRunId,
                            "koog.node.name" to "__start__",
                        ),
                        "events" to emptyMap()
                    )
                ),
            )

            assertSpans(expectedSpans, collectedSpans)
        }
    }

    @Test
    fun `test spans for agent with tool call and verbose level set to false`() = runBlocking {
        MockSpanExporter().use { mockExporter ->

            val userPrompt = "What's the weather in Paris?"
            val mockResponse = "The weather in Paris is rainy and overcast, with temperatures around 57°F"

            val agentId = "test-agent-id"
            val promptId = "test-prompt-id"
            val testClock = Clock.System
            val model = OpenAIModels.Chat.GPT4o
            val temperature = 0.4

            val strategy = strategy("test-strategy") {
                val nodeSendInput by nodeLLMRequest("test-llm-call")
                val nodeExecuteTool by nodeExecuteTool("test-tool-call")
                val nodeSendToolResult by nodeLLMSendToolResult("test-node-llm-send-tool-result")

                edge(nodeStart forwardTo nodeSendInput)
                edge(nodeSendInput forwardTo nodeExecuteTool onToolCall { true })
                edge(nodeSendInput forwardTo nodeFinish onAssistantMessage { true })
                edge(nodeExecuteTool forwardTo nodeSendToolResult)
                edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
                edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
            }

            val toolRegistry = ToolRegistry {
                tool(TestGetWeatherTool)
            }

            val mockExecutor = getMockExecutor(clock = testClock) {
                mockLLMToolCall(TestGetWeatherTool, TestGetWeatherTool.Args("Paris")) onRequestEquals userPrompt
                mockLLMAnswer(mockResponse) onRequestContains "57°F"
            }

            val agent = createAgent(
                agentId = agentId,
                strategy = strategy,
                promptId = promptId,
                toolRegistry = toolRegistry,
                promptExecutor = mockExecutor,
                model = model,
                clock = testClock,
                temperature = temperature
            ) {
                install(OpenTelemetry) {
                    addSpanExporter(mockExporter)
                    setVerbose(false)
                }
            }

            agent.run(userPrompt)

            val collectedSpans = mockExporter.collectedSpans
            assertTrue(collectedSpans.isNotEmpty(), "Spans should be created during agent execution")

            agent.close()

            // Check Spans

            val expectedSpans = listOf(
                mapOf(
                    "agent.$agentId" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.system" to model.provider.id,
                            "gen_ai.agent.id" to agentId,
                            "gen_ai.request.model" to model.id,
                            "gen_ai.operation.name" to "create_agent",
                        ),
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    "run.${mockExporter.lastRunId}" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.system" to model.provider.id,
                            "gen_ai.agent.id" to agentId,
                            "gen_ai.conversation.id" to mockExporter.lastRunId,
                            "gen_ai.operation.name" to "invoke_agent",
                            "koog.agent.strategy.name" to "test-strategy",
                        ),
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    "node.test-node-llm-send-tool-result" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.lastRunId,
                            "koog.node.name" to "test-node-llm-send-tool-result",
                        ),
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    "llm.$promptId" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.system" to model.provider.id,
                            "gen_ai.request.model" to model.id,
                            "gen_ai.conversation.id" to mockExporter.lastRunId,
                            "gen_ai.operation.name" to "chat",
                            "gen_ai.request.temperature" to temperature,
                        ),
                        "events" to mapOf(
                            "gen_ai.choice" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "body" to "{\"index\":0}"
                            )
                        )
                    )
                ),
                mapOf(
                    "node.test-tool-call" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.lastRunId,
                            "koog.node.name" to "test-tool-call",
                        ),
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    "tool.Get whether" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.tool.description" to "The test tool to get a whether based on provided location.",
                            "gen_ai.tool.name" to "Get whether",
                        ),
                        "events" to mapOf(
                            "gen_ai.tool.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                            ),
                        )
                    )
                ),
                mapOf(
                    "node.test-llm-call" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.lastRunId,
                            "koog.node.name" to "test-llm-call",
                        ),
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    "llm.$promptId" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.system" to model.provider.id,
                            "gen_ai.request.model" to model.id,
                            "gen_ai.conversation.id" to mockExporter.lastRunId,
                            "gen_ai.operation.name" to "chat",
                            "gen_ai.request.temperature" to temperature,
                        ),
                        "events" to mapOf(
                            "gen_ai.user.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                            ),
                        )
                    )
                ),
                mapOf(
                    "node.__start__" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.lastRunId,
                            "koog.node.name" to "__start__",
                        ),
                        "events" to emptyMap()
                    )
                ),
            )

            assertSpans(expectedSpans, collectedSpans)
        }
    }

    @Test
    fun `test spans are created for agent with parallel nodes execution`() = runBlocking {
        MockSpanExporter().use { mockExporter ->

            val userPrompt = "What's the best joke about programming?"
            val agentId = "test-agent-id"
            val promptId = "test-prompt-id"
            val testClock = Clock.System
            val model = OpenAIModels.Chat.GPT4o
            val temperature = 0.4

            val strategy = strategy("test-strategy") {
                val nodeFirstJoke by node<String, String> { topic ->
                    "First joke about $topic: Why do programmers prefer dark mode? Because light attracts bugs!"
                }

                val nodeSecondJoke by node<String, String> { topic ->
                    "Second joke about $topic: Why do Java developers wear glasses? Because they don't C#!"
                }

                val nodeThirdJoke by node<String, String> { topic ->
                    "Third joke about $topic: A SQL query walks into a bar, walks up to two tables and asks, 'Can I join you?'"
                }

                // Define a node to run joke generation in parallel
                val nodeGenerateJokes by parallel(
                    nodeFirstJoke, nodeSecondJoke, nodeThirdJoke
                ) {
                    selectByIndex { jokes ->
                        // Always select the first joke for testing purposes
                        0
                    }
                }

                edge(nodeStart forwardTo nodeGenerateJokes)
                edge(nodeGenerateJokes forwardTo nodeFinish)
            }

            val mockResponse = "Why do programmers prefer dark mode? Because light attracts bugs!"

            val mockExecutor = getMockExecutor(clock = testClock) {
                mockLLMAnswer(mockResponse) onRequestEquals userPrompt
            }

            val agent = createAgent(
                agentId = agentId,
                strategy = strategy,
                promptId = promptId,
                promptExecutor = mockExecutor,
                model = model,
                clock = testClock,
                temperature = temperature
            ) {
                install(OpenTelemetry) {
                    addSpanExporter(mockExporter)
                    setVerbose(true)
                }
            }

            agent.run(userPrompt)

            val collectedSpans = mockExporter.collectedSpans
            assertTrue(collectedSpans.isNotEmpty(), "Spans should be created during agent execution")

            agent.close()
            // Check each span
            // We expect spans for:
            // 1. Agent creation
            // 2. Agent run
            // 3. Start node
            // 4. Each parallel node (3 nodes)
            // 5. Merge node
            // 6. Finish node

            // Verify that we have spans for all parallel nodes
            val nodeSpanNames = collectedSpans.map { it.name }
                .filter { it.startsWith("node.") }
                .sorted()

            logger.debug { "Node span names: $nodeSpanNames" }

            // Print all node spans with their attributes for debugging
            collectedSpans.filter { it.name.startsWith("node.") }.forEach { span ->
                val attributes = span.attributes.asMap().asSequence().associate { it.key.key to it.value }
                logger.debug { "Node span: ${span.name}, attributes: $attributes" }
            }

            // Check if we have the expected number of node spans (5 nodes)
            assertEquals(5, nodeSpanNames.size, "Expected 6 node spans but found ${nodeSpanNames.size}")

            // Check for each specific node span
            assertTrue(nodeSpanNames.any { it.contains("nodeFirstJoke") }, "First joke node span should be created")
            assertTrue(nodeSpanNames.any { it.contains("nodeSecondJoke") }, "Second joke node span should be created")
            assertTrue(nodeSpanNames.any { it.contains("nodeThirdJoke") }, "Third joke node span should be created")
            assertTrue(
                nodeSpanNames.any { it.contains("nodeGenerateJokes") },
                "Generate jokes node span should be created"
            )

            // Verify parallel node spans have the correct conversation ID
            val parallelNodeSpans = collectedSpans.filter {
                it.name.startsWith("node.") &&
                        (it.name.contains("nodeFirstJoke") || it.name.contains("nodeSecondJoke") || it.name.contains("nodeThirdJoke"))
            }

            assertEquals(3, parallelNodeSpans.size, "Should have 3 parallel node spans")

            parallelNodeSpans.forEach { span ->
                val spanAttributes = span.attributes.asMap().asSequence().associate {
                    it.key.key to it.value
                }

                assertEquals(
                    mockExporter.lastRunId, spanAttributes["gen_ai.conversation.id"],
                    "Parallel node span ${span.name} should have conversation ID '${mockExporter.lastRunId}'"
                )
            }
        }
    }

    //region Private Methods

    /**
     * Expected Span:
     *   Map<SpanName, Map<Any>>
     *       where Any = "attributes" or "events"
     *       attributes: Map<AttributeKey, AttributeValue>
     *       events: Map<EventName, Attributes>
     *           Attributes: Map<AttributeKey, AttributeValue>
     */
    @Suppress("UNCHECKED_CAST")
    private fun assertSpans(expectedSpans: List<Map<String, Map<String, Any>>>, actualSpans: List<SpanData>) {
        // Span names
        val expectedSpanNames = expectedSpans.flatMap { it.keys }
        val actualSpanNames = actualSpans.map { it.name }

        assertSpanNames(expectedSpanNames, actualSpanNames)

        // Span attributes + events
        actualSpans.forEachIndexed { index, actualSpan ->

            val expectedSpan = expectedSpans[index]

            val expectedSpanData = expectedSpan[actualSpan.name]
            assertNotNull(expectedSpanData, "Span (name: ${actualSpan.name}) not found in expected spans")

            val spanName = actualSpan.name

            // Attributes
            val expectedAttributes = expectedSpanData["attributes"] as Map<String, Any>
            val actualAttributes = actualSpan.attributes.asMap().asSequence().associate {
                it.key.key to it.value
            }

            assertAttributes(spanName, expectedAttributes, actualAttributes)

            // Events
            val expectedEvents = expectedSpanData["events"] as Map<String, Map<String, Any>>
            val actualEvents = actualSpan.events.associate { event ->
                val actualEventAttributes = event.attributes.asMap().asSequence().associate { (key, value) ->
                    key.key to value
                }
                event.name to actualEventAttributes
            }

            assertEventsForSpan(spanName, expectedEvents, actualEvents)
        }
    }

    private fun assertSpanNames(expectedSpanNames: List<String>, actualSpanNames: List<String>) {
        assertEquals(
            expectedSpanNames.size,
            actualSpanNames.size,
            "Expected collection of spans should be the same size"
        )
        assertContentEquals(
            expectedSpanNames,
            actualSpanNames,
            "Expected collection of spans should be the same as actual"
        )
    }

    /**
     * Event:
     *   Map<EventName, Attributes> -> Map<EventName, Map<AttributeKey, AttributeValue>>
     */
    private fun assertEventsForSpan(
        spanName: String,
        expectedEvents: Map<String, Map<String, Any>>,
        actualEvents: Map<String, Map<String, Any>>
    ) {
        logger.debug { "Asserting events for the Span (name: $spanName).\nExpected events:\n$expectedEvents\nActual events:\n$actualEvents" }

        assertEquals(
            expectedEvents.size,
            actualEvents.size,
            "Expected collection of events should be the same size for the span (name: $spanName)"
        )

        actualEvents.forEach { (actualEventName, actualEventAttributes) ->

            logger.debug { "Asserting event (name: $actualEventName) for the Span (name: $spanName)" }

            val expectedEventAttributes = expectedEvents[actualEventName]
            assertNotNull(
                expectedEventAttributes,
                "Event (name: $actualEventName) not found in expected events for span (name: $spanName)"
            )

            assertAttributes(spanName, expectedEventAttributes, actualEventAttributes)
        }
    }

    /**
     * Attribute:
     *   Map<AttributeKey, AttributeValue>
     */
    private fun assertAttributes(
        spanName: String,
        expectedAttributes: Map<String, Any>,
        actualAttributes: Map<String, Any>
    ) {
        logger.debug { "Asserting attributes for the Span (name: $spanName).\nExpected attributes:\n$expectedAttributes\nActual attributes:\n$actualAttributes" }

        assertEquals(
            expectedAttributes.size,
            actualAttributes.size,
            "Expected collection of attributes should be the same size for the span (name: $spanName)"
        )

        actualAttributes.forEach { (actualArgName, actualArgValue) ->

            logger.debug { "Find expected attribute (name: $actualArgName) for the Span (name: $spanName)" }
            val expectedArgValue = expectedAttributes[actualArgName]

            assertNotNull(
                expectedArgValue,
                "Attribute (name: $actualArgName) not found in expected attributes for span (name: $spanName)"
            )
            assertEquals(
                expectedArgValue,
                actualArgValue,
                "Attribute values should be the same for the span (name: $spanName)()"
            )
        }
    }

    //endregion Private Methods
}
