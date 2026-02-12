package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.FINISH_NODE_PREFIX
import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.START_NODE_PREFIX
import ai.koog.agents.features.opentelemetry.AgentType
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.DEFAULT_AGENT_ID
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.DEFAULT_PROMPT_ID
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.DEFAULT_STRATEGY_NAME
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.SYSTEM_PROMPT
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.TEMPERATURE
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.USER_PROMPT_PARIS
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.defaultModel
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Strategy.getSimpleStrategy
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Strategy.getSingleLLMCallStrategy
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.createAgent
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.defaultMockExecutor
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.getMessagesString
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.getSystemInstructionsString
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.testClock
import ai.koog.agents.features.opentelemetry.assertSpans
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes.Operation.OperationNameType
import ai.koog.agents.features.opentelemetry.integration.SpanAdapter
import ai.koog.agents.features.opentelemetry.mock.MockSpanExporter
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.utils.io.use
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.OpenTelemetrySdk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * Tests for the OpenTelemetry feature.
 *
 * These tests verify that spans are created correctly during agent execution
 * and that the structure of spans matches the expected hierarchy.
 */
class OpenTelemetryConfigTest : OpenTelemetryTestBase() {

    @ParameterizedTest
    @EnumSource(AgentType::class)
    fun `test Open Telemetry feature default configuration`(agentType: AgentType) = runTest {
        var actualServiceName: String? = null
        var actualServiceVersion: String? = null
        var actualIsVerbose: Boolean? = null

        createAgent(
            strategy = getSimpleStrategy(agentType)
        ) {
            actualServiceName = serviceName
            actualServiceVersion = serviceVersion
            actualIsVerbose = isVerbose
        }

        val props = Properties()
        this::class.java.classLoader.getResourceAsStream("product.properties")?.use { stream -> props.load(stream) }

        assertEquals(props["name"], actualServiceName)
        assertEquals(props["version"], actualServiceVersion)
        assertEquals(false, actualIsVerbose)
    }

    @ParameterizedTest
    @EnumSource(AgentType::class)
    fun `test custom configuration is applied`(agentType: AgentType) = runTest {
        val expectedServiceName = "test-service-name"
        val expectedServiceVersion = "test-service-version"
        val expectedIsVerbose = true

        var actualServiceName: String? = null
        var actualServiceVersion: String? = null
        var actualIsVerbose: Boolean? = null

        createAgent(
            strategy = getSimpleStrategy(agentType),
        ) {
            setServiceInfo(expectedServiceName, expectedServiceVersion)
            setVerbose(expectedIsVerbose)

            actualServiceName = serviceName
            actualServiceVersion = serviceVersion
            actualIsVerbose = isVerbose
        }

        assertEquals(expectedServiceName, actualServiceName)
        assertEquals(expectedServiceVersion, actualServiceVersion)
        assertEquals(expectedIsVerbose, actualIsVerbose)
    }

    @ParameterizedTest
    @EnumSource(AgentType::class)
    fun `test filter is not allowed for open telemetry feature`(agentType: AgentType) = runTest {
        val throwable = assertFails {
            createAgent(
                strategy = getSimpleStrategy(agentType),
            ) {
                // Try to filter out all events. OpenTelemetryConfig should ignore this filter
                setEventFilter { false }
            }
        }

        assertTrue(
            throwable is UnsupportedOperationException,
            "Unexpected exception type. Expected <${UnsupportedOperationException::class.simpleName}>, but got: <${throwable::class.simpleName}>"
        )

        assertEquals(
            "Events filtering is not allowed for the OpenTelemetry feature.",
            throwable.message
        )
    }

    @ParameterizedTest
    @EnumSource(AgentType::class)
    fun `test install Open Telemetry feature with custom sdk, should use provided sdk`(agentType: AgentType) = runTest {
        val expectedSdk = OpenTelemetrySdk.builder().build()
        var actualSdk: OpenTelemetrySdk? = null

        createAgent(
            strategy = getSimpleStrategy(agentType),
        ) {
            setSdk(expectedSdk)
            actualSdk = sdk
        }

        assertEquals(expectedSdk, actualSdk)
    }

