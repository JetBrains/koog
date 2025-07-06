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
                            "koog.agent.strategy" to "test-strategy",
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
                                "content" to userPrompt
                            )
                        ),

                        "events" to mapOf(
                            "gen_ai.user.message" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "content" to userPrompt
                            ),
                            "gen_ai.choice" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "message" to mapOf(
                                    "content" to mockResponse,
                                ),
                                "index" to 0
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
                            "koog.agent.strategy" to "test-strategy",
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
                                "content" to userPrompt1
                            ),
                            "gen_ai.choice" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "message" to mapOf(
                                    "content" to mockResponse1,
                                ),
                                "index" to 0
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
                            "koog.agent.strategy" to "test-strategy",
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
                                "content" to userPrompt0
                            ),
                            "gen_ai.choice" to mapOf(
                                "gen_ai.system" to model.provider.id,
                                "message" to mapOf(
                                    "content" to mockResponse0,
                                ),
                                "index" to 0
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
                        "events" to mapOf(
                        )
                    )
                ),
                mapOf(
                    "run.${mockExporter.lastRunId}" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.system" to model.provider.id,
                            "gen_ai.agent.id" to agentId,
                            "gen_ai.conversation.id" to mockExporter.lastRunId,
                            "gen_ai.operation.name" to "invoke_agent",
                            "koog.agent.strategy" to "test-strategy",
                        ),
                        "events" to mapOf(
                        )
                    )
                ),
                mapOf(
                    "node.test-node-llm-send-tool-result" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.conversation.id" to mockExporter.lastRunId,
                            "koog.node.name" to "test-node-llm-send-tool-result",
                        ),
                        "events" to mapOf(
                        )
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
                                "message" to mapOf(
                                    "content" to mockResponse,
                                ),
                                "index" to 0
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
                        "events" to mapOf(
                        )
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
                                "content" to "rainy, 57°F" // Mocked return result defined in the Tool
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
                        "events" to mapOf(
                        )
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
                                "content" to userPrompt
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
                        "events" to mapOf(
                        )
                    )
                ),
            )

            assertSpans(expectedSpans, collectedSpans)
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
        assertEquals(expectedSpanNames.size, actualSpanNames.size, "Expected collection of spans should be the same size")
        assertContentEquals(expectedSpanNames, actualSpanNames, "Expected collection of spans should be the same as actual")
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
            assertNotNull(expectedEventAttributes, "Event (name: $actualEventName) not found in expected events for span (name: $spanName)")

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

            assertNotNull(expectedArgValue, "Attribute (name: $actualArgName) not found in expected attributes for span (name: $spanName)")
            assertEquals(expectedArgValue, actualArgValue, "Attribute values should be the same for the span (name: $spanName)()")
        }
    }

    //endregion Private Methods
}
