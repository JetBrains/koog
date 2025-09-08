package ai.koog.agents.features.opentelemetry.integration

import ai.koog.agents.core.agent.context.DetachedPromptExecutorAPI
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStructured
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.ext.agent.ProvideStringSubgraphResult
import ai.koog.agents.ext.agent.StringSubgraphResult
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.assertMapsEqual
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.createAgent
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes.Response.FinishReasonType
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig
import ai.koog.agents.features.opentelemetry.mock.MockSpanExporter
import ai.koog.agents.features.opentelemetry.mock.TestGetWeatherTool
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.agents.utils.use
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
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

abstract class TraceStructureTestBase(private val openTelemetryConfigurator: OpenTelemetryConfig.() -> Unit) {
    private val json = Json { allowStructuredMapKeys = true }

    @Test
    fun testSingleLLMCall() = runTest {
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

            val actualSpans = mockSpanExporter.collectedSpans

            val spansRun = actualSpans.filter { it.name.startsWith("run.") }
            assertEquals(1, spansRun.size)

            val spansStartNode = actualSpans.filter { it.name == "node.__start__" }
            assertEquals(1, spansStartNode.size)

            val spansLLMCall = actualSpans.filter { it.name == "node.llm-call" }
            assertEquals(1, spansLLMCall.size)

            val spansLLMGeneration = actualSpans.filter { it.name == "llm.test-prompt-id" }
            assertEquals(1, spansLLMGeneration.size)

            val spanRunNode = spansRun.first()
            val spanStartNode = spansStartNode.first()
            val spanLLMNode = spansLLMCall.first()
            val spanLLMGeneration = spansLLMGeneration.first()

            assertEquals(spanStartNode.parentSpanId, spanRunNode.spanId)
            assertEquals(spanLLMNode.parentSpanId, spanRunNode.spanId)
            assertEquals(spanLLMGeneration.parentSpanId, spanLLMNode.spanId)

            val actualSpanAttributes = spanLLMGeneration.attributes.asMap()
                .map { (key, value) -> key.key to value }
                .toMap()

            // Assert expected LLM Call Span (IntentSpan) attributes for Langfuse/Weave

            val expectedAttributes = mapOf(
                // General attributes
                "gen_ai.system" to model.provider.id,
                "gen_ai.conversation.id" to mockSpanExporter.lastRunId,
                "gen_ai.operation.name" to "chat",
                "gen_ai.request.model" to model.id,
                "gen_ai.request.temperature" to 0.4,
                "gen_ai.response.finish_reasons" to listOf(FinishReasonType.Stop.id),

                // Langfuse/Weave specific attributes
                "gen_ai.prompt.0.role" to "system",
                "gen_ai.prompt.0.content" to systemPrompt,
                "gen_ai.prompt.1.role" to "user",
                "gen_ai.prompt.1.content" to userPrompt,
                "gen_ai.completion.0.role" to "assistant",
                "gen_ai.completion.0.content" to mockResponse,
            )

            assertEquals(expectedAttributes.size, actualSpanAttributes.size)
            assertMapsEqual(expectedAttributes, actualSpanAttributes)
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
    fun testLLMCallToolCallLLMCall() = runTest {
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

            // Assert collected spans
            val actualSpans = mockSpanExporter.collectedSpans

            val toolSpans = actualSpans.filter { it.name == "tool.Get whether" }
            assertEquals(1, toolSpans.size)

            val runNode = actualSpans.firstOrNull { it.name.startsWith("run.") }
            assertNotNull(runNode)

            val startNode = actualSpans.firstOrNull { it.name == "node.__start__" }
            assertNotNull(startNode)

            val llmRequestNode = actualSpans.firstOrNull { it.name == "node.LLM Request" }
            assertNotNull(llmRequestNode)

            val executeToolNode = actualSpans.firstOrNull { it.name == "node.Execute Tool" }
            assertNotNull(executeToolNode)

            val sendToolResultNode = actualSpans.firstOrNull { it.name == "node.Send Tool Result" }
            assertNotNull(sendToolResultNode)

            val toolCallSpan = actualSpans.firstOrNull { it.name == "tool.Get whether" }
            assertNotNull(toolCallSpan)

            // All nodes should have runNode as parent
            assertEquals(startNode.parentSpanId, runNode.spanId)
            assertEquals(llmRequestNode.parentSpanId, runNode.spanId)
            assertEquals(executeToolNode.parentSpanId, runNode.spanId)
            assertEquals(sendToolResultNode.parentSpanId, runNode.spanId)

            // Check LLM Call span with the initial call and tool call request
            val llmSpans = actualSpans.filter { it.name == "llm.test-prompt-id" }
            val actualInitialLLMCallSpan = llmSpans.firstOrNull { it.parentSpanId == llmRequestNode.spanId }
            assertNotNull(actualInitialLLMCallSpan)

            val actualInitialLLMCallSpanAttributes =
                actualInitialLLMCallSpan.attributes.asMap().map { (key, value) -> key.key to value }.toMap()

            val expectedInitialLLMCallSpansAttributes =
                testLLMCallToolCallLLMCallGetExpectedInitialLLMCallSpanAttributes(
                    model = model,
                    temperature = temperature,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    runId = mockSpanExporter.lastRunId,
                    toolCallId = toolCallId,
                )

            assertEquals(expectedInitialLLMCallSpansAttributes.size, actualInitialLLMCallSpanAttributes.size)
            assertMapsEqual(expectedInitialLLMCallSpansAttributes, actualInitialLLMCallSpanAttributes)

            // Check LLM Call span with the final LLM response after the tool is executed
            val actualFinalLLMCallSpan = llmSpans.firstOrNull { it.parentSpanId == sendToolResultNode.spanId }
            assertNotNull(actualFinalLLMCallSpan)

            val actualFinalLLMCallSpanAttributes =
                actualFinalLLMCallSpan.attributes.asMap().map { (key, value) -> key.key to value }.toMap()

            val expectedFinalLLMCallSpansAttributes = testLLMCallToolCallLLMCallGetExpectedFinalLLMCallSpansAttributes(
                model = model,
                temperature = temperature,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                runId = mockSpanExporter.lastRunId,
                toolCallId = toolCallId,
                toolResponse = toolResponse,
                finalResponse = finalResponse,
            )

            assertEquals(expectedFinalLLMCallSpansAttributes.size, actualFinalLLMCallSpanAttributes.size)
            assertMapsEqual(expectedFinalLLMCallSpansAttributes, actualFinalLLMCallSpanAttributes)

            // Tool span should have executed tool node as parent
            assertEquals(executeToolNode.spanId, toolCallSpan.parentSpanId)
            val actualToolCallSpanAttributes =
                toolCallSpan.attributes.asMap().map { (key, value) -> key.key to value }.toMap()

            val expectedToolCallSpanAttributes = mapOf(
                "gen_ai.tool.name" to TestGetWeatherTool.name,
                "gen_ai.tool.description" to TestGetWeatherTool.descriptor.description,
                "gen_ai.tool.call.id" to toolCallId,
                "input.value" to "{\"location\":\"Paris\"}",
                "output.value" to TestGetWeatherTool.DEFAULT_PARIS_RESULT,
            )

            assertEquals(expectedToolCallSpanAttributes.size, actualToolCallSpanAttributes.size)
            assertMapsEqual(expectedToolCallSpanAttributes, actualToolCallSpanAttributes)
        }
    }

