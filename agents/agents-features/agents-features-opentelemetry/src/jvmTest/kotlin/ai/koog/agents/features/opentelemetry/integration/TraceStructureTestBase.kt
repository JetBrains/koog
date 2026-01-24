package ai.koog.agents.features.opentelemetry.integration

import ai.koog.agents.core.agent.context.DetachedPromptExecutorAPI
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStructured
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.asTool
import ai.koog.agents.core.tools.reflect.tool
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.assistantMessage
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.createAgent
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.getMessagesString
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.getSystemInstructionsString
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.testClock
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestData
import ai.koog.agents.features.opentelemetry.assertSpans
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes.Response.FinishReasonType
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig
import ai.koog.agents.features.opentelemetry.mock.MockSpanExporter
import ai.koog.agents.features.opentelemetry.mock.TestGetWeatherTool
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMToolCall
import ai.koog.prompt.dsl.ModerationCategory
import ai.koog.prompt.dsl.ModerationCategoryResult
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.tokenizer.SimpleRegexBasedTokenizer
import ai.koog.utils.io.use
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

abstract class TraceStructureTestBase(private val openTelemetryConfigurator: OpenTelemetryConfig.() -> Unit) {
    private val json = Json { allowStructuredMapKeys = true }

    @Test
    fun testSingleLLMCall() = runBlocking {
        MockSpanExporter().use { mockSpanExporter ->

            val strategy = strategy("single-llm-call-strategy") {
                val llmRequest by nodeLLMRequest("llm-call")

                edge(nodeStart forwardTo llmRequest)
                edge(llmRequest forwardTo nodeFinish onAssistantMessage { true })
            }

            val model = OpenAIModels.Chat.GPT4o
            val temperature = 0.4
            val systemPrompt = "You are the application that predicts weather"
            val userPrompt = "What's the weather in Paris?"
            val mockResponse = "The weather in Paris is rainy and overcast, with temperatures around 57°F"

            val promptExecutor = getMockExecutor {
                mockLLMAnswer(mockResponse) onRequestEquals userPrompt
            }

            runAgentWithStrategy(
                strategy = strategy,
                promptExecutor = promptExecutor,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                model = model,
                temperature = temperature,
                spanExporter = mockSpanExporter,
            )

            val testData = createTestData(mockSpanExporter)

            // Filter only LLM generation spans
            val actualLLMSpans = testData.filterInferenceSpans()
            assertTrue(actualLLMSpans.isNotEmpty(), "LLM spans should be created during agent execution")

            // Get event ID from actual span
            val llmCallEventId = testData.singleAttributeValue(actualLLMSpans.single(), "koog.event.id")

            // Assert expected LLM Call Span (InferenceSpan) attributes for Langfuse/Weave
            val expectedLLMSpans = listOf(
                mapOf(
                    "${SpanAttributes.Operation.OperationNameType.CHAT.id} ${model.id}" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.operation.name" to "chat",
                            "gen_ai.provider.name" to model.provider.id,
                            "gen_ai.conversation.id" to mockSpanExporter.lastRunId,
                            "gen_ai.output.type" to "text",
                            "gen_ai.request.model" to model.id,
                            "gen_ai.request.temperature" to temperature,
                            "gen_ai.response.finish_reasons" to listOf(FinishReasonType.Stop.id),
                            "gen_ai.response.model" to model.id,
                            "gen_ai.usage.input_tokens" to 0L,
                            "gen_ai.usage.output_tokens" to 0L,
                            "koog.event.id" to llmCallEventId,
                            "gen_ai.input.messages" to getMessagesString(
                                listOf(
                                    Message.System(systemPrompt, RequestMetaInfo(testClock.now())),
                                    Message.User(userPrompt, RequestMetaInfo(testClock.now())),
                                )
                            ),
                            "system_instructions" to getSystemInstructionsString(listOf(systemPrompt)),
                            "gen_ai.output.messages" to getMessagesString(listOf(assistantMessage(mockResponse))),

                            // Langfuse/Weave specific attributes
                            "gen_ai.prompt.0.role" to "system",
                            "gen_ai.prompt.0.content" to systemPrompt,
                            "gen_ai.prompt.1.role" to "user",
                            "gen_ai.prompt.1.content" to userPrompt,
                            "gen_ai.completion.0.role" to "assistant",
                            "gen_ai.completion.0.content" to mockResponse,
                        ),
                        "events" to emptyMap()
                    )
                )
            )

