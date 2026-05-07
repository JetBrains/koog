package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.context.DetachedPromptExecutorAPI
import ai.koog.agents.core.agent.entity.AIAgentSubgraphBase.Companion.START_NODE_PREFIX
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.builder.subgraph
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.features.opentelemetry.AgentType
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.MockToolCallResponse
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.assistantMessage
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.getMessagesString
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.getSystemInstructionsString
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.getToolDefinitionsString
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.runAgentWithSingleLLMCallStrategy
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.runAgentWithSingleToolCallStrategy
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.runAgentWithStrategy
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.testClock
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.toolCallMessage
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestData
import ai.koog.agents.features.opentelemetry.assertSpans
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes.Operation.OperationNameType
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes.Response.FinishReasonType
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryTestBase
import ai.koog.agents.features.opentelemetry.mock.MockSpanExporter
import ai.koog.agents.features.opentelemetry.mock.TestGetWeatherTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.utils.HiddenString
import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.dsl.ModerationCategory
import ai.koog.prompt.dsl.ModerationCategoryResult
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.tokenizer.SimpleRegexBasedTokenizer
import ai.koog.serialization.kotlinx.KotlinxSerializer
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestPipeline
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.export.simpleSpanProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class OpenTelemetryInferenceSpanTest : OpenTelemetryTestBase() {

    private val serializer = KotlinxSerializer()

    private val json = Json { allowStructuredMapKeys = true }

    @ParameterizedTest
    @EnumSource(AgentType::class)
    fun `test inference spans are collected`(agentType: AgentType) = runTest {
        val userInput = OpenTelemetryTestAPI.Parameter.USER_PROMPT_PARIS
        val mockLLMResponse = OpenTelemetryTestAPI.Parameter.MOCK_LLM_RESPONSE_PARIS

        val collectedTestData = runAgentWithSingleLLMCallStrategy(
            userPrompt = userInput,
            mockLLMResponse = mockLLMResponse,
            verbose = true,
            agentType = agentType
        )

        val runId = collectedTestData.lastRunId

        val actualSpans = collectedTestData.filterInferenceSpans()
        assertTrue(actualSpans.isNotEmpty(), "Inference spans should be created during agent execution")

        val actualLLMCallEventIds = collectedTestData.filterInferenceEventIds()
        assertTrue(actualLLMCallEventIds.isNotEmpty(), "LLM Call event ids should be collected during agent execution")

        val expectedInputMessages = listOf(
            Message.System(OpenTelemetryTestAPI.Parameter.SYSTEM_PROMPT, RequestMetaInfo(testClock.now())),
            Message.User(userInput, RequestMetaInfo(testClock.now())),
            Message.User(userInput, RequestMetaInfo(testClock.now())),
        )

        val expectedOutputMessages = listOf(
            assistantMessage(mockLLMResponse)
        )

        val expectedSpans = listOf(
            mapOf(
                "${OperationNameType.CHAT.id} ${OpenTelemetryTestAPI.Parameter.defaultModel.id}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.operation.name" to OperationNameType.CHAT.id,
                        "gen_ai.provider.name" to OpenTelemetryTestAPI.Parameter.defaultModel.provider.id,
                        "gen_ai.conversation.id" to runId,
                        "gen_ai.output.type" to "text",
                        "gen_ai.request.model" to OpenTelemetryTestAPI.Parameter.defaultModel.id,
                        "gen_ai.request.temperature" to OpenTelemetryTestAPI.Parameter.TEMPERATURE,
                        "gen_ai.input.messages" to getMessagesString(expectedInputMessages),
                        "system_instructions" to getSystemInstructionsString(listOf(OpenTelemetryTestAPI.Parameter.SYSTEM_PROMPT)),
                        "koog.event.id" to actualLLMCallEventIds.first(),
                        "gen_ai.response.model" to OpenTelemetryTestAPI.Parameter.defaultModel.id,
                        "gen_ai.usage.input_tokens" to 0L,
                        "gen_ai.usage.output_tokens" to 0L,
                        "gen_ai.output.messages" to getMessagesString(expectedOutputMessages),
                        "gen_ai.response.finish_reasons" to listOf(FinishReasonType.Stop.id)
                    )
                )
            ),
        )

        assertSpans(expectedSpans, actualSpans)

        val expectedInvokeAgentAttrs = setOf(
            "gen_ai.response.finish_reasons" to listOf(FinishReasonType.Stop.id)
        )
        collectedTestData.filterAgentInvokeSpans().single().let { actual ->
            assertEquals(
                expected = expectedInvokeAgentAttrs,
                actual = actual.asKeyValue().intersect(expectedInvokeAgentAttrs),
                message = "invoke_agent span missing finish_reasons=[stop]:\nActual:${actual.asKeyValue()}"
            )
        }
    }

    @ParameterizedTest
    @EnumSource(AgentType::class)
    fun `test inference spans with tool calls collect events`(agentType: AgentType) = runTest {
        val userInput = OpenTelemetryTestAPI.Parameter.USER_PROMPT_PARIS
        val toolCallId = "tool-call-id"
        val location = "Paris"

        val mockToolCallResponse = MockToolCallResponse(
            tool = TestGetWeatherTool,
            arguments = TestGetWeatherTool.Args(location),
            toolResult = TestGetWeatherTool.DEFAULT_PARIS_RESULT,
            toolCallId = toolCallId,
        )

        val mockLLMResponse = OpenTelemetryTestAPI.Parameter.MOCK_LLM_RESPONSE_PARIS

        val collectedTestData = runAgentWithSingleToolCallStrategy(
            userPrompt = userInput,
            mockToolCallResponse = mockToolCallResponse,
            mockLLMResponse = mockLLMResponse,
            verbose = true,
            agentType = agentType
        )

        val runId = collectedTestData.lastRunId
        val model = OpenTelemetryTestAPI.Parameter.defaultModel

        val actualSpans = collectedTestData.filterInferenceSpans()
        assertTrue(actualSpans.isNotEmpty(), "Inference spans should be created during agent execution")

        val actualLLMCallEventIds = collectedTestData.filterInferenceEventIds()
        assertTrue(actualLLMCallEventIds.isNotEmpty(), "LLM event IDs should be collected during agent execution")

        val expectedInputMessages1 = listOf(
            Message.System(OpenTelemetryTestAPI.Parameter.SYSTEM_PROMPT, RequestMetaInfo(testClock.now())),
            Message.User(userInput, RequestMetaInfo(testClock.now())),
        )

        val expectedOutputMessages1 = listOf(
            toolCallMessage(toolCallId, TestGetWeatherTool.name, """{"location":"$location"}""")
        )

        val expectedInputMessages2 = listOf(
            Message.System(OpenTelemetryTestAPI.Parameter.SYSTEM_PROMPT, RequestMetaInfo(testClock.now())),
            Message.User(userInput, RequestMetaInfo(testClock.now())),
            toolCallMessage(toolCallId, TestGetWeatherTool.name, """{"location":"$location"}"""),
            Message.Tool.Result(toolCallId, TestGetWeatherTool.name, mockToolCallResponse.toolResult, RequestMetaInfo(testClock.now())),
        )

        val expectedOutputMessages2 = listOf(
            assistantMessage(mockLLMResponse)
        )

        val expectedSpans = listOf(
            mapOf(
                "${OperationNameType.CHAT.id} ${OpenTelemetryTestAPI.Parameter.defaultModel.id}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.operation.name" to OperationNameType.CHAT.id,
                        "gen_ai.provider.name" to model.provider.id,
                        "gen_ai.conversation.id" to runId,
                        "gen_ai.output.type" to "text",
                        "gen_ai.request.model" to model.id,
                        "gen_ai.request.temperature" to OpenTelemetryTestAPI.Parameter.TEMPERATURE,
                        "gen_ai.input.messages" to getMessagesString(expectedInputMessages1),
                        "system_instructions" to getSystemInstructionsString(listOf(OpenTelemetryTestAPI.Parameter.SYSTEM_PROMPT)),
                        "gen_ai.tool.definitions" to getToolDefinitionsString(listOf(TestGetWeatherTool.descriptor)),
                        "koog.event.id" to actualLLMCallEventIds[0],
                        "gen_ai.response.model" to model.id,
                        "gen_ai.usage.input_tokens" to 0L,
                        "gen_ai.usage.output_tokens" to 0L,
                        "gen_ai.output.messages" to getMessagesString(expectedOutputMessages1),
                        "gen_ai.response.finish_reasons" to listOf(FinishReasonType.ToolCalls.id)
                    )
                )
            ),
            mapOf(
                "${OperationNameType.CHAT.id} ${OpenTelemetryTestAPI.Parameter.defaultModel.id}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.operation.name" to OperationNameType.CHAT.id,
                        "gen_ai.provider.name" to model.provider.id,
                        "gen_ai.conversation.id" to runId,
                        "gen_ai.output.type" to "text",
                        "gen_ai.request.model" to model.id,
                        "gen_ai.request.temperature" to OpenTelemetryTestAPI.Parameter.TEMPERATURE,
                        "gen_ai.input.messages" to getMessagesString(expectedInputMessages2),
                        "system_instructions" to getSystemInstructionsString(listOf(OpenTelemetryTestAPI.Parameter.SYSTEM_PROMPT)),
                        "gen_ai.tool.definitions" to getToolDefinitionsString(listOf(TestGetWeatherTool.descriptor)),
                        "koog.event.id" to actualLLMCallEventIds[1],
                        "gen_ai.response.model" to model.id,
                        "gen_ai.usage.input_tokens" to 0L,
                        "gen_ai.usage.output_tokens" to 0L,
                        "gen_ai.output.messages" to getMessagesString(expectedOutputMessages2),
                        "gen_ai.response.finish_reasons" to listOf(FinishReasonType.Stop.id)
                    )
                ),
            ),
        )

        assertSpans(expectedSpans, actualSpans)
    }

    @ParameterizedTest
    @EnumSource(AgentType::class)
    fun `test inference spans with verbose logging disabled`(agentType: AgentType) = runTest {
        val userInput = OpenTelemetryTestAPI.Parameter.USER_PROMPT_PARIS
        val toolCallId = "tool-call-id"
        val location = "Paris"

        val mockToolCallResponse = MockToolCallResponse(
            tool = TestGetWeatherTool,
            arguments = TestGetWeatherTool.Args(location),
            toolResult = TestGetWeatherTool.DEFAULT_PARIS_RESULT,
            toolCallId = toolCallId,
        )

        val mockLLMResponse = OpenTelemetryTestAPI.Parameter.MOCK_LLM_RESPONSE_PARIS

        val collectedTestData = runAgentWithSingleToolCallStrategy(
            userPrompt = userInput,
            mockToolCallResponse = mockToolCallResponse,
            mockLLMResponse = mockLLMResponse,
            verbose = false,
            agentType = agentType
        )

        val runId = collectedTestData.lastRunId
        val model = OpenTelemetryTestAPI.Parameter.defaultModel

        val actualSpans = collectedTestData.filterInferenceSpans()
        assertTrue(actualSpans.isNotEmpty(), "Inference spans should be created during agent execution")

        val actualLLMCallEventIds = collectedTestData.filterInferenceEventIds()
        assertTrue(actualLLMCallEventIds.isNotEmpty(), "LLM event IDs should be collected during agent execution")

        val expectedSpans = listOf(
            mapOf(
                "${OperationNameType.CHAT.id} ${OpenTelemetryTestAPI.Parameter.defaultModel.id}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.operation.name" to OperationNameType.CHAT.id,
                        "gen_ai.provider.name" to model.provider.id,
                        "gen_ai.conversation.id" to runId,
                        "gen_ai.output.type" to "text",
                        "gen_ai.request.model" to model.id,
                        "gen_ai.request.temperature" to OpenTelemetryTestAPI.Parameter.TEMPERATURE,
                        "gen_ai.input.messages" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "system_instructions" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "gen_ai.tool.definitions" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "koog.event.id" to actualLLMCallEventIds[0],
                        "gen_ai.response.model" to model.id,
                        "gen_ai.usage.input_tokens" to 0L,
                        "gen_ai.usage.output_tokens" to 0L,
                        "gen_ai.output.messages" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "gen_ai.response.finish_reasons" to listOf(FinishReasonType.ToolCalls.id)
                    )
                )
            ),
            mapOf(
                "${OperationNameType.CHAT.id} ${OpenTelemetryTestAPI.Parameter.defaultModel.id}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.operation.name" to OperationNameType.CHAT.id,
                        "gen_ai.provider.name" to model.provider.id,
                        "gen_ai.conversation.id" to runId,
                        "gen_ai.output.type" to "text",
                        "gen_ai.request.model" to model.id,
                        "gen_ai.request.temperature" to OpenTelemetryTestAPI.Parameter.TEMPERATURE,
                        "gen_ai.input.messages" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "system_instructions" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "gen_ai.tool.definitions" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "koog.event.id" to actualLLMCallEventIds[1],
                        "gen_ai.response.model" to model.id,
                        "gen_ai.usage.input_tokens" to 0L,
                        "gen_ai.usage.output_tokens" to 0L,
                        "gen_ai.output.messages" to HiddenString.HIDDEN_STRING_PLACEHOLDER,
                        "gen_ai.response.finish_reasons" to listOf(FinishReasonType.Stop.id)
                    )
                ),
            ),
        )

        assertSpans(expectedSpans, actualSpans)
    }

    @Test
    fun `test inner and outer inference spans in subgraphs are collected`() = runTest {
        val userInput = "User input (root)"

        val rootNodeCallLLMName = "root-node-call-llm"
        val rootLLMResponse = "LLM Response (root)"

        val subgraphName = "test-subgraph"
        val subgraphLLMCallNodeName = "test-subgraph-llm-call"
        val subgraphLLMResponse = "LLM Response (subgraph)"
        val model = OpenTelemetryTestAPI.Parameter.defaultModel

        val strategy = strategy<String, String>("test-strategy") {
            val subgraph by subgraph<String, String>(subgraphName) {
                val nodeSubgraphLLMCall by nodeLLMRequest(subgraphLLMCallNodeName)

                edge(nodeStart forwardTo nodeSubgraphLLMCall)
                edge(nodeSubgraphLLMCall forwardTo nodeFinish onAssistantMessage { true })
            }

            val nodeLLMCall by nodeLLMRequest(rootNodeCallLLMName)

            edge(nodeStart forwardTo subgraph)
            edge(subgraph forwardTo nodeLLMCall)
            edge(nodeLLMCall forwardTo nodeFinish onAssistantMessage { true })
        }

        val executor = getMockExecutor(serializer, testClock) {
            mockLLMAnswer(subgraphLLMResponse) onRequestEquals userInput
            mockLLMAnswer(rootLLMResponse) onRequestEquals subgraphLLMResponse
        }

        val collectedTestData = runAgentWithStrategy(
            strategy = strategy,
            userPrompt = userInput,
            executor = executor,
            verbose = true
        )

        val runId = collectedTestData.lastRunId

        val actualSpans = collectedTestData.filterInferenceSpans()
        assertTrue(actualSpans.isNotEmpty(), "Inference spans should be created during agent execution")

        val actualLLMCallEventIds = collectedTestData.filterInferenceEventIds()
        assertTrue(actualLLMCallEventIds.isNotEmpty(), "LLM event IDs should be collected during agent execution")

        val expectedInputMessages1 = listOf(
            Message.System(OpenTelemetryTestAPI.Parameter.SYSTEM_PROMPT, RequestMetaInfo(testClock.now())),
            Message.User(userInput, RequestMetaInfo(testClock.now())),
            Message.User(userInput, RequestMetaInfo(testClock.now())),
        )

        val expectedOutputMessages1 = listOf(
            assistantMessage(subgraphLLMResponse)
        )

        val expectedInputMessages2 = listOf(
            Message.System(OpenTelemetryTestAPI.Parameter.SYSTEM_PROMPT, RequestMetaInfo(testClock.now())),
            Message.User(userInput, RequestMetaInfo(testClock.now())),
            Message.User(userInput, RequestMetaInfo(testClock.now())),
            assistantMessage(subgraphLLMResponse),
            Message.User(subgraphLLMResponse, RequestMetaInfo(testClock.now())),
        )

        val expectedOutputMessages2 = listOf(
            assistantMessage(rootLLMResponse)
        )

        val expectedSpans = listOf(
            mapOf(
                "${OperationNameType.CHAT.id} ${OpenTelemetryTestAPI.Parameter.defaultModel.id}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.operation.name" to OperationNameType.CHAT.id,
                        "gen_ai.provider.name" to model.provider.id,
                        "gen_ai.conversation.id" to runId,
                        "gen_ai.output.type" to "text",
                        "gen_ai.request.model" to model.id,
                        "gen_ai.request.temperature" to OpenTelemetryTestAPI.Parameter.TEMPERATURE,
                        "gen_ai.input.messages" to getMessagesString(expectedInputMessages1),
                        "system_instructions" to getSystemInstructionsString(listOf(OpenTelemetryTestAPI.Parameter.SYSTEM_PROMPT)),
                        "koog.event.id" to actualLLMCallEventIds[0],
                        "gen_ai.response.model" to model.id,
                        "gen_ai.usage.input_tokens" to 0L,
                        "gen_ai.usage.output_tokens" to 0L,
                        "gen_ai.output.messages" to getMessagesString(expectedOutputMessages1),
                        "gen_ai.response.finish_reasons" to listOf(FinishReasonType.Stop.id)
                    )
                )
            ),
            mapOf(
                "${OperationNameType.CHAT.id} ${OpenTelemetryTestAPI.Parameter.defaultModel.id}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.operation.name" to OperationNameType.CHAT.id,
                        "gen_ai.provider.name" to model.provider.id,
                        "gen_ai.conversation.id" to runId,
                        "gen_ai.output.type" to "text",
                        "gen_ai.request.model" to model.id,
                        "gen_ai.request.temperature" to OpenTelemetryTestAPI.Parameter.TEMPERATURE,
                        "gen_ai.input.messages" to getMessagesString(expectedInputMessages2),
                        "system_instructions" to getSystemInstructionsString(listOf(OpenTelemetryTestAPI.Parameter.SYSTEM_PROMPT)),
                        "koog.event.id" to actualLLMCallEventIds[1],
                        "gen_ai.response.model" to model.id,
                        "gen_ai.usage.input_tokens" to 0L,
                        "gen_ai.usage.output_tokens" to 0L,
                        "gen_ai.output.messages" to getMessagesString(expectedOutputMessages2),
                        "gen_ai.response.finish_reasons" to listOf(FinishReasonType.Stop.id)
                    )
                )
            ),
        )

        assertSpans(expectedSpans, actualSpans)
    }

    @Test
    fun `test inference span contains tokens data`() = runTest {
        val userInput = OpenTelemetryTestAPI.Parameter.USER_PROMPT_PARIS
        val mockLLMResponse = OpenTelemetryTestAPI.Parameter.MOCK_LLM_RESPONSE_PARIS
        val model = OpenTelemetryTestAPI.Parameter.defaultModel
        val maxTokens = 100

        val nodeLLMCallName = "test-llm-call-node"
        val strategy = strategy<String, String>("test-strategy") {
            val nodeLLMCall by nodeLLMRequest(nodeLLMCallName)

            edge(nodeStart forwardTo nodeLLMCall)
            edge(nodeLLMCall forwardTo nodeFinish onAssistantMessage { true })
        }

        // Use tokenizer in the prompt executor to count tokens
        val tokenizer = SimpleRegexBasedTokenizer()
        val mockExecutor = getMockExecutor(serializer, testClock, tokenizer) {
            mockLLMAnswer(mockLLMResponse) onRequestEquals userInput
        }

        val collectedTestData = runAgentWithStrategy(
            strategy = strategy,
            userPrompt = userInput,
            executor = mockExecutor,
            model = model,
            maxTokens = maxTokens,
            verbose = true
        )

        val runId = collectedTestData.lastRunId

        val actualSpans = collectedTestData.filterInferenceSpans()
        assertTrue(actualSpans.isNotEmpty(), "Inference spans should be created during agent execution")

        val actualLLMCallEventIds = collectedTestData.filterInferenceEventIds()
        assertTrue(actualLLMCallEventIds.isNotEmpty(), "LLM event IDs should be collected during agent execution")

        val expectedInputMessages = listOf(
            Message.System(OpenTelemetryTestAPI.Parameter.SYSTEM_PROMPT, RequestMetaInfo(testClock.now())),
            Message.User(userInput, RequestMetaInfo(testClock.now())),
            Message.User(userInput, RequestMetaInfo(testClock.now())),
        )

        val expectedOutputMessages = listOf(
            assistantMessage(mockLLMResponse)
        )

        val expectedSpans = listOf(
            mapOf(
                "${OperationNameType.CHAT.id} ${OpenTelemetryTestAPI.Parameter.defaultModel.id}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.operation.name" to OperationNameType.CHAT.id,
                        "gen_ai.provider.name" to model.provider.id,
                        "gen_ai.conversation.id" to runId,
                        "gen_ai.output.type" to "text",
                        "gen_ai.request.model" to model.id,
                        "gen_ai.request.max_tokens" to maxTokens.toLong(),
                        "gen_ai.request.temperature" to OpenTelemetryTestAPI.Parameter.TEMPERATURE,
                        "gen_ai.input.messages" to getMessagesString(expectedInputMessages),
                        "system_instructions" to getSystemInstructionsString(listOf(OpenTelemetryTestAPI.Parameter.SYSTEM_PROMPT)),
                        "koog.event.id" to actualLLMCallEventIds.first(),
                        "gen_ai.response.model" to model.id,
                        "gen_ai.usage.input_tokens" to tokenizer.countTokens(text = userInput).toLong(),
                        "gen_ai.usage.output_tokens" to tokenizer.countTokens(text = mockLLMResponse).toLong(),
                        "gen_ai.output.messages" to getMessagesString(expectedOutputMessages),
                        "gen_ai.response.finish_reasons" to listOf(FinishReasonType.Stop.id),
                    )
                )
            ),
        )

        assertSpans(expectedSpans, actualSpans)
    }

    @Test
    fun `expected spans on llm call failed`() = runTest {
        // Http client that fails each request
        val failingHttpClient = HttpClient {
            install("FailingInterceptor") {
                requestPipeline.intercept(HttpRequestPipeline.Before) {
                    throw KoogHttpClientException("openai", statusCode = 429)
                }
            }
        }

        val spanExporter = MockSpanExporter()
        val testData = OpenTelemetryTestData()
        val result = runCatching {
            AIAgent(
                promptExecutor = MultiLLMPromptExecutor(OpenAILLMClient("fake-key", baseClient = failingHttpClient)),
                llmModel = OpenTelemetryTestAPI.Parameter.defaultModel,
                strategy = singleRunStrategy(),
                systemPrompt = OpenTelemetryTestAPI.Parameter.SYSTEM_PROMPT
            ) {
                install(OpenTelemetry) {
                    addSpanProcessor { simpleSpanProcessor(spanExporter) }
                }
            }.run(OpenTelemetryTestAPI.Parameter.USER_PROMPT_PARIS)
        }
        val exception = result.exceptionOrNull()
        assertNotNull(exception, "Unexpected successful result $result")
        assertFalse(exception is CancellationException, "Unexpected cancellation exception")

        testData.collectedSpans = withTimeout(10.seconds) {
            spanExporter.isCollected.first()
            spanExporter.collectedSpans
        }

        // CHECKS
        // We are expecting to provide the root cause of LLMClientException
        val expectedSpans = setOf(
            "error.type" to "KoogHttpClientException-openai-httpCode=429",
        )
        testData.filterInferenceSpans().single().let { actual ->
            assertEquals(
                expected = expectedSpans,
                actual = actual.asKeyValue().intersect(expectedSpans),
                message = "Unexpected inference spans:\nExpected:${expectedSpans}\nActual:${actual.asKeyValue()}"
            )
        }
        testData.filterNodeExecutionSpans().filter { span ->
            // KoogAttributes.Koog.Node.Id
            "koog.node.id" to START_NODE_PREFIX !in span.asKeyValue()
        }.forEach { actual ->
            assertEquals(
                expected = expectedSpans,
                actual = actual.asKeyValue().intersect(expectedSpans),
                message = "Unexpected node execution spans:\nExpected:${expectedSpans}\nActual:${actual.asKeyValue()}"
            )
        }
        testData.filterAgentInvokeSpans().single().let { actual ->
            assertEquals(
                expected = expectedSpans,
                actual = actual.asKeyValue().intersect(expectedSpans),
                message = "Unexpected create agent spans:\nExpected:${expectedSpans}\nActual:${actual.asKeyValue()}"
            )
        }
    }

    @Test
    fun `test moderation response is recorded as koog moderation result attribute`() = runTest {
        val userInput = "I want to build a bomb"
        val moderationResult = ModerationResult(
            isHarmful = true,
            categories = mapOf(
                ModerationCategory.Illicit to ModerationCategoryResult(
                    detected = true,
                    confidenceScore = 0.9998,
                ),
            ),
        )

        val moderationModel = OpenAIModels.Moderation.Omni

        val strategy = strategy<String, String>("moderation-strategy") {
            val moderate by node<String, String>("moderate-message") { input ->
                llm.writeSession {
                    val moderationPrompt = prompt("single-message-moderation") {
                        message(Message.User(input, RequestMetaInfo.create(testClock)))
                    }
                    @OptIn(DetachedPromptExecutorAPI::class)
                    llm.promptExecutor.moderate(moderationPrompt, moderationModel)
                }
                input
            }
            edge(nodeStart forwardTo moderate)
            edge(moderate forwardTo nodeFinish transformed { it })
        }

        val executor = getMockExecutor(KotlinxSerializer(), testClock) {
            addModerationResponseExactPattern(userInput, moderationResult)
        }

        val testData = runAgentWithStrategy(
            strategy = strategy,
            userPrompt = userInput,
            executor = executor,
            model = moderationModel,
            verbose = true,
        )

        val expectedJson = json
            .encodeToString(ModerationResult.serializer(), moderationResult)

        // The moderation call goes through ContextualPromptExecutor, which fires LLMCallStarting /
        // LLMCallCompleted with moderationResponse populated; that produces an inference span
        // carrying the koog.moderation.result attribute.
        val moderationSpan = testData.filterInferenceSpans().firstOrNull {
            it.attributes["koog.moderation.result"] != null
        } ?: error("No inference span carrying 'koog.moderation.result' attribute was emitted")

        assertEquals(expectedJson, moderationSpan.attributes["koog.moderation.result"])
    }

    private fun SpanData.asKeyValue(): List<Pair<String, Any>> {
        return attributes.entries.map { it.key to it.value }
    }
}