    @Test
    fun testMultipleToolCalls() = runTest {
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

            val actualSpans = mockSpanExporter.collectedSpans

            // Execute Tool 1 Spans
            val executeTool1NodeSpan = actualSpans.first { it.name == "node.Execute Tool 1" }
            val executeTool1Span = actualSpans.firstOrNull { spanData ->
                spanData.name == "tool.${TestGetWeatherTool.name}" &&
                    spanData.parentSpanId == executeTool1NodeSpan.spanId
            }

            assertNotNull(executeTool1Span)

            // Execute Tool 2 Spans
            val executeTool2NodeSpan = actualSpans.first { it.name == "node.Execute Tool 2" }
            val executeTool2Span = actualSpans.firstOrNull { spanData ->
                spanData.name == "tool.${TestGetWeatherTool.name}" &&
                    spanData.spanId != executeTool2NodeSpan.spanId
            }

            assertNotNull(executeTool2Span)

            // Assert Execute Tool 1 Span
            val actualExecuteTool1SpanAttributes =
                executeTool1Span.attributes.asMap().map { (key, value) -> key.key to value }.toMap()

            val expectedExecuteTool1SpanAttributes = mapOf(
                "gen_ai.tool.name" to TestGetWeatherTool.name,
                "gen_ai.tool.description" to TestGetWeatherTool.descriptor.description,
                "input.value" to "{\"location\":\"Paris\"}",
                "output.value" to toolResponse1,
            )

            assertEquals(expectedExecuteTool1SpanAttributes.size, actualExecuteTool1SpanAttributes.size)
            assertMapsEqual(expectedExecuteTool1SpanAttributes, actualExecuteTool1SpanAttributes)

            // Assert Execute Tool 2 Span
            val actualExecuteTool2SpanAttributes =
                executeTool2Span.attributes.asMap().map { (key, value) -> key.key to value }.toMap()

            val expectedExecuteTool2SpanAttributes = mapOf(
                "gen_ai.tool.name" to TestGetWeatherTool.name,
                "gen_ai.tool.description" to TestGetWeatherTool.descriptor.description,
                "input.value" to "{\"location\":\"London\"}",
                "output.value" to toolResponse2,
            )

            assertEquals(expectedExecuteTool2SpanAttributes.size, actualExecuteTool2SpanAttributes.size)
            assertMapsEqual(expectedExecuteTool2SpanAttributes, actualExecuteTool2SpanAttributes)
        }
    }

    @Test
    fun testSubgraphWithFinishTool() = runTest {
        MockSpanExporter().use { mockSpanExporter ->
            val strategy = strategy("subgraph-finish-tool-strategy") {
                val sg by subgraphWithTask<String>(
                    toolSelectionStrategy = ToolSelectionStrategy.Tools(
                        listOf(ProvideStringSubgraphResult.descriptor)
                    )
                ) { input ->
                    "Please finish the task by calling the finish tool with the final result for: $input"
                }

                edge(nodeStart forwardTo sg)
                edge(sg forwardTo nodeFinish transformed { it.result })
            }

            val model = OpenAIModels.Chat.GPT4o
            val temperature = 0.3
            val systemPrompt = "You orchestrate a subtask."
            val userPrompt = "Summarize: test subgraph"
            val finalString = "Task done for: test subgraph"

            val mockExecutor = getMockExecutor {
                mockLLMToolCall(
                    ProvideStringSubgraphResult,
                    StringSubgraphResult(finalString)
                ) onRequestContains "Please finish the task"
            }

            val toolRegistry = ToolRegistry {
                tool(ProvideStringSubgraphResult)
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

            assertTrue { actualSpans.count { it.name == "tool.finish_task_execution_string" } == 1 }
            assertTrue { actualSpans.count { it.name == "llm.test-prompt-id" } == 1 }

            val toolSpan = actualSpans.first { it.name == "tool.finish_task_execution_string" }

            val toolAttrs = toolSpan.attributes.asMap().map { (k, v) -> k.key to v }.toMap()
            val expectedToolAttrs = mapOf(
                "gen_ai.tool.name" to ProvideStringSubgraphResult.name,
                "gen_ai.tool.description" to ProvideStringSubgraphResult.descriptor.description,
                "input.value" to "{\"result\":\"$finalString\"}",
                "output.value" to "{\"result\":\"$finalString\"}",
            )
            assertEquals(expectedToolAttrs.size, toolAttrs.size)
            assertMapsEqual(expectedToolAttrs, toolAttrs)
        }
    }

    @Test
    fun `test adapter customizes spans after creation`() = runTest {
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
                promptExecutor = mockExecutor,
                model = model,
                clock = testClock,
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

            val nodeSpan = spans.first { it.name == "node.test-llm-call" }
            val nodeAttrs = nodeSpan.attributes.asMap().asSequence().associate { it.key.key to it.value }
            assertEquals("value-start", nodeAttrs["custom.after.start"])
            val llmSpan = spans.first { it.name == "llm.$promptId" }
            val llmAttrs = llmSpan.attributes.asMap().asSequence().associate { it.key.key to it.value }

            assertEquals(123L, llmAttrs["custom.before.finish"])

            val expectedLlmAttrs = mapOf(
                "gen_ai.system" to model.provider.id,
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
    fun testStructuredDataLLMCall() = runTest {
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

            val actualSpans = mockSpanExporter.collectedSpans

            assertTrue { actualSpans.any { it.name.startsWith("run.") } }
            assertTrue { actualSpans.any { it.name == "node.__start__" } }
            assertTrue { actualSpans.any { it.name == "node.llm-structured" } }
            assertTrue { actualSpans.any { it.name == "llm.test-prompt-id" } }

            val llmGeneration = actualSpans.first { it.name == "llm.test-prompt-id" }
            val actualSpanAttributes = llmGeneration.attributes.asMap()
                .map { (key, value) -> key.key to value }
                .toMap()

            val expectedAttributes = mapOf(
                "gen_ai.system" to model.provider.id,
                "gen_ai.conversation.id" to mockSpanExporter.lastRunId,
                "gen_ai.operation.name" to "chat",
                "gen_ai.request.model" to model.id,
                "gen_ai.request.temperature" to temperature,
                "gen_ai.response.finish_reasons" to listOf(FinishReasonType.Stop.id),

                "gen_ai.prompt.0.role" to Message.Role.System.name.lowercase(),
                "gen_ai.prompt.0.content" to systemPrompt,
                "gen_ai.prompt.1.role" to Message.Role.User.name.lowercase(),
                "gen_ai.prompt.1.content" to userPrompt,
                "gen_ai.completion.0.role" to Message.Role.Assistant.name.lowercase(),
                "gen_ai.completion.0.content" to mockAssistantText,
            )

            assertEquals(expectedAttributes.size, actualSpanAttributes.size)
            assertMapsEqual(expectedAttributes, actualSpanAttributes)
        }
    }

    @Disabled("KG-288")
    @OptIn(DetachedPromptExecutorAPI::class)
    @Test
    fun testContentModerationEventOnLLMSpan() = runTest {
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
                addModerationResponseExactPattern<ToolArgs>(userPrompt, moderationResult)
            }

            runAgentWithStrategy(
                strategy = strategy,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                promptExecutor = promptExecutor,
                spanExporter = mockSpanExporter,
            )

            val spans = mockSpanExporter.collectedSpans
            assertTrue(spans.any { it.name == "node.moderate-message" })

            val llmSpan = spans.firstOrNull { it.name == "llm.single-message-moderation" }
                ?: spans.firstOrNull { span -> span.events.any { it.name == "moderation.result" } }
                ?: error("No LLM span for moderation found (expected 'llm.single-message-moderation' or a span with 'moderation.result' event)")

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

    @Disabled("KG-288")
    @Test
    fun testEmbeddingsTracingWithOpenAI() = runTest {
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
            assertTrue(spans.any { it.name == "node.__start__" })
            assertTrue(spans.any { it.name == "node.embeddings-call" })

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
        spanExporter: SpanExporter? = null,
        verbose: Boolean = true
    ) {
        val agentId = "test-agent-id"
        val promptId = "test-prompt-id"
        val testClock = Clock.System

        createAgent(
            agentId = agentId,
            strategy = strategy,
            promptId = promptId,
            promptExecutor = promptExecutor,
            model = model,
            clock = testClock,
            temperature = temperature,
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
}