            assertSpans(expectedLLMSpans, actualLLMSpans)
        }
    }

    abstract fun testLLMCallToolCallLLMCallGetExpectedInitialLLMCallSpanAttributes(
        model: LLModel,
        temperature: Double,
        systemPrompt: String,
        userPrompt: String,
        runId: String,
        toolCallId: String
    ): Map<String, Any>

    abstract fun testLLMCallToolCallLLMCallGetExpectedFinalLLMCallSpansAttributes(
        model: LLModel,
        temperature: Double,
        systemPrompt: String,
        userPrompt: String,
        runId: String,
        toolCallId: String,
        toolResponse: String,
        finalResponse: String
    ): Map<String, Any>

    @Test
    fun testLLMCallToolCallLLMCall() = runBlocking {
        MockSpanExporter().use { mockSpanExporter ->
            val strategy = strategy("llm-tool-llm-strategy") {
                val llmRequest by nodeLLMRequest("LLM Request", allowToolCalls = true)
                val executeTool by nodeExecuteTool("Execute Tool")
                val sendToolResult by nodeLLMSendToolResult("Send Tool Result")

                edge(nodeStart forwardTo llmRequest)
                edge(llmRequest forwardTo executeTool onToolCall { true })
                edge(executeTool forwardTo sendToolResult)
                edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
            }

            val model = OpenAIModels.Chat.GPT4o
            val temperature = 0.4
            val systemPrompt = "You are the application that predicts weather"
            val userPrompt = "What's the weather in Paris?"
            val toolCallArgs = TestGetWeatherTool.Args("Paris")
            val toolResponse = TestGetWeatherTool.DEFAULT_PARIS_RESULT
            val finalResponse = "The weather in Paris is rainy and overcast, with temperatures around 57°F"

            val toolCallId = "get-weather-tool-call-id"

            val mockExecutor = getMockExecutor {
                mockLLMToolCall(
                    tool = TestGetWeatherTool,
                    args = toolCallArgs,
                    toolCallId = toolCallId
                ) onRequestEquals userPrompt
                mockLLMAnswer(response = finalResponse) onRequestContains toolResponse
            }

            val toolRegistry = ToolRegistry {
                tool(TestGetWeatherTool)
            }

            runAgentWithStrategy(
                strategy = strategy,
                promptExecutor = mockExecutor,
                toolRegistry = toolRegistry,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                model = model,
                temperature = temperature,
                spanExporter = mockSpanExporter
            )

            val testData = createTestData(mockSpanExporter)

            // Filter LLM spans
            val actualLLMSpans = testData.filterInferenceSpans()
            assertEquals(2, actualLLMSpans.size, "Should have 2 LLM calls")

            // Filter tool execution spans
            val actualToolSpans = testData.filterExecuteToolSpans()
            assertEquals(1, actualToolSpans.size, "Should have 1 tool execution")

            // Extract event IDs from actual spans
            val initialLLMEventId = testData.singleAttributeValue(actualLLMSpans[0], "koog.event.id")
            val finalLLMEventId = testData.singleAttributeValue(actualLLMSpans[1], "koog.event.id")

            // Build expected spans using abstract methods
            val expectedInitialLLMSpanAttributes = testLLMCallToolCallLLMCallGetExpectedInitialLLMCallSpanAttributes(
                model = model,
                temperature = temperature,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                runId = mockSpanExporter.lastRunId,
                toolCallId = toolCallId,
            ).plus(
                mapOf(
                    "koog.event.id" to initialLLMEventId
                )
            )

            val expectedFinalLLMSpanAttributes = testLLMCallToolCallLLMCallGetExpectedFinalLLMCallSpansAttributes(
                model = model,
                temperature = temperature,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                runId = mockSpanExporter.lastRunId,
                toolCallId = toolCallId,
                toolResponse = toolResponse,
                finalResponse = finalResponse,
            ).plus(
                mapOf(
                    "koog.event.id" to finalLLMEventId
                )
            )

            val expectedLLMSpans = listOf(
                mapOf(
                    "${SpanAttributes.Operation.OperationNameType.CHAT.id} ${model.id}" to mapOf(
                        "attributes" to expectedInitialLLMSpanAttributes,
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    "${SpanAttributes.Operation.OperationNameType.CHAT.id} ${model.id}" to mapOf(
                        "attributes" to expectedFinalLLMSpanAttributes,
                        "events" to emptyMap()
                    )
                )
            )

            assertSpans(expectedLLMSpans, actualLLMSpans)

            // Assert tool execution span
            val toolEventId = testData.singleAttributeValue(actualToolSpans.single(), "koog.event.id")
            val expectedToolSpans = listOf(
                mapOf(
                    "${SpanAttributes.Operation.OperationNameType.EXECUTE_TOOL.id} ${TestGetWeatherTool.name}" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.operation.name" to "execute_tool",
                            "gen_ai.tool.name" to TestGetWeatherTool.name,
                            "gen_ai.tool.description" to TestGetWeatherTool.descriptor.description,
                            "gen_ai.tool.call.id" to toolCallId,
                            "gen_ai.tool.call.arguments" to "{\"location\":\"Paris\"}",
                            "gen_ai.tool.call.result" to "\"${TestGetWeatherTool.DEFAULT_PARIS_RESULT}\"",
                            "koog.event.id" to toolEventId,
                        ),
                        "events" to emptyMap()
                    )
                )
            )

            assertSpans(expectedToolSpans, actualToolSpans)
        }
    }

    @Test
    fun testMultipleToolCalls() = runBlocking {
        MockSpanExporter().use { mockSpanExporter ->
            val strategy = strategy("multiple-tool-calls-strategy") {
                val llmRequest by nodeLLMRequest("Initial LLM Request", allowToolCalls = true)
                val executeTool1 by nodeExecuteTool("Execute Tool 1")
                val sendToolResult1 by nodeLLMSendToolResult("Send Tool Result 1")
                val executeTool2 by nodeExecuteTool("Execute Tool 2")
                val sendToolResult2 by nodeLLMSendToolResult("Send Tool Result 2")

                edge(nodeStart forwardTo llmRequest)
                edge(llmRequest forwardTo executeTool1 onToolCall { true })
                edge(executeTool1 forwardTo sendToolResult1)
                edge(sendToolResult1 forwardTo executeTool2 onToolCall { true })
                edge(sendToolResult1 forwardTo nodeFinish onAssistantMessage { true })
                edge(executeTool2 forwardTo sendToolResult2)
                edge(sendToolResult2 forwardTo nodeFinish transformed { input -> input.content })
            }

            val model = OpenAIModels.Chat.GPT4o
            val temperature = 0.4

            val systemPrompt = "You are the application that predicts weather"
            val userPrompt = "What's the weather in Paris and London?"

            val toolCallArgs1 = TestGetWeatherTool.Args("Paris")
            val toolResponse1 = TestGetWeatherTool.DEFAULT_PARIS_RESULT

            val toolCallArgs2 = TestGetWeatherTool.Args("London")
            val toolResponse2 = TestGetWeatherTool.DEFAULT_LONDON_RESULT

            val finalResponse = "The weather in Paris is rainy (57°F) and in London it's cloudy (62°F)"

            val mockExecutor = getMockExecutor {
                mockLLMToolCall(tool = TestGetWeatherTool, args = toolCallArgs1) onRequestEquals userPrompt
                mockLLMToolCall(tool = TestGetWeatherTool, args = toolCallArgs2) onRequestContains toolResponse1
                mockLLMAnswer(finalResponse) onRequestContains toolResponse2
            }

            val toolRegistry = ToolRegistry {
                tool(TestGetWeatherTool)
            }

            runAgentWithStrategy(
                strategy = strategy,
                promptExecutor = mockExecutor,
                toolRegistry = toolRegistry,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                model = model,
                temperature = temperature,
                spanExporter = mockSpanExporter
            )

            val testData = createTestData(mockSpanExporter)

            // Filter tool execution spans
            val actualToolSpans = testData.filterExecuteToolSpans()
            assertEquals(2, actualToolSpans.size, "Should have 2 tool executions")

            // Get event IDs from actual spans
            val toolEventId1 = testData.singleAttributeValue(actualToolSpans[0], "koog.event.id")
            val toolEventId2 = testData.singleAttributeValue(actualToolSpans[1], "koog.event.id")

            // Assert tool execution spans
            val expectedToolSpans = listOf(
                mapOf(
                    "${SpanAttributes.Operation.OperationNameType.EXECUTE_TOOL.id} ${TestGetWeatherTool.name}" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.operation.name" to "execute_tool",
                            "gen_ai.tool.name" to TestGetWeatherTool.name,
                            "gen_ai.tool.description" to TestGetWeatherTool.descriptor.description,
                            "gen_ai.tool.call.arguments" to "{\"location\":\"Paris\"}",
                            "gen_ai.tool.call.result" to "\"$toolResponse1\"",
                            "koog.event.id" to toolEventId1,
                        ),
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    "${SpanAttributes.Operation.OperationNameType.EXECUTE_TOOL.id} ${TestGetWeatherTool.name}" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.operation.name" to "execute_tool",
                            "gen_ai.tool.name" to TestGetWeatherTool.name,
                            "gen_ai.tool.description" to TestGetWeatherTool.descriptor.description,
                            "gen_ai.tool.call.arguments" to "{\"location\":\"London\"}",
                            "gen_ai.tool.call.result" to "\"$toolResponse2\"",
                            "koog.event.id" to toolEventId2,
                        ),
                        "events" to emptyMap()
                    )
                )
            )

            assertSpans(expectedToolSpans, actualToolSpans)
        }
    }

    @Tool
    @LLMDescription("Provides final result of the task")
    fun subgraphFinish(result: String): String = result

    @Test
    fun testSubgraphWithFinishTool() = runBlocking {
        MockSpanExporter().use { mockSpanExporter ->
            val finishTool = ::subgraphFinish.asTool()

            val strategy = strategy("subgraph-finish-tool-strategy") {
                val sg by subgraphWithTask<String, String>(
                    tools = listOf(finishTool),
                    finishToolFunction = ::subgraphFinish
                ) { input ->
                    "Please finish the task by calling the finish tool with the final result for: $input"
                }

                nodeStart then sg then nodeFinish
            }

            val model = OpenAIModels.Chat.GPT4o
            val temperature = 0.3
            val systemPrompt = "You orchestrate a subtask."
            val userPrompt = "Summarize: test subgraph"
            val finalString = "Task done for: test subgraph"

            val mockExecutor = getMockExecutor {
                mockLLMToolCall(::subgraphFinish, finalString) onRequestContains "Please finish the task"
            }

            val toolRegistry = ToolRegistry {
                tool(::subgraphFinish)
            }

            runAgentWithStrategy(
                strategy = strategy,
                promptExecutor = mockExecutor,
                toolRegistry = toolRegistry,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                model = model,
                temperature = temperature,
                spanExporter = mockSpanExporter
            )

            val actualSpans = mockSpanExporter.collectedSpans

            // Verify that basic spans are present
            assertTrue { actualSpans.count { it.name == "${SpanAttributes.Operation.OperationNameType.CHAT.id} ${model.id}" } == 1 }
            assertTrue { actualSpans.any { it.name == "subgraph sg" } }
            assertTrue { actualSpans.any { it.name == "strategy subgraph-finish-tool-strategy" } }
        }
    }

    @Test
    fun `test adapter customizes spans after creation`() = runBlocking {
        MockSpanExporter().use { mockExporter ->
            val systemPrompt = "You are the application that predicts weather"
            val userPrompt = "What's the weather in Paris?"
            val mockResponse = "The weather in Paris is rainy and overcast, with temperatures around 58°F"

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
                mockLLMAnswer(mockResponse) onRequestEquals userPrompt
            }

            val agent = createAgent(
                agentId = agentId,
                strategy = strategy,
                promptId = promptId,
                systemPrompt = systemPrompt,
                executor = mockExecutor,
                model = model,
                temperature = temperature,
            ) {
                install(OpenTelemetry) {
                    addSpanExporter(mockExporter)
                    setVerbose(true)
                    openTelemetryConfigurator()
                    addSpanAdapter(object : SpanAdapter() {
                        override fun onBeforeSpanStarted(span: GenAIAgentSpan) {
                            span.addAttribute(CustomAttribute("custom.after.start", "value-start"))
                        }

                        override fun onBeforeSpanFinished(span: GenAIAgentSpan) {
                            span.addAttribute(CustomAttribute("custom.before.finish", 123))
                        }
                    })
                }
            }

            agent.run(userPrompt)

            val spans = mockExporter.collectedSpans
            assertTrue(spans.isNotEmpty(), "Spans should be created during agent execution")
            agent.close()

            val nodeSpan = spans.first { it.name == "node test-llm-call" }
            val nodeAttrs = nodeSpan.attributes.asMap().asSequence().associate { it.key.key to it.value }
            assertEquals("value-start", nodeAttrs["custom.after.start"])
            val llmSpan = spans.first { it.name == "${SpanAttributes.Operation.OperationNameType.CHAT.id} ${model.id}" }
            val llmAttrs = llmSpan.attributes.asMap().asSequence().associate { it.key.key to it.value }

            assertEquals(123L, llmAttrs["custom.before.finish"])

            val expectedLlmAttrs = mapOf(
                "gen_ai.provider.name" to model.provider.id,
                "gen_ai.conversation.id" to mockExporter.lastRunId,
                "gen_ai.operation.name" to "chat",
                "gen_ai.request.model" to model.id,
                "gen_ai.request.temperature" to temperature,
                "gen_ai.response.finish_reasons" to listOf(FinishReasonType.Stop.id),
            )

            expectedLlmAttrs.forEach { (k, v) ->
                assertTrue(llmAttrs.containsKey(k), "LLM span attributes should contain key: '$k'")
                assertEquals(v, llmAttrs[k], "LLM span attribute '$k' should match expected value")
            }
        }
    }

    @Test
    fun testStructuredDataLLMCall() = runBlocking {
        MockSpanExporter().use { mockSpanExporter ->
            @Serializable
            @SerialName("SimpleWeatherForecast")
            @LLMDescription("Simple weather forecast for a location")
            data class SimpleWeatherForecast(
                @property:LLMDescription("Location name")
                val location: String,
                @property:LLMDescription("Temperature in Celsius")
                val temperature: Int,
                @property:LLMDescription("Weather conditions (e.g., sunny, cloudy, rainy)")
                val conditions: String
            )

            val strategy = strategy<String, String>("structured-llm-call-strategy") {
                val llmStructured by nodeLLMRequestStructured<SimpleWeatherForecast>(
                    name = "llm-structured",
                )

                edge(nodeStart forwardTo llmStructured)
                edge(
                    llmStructured forwardTo nodeFinish transformed { result ->
                        result.getOrThrow().message.content
                    }
                )
            }

            val model = OpenAIModels.Chat.GPT4o
            val temperature = 0.2
            val systemPrompt = "You are the application that predicts weather"
            val userPrompt = "Give a simple forecast for Helsinki"
            val mockAssistantText = "{" +
                "\"location\":\"Helsinki\"," +
                "\"temperature\":20," +
                "\"conditions\":\"Cloudy\"}"

            val promptExecutor = getMockExecutor {
                mockLLMAnswer(mockAssistantText) onRequestContains "Helsinki"
            }

            runAgentWithStrategy(
                strategy = strategy,
                promptExecutor = promptExecutor,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                model = model,
                temperature = temperature,
                spanExporter = mockSpanExporter,
            )

            val testData = createTestData(mockSpanExporter)

            // Filter only LLM generation spans
            val actualLLMSpans = testData.filterInferenceSpans()
            assertTrue(actualLLMSpans.isNotEmpty(), "LLM spans should be created during agent execution")

            // Get event ID from actual span
            val llmCallEventId = testData.singleAttributeValue(actualLLMSpans.single(), "koog.event.id")

            // Assert expected LLM Call Span attributes for structured output
            val expectedLLMSpans = listOf(
                mapOf(
                    "${SpanAttributes.Operation.OperationNameType.CHAT.id} ${model.id}" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.operation.name" to "chat",
                            "gen_ai.provider.name" to model.provider.id,
                            "gen_ai.conversation.id" to mockSpanExporter.lastRunId,
                            "gen_ai.output.type" to "json",
                            "gen_ai.request.model" to model.id,
                            "gen_ai.request.temperature" to temperature,
                            "gen_ai.response.finish_reasons" to listOf(FinishReasonType.Stop.id),
                            "gen_ai.response.model" to model.id,
                            "gen_ai.usage.input_tokens" to 0L,
                            "gen_ai.usage.output_tokens" to 0L,
                            "koog.event.id" to llmCallEventId,
                            "gen_ai.input.messages" to getMessagesString(
                                listOf(
                                    Message.System(systemPrompt, RequestMetaInfo(testClock.now())),
                                    Message.User(userPrompt, RequestMetaInfo(testClock.now())),
                                )
                            ),
                            "system_instructions" to getSystemInstructionsString(listOf(systemPrompt)),
                            "gen_ai.output.messages" to getMessagesString(
                                listOf(
                                    assistantMessage(mockAssistantText)
                                )
                            ),
                            "gen_ai.prompt.0.role" to Message.Role.System.name.lowercase(),
                            "gen_ai.prompt.0.content" to systemPrompt,
                            "gen_ai.prompt.1.role" to Message.Role.User.name.lowercase(),
                            "gen_ai.prompt.1.content" to userPrompt,
                            "gen_ai.completion.0.role" to Message.Role.Assistant.name.lowercase(),
                            "gen_ai.completion.0.content" to mockAssistantText,
                        ),
                        "events" to emptyMap()
                    )
                )
            )

            assertSpans(expectedLLMSpans, actualLLMSpans)
        }
    }

    abstract fun testTokensCountAttributesGetExpectedInitialLLMCallSpanAttributes(
        model: LLModel,
        temperature: Double,
        maxTokens: Long,
        systemPrompt: String,
        userPrompt: String,
        runId: String,
        toolCallId: String,
        outputTokens: Long
    ): Map<String, Any>

    abstract fun testTokensCountAttributesGetExpectedFinalLLMCallSpansAttributes(
        model: LLModel,
        temperature: Double,
        maxTokens: Long,
        systemPrompt: String,
        userPrompt: String,
        runId: String,
        toolCallId: String,
        toolResponse: String,
        finalResponse: String,
        outputTokens: Long
    ): Map<String, Any>

    @Test
    fun testTokensCountAttributes() = runBlocking {
        MockSpanExporter().use { mockSpanExporter ->
            val strategy = strategy("llm-tool-llm-strategy") {
                val llmRequest by nodeLLMRequest("LLM Request", allowToolCalls = true)
                val executeTool by nodeExecuteTool("Execute Tool")
                val sendToolResult by nodeLLMSendToolResult("Send Tool Result")

                edge(nodeStart forwardTo llmRequest)
                edge(llmRequest forwardTo executeTool onToolCall { true })
                edge(executeTool forwardTo sendToolResult)
                edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
            }

            val model = OpenAIModels.Chat.GPT4o
            val temperature = 0.4
            val maxTokens = 123
            val systemPrompt = "You are the application that predicts weather"
            val userPrompt = "What's the weather in Paris?"
            val toolCallArgs = TestGetWeatherTool.Args("Paris")
            val toolResponse = TestGetWeatherTool.DEFAULT_PARIS_RESULT
            val finalResponse = "The weather in Paris is rainy and overcast, with temperatures around 57°F"

            val toolCallId = "get-weather-tool-call-id"
            val tokenizer = SimpleRegexBasedTokenizer()

            // Set tokenizer explicitly to calculate output tokens and return the value in responses
            val mockExecutor = getMockExecutor(tokenizer = tokenizer) {
                mockLLMToolCall(
                    tool = TestGetWeatherTool,
                    args = toolCallArgs,
                    toolCallId = toolCallId
                ) onRequestEquals userPrompt
                mockLLMAnswer(response = finalResponse) onRequestContains toolResponse
            }

            val toolRegistry = ToolRegistry {
                tool(TestGetWeatherTool)
            }

            runAgentWithStrategy(
                strategy = strategy,
                promptExecutor = mockExecutor,
                toolRegistry = toolRegistry,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                model = model,
                temperature = temperature,
                maxTokens = maxTokens,
                spanExporter = mockSpanExporter
            )

            val testData = createTestData(mockSpanExporter)

            // Filter LLM spans
            val actualLLMSpans = testData.filterInferenceSpans()
            assertEquals(2, actualLLMSpans.size, "Should have 2 LLM calls")

            // Extract event IDs and token counts from actual spans
            val initialLLMEventId = testData.singleAttributeValue(actualLLMSpans[0], "koog.event.id")
            val finalLLMEventId = testData.singleAttributeValue(actualLLMSpans[1], "koog.event.id")

            // Get actual token counts from spans
            val inputTokens0 = getAttributeValue<Long>(actualLLMSpans[0], "gen_ai.usage.input_tokens") ?: 0L
            val inputTokens1 = getAttributeValue<Long>(actualLLMSpans[1], "gen_ai.usage.input_tokens") ?: 0L

            // Build expected spans using abstract methods
            val expectedInitialLLMSpanAttributes =
                testTokensCountAttributesGetExpectedInitialLLMCallSpanAttributes(
                    model = model,
                    temperature = temperature,
                    maxTokens = maxTokens.toLong(),
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    runId = mockSpanExporter.lastRunId,
                    toolCallId = toolCallId,
                    outputTokens = tokenizer.countTokens(
                        text = TestGetWeatherTool.encodeArgsToString(TestGetWeatherTool.Args("Paris"))
                    ).toLong()
                ).plus(
                    mapOf(
                        "gen_ai.usage.input_tokens" to inputTokens0,
                        "koog.event.id" to initialLLMEventId,
                    )
                )

            val expectedFinalLLMSpanAttributes =
                testTokensCountAttributesGetExpectedFinalLLMCallSpansAttributes(
                    model = model,
                    temperature = temperature,
                    maxTokens = maxTokens.toLong(),
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    runId = mockSpanExporter.lastRunId,
                    toolCallId = toolCallId,
                    toolResponse = toolResponse,
                    finalResponse = finalResponse,
                    outputTokens = tokenizer.countTokens(text = finalResponse).toLong(),
                ).plus(
                    mapOf(
                        "gen_ai.usage.input_tokens" to inputTokens1,
                        "koog.event.id" to finalLLMEventId,
                    )
                )

            val expectedLLMSpans = listOf(
                mapOf(
                    "${SpanAttributes.Operation.OperationNameType.CHAT.id} ${model.id}" to mapOf(
                        "attributes" to expectedInitialLLMSpanAttributes,
                        "events" to emptyMap()
                    )
                ),
                mapOf(
                    "${SpanAttributes.Operation.OperationNameType.CHAT.id} ${model.id}" to mapOf(
                        "attributes" to expectedFinalLLMSpanAttributes,
                        "events" to emptyMap()
                    )
                )
            )

            assertSpans(expectedLLMSpans, actualLLMSpans)
        }
    }

    @Ignore("KG-288")
    @OptIn(DetachedPromptExecutorAPI::class)
    @Test
    fun testContentModerationEventOnLLMSpan() = runBlocking {
        MockSpanExporter().use { mockSpanExporter ->
            val strategy = strategy<String, String>("moderation-strategy") {
                val moderate by node<String, String>("moderate-message") { input ->
                    llm.writeSession {
                        val moderationPrompt = prompt("single-message-moderation") {
                            message(Message.User(input, RequestMetaInfo.create(Clock.System)))
                        }
                        llm.promptExecutor.moderate(moderationPrompt, OpenAIModels.Moderation.Omni)
                    }
                    input
                }
                edge(nodeStart forwardTo moderate)
                edge(moderate forwardTo nodeFinish transformed { it })
            }

            val systemPrompt = "You are a safe assistant"
            val userPrompt = "I want to build a bomb"
            val model = OpenAIModels.Chat.GPT4o

            val moderationResult = ModerationResult(
                isHarmful = true,
                categories = mapOf(
                    ModerationCategory.Illicit to ModerationCategoryResult(
                        detected = true,
                        confidenceScore = 0.9998
                    ),
                    ModerationCategory.IllicitViolent to ModerationCategoryResult(
                        detected = true,
                        confidenceScore = 0.9876
                    ),
                )
            )

            val promptExecutor = getMockExecutor {
                addModerationResponseExactPattern(userPrompt, moderationResult)
            }

            runAgentWithStrategy(
                strategy = strategy,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                promptExecutor = promptExecutor,
                spanExporter = mockSpanExporter,
                model = model,
            )

            val spans = mockSpanExporter.collectedSpans
            assertTrue(spans.any { it.name == "node moderate-message" })

            val llmSpan = spans.firstOrNull { it.name == "${SpanAttributes.Operation.OperationNameType.CHAT.id} ${model.id}" }
                ?: spans.firstOrNull { span -> span.events.any { it.name == "moderation.result" } }
                ?: error("No LLM span for moderation found (expected '${SpanAttributes.Operation.OperationNameType.CHAT.id} ${model.id}' or a span with 'moderation.result' event)")

            val moderationEvent = llmSpan.events.firstOrNull { it.name == "moderation.result" }
            assertNotNull(moderationEvent, "LLM span should contain a moderation.result event")

            val eventAttrs = moderationEvent.attributes.asMap().map { (k, v) -> k.key to v }.toMap()

            val expectedContent = json.encodeToString(ModerationResult.serializer(), moderationResult)

            assertEquals(expectedContent, eventAttrs["content"])
            assertEquals(OpenAIModels.Moderation.Omni.provider.id, eventAttrs["gen_ai.system"])

            val llmAttrs = llmSpan.attributes.asMap().map { (k, v) -> k.key to v }.toMap()
            assertEquals(expectedContent, llmAttrs["gen_ai.completion.0.content"])
        }
    }

    @Ignore("KG-288")
    @Test
    fun testEmbeddingsTracingWithOpenAI() = runBlocking {
        MockSpanExporter().use { mockSpanExporter ->
            val model = OpenAIModels.Embeddings.TextEmbeddingAda002
            val openaiKey = System.getenv("OPENAI_API_KEY")

            val texts = listOf(
                "Langfuse helps you observe and evaluate your LLM apps.",
                "Embeddings map text to high-dimensional vectors for semantic search.",
            )

            val strategy = strategy("embeddings-tracing-strategy") {
                val embeddingsNode by node<String, String>("embeddings-call") { _ ->
                    val client = OpenAILLMClient(openaiKey)
                    val vectors: List<List<Double>> = texts.map { t -> client.embed(t, model) }
                    val dim = if (vectors.isNotEmpty()) vectors.first().size else 0
                    "model=$${model.id}; count=${vectors.size}; dim=$dim"
                }
                edge(nodeStart forwardTo embeddingsNode)
                edge(embeddingsNode forwardTo nodeFinish transformed { it })
            }

            runAgentWithStrategy(
                strategy = strategy,
                model = model,
                temperature = 0.0,
                spanExporter = mockSpanExporter,
                verbose = true,
            )

            val spans = mockSpanExporter.collectedSpans
            assertTrue(spans.any { it.name.startsWith("run.") })
            assertTrue(spans.any { it.name == "node __start__" })
            assertTrue(spans.any { it.name == "node embeddings-call" })

            val embeddingsSpan = spans.firstOrNull { span ->
                val attrs = span.attributes.asMap().asSequence().associate { it.key.key to it.value }
                attrs["gen_ai.operation.name"] == "embeddings"
            } ?: error("No embeddings span found (expected a span with gen_ai.operation.name = 'embeddings')")

            val attrs = embeddingsSpan.attributes.asMap().asSequence().associate { it.key.key to it.value }

            assertEquals(model.provider.id, attrs["gen_ai.system"], "gen_ai.system should match provider id")
            assertEquals("embeddings", attrs["gen_ai.operation.name"], "operation should be embeddings")
            assertEquals(model.id, attrs["gen_ai.request.model"], "model id should match embeddings model")
        }
    }

    //region Private Methods

    /**
     * Creates test data from the mock span exporter.
     */
    private fun createTestData(mockSpanExporter: MockSpanExporter): OpenTelemetryTestData {
        return OpenTelemetryTestData().apply {
            this.collectedSpans = mockSpanExporter.collectedSpans
        }
    }

    /**
     * Gets an attribute value from a span by key.
     */
    private inline fun <reified T> getAttributeValue(spanData: SpanData, key: String): T? {
        return spanData.attributes?.asMap()?.mapKeys { it.key.key }?.get(key) as? T
    }

    /**
     * Runs an agent with the given strategy and verifies the spans.
     */
    private suspend fun runAgentWithStrategy(
        strategy: AIAgentGraphStrategy<String, String>,
        systemPrompt: String? = null,
        userPrompt: String? = null,
        promptExecutor: PromptExecutor? = null,
        toolRegistry: ToolRegistry? = null,
        model: LLModel? = null,
        temperature: Double? = null,
        maxTokens: Int? = null,
        spanExporter: SpanExporter? = null,
        verbose: Boolean = true
    ) {
        val agentId = "test-agent-id"
        val promptId = "test-prompt-id"

        createAgent(
            agentId = agentId,
            strategy = strategy,
            promptId = promptId,
            executor = promptExecutor,
            model = model,
            temperature = temperature,
            maxTokens = maxTokens,
            systemPrompt = systemPrompt,
            toolRegistry = toolRegistry,
        ) {
            install(OpenTelemetry.Feature) {
                spanExporter?.let { exporter -> addSpanExporter(exporter) }
                setVerbose(verbose)
                openTelemetryConfigurator()
            }
        }.use { agent ->
            agent.run(userPrompt ?: "User prompt message")
        }
    }

    //endregion Private Methods
}
