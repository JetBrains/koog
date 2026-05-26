package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.agent.entity.AIAgentSubgraphBase.Companion.FINISH_NODE_PREFIX
import ai.koog.agents.core.agent.entity.AIAgentSubgraphBase.Companion.START_NODE_PREFIX
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
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes.Operation.OperationNameType
import ai.koog.agents.features.opentelemetry.integration.SpanAdapter
import ai.koog.agents.features.opentelemetry.mock.MockSpanExporter
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.utils.io.use
import io.opentelemetry.kotlin.createOpenTelemetry
import io.opentelemetry.kotlin.tracing.export.batchSpanProcessor
import io.opentelemetry.kotlin.tracing.export.simpleSpanProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

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
    fun `test custom exporter configuration emits correct spans`(agentType: AgentType) = runTest {
        MockSpanExporter().use { mockExporter ->

            val agent = createAgent(
                strategy = getSingleLLMCallStrategy(agentType),
                executor = defaultMockExecutor,
            ) {
                addSpanProcessor { simpleSpanProcessor(mockExporter) }
            }

            agent.run(USER_PROMPT_PARIS, null)
            // Wait for async span exports (Kotlin SDK exports on Dispatchers.Default)
            withContext(Dispatchers.Default) {
                withTimeoutOrNull(5.seconds) { mockExporter.isCollected.first { it } }
            }
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

            assertEquals(expectedSpanNames.sorted(), actualSpanNames.sorted())
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
                addSpanProcessor { simpleSpanProcessor(mockExporter) }

                // Add custom span adapter
                addSpanAdapter(adapter)
                setVerbose(true)
            }.use { agent ->
                agent.run("", null)
            }

            // Wait for async span exports (Kotlin SDK exports on Dispatchers.Default)
            withContext(Dispatchers.Default) {
                withTimeoutOrNull(5.seconds) { mockExporter.isCollected.first { it } }
            }

            val collectedSpans = mockExporter.collectedSpans
            assertTrue(collectedSpans.isNotEmpty(), "Spans should be created during agent execution")

            val conversationIdAttribute = GenAIAttributes.Conversation.Id(mockExporter.lastRunId)
            val operationNameAttribute = GenAIAttributes.Operation.Name(OperationNameType.INVOKE_AGENT)

            fun attributesMatches(attributes: Map<String, Any>): Boolean {
                val conversationIdAttributeExists =
                    attributes[conversationIdAttribute.key] == conversationIdAttribute.value
                val operationNameAttributeExists =
                    attributes[operationNameAttribute.key] == operationNameAttribute.value
                return conversationIdAttributeExists && operationNameAttributeExists
            }

            val actualInvokeAgentSpans = collectedSpans.filter { span ->
                attributesMatches(span.attributes)
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
                            "gen_ai.response.finish_reasons" to listOf(GenAIAttributes.Response.FinishReasonType.Stop.id),
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

    @Test
    fun `test addSpanProcessor with batchSpanProcessor exports spans through the SDK`() = runTest {
        MockSpanExporter().use { mockExporter ->
            val agent = createAgent(
                strategy = getSingleLLMCallStrategy(AgentType.Graph),
                executor = defaultMockExecutor,
            ) {
                addSpanProcessor { batchSpanProcessor(mockExporter) }
            }

            agent.run(USER_PROMPT_PARIS, null)
            // BatchSpanProcessor flushes asynchronously on its own schedule; closing the agent
            // (with shutdown-on-close) drains the queue. We use the existing isCollected flag
            // which fires on the first CreateAgentSpan that lands at the exporter - that span
            // is only delivered after a flush, so receiving it proves the batch path works.
            withContext(Dispatchers.Default) {
                withTimeoutOrNull(10.seconds) { mockExporter.isCollected.first { it } }
            }
            agent.close()

            assertTrue(
                mockExporter.collectedSpans.isNotEmpty(),
                "Spans should eventually reach the exporter when wrapped in batchSpanProcessor."
            )
        }
    }

    @Test
    fun testAddSpanExporterWrapsExporterInBatchProcessorAndDeliversSpans() = runTest {
        MockSpanExporter().use { mockExporter ->
            val agent = createAgent(
                strategy = getSingleLLMCallStrategy(AgentType.Graph),
                executor = defaultMockExecutor,
            ) {
                addSpanExporter(mockExporter)
            }

            agent.run(USER_PROMPT_PARIS, null)
            withContext(Dispatchers.Default) {
                withTimeoutOrNull(10.seconds) { mockExporter.isCollected.first { it } }
            }
            agent.close()

            assertTrue(
                mockExporter.collectedSpans.isNotEmpty(),
                "addSpanExporter must deliver spans through the batchSpanProcessor it registers internally"
            )
        }
    }

    @Test
    fun testAddResourceAttributesAppearsInBuildResourceMap() {
        val config = OpenTelemetryConfig()

        config.addResourceAttributes(
            mapOf(
                "custom.string" to "hello",
                "custom.long" to 42L,
                "custom.bool" to true,
                "custom.double" to 3.14,
            )
        )

        val resourceMap = config.buildResourceMap()

        assertEquals("hello", resourceMap["custom.string"])
        assertEquals(42L, resourceMap["custom.long"])
        assertEquals(true, resourceMap["custom.bool"])
        assertEquals(3.14, resourceMap["custom.double"])
    }

    @Test
    fun testSetServiceInfoWithNamespaceStoresNamespaceInBuildResourceMap() {
        val config = OpenTelemetryConfig()

        config.setServiceInfo("my-service", "1.0.0", "my-namespace")

        val resourceMap = config.buildResourceMap()

        assertEquals("my-service", resourceMap["service.name"])
        assertEquals("1.0.0", resourceMap["service.version"])
        assertEquals("my-namespace", resourceMap["service.namespace"])
    }

    @Test
    fun testSetServiceInfoWithoutNamespaceOmitsNamespaceFromBuildResourceMap() {
        val config = OpenTelemetryConfig()

        config.setServiceInfo("my-service", "1.0.0")

        val resourceMap = config.buildResourceMap()

        assertEquals("my-service", resourceMap["service.name"])
        assertEquals("1.0.0", resourceMap["service.version"])
        assertTrue(
            !resourceMap.containsKey("service.namespace"),
            "service.namespace key must be absent from buildResourceMap when namespace is not set"
        )
    }

    @Test
    fun testMultipleAddSpanProcessorCallsSendSpansToBothProcessors() = runTest {
        MockSpanExporter().use { exporter1 ->
            MockSpanExporter().use { exporter2 ->
                val agent = createAgent(
                    strategy = getSingleLLMCallStrategy(AgentType.Graph),
                    executor = defaultMockExecutor,
                ) {
                    addSpanProcessor { simpleSpanProcessor(exporter1) }
                    addSpanProcessor { simpleSpanProcessor(exporter2) }
                }

                agent.run(USER_PROMPT_PARIS, null)
                withContext(Dispatchers.Default) {
                    withTimeoutOrNull(5.seconds) { exporter1.isCollected.first { it } }
                    withTimeoutOrNull(5.seconds) { exporter2.isCollected.first { it } }
                }
                agent.close()

                assertTrue(exporter1.collectedSpans.isNotEmpty(), "First processor's exporter must receive spans when composite processor is used")
                assertTrue(exporter2.collectedSpans.isNotEmpty(), "Second processor's exporter must receive spans when composite processor is used")
            }
        }
    }

    @Test
    fun `test setSdk overrides feature configuration and uses the user-supplied SDK`() = runTest {
        MockSpanExporter().use { sdkExporter ->
            MockSpanExporter().use { ignoredExporter ->
                // User builds their own SDK directly with the Kotlin OTel DSL.
                val userSdk = createOpenTelemetry {
                    tracerProvider {
                        export { simpleSpanProcessor(sdkExporter) }
                    }
                }

                val agent = createAgent(
                    strategy = getSingleLLMCallStrategy(AgentType.Graph),
                    executor = defaultMockExecutor,
                ) {
                    setSdk(userSdk)
                    // Must be ignored - the user-supplied SDK is in charge.
                    addSpanProcessor { simpleSpanProcessor(ignoredExporter) }
                }

                agent.run(USER_PROMPT_PARIS, null)
                withContext(Dispatchers.Default) {
                    withTimeoutOrNull(5.seconds) { sdkExporter.isCollected.first { it } }
                }
                agent.close()

                assertTrue(
                    sdkExporter.collectedSpans.isNotEmpty(),
                    "User-supplied SDK's exporter should receive spans."
                )
                assertTrue(
                    ignoredExporter.collectedSpans.isEmpty(),
                    "Processor registered via addSpanProcessor must be ignored when setSdk is used."
                )
            }
        }
    }
}
