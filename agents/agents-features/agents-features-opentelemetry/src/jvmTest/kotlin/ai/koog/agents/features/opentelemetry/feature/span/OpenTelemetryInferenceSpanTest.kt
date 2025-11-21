package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.utils.SerializationUtils
import ai.koog.agents.features.opentelemetry.OpenTelemetrySpanAsserts.assertSpans
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.MockToolCallResponse
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.MOCK_LLM_RESPONSE_PARIS
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.SYSTEM_PROMPT
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.TEMPERATURE
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.USER_PROMPT_PARIS
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.defaultModel
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.runAgentWithSingleLLMCallStrategy
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.runAgentWithSingleToolCallStrategy
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.runAgentWithStrategy
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.testClock
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes.Operation.OperationNameType
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes.Response.FinishReasonType
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryTestBase
import ai.koog.agents.features.opentelemetry.mock.TestGetWeatherTool
import ai.koog.agents.features.opentelemetry.span.sha256base64
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.utils.HiddenString
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.tokenizer.SimpleRegexBasedTokenizer
import kotlinx.coroutines.test.runTest
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertTrue

class OpenTelemetryInferenceSpanTest : OpenTelemetryTestBase() {

    @Test
    fun `test inference spans are collected`() = runTest {
        val userInput = USER_PROMPT_PARIS
        val mockLLMResponse = MOCK_LLM_RESPONSE_PARIS

        val collectedTestData = runAgentWithSingleLLMCallStrategy(
            userPrompt = userInput,
            mockLLMResponse = mockLLMResponse
        )

        val runId = collectedTestData.lastRunId
        val result = collectedTestData.result

        val actualSpans = collectedTestData.filterInferenceSpans()
        assertTrue(actualSpans.isNotEmpty(), "Spans should be created during agent execution")

        val expectedSpans = listOf(
            mapOf(
                // TODO: Replace sha256base64() with unique event id for the LLM Call event
                "llm.${userInput.sha256base64()}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.operation.name" to OperationNameType.CHAT.id,
                        "gen_ai.system" to defaultModel.provider.id,
                        "gen_ai.conversation.id" to runId,
                        "gen_ai.request.temperature" to TEMPERATURE,
                        "gen_ai.request.model" to defaultModel.id,
                        "gen_ai.response.finish_reasons" to listOf(FinishReasonType.Stop.id)
                    ),
                    "events" to mapOf(
                        "gen_ai.system.message" to mapOf(
                            "gen_ai.system" to defaultModel.provider.id,
                            "role" to Message.Role.System.name.lowercase(),
                            "content" to SYSTEM_PROMPT,
                        ),
                        "gen_ai.user.message" to mapOf(
                            "gen_ai.system" to defaultModel.provider.id,
                            "role" to Message.Role.User.name.lowercase(),
                            "content" to userInput,
                        ),
                        "gen_ai.assistant.message" to mapOf(
                            "gen_ai.system" to defaultModel.provider.id,
                            "role" to Message.Role.Assistant.name.lowercase(),
                            "content" to result,
                        )
                    )
                )
            ),
        )

        assertSpans(expectedSpans, actualSpans)
    }

    @Test
    fun `test inference spans with tool calls collect events`() = runTest {
        val userInput = USER_PROMPT_PARIS
        val toolCallId = "tool-call-id"
        val location = "Paris"

        val mockToolCallResponse = MockToolCallResponse(
            tool = TestGetWeatherTool,
            arguments = TestGetWeatherTool.Args(location),
            toolResult = TestGetWeatherTool.DEFAULT_PARIS_RESULT,
            toolCallId = toolCallId,
        )

        val mockLLMResponse = MOCK_LLM_RESPONSE_PARIS

        val collectedTestData = runAgentWithSingleToolCallStrategy(
            userPrompt = userInput,
            mockToolCallResponse = mockToolCallResponse,
            mockLLMResponse = mockLLMResponse
        )

        val runId = collectedTestData.lastRunId
        val model = defaultModel

        val actualSpans = collectedTestData.filterInferenceSpans()
        assertTrue(actualSpans.isNotEmpty(), "Spans should be created during agent execution")

        val expectedSpans = listOf(
            mapOf(
                // TODO: Replace sha256base64() with unique event id for the LLM Call event
                "llm.${mockToolCallResponse.toolResult.sha256base64()}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.operation.name" to OperationNameType.CHAT.id,
                        "gen_ai.system" to model.provider.id,
                        "gen_ai.conversation.id" to runId,
                        "gen_ai.request.temperature" to TEMPERATURE,
                        "gen_ai.request.model" to model.id,
                        "gen_ai.response.finish_reasons" to listOf(FinishReasonType.Stop.id)
                    ),
                    "events" to mapOf(
                        "gen_ai.system.message" to mapOf(
                            "gen_ai.system" to model.provider.id,
                            "role" to Message.Role.System.name.lowercase(),
                            "content" to SYSTEM_PROMPT,
                        ),
                        "gen_ai.user.message" to mapOf(
                            "gen_ai.system" to model.provider.id,
                            "role" to Message.Role.User.name.lowercase(),
                            "content" to userInput,
                        ),
                        "gen_ai.choice" to mapOf(
                            "gen_ai.system" to model.provider.id,
                            "role" to Message.Role.Tool.name.lowercase(),
                            "tool_calls" to """[{"function":{"name":"${TestGetWeatherTool.name}","arguments":"{\"location\":\"$location\"}"},"id":"$toolCallId","type":"function"}]""",
                            "finish_reason" to FinishReasonType.ToolCalls.id,
                        ),
                        "gen_ai.tool.message" to mapOf(
                            "gen_ai.system" to model.provider.id,
                            "role" to Message.Role.Tool.name.lowercase(),
                            "content" to mockToolCallResponse.toolResult,
                            "id" to toolCallId,
                        ),
                        "gen_ai.assistant.message" to mapOf(
                            "gen_ai.system" to model.provider.id,
                            "role" to Message.Role.Assistant.name.lowercase(),
                            "content" to mockLLMResponse,
                        ),
                    )
                ),
            ),
            mapOf(
                // TODO: Replace sha256base64() with unique event id for the LLM Call event
                "llm.${userInput.sha256base64()}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.operation.name" to OperationNameType.CHAT.id,
                        "gen_ai.system" to model.provider.id,
                        "gen_ai.conversation.id" to runId,
                        "gen_ai.request.temperature" to TEMPERATURE,
                        "gen_ai.request.model" to model.id,
                        "gen_ai.response.finish_reasons" to listOf(FinishReasonType.ToolCalls.id)
                    ),
                    "events" to mapOf(
                        "gen_ai.system.message" to mapOf(
                            "gen_ai.system" to model.provider.id,
                            "role" to Message.Role.System.name.lowercase(),
                            "content" to SYSTEM_PROMPT,
                        ),
                        "gen_ai.user.message" to mapOf(
                            "gen_ai.system" to model.provider.id,
                            "role" to Message.Role.User.name.lowercase(),
                            "content" to userInput,
                        ),
                        "gen_ai.choice" to mapOf(
                            "gen_ai.system" to model.provider.id,
                            "role" to Message.Role.Tool.name.lowercase(),
                            "tool_calls" to """[{"function":{"name":"${TestGetWeatherTool.name}","arguments":"{\"location\":\"$location\"}"},"id":"$toolCallId","type":"function"}]""",
                            "index" to 0L,
                            "finish_reason" to FinishReasonType.ToolCalls.id,
                        )
                    )
                )
            )
        )

        assertSpans(expectedSpans, actualSpans)
    }

    @Test
    fun `test inference spans with verbose logging disabled`() = runTest {
        val userInput = USER_PROMPT_PARIS
        val toolCallId = "tool-call-id"
        val location = "Paris"

        val mockToolCallResponse = MockToolCallResponse(
            tool = TestGetWeatherTool,
            arguments = TestGetWeatherTool.Args(location),
            toolResult = TestGetWeatherTool.DEFAULT_PARIS_RESULT,
            toolCallId = toolCallId,
        )

        val mockLLMResponse = MOCK_LLM_RESPONSE_PARIS

        val collectedTestData = runAgentWithSingleToolCallStrategy(
            userPrompt = userInput,
            mockToolCallResponse = mockToolCallResponse,
            mockLLMResponse = mockLLMResponse,
            verbose = false
        )

        val runId = collectedTestData.lastRunId
        val model = defaultModel

        val actualSpans = collectedTestData.filterInferenceSpans()
        assertTrue(actualSpans.isNotEmpty(), "Spans should be created during agent execution")

        val expectedSpans = listOf(
            mapOf(
                // TODO: Replace sha256base64() with unique event id for the LLM Call event
                "llm.${mockToolCallResponse.toolResult.sha256base64()}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.operation.name" to OperationNameType.CHAT.id,
                        "gen_ai.system" to model.provider.id,
                        "gen_ai.conversation.id" to runId,
                        "gen_ai.request.temperature" to TEMPERATURE,
                        "gen_ai.request.model" to model.id,
                        "gen_ai.response.finish_reasons" to listOf(FinishReasonType.Stop.id)
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
                ),
            ),
            mapOf(
                // TODO: Replace sha256base64() with unique event id for the LLM Call event
                "llm.${userInput.sha256base64()}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.operation.name" to OperationNameType.CHAT.id,
                        "gen_ai.system" to model.provider.id,
                        "gen_ai.conversation.id" to runId,
                        "gen_ai.request.temperature" to TEMPERATURE,
                        "gen_ai.request.model" to model.id,
                        "gen_ai.response.finish_reasons" to listOf(FinishReasonType.ToolCalls.id)
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
                            "index" to 0L,
                            "finish_reason" to FinishReasonType.ToolCalls.id,
                        )
                    )
                )
            )
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

        val strategy = strategy<String, String>("test-strategy") {
            val nodeSubgraph by subgraph<String, String>(subgraphName) {
                val nodeSubgraphLLMCall by nodeLLMRequest(subgraphLLMCallNodeName)

                edge(nodeStart forwardTo nodeSubgraphLLMCall)
                edge(nodeSubgraphLLMCall forwardTo nodeFinish onAssistantMessage { true })
            }

            val nodeLLMCall by nodeLLMRequest(rootNodeCallLLMName)

            edge(nodeStart forwardTo nodeSubgraph)
            edge(nodeSubgraph forwardTo nodeLLMCall)
            edge(nodeLLMCall forwardTo nodeFinish onAssistantMessage { true })
        }

        val executor = getMockExecutor(clock = testClock) {
            mockLLMAnswer(subgraphLLMResponse) onRequestEquals userInput
            mockLLMAnswer(rootLLMResponse) onRequestEquals subgraphLLMResponse
        }

        val collectedTestData = runAgentWithStrategy(strategy = strategy, userPrompt = userInput, executor = executor)

        val runId = collectedTestData.lastRunId

        val actualSpans = collectedTestData.filterNodeExecutionSpans()
        assertTrue(actualSpans.isNotEmpty(), "Spans should be created during agent execution")

        @OptIn(InternalAgentsApi::class)
        val serializedRootAssistantResponse = SerializationUtils.encodeDataToStringOrDefault(
            data = Message.Assistant(
                content = rootLLMResponse,
                metaInfo = ResponseMetaInfo(
                    timestamp = testClock.now()
                )
            ),
            dataType = typeOf<Message>()
        )

        @OptIn(InternalAgentsApi::class)
        val serializedSubgraphAssistantResponse = SerializationUtils.encodeDataToStringOrDefault(
            data = Message.Assistant(
                content = subgraphLLMResponse,
                metaInfo = ResponseMetaInfo(
                    timestamp = testClock.now()
                )
            ),
            dataType = typeOf<Message>()
        )

        val expectedSpans = listOf(
            mapOf(
                "node.__finish__.${collectedTestData.singleNodeIdByName("__finish__")}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to "__finish__",
                        "koog.node.output" to "\"$rootLLMResponse\"",
                        "koog.node.input" to "\"$rootLLMResponse\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node.$rootNodeCallLLMName.${collectedTestData.singleNodeIdByName(rootNodeCallLLMName)}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to rootNodeCallLLMName,
                        "koog.node.output" to serializedRootAssistantResponse,
                        "koog.node.input" to "\"$subgraphLLMResponse\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node.$subgraphName.${collectedTestData.singleNodeIdByName(subgraphName)}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to subgraphName,
                        "koog.node.output" to "\"$subgraphLLMResponse\"",
                        "koog.node.input" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node.__finish__$subgraphName.${collectedTestData.singleNodeIdByName("__finish__$subgraphName")}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to "__finish__$subgraphName",
                        "koog.node.output" to "\"$subgraphLLMResponse\"",
                        "koog.node.input" to "\"$subgraphLLMResponse\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node.$subgraphLLMCallNodeName.${collectedTestData.singleNodeIdByName(subgraphLLMCallNodeName)}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to subgraphLLMCallNodeName,
                        "koog.node.output" to serializedSubgraphAssistantResponse,
                        "koog.node.input" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node.__start__$subgraphName.${collectedTestData.singleNodeIdByName("__start__$subgraphName")}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to "__start__$subgraphName",
                        "koog.node.input" to "\"$userInput\"",
                        "koog.node.output" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            ),
            mapOf(
                "node.__start__.${collectedTestData.singleNodeIdByName("__start__")}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to runId,
                        "koog.node.name" to "__start__",
                        "koog.node.input" to "\"$userInput\"",
                        "koog.node.output" to "\"$userInput\"",
                    ),
                    "events" to emptyMap()
                )
            )
        )

        assertSpans(expectedSpans, actualSpans)
    }

    @Test
    fun `test inference span contains tokens data`() = runTest {
        val userInput = USER_PROMPT_PARIS
        val mockLLMResponse = MOCK_LLM_RESPONSE_PARIS
        val model = defaultModel
        val temperature = TEMPERATURE
        val maxTokens = 100

        val nodeLLMCallName = "test-llm-call-node"
        val strategy = strategy<String, String>("test-strategy") {
            val nodeLLMCall by nodeLLMRequest(nodeLLMCallName)

            edge(nodeStart forwardTo nodeLLMCall)
            edge(nodeLLMCall forwardTo nodeFinish onAssistantMessage { true })
        }

        // Use tokenizer in the prompt executor to count tokens
        val tokenizer = SimpleRegexBasedTokenizer()
        val mockExecutor = getMockExecutor(clock = testClock, tokenizer = tokenizer) {
            mockLLMAnswer(mockLLMResponse) onRequestEquals userInput
        }

        val collectedTestData = runAgentWithStrategy(
            strategy = strategy,
            userPrompt = userInput,
            executor = mockExecutor,
            model = model,
            maxTokens = maxTokens,
        )

        val runId = collectedTestData.lastRunId
        val result = collectedTestData.result

        val actualSpans = collectedTestData.filterInferenceSpans()
        assertTrue(actualSpans.isNotEmpty(), "Spans should be created during agent execution")

        val expectedSpans = listOf(
            mapOf(
                // TODO: Replace sha256base64() with unique event id for the LLM Call event
                "llm.${userInput.sha256base64()}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.system" to model.provider.id,
                        "gen_ai.request.model" to model.id,
                        "gen_ai.request.max_tokens" to maxTokens.toLong(),
                        "gen_ai.conversation.id" to runId,
                        "gen_ai.operation.name" to "chat",
                        "gen_ai.request.temperature" to temperature,
                        "gen_ai.response.finish_reasons" to listOf(FinishReasonType.Stop.id),
                        "gen_ai.usage.input_tokens" to tokenizer.countTokens(text = userInput).toLong(),
                        "gen_ai.usage.output_tokens" to tokenizer.countTokens(text = mockLLMResponse).toLong(),
                        "gen_ai.usage.total_tokens" to tokenizer.countTokens(text = userInput)
                            .toLong() + tokenizer.countTokens(text = mockLLMResponse).toLong(),
                    ),
                    "events" to mapOf(
                        "gen_ai.system.message" to mapOf(
                            "gen_ai.system" to defaultModel.provider.id,
                            "role" to Message.Role.System.name.lowercase(),
                            "content" to SYSTEM_PROMPT,
                        ),
                        "gen_ai.user.message" to mapOf(
                            "gen_ai.system" to defaultModel.provider.id,
                            "role" to Message.Role.User.name.lowercase(),
                            "content" to userInput,
                        ),
                        "gen_ai.assistant.message" to mapOf(
                            "gen_ai.system" to defaultModel.provider.id,
                            "role" to Message.Role.Assistant.name.lowercase(),
                            "content" to result,
                        )
                    )
                )
            ),
        )

        assertSpans(expectedSpans, actualSpans)
    }
}