    @ParameterizedTest
    @EnumSource(AgentType::class)
    fun `test custom sdk configuration emits correct spans`(agentType: AgentType) = runTest {
        MockSpanExporter().use { mockExporter ->

            val expectedSdk = createCustomSdk(mockExporter)

            val agent = createAgent(
                strategy = getSingleLLMCallStrategy(agentType),
                executor = defaultMockExecutor,
            ) {
                setSdk(expectedSdk)
            }

            agent.run(USER_PROMPT_PARIS, null)
            val actualSpanNames = mockExporter.collectedSpans.map { it.name }
            agent.close()

            val expectedSpanNames = when (agentType) {
                AgentType.Graph -> listOf(
                    "node $START_NODE_PREFIX",
                    "${OperationNameType.CHAT.id} ${defaultModel.id}",
                    "node test-llm-call",
                    "node $FINISH_NODE_PREFIX",
                    "strategy $DEFAULT_STRATEGY_NAME",
                    "${OperationNameType.INVOKE_AGENT.id} $DEFAULT_AGENT_ID",
                    "${OperationNameType.CREATE_AGENT.id} $DEFAULT_AGENT_ID"
                )

                AgentType.Functional -> listOf(
                    "${OperationNameType.CHAT.id} ${defaultModel.id}",
                    "strategy $DEFAULT_STRATEGY_NAME",
                    "${OperationNameType.INVOKE_AGENT.id} $DEFAULT_AGENT_ID",
                    "${OperationNameType.CREATE_AGENT.id} $DEFAULT_AGENT_ID"
                )
            }

            assertEquals(expectedSpanNames.size, actualSpanNames.size)
            expectedSpanNames.zip(actualSpanNames).forEach { (expectedSpanName, actualSpanName) ->
                assertEquals(expectedSpanName, actualSpanName)
            }
        }
    }

    @ParameterizedTest
    @EnumSource(AgentType::class)
    fun `test span adapter applies custom attribute to invoke agent span`(agentType: AgentType) = runTest {
        MockSpanExporter().use { mockExporter ->

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
                strategy = getSingleLLMCallStrategy(agentType),
                agentId = DEFAULT_AGENT_ID,
                promptId = DEFAULT_PROMPT_ID,
                executor = defaultMockExecutor,
                model = defaultModel,
                systemPrompt = SYSTEM_PROMPT,
                temperature = TEMPERATURE,
                userPrompt = USER_PROMPT_PARIS,
            ) {
                addSpanExporter(mockExporter)

                // Add custom span adapter
                addSpanAdapter(adapter)
                setVerbose(true)
            }.use { agent ->
                agent.run("", null)
            }

            val collectedSpans = mockExporter.collectedSpans
            assertTrue(collectedSpans.isNotEmpty(), "Spans should be created during agent execution")

            val conversationIdAttribute = SpanAttributes.Conversation.Id(mockExporter.lastRunId)
            val operationNameAttribute = SpanAttributes.Operation.Name(OperationNameType.INVOKE_AGENT)

            fun attributesMatches(attributes: Map<AttributeKey<*>, Any>): Boolean {
                var conversationIdAttributeExists = false
                var operationNameAttributeExists = false
                attributes.forEach { (key, value) ->
                    if (key.key == conversationIdAttribute.key && value == conversationIdAttribute.value) {
                        conversationIdAttributeExists = true
                    }

                    if (key.key == operationNameAttribute.key && value == operationNameAttribute.value) {
                        operationNameAttributeExists = true
                    }
                }
                return conversationIdAttributeExists && operationNameAttributeExists
            }

            val actualInvokeAgentSpans = collectedSpans.filter { span ->
                attributesMatches(span.attributes.asMap())
            }

            assertEquals(1, actualInvokeAgentSpans.size, "Invoke agent span should be present")

            val expectedInvokeAgentSpans = listOf(
                mapOf(
                    "${OperationNameType.INVOKE_AGENT.id} $DEFAULT_AGENT_ID" to mapOf(
                        "attributes" to mapOf(
                            "gen_ai.operation.name" to OperationNameType.INVOKE_AGENT.id,
                            "gen_ai.provider.name" to defaultModel.provider.id,
                            "gen_ai.agent.id" to DEFAULT_AGENT_ID,
                            "gen_ai.conversation.id" to mockExporter.lastRunId,
                            "gen_ai.output.type" to "text",
                            "gen_ai.request.model" to defaultModel.id,
                            "gen_ai.request.temperature" to TEMPERATURE,
                            "gen_ai.input.messages" to getMessagesString(
                                listOf(
                                    Message.System(SYSTEM_PROMPT, RequestMetaInfo(testClock.now())),
                                    Message.User(USER_PROMPT_PARIS, RequestMetaInfo(testClock.now()))
                                )
                            ),
                            "system_instructions" to getSystemInstructionsString(listOf(SYSTEM_PROMPT)),
                            "gen_ai.response.model" to defaultModel.id,
                            "gen_ai.usage.input_tokens" to 0L,
                            "gen_ai.usage.output_tokens" to 0L,
                            "gen_ai.output.messages" to getMessagesString(
                                listOf(
                                    Message.System(SYSTEM_PROMPT, RequestMetaInfo(testClock.now())),
                                    Message.User(USER_PROMPT_PARIS, RequestMetaInfo(testClock.now()))
                                )
                            ),
                            customBeforeStartAttribute.key to customBeforeStartAttribute.value,
                            customBeforeFinishAttribute.key to customBeforeFinishAttribute.value,
                            "koog.event.id" to mockExporter.lastRunId,
                        ),
                        "events" to emptyMap()
                    )
                )
            )

            assertSpans(expectedInvokeAgentSpans, actualInvokeAgentSpans)
        }
    }
}
