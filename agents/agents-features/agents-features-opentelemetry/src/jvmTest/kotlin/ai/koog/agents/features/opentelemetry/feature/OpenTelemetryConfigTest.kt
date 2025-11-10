package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.features.opentelemetry.OpenTelemetrySpanAsserts.assertSpans
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.createAgent
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.testClock
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import ai.koog.agents.features.opentelemetry.integration.SpanAdapter
import ai.koog.agents.features.opentelemetry.mock.MockSpanExporter
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.utils.io.use
import io.opentelemetry.sdk.OpenTelemetrySdk
import kotlinx.coroutines.test.runTest
import java.util.Properties
import kotlin.test.Test
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

    @Test
    fun `test Open Telemetry feature default configuration`() = runTest {
        val strategy = strategy<String, String>("test-strategy") {
            nodeStart then nodeFinish
        }

        var actualServiceName: String? = null
        var actualServiceVersion: String? = null
        var actualIsVerbose: Boolean? = null

        createAgent(strategy = strategy) {
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
    fun `test custom configuration is applied`() = runTest {
        val strategy = strategy<String, String>("test-strategy") {
            nodeStart then nodeFinish
        }

        val expectedServiceName = "test-service-name"
        val expectedServiceVersion = "test-service-version"
        val expectedIsVerbose = true

        var actualServiceName: String? = null
        var actualServiceVersion: String? = null
        var actualIsVerbose: Boolean? = null

        createAgent(strategy = strategy) {
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
    fun `test filter is not allowed for open telemetry feature`() = runTest {
        val strategy = strategy<String, String>("test-strategy") {
            nodeStart then nodeFinish
        }

        val throwable = assertFails {
            createAgent(strategy = strategy) {
                install(OpenTelemetry) {
                    // Try to filter out all events. OpenTelemetryConfig should ignore this filter
                    setEventFilter { false }
                }
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

    @Test
    fun `test install Open Telemetry feature with custom sdk, should use provided sdk`() = runTest {
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
    fun `test custom sdk configuration emits correct spans`() = runTest {
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
                executor = mockExecutor,
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
    fun `test span adapter applies custom attribute to invoke agent span`() = runTest {
        MockSpanExporter().use { mockExporter ->

            val userPrompt = "What's the weather in Paris?"
            val agentId = "test-agent-id"
            val promptId = "test-prompt-id"
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
                executor = mockExecutor,
                model = model,
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
}
