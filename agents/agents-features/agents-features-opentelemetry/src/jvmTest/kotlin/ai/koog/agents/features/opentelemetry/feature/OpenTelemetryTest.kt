package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.assertMapsEqual
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.createAgent
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.createAgentService
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes.Response.FinishReasonType
import ai.koog.agents.features.opentelemetry.integration.SpanAdapter
import ai.koog.agents.features.opentelemetry.mock.MockSpanExporter
import ai.koog.agents.features.opentelemetry.mock.TestGetWeatherTool
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.agents.utils.HiddenString
import ai.koog.agents.utils.use
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.tokenizer.SimpleRegexBasedTokenizer
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
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

            val systemPrompt = "You are the application that predicts weather"
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
                systemPrompt = systemPrompt,
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
                            "gen_ai.system" to model.provider.id,
                            "gen_ai.agent.id" to agentId,
                            "gen_ai.conversation.id" to mockExporter.lastRunId
                        ),
                        "events" to emptyMap()
                    )
                ),

                mapOf(
                    "node.__finish__" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.lastRunId,
                            "koog.node.name" to "__finish__"
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
                            "gen_ai.response.finish_reasons" to listOf(FinishReasonType.Stop.id)
                        ),
                        "events" to mapOf(
                            "gen_ai.user.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.User.name.lowercase(),
                                "content" to userPrompt
                            )
                        ),

                        "events" to mapOf(
                            "gen_ai.system.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.System.name.lowercase(),
                                "content" to systemPrompt,
                            ),
                            "gen_ai.user.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.User.name.lowercase(),
                                "content" to userPrompt,
                            ),
                            "gen_ai.assistant.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.Assistant.name.lowercase(),
                                "content" to mockResponse,
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

            val systemPrompt = "You are the application that predicts weather"

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

            val agentService = createAgentService(
                strategy = strategy,
                promptId = promptId,
                systemPrompt = systemPrompt,
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

            agentService.createAgentAndRun(userPrompt0, id = agentId)
            agentService.createAgentAndRun(userPrompt1, id = agentId)

            val collectedSpans = mockExporter.collectedSpans
            assertTrue(collectedSpans.isNotEmpty(), "Spans should be created during agent execution")

            agentService.closeAll()

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
                            "gen_ai.system" to model.provider.id,
                            "gen_ai.agent.id" to agentId,
                            "gen_ai.conversation.id" to mockExporter.runIds[1]
                        ),
                        "events" to emptyMap()
                    )
                ),

                mapOf(
                    "node.__finish__" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.runIds[1],
                            "koog.node.name" to "__finish__"
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
                            "gen_ai.response.finish_reasons" to listOf(FinishReasonType.Stop.id),
                        ),
                        "events" to mapOf(
                            "gen_ai.system.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.System.name.lowercase(),
                                "content" to systemPrompt,
                            ),
                            "gen_ai.user.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.User.name.lowercase(),
                                "content" to userPrompt1,
                            ),
                            "gen_ai.assistant.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.Assistant.name.lowercase(),
                                "content" to mockResponse1,
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
                            "gen_ai.system" to model.provider.id,
                            "gen_ai.agent.id" to agentId,
                            "gen_ai.conversation.id" to mockExporter.runIds[0]
                        ),
                        "events" to emptyMap()
                    )
                ),

                mapOf(
                    "node.__finish__" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.runIds[0],
                            "koog.node.name" to "__finish__"
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
                            "gen_ai.response.finish_reasons" to listOf(FinishReasonType.Stop.id),
                        ),
                        "events" to mapOf(
                            "gen_ai.system.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.System.name.lowercase(),
                                "content" to systemPrompt,
                            ),
                            "gen_ai.user.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.User.name.lowercase(),
                                "content" to userPrompt0,
                            ),
                            "gen_ai.assistant.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.Assistant.name.lowercase(),
                                "content" to mockResponse0,
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

            val systemPrompt = "You are the application that predicts weather"
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

            val toolCallId = "tool-call-id"

            val mockExecutor = getMockExecutor(clock = testClock) {
                mockLLMToolCall(tool = TestGetWeatherTool, args = TestGetWeatherTool.Args("Paris"), toolCallId = toolCallId) onRequestEquals userPrompt
                mockLLMAnswer(mockResponse) onRequestContains TestGetWeatherTool.DEFAULT_PARIS_RESULT
            }

            val agent = createAgent(
                agentId = agentId,
                strategy = strategy,
                promptId = promptId,
                systemPrompt = systemPrompt,
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
                        ),
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    "node.__finish__" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.lastRunId,
                            "koog.node.name" to "__finish__",
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
                            "gen_ai.response.finish_reasons" to listOf(FinishReasonType.Stop.id),
                        ),
                        "events" to mapOf(
                            "gen_ai.system.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.System.name.lowercase(),
                                "content" to systemPrompt,
                            ),
                            "gen_ai.user.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.User.name.lowercase(),
                                "content" to userPrompt,
                            ),
                            "gen_ai.choice" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.Tool.name.lowercase(),
                                "tool_calls" to """[{"function":{"name":"${TestGetWeatherTool.name}","arguments":"{\"location\":\"Paris\"}"},"id":"$toolCallId","type":"function"}]""",
                                "finish_reason" to FinishReasonType.ToolCalls.id,
                            ),
                            "gen_ai.tool.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.Tool.name.lowercase(),
                                "content" to TestGetWeatherTool.DEFAULT_PARIS_RESULT,
                                "id" to toolCallId,
                            ),
                            "gen_ai.assistant.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.Assistant.name.lowercase(),
                                "content" to mockResponse,
                            ),
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
                            "output.value" to TestGetWeatherTool.DEFAULT_PARIS_RESULT,
                            "input.value" to "{\"location\":\"Paris\"}",
                            "gen_ai.tool.description" to "The test tool to get a whether based on provided location.",
                            "gen_ai.tool.name" to "Get whether",
                            "gen_ai.tool.call.id" to toolCallId,
                        ),
                        "events" to mapOf()
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
                            "gen_ai.response.finish_reasons" to listOf(FinishReasonType.ToolCalls.id),
                        ),
                        "events" to mapOf(
                            "gen_ai.system.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.System.name.lowercase(),
                                "content" to systemPrompt,
                            ),
                            "gen_ai.user.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.User.name.lowercase(),
                                "content" to userPrompt,
                            ),
                            "gen_ai.choice" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "index" to 0L,
                                "role" to Message.Role.Tool.name.lowercase(),
                                "tool_calls" to """[{"function":{"name":"${TestGetWeatherTool.name}","arguments":"{\"location\":\"Paris\"}"},"id":"$toolCallId","type":"function"}]""",
                                "finish_reason" to FinishReasonType.ToolCalls.id,
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

            val systemPrompt = "You are the application that predicts weather"
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

            val toolCallId = "tool-call-id"

            val mockExecutor = getMockExecutor(clock = testClock) {
                mockLLMToolCall(tool = TestGetWeatherTool, args = TestGetWeatherTool.Args("Paris"), toolCallId = toolCallId) onRequestEquals userPrompt
                mockLLMAnswer(mockResponse) onRequestContains "57°F"
            }

            val agent = createAgent(
                agentId = agentId,
                strategy = strategy,
                systemPrompt = systemPrompt,
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
                        ),
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    "node.__finish__" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.lastRunId,
                            "koog.node.name" to "__finish__",
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
                            "gen_ai.response.finish_reasons" to listOf(FinishReasonType.Stop.id),
                        ),
                        "events" to mapOf(
                            "gen_ai.system.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.System.name.lowercase(),
                                "content" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                            ),
                            "gen_ai.user.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.User.name.lowercase(),
                                "content" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                            ),
                            "gen_ai.choice" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.Tool.name.lowercase(),
                                "tool_calls" to "[{\"function\":{\"name\":\"${HiddenString.HIDDEN_STRING_PLACEHOLDER}\",\"arguments\":\"${HiddenString.HIDDEN_STRING_PLACEHOLDER}\"},\"id\":\"$toolCallId\",\"type\":\"function\"}]",
                                "finish_reason" to FinishReasonType.ToolCalls.id,
                            ),
                            "gen_ai.tool.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.Tool.name.lowercase(),
                                "content" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                                "id" to toolCallId,
                            ),
                            "gen_ai.assistant.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.Assistant.name.lowercase(),
                                "content" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                            ),
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
                            "output.value" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                            "input.value" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                            "gen_ai.tool.description" to "The test tool to get a whether based on provided location.",
                            "gen_ai.tool.name" to "Get whether",
                            "gen_ai.tool.call.id" to toolCallId,
                        ),
                        "events" to mapOf()
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
                            "gen_ai.response.finish_reasons" to listOf(FinishReasonType.ToolCalls.id),
                        ),
                        "events" to mapOf(
                            "gen_ai.system.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.System.name.lowercase(),
                                "content" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                            ),
                            "gen_ai.user.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.User.name.lowercase(),
                                "content" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                            ),
                            "gen_ai.choice" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "index" to 0L,
                                "role" to Message.Role.Tool.name.lowercase(),
                                "tool_calls" to "[{\"function\":{\"name\":\"${HiddenString.HIDDEN_STRING_PLACEHOLDER}\",\"arguments\":\"${HiddenString.HIDDEN_STRING_PLACEHOLDER}\"},\"id\":\"$toolCallId\",\"type\":\"function\"}]",
                                "finish_reason" to FinishReasonType.ToolCalls.id,
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
                    nodeFirstJoke,
                    nodeSecondJoke,
                    nodeThirdJoke
                ) {
                    selectByIndex {
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
            assertEquals(6, nodeSpanNames.size, "Expected 6 node spans but found ${nodeSpanNames.size}")

            // Check for each node span
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
                    (
                        it.name.contains("nodeFirstJoke") ||
                            it.name.contains("nodeSecondJoke") ||
                            it.name.contains("nodeThirdJoke")
                        )
            }

            assertEquals(3, parallelNodeSpans.size, "Should have 3 parallel node spans")

            parallelNodeSpans.forEach { span ->
                val spanAttributes = span.attributes.asMap().asSequence().associate {
                    it.key.key to it.value
                }

                assertEquals(
                    mockExporter.lastRunId,
                    spanAttributes["gen_ai.conversation.id"],
                    "Parallel node span ${span.name} should have conversation ID '${mockExporter.lastRunId}'"
                )
            }
        }
    }

    @Test
    fun `test install Open Telemetry feature with custom sdk, should use provided sdk`() = runBlocking {
        val strategy = strategy<String, String>("test-strategy") {
            edge(nodeStart forwardTo nodeFinish transformed { "Done" })
        }

        val expectedSdk = OpenTelemetrySdk.builder().build()
        var actualSdk: OpenTelemetrySdk? = null

        createAgent(
            strategy = strategy,
        ) {
            install(OpenTelemetry) {
                setSdk(expectedSdk)
                actualSdk = sdk
            }
        }

        assertEquals(expectedSdk, actualSdk)
    }

    @Test
    fun `test Open Telemetry feature with custom sdk configuration emits correct spans`() = runBlocking {
        MockSpanExporter().use { mockExporter ->
            val userPrompt = "What's the weather in Paris?"

            val strategy = strategy("test-strategy") {
                val nodeSendInput by nodeLLMRequest("test-llm-call")
                edge(nodeStart forwardTo nodeSendInput)
                edge(nodeSendInput forwardTo nodeFinish onAssistantMessage { true })
            }

            val mockResponse = "The weather in Paris is rainy and overcast, with temperatures around 57°F"

            val mockExecutor = getMockExecutor {
                mockLLMAnswer(mockResponse) onRequestEquals userPrompt
            }

            val expectedSdk = createCustomSdk(mockExporter)

            val agent = createAgent(
                promptExecutor = mockExecutor,
                strategy = strategy,
            ) {
                install(OpenTelemetry) {
                    setSdk(expectedSdk)
                }
            }

            agent.run(userPrompt)
            val collectedSpans = mockExporter.collectedSpans
            agent.close()

            assertEquals(6, collectedSpans.size)
        }
    }

    @Test
    fun `test spans are created for agent with node execution error`() = runBlocking {
        MockSpanExporter().use { mockExporter ->

            val userPrompt = "What's the weather in Paris?"
            val agentId = "test-agent-id"
            val promptId = "test-prompt-id"
            val testClock = Clock.System
            val model = OpenAIModels.Chat.GPT4o
            val temperature = 0.4

            val nodeWithErrorName = "node-with-error"
            val testErrorMessage = "Test error"

            val strategy = strategy("test-strategy") {
                val nodeWithError by node<String, String>(nodeWithErrorName) {
                    throw IllegalStateException(testErrorMessage)
                }

                edge(nodeStart forwardTo nodeWithError)
                edge(nodeWithError forwardTo nodeFinish)
            }

            createAgent(
                agentId = agentId,
                strategy = strategy,
                promptId = promptId,
                model = model,
                clock = testClock,
                temperature = temperature
            ) {
                install(OpenTelemetry) {
                    addSpanExporter(mockExporter)
                }
            }.use { agent ->
                val throwable = assertFails {
                    agent.run(userPrompt)
                }

                assertEquals(testErrorMessage, throwable.message)
                assertTrue(mockExporter.collectedSpans.isNotEmpty(), "Spans should be created during agent execution")
            }

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
                            "gen_ai.response.finish_reasons" to listOf(FinishReasonType.Error.id),
                            "gen_ai.system" to model.provider.id,
                            "gen_ai.agent.id" to agentId,
                            "gen_ai.conversation.id" to mockExporter.lastRunId
                        ),
                        "events" to emptyMap()
                    )
                ),

                mapOf(
                    "node.node-with-error" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.lastRunId,
                            "koog.node.name" to "node-with-error",
                        ),
                        "events" to emptyMap()
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

            assertSpans(expectedSpans, mockExporter.collectedSpans)
        }
    }

    @Test
    fun `test span adapter applies custom attribute to invoke agent span`() = runBlocking {
        MockSpanExporter().use { mockExporter ->

            val userPrompt = "What's the weather in Paris?"
            val agentId = "test-agent-id"
            val promptId = "test-prompt-id"
            val testClock = Clock.System
            val model = OpenAIModels.Chat.GPT4o

            val strategyName = "test-strategy"

            val strategy = strategy(strategyName) {
                val nodeSendInput by nodeLLMRequest("test-llm-call")

                edge(nodeStart forwardTo nodeSendInput)
                edge(nodeSendInput forwardTo nodeFinish onAssistantMessage { true })
            }

            val mockResponse = "Sunny"
            val mockExecutor = getMockExecutor(clock = testClock) {
                mockLLMAnswer(mockResponse) onRequestEquals userPrompt
            }

            // Custom SpanAdapter that adds a test attribute to each processed span
            val customBeforeStartAttribute = CustomAttribute(key = "test.adapter.before.start.key", value = "test-value-before-start")
            val customBeforeFinishAttribute = CustomAttribute(key = "test.adapter.before.finish.key", value = "test-value-before-finish")
            val adapter = object : SpanAdapter() {
                override fun onBeforeSpanStarted(span: GenAIAgentSpan) {
                    span.addAttribute(customBeforeStartAttribute)
                }

                override fun onBeforeSpanFinished(span: GenAIAgentSpan) {
                    span.addAttribute(customBeforeFinishAttribute)
                }
            }

            createAgent(
                agentId = agentId,
                strategy = strategy,
                promptId = promptId,
                promptExecutor = mockExecutor,
                model = model,
                clock = testClock,
            ) {
                install(OpenTelemetry) {
                    addSpanExporter(mockExporter)

                    // Add custom span adapter
                    addSpanAdapter(adapter)
                }
            }.use { agent ->
                agent.run(userPrompt)
            }

            val collectedSpans = mockExporter.collectedSpans
            assertTrue(collectedSpans.isNotEmpty(), "Spans should be created during agent execution")

            val actualInvokeAgentSpans = collectedSpans.filter { span -> span.name.startsWith("run.") }
            assertEquals(1, actualInvokeAgentSpans.size, "Invoke agent span should be present")

            val expectedInvokeAgentSpans = listOf(
                mapOf(

                    "run.${mockExporter.lastRunId}" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.lastRunId,
                            customBeforeStartAttribute.key to customBeforeStartAttribute.value,
                            customBeforeFinishAttribute.key to customBeforeFinishAttribute.value,
                            "gen_ai.system" to model.provider.id,
                            "gen_ai.agent.id" to agentId,
                            "gen_ai.operation.name" to SpanAttributes.Operation.OperationNameType.INVOKE_AGENT.id,
                        ),
                        "events" to emptyMap()
                    )
                )
            )

            assertSpans(expectedInvokeAgentSpans, actualInvokeAgentSpans)
        }
    }

    @Test
    fun `test tokens attributes are captured for inference spans`() = runBlocking {
        MockSpanExporter().use { mockExporter ->

            val systemPrompt = "You are the application that predicts weather"
            val userPrompt = "What's the weather in Paris?"
            val mockResponse = "The weather in Paris is rainy and overcast, with temperatures around 57°F"

            val agentId = "test-agent-id"
            val promptId = "test-prompt-id"
            val testClock = Clock.System
            val model = OpenAIModels.Chat.GPT4o
            val temperature = 0.4
            val maxTokens = 123

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

            val toolCallId = "tool-call-id"
            val tokenizer = SimpleRegexBasedTokenizer()

            val mockExecutor = getMockExecutor(clock = testClock, tokenizer = tokenizer) {
                mockLLMToolCall(tool = TestGetWeatherTool, args = TestGetWeatherTool.Args("Paris"), toolCallId = toolCallId) onRequestEquals userPrompt
                mockLLMAnswer(mockResponse) onRequestContains TestGetWeatherTool.DEFAULT_PARIS_RESULT
            }

            createAgent(
                agentId = agentId,
                strategy = strategy,
                promptId = promptId,
                systemPrompt = systemPrompt,
                toolRegistry = toolRegistry,
                promptExecutor = mockExecutor,
                model = model,
                clock = testClock,
                temperature = temperature,
                maxTokens = maxTokens,
            ) {
                install(OpenTelemetry) {
                    addSpanExporter(mockExporter)
                    setVerbose(true)
                }
            }.use { agent ->
                agent.run(userPrompt)
            }

            val collectedSpans = mockExporter.collectedSpans
            assertTrue(collectedSpans.isNotEmpty(), "Spans should be created during agent execution")

            val actualInferenceSpans = collectedSpans.filter { span -> span.name.startsWith("llm.") }
            assertEquals(2, actualInferenceSpans.size)

            // Check Spans

            val expectedInferenceSpans = listOf(
                mapOf(
                    "llm.$promptId" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.system" to model.provider.id,
                            "gen_ai.request.model" to model.id,
                            "gen_ai.request.max_tokens" to maxTokens.toLong(),
                            "gen_ai.conversation.id" to mockExporter.lastRunId,
                            "gen_ai.operation.name" to "chat",
                            "gen_ai.request.temperature" to temperature,
                            "gen_ai.response.finish_reasons" to listOf(FinishReasonType.Stop.id),
                            "gen_ai.usage.output_tokens" to tokenizer.countTokens(text = mockResponse).toLong()
                        ),
                        "events" to mapOf(
                            "gen_ai.system.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.System.name.lowercase(),
                                "content" to systemPrompt,
                            ),
                            "gen_ai.user.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.User.name.lowercase(),
                                "content" to userPrompt,
                            ),
                            "gen_ai.choice" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.Tool.name.lowercase(),
                                "tool_calls" to """[{"function":{"name":"${TestGetWeatherTool.name}","arguments":"{\"location\":\"Paris\"}"},"id":"$toolCallId","type":"function"}]""",
                                "finish_reason" to FinishReasonType.ToolCalls.id,
                            ),
                            "gen_ai.tool.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.Tool.name.lowercase(),
                                "content" to TestGetWeatherTool.DEFAULT_PARIS_RESULT,
                                "id" to toolCallId,
                            ),
                            "gen_ai.assistant.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.Assistant.name.lowercase(),
                                "content" to mockResponse,
                            ),
                        )
                    )
                ),
                mapOf(
                    "llm.$promptId" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.system" to model.provider.id,
                            "gen_ai.request.model" to model.id,
                            "gen_ai.request.max_tokens" to maxTokens.toLong(),
                            "gen_ai.conversation.id" to mockExporter.lastRunId,
                            "gen_ai.operation.name" to "chat",
                            "gen_ai.request.temperature" to temperature,
                            "gen_ai.response.finish_reasons" to listOf(FinishReasonType.ToolCalls.id),
                            "gen_ai.usage.output_tokens" to tokenizer.countTokens(
                                text = TestGetWeatherTool.encodeArgsToString(TestGetWeatherTool.Args("Paris"))
                            ).toLong(),
                        ),
                        "events" to mapOf(
                            "gen_ai.system.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.System.name.lowercase(),
                                "content" to systemPrompt,
                            ),
                            "gen_ai.user.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "role" to Message.Role.User.name.lowercase(),
                                "content" to userPrompt,
                            ),
                            "gen_ai.choice" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "index" to 0L,
                                "role" to Message.Role.Tool.name.lowercase(),
                                "tool_calls" to """[{"function":{"name":"${TestGetWeatherTool.name}","arguments":"{\"location\":\"Paris\"}"},"id":"$toolCallId","type":"function"}]""",
                                "finish_reason" to FinishReasonType.ToolCalls.id,
                            ),
                        )
                    )
                ),
            )

            assertSpans(expectedInferenceSpans, actualInferenceSpans)
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

    private fun createCustomSdk(exporter: SpanExporter): OpenTelemetrySdk {
        val builder = OpenTelemetrySdk.builder()

        val traceProviderBuilder = SdkTracerProvider
            .builder()
            .addSpanProcessor(SimpleSpanProcessor.builder(exporter).build())

        val sdk = builder
            .setTracerProvider(traceProviderBuilder.build())
            .build()

        return sdk
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
        logger.debug {
            "Asserting events for the Span (name: $spanName).\nExpected events:\n$expectedEvents\nActual events:\n$actualEvents"
        }

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
        logger.debug {
            "Asserting attributes for the Span (name: $spanName).\nExpected attributes:\n$expectedAttributes\nActual attributes:\n$actualAttributes"
        }

        assertEquals(
            expectedAttributes.size,
            actualAttributes.size,
            "Expected collection of attributes should be the same size for the span (name: $spanName)\n" +
                "Expected: <${expectedAttributes.toList().joinToString(
                    prefix = "\n{\n",
                    postfix = "\n}",
                    separator = "\n"
                ) { pair ->
                    "  ${pair.first}=${pair.second}"
                }}>,\n" +
                "Actual: <${actualAttributes.toList().joinToString(
                    prefix = "\n{\n",
                    postfix = "\n}",
                    separator = "\n"
                ) { pair ->
                    "  ${pair.first}=${pair.second}"
                }}>"
        )

        actualAttributes.forEach { (actualArgName: String, actualArgValue: Any) ->

            logger.debug { "Find expected attribute (name: $actualArgName) for the Span (name: $spanName)" }
            val expectedArgValue = expectedAttributes[actualArgName]

            assertNotNull(
                expectedArgValue,
                "Attribute (name: $actualArgName) not found in expected attributes for span (name: $spanName)"
            )

            when (actualArgValue) {
                is Map<*, *> -> {
                    assertMapsEqual(expectedArgValue as Map<*, *>, actualArgValue)
                }

                is Iterable<*> -> {
                    assertContentEquals(expectedArgValue as Iterable<*>, actualArgValue.asIterable())
                }

                else -> {
                    assertEquals(
                        expectedArgValue,
                        actualArgValue,
                        "Attribute values should be the same for the span (name: $spanName)\n" +
                            "Expected: <$expectedArgValue>,\n" +
                            "Actual: <$actualArgValue>"
                    )
                }
            }
        }
    }

    //endregion Private Methods
}
