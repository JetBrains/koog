package ai.koog.prompt.executor.clients.bedrock

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.dsl.ModerationCategory
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.utils.time.KoogClock
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.bedrockruntime.model.ApplyGuardrailRequest
import aws.sdk.kotlin.services.bedrockruntime.model.ApplyGuardrailResponse
import aws.sdk.kotlin.services.bedrockruntime.model.ContentBlock
import aws.sdk.kotlin.services.bedrockruntime.model.ContentBlockDelta
import aws.sdk.kotlin.services.bedrockruntime.model.ContentBlockDeltaEvent
import aws.sdk.kotlin.services.bedrockruntime.model.ConversationRole
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseOutput
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseRequest
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseResponse
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseStreamOutput
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseStreamRequest
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseStreamResponse
import aws.sdk.kotlin.services.bedrockruntime.model.CountTokensRequest
import aws.sdk.kotlin.services.bedrockruntime.model.CountTokensResponse
import aws.sdk.kotlin.services.bedrockruntime.model.GetAsyncInvokeRequest
import aws.sdk.kotlin.services.bedrockruntime.model.GetAsyncInvokeResponse
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailAction
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailAssessment
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailContentFilter
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailContentFilterConfidence
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailContentFilterType
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailContentPolicyAction
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailContentPolicyAssessment
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailContentSource
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailStreamProcessingMode
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelRequest
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelResponse
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelWithBidirectionalStreamRequest
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelWithBidirectionalStreamResponse
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelWithResponseStreamResponse
import aws.sdk.kotlin.services.bedrockruntime.model.ListAsyncInvokesRequest
import aws.sdk.kotlin.services.bedrockruntime.model.ListAsyncInvokesResponse
import aws.sdk.kotlin.services.bedrockruntime.model.MessageStopEvent
import aws.sdk.kotlin.services.bedrockruntime.model.StartAsyncInvokeRequest
import aws.sdk.kotlin.services.bedrockruntime.model.StartAsyncInvokeResponse
import aws.sdk.kotlin.services.bedrockruntime.model.StopReason
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.random.Random.Default.nextInt
import kotlin.random.Random.Default.nextLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import aws.sdk.kotlin.services.bedrockruntime.model.Message as BedrockMessage

class BedrockLLMClientTest {

    companion object {
        /*
         * This is obscure, but it has a purpose.
         *
         * 1. GuardrailContentSource is a class with a static initializer (<clinit>)
         * 2. GuardrailContentSource.values() returns a list of its inheritors
         * 3. GuardrailContentSource.Input and GuardrailContentSource.Output are inheritors of GuardrailContentSource and are Kotlin objects (singletons)
         * 4. Accessing GuardrailContentSource.Input or GuardrailContentSource.Output triggers their own class initialization, which is also synchronized by the JVM
         * 5. During GuardrailContentSource initialization, we eagerly construct `values` list, which references Input and Output
         * 6. If two threads initialize these in different orders:
         *    - Thread A starts initializing GuardrailContentSource and, while building `values`, tries to initialize Output
         *    - Thread B starts initializing Output and, as part of that, needs GuardrailContentSource to be initialized first
         * 7. This creates a circular wait:
         *    - Thread A holds GuardrailContentSource init lock and waits for Output
         *    - Thread B holds Output init lock and waits for GuardrailContentSource
         * 8. Result: JVM class initialization deadlock.
         *
         * We can avoid this by force-initing values before all tests, so we do
         * Alternatively, we could run tests sequentially, but that's not ideal for CI speed
         * */
        @BeforeAll
        @JvmStatic
        fun setUp() {
            print("GuardrailContentSource values: ${GuardrailContentSource.values()}")
        }
    }

    @Test
    fun `can create BedrockLLMClient`() {
        val client = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = BedrockClientSettings(region = BedrockRegions.US_EAST_1.regionCode),
            clock = KoogClock.System
        )

        assertNotNull(client)
    }

    @Test
    fun `can create BedrockLLMClient with API key`() {
        val client = BedrockLLMClient(
            identityProvider = StaticBearerTokenProvider(token = "test-token"),
            settings = BedrockClientSettings(region = BedrockRegions.US_EAST_1.regionCode),
            clock = KoogClock.System
        )
        assertNotNull(client)
    }

    @Test
    fun `can create BedrockModel with custom inference prefix`() {
        val originalModel = BedrockModels.AnthropicClaude4Sonnet

        val globalModel = originalModel.withInferenceProfile(BedrockInferencePrefixes.GLOBAL.prefix)
        val euModel = originalModel.withInferenceProfile(BedrockInferencePrefixes.EU.prefix)
        val apModel = originalModel.withInferenceProfile(BedrockInferencePrefixes.AP.prefix)

        assertTrue(originalModel.id.startsWith(BedrockInferencePrefixes.US.prefix))
        assertTrue(originalModel.id.contains("us.anthropic"))

        assertTrue(globalModel.id.startsWith(BedrockInferencePrefixes.GLOBAL.prefix))
        assertFalse(globalModel.id.contains("us.anthropic"))

        assertTrue(euModel.id.startsWith(BedrockInferencePrefixes.EU.prefix))
        assertFalse(euModel.id.contains("us.anthropic"))

        assertTrue(apModel.id.startsWith(BedrockInferencePrefixes.AP.prefix))
        assertFalse(apModel.id.contains("us.anthropic"))

        // Verify global model properties
        assertEquals(originalModel.provider, globalModel.provider)
        assertEquals(originalModel.capabilities, globalModel.capabilities)
        assertEquals(originalModel.contextLength, globalModel.contextLength)
        assertEquals(originalModel.maxOutputTokens, globalModel.maxOutputTokens)

        // Verify EU model properties
        assertEquals(originalModel.provider, euModel.provider)
        assertEquals(originalModel.capabilities, euModel.capabilities)
        assertEquals(originalModel.contextLength, euModel.contextLength)
        assertEquals(originalModel.maxOutputTokens, euModel.maxOutputTokens)

        // Verify AP model properties
        assertEquals(originalModel.provider, apModel.provider)
        assertEquals(originalModel.capabilities, apModel.capabilities)
        assertEquals(originalModel.contextLength, apModel.contextLength)
        assertEquals(originalModel.maxOutputTokens, apModel.maxOutputTokens)
    }

    @Test
    fun `can apply inference profile prefix to embedding model with default null prefix`() {
        val originalModel = BedrockModels.Embeddings.CohereEmbedEnglishV3
        val euModel = originalModel.withInferenceProfile(BedrockInferencePrefixes.EU.prefix)
        val apModel = originalModel.withInferenceProfile(BedrockInferencePrefixes.AP.prefix)

        // Default should not have any prefix
        assertFalse(originalModel.id.contains(".cohere.embed-english-v3"))
        assertFalse(originalModel.id.startsWith(BedrockInferencePrefixes.EU.prefix + "."))
        assertFalse(originalModel.id.startsWith(BedrockInferencePrefixes.AP.prefix + "."))

        // Overridden should have explicit prefix
        assertTrue(euModel.id.startsWith(BedrockInferencePrefixes.EU.prefix + "."))
        assertTrue(apModel.id.startsWith(BedrockInferencePrefixes.AP.prefix + "."))

        // Make sure model ids are as expected
        assertEquals("${BedrockInferencePrefixes.EU.prefix}.cohere.embed-english-v3", euModel.id)
        assertEquals("${BedrockInferencePrefixes.AP.prefix}.cohere.embed-english-v3", apModel.id)

        // Capabilities and other properties should remain unchanged
        assertEquals(originalModel.provider, euModel.provider)
        assertEquals(originalModel.capabilities, euModel.capabilities)
        assertEquals(originalModel.contextLength, euModel.contextLength)
    }

    @Test
    fun `withInferencePrefix throws exception for non-Bedrock models`() {
        val nonBedrockModel = LLModel(
            provider = LLMProvider.Anthropic,
            id = "claude-3-sonnet-20240229",
            capabilities = listOf(LLMCapability.Completion),
            contextLength = 200_000
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            nonBedrockModel.withInferenceProfile("eu")
        }

        assertNotNull(exception.message, "Exception message should not be null")
        assertTrue(exception.message!!.contains("withInferenceProfile() can only be used with Bedrock models"))
        assertTrue(exception.message!!.contains("AnthropicLLMProvider"))
    }

    @Test
    fun `client configuration options work correctly`() {
        // given
        val requestTimeoutMillis = nextLong(1000, 2000)
        val connectTimeoutMillis = nextLong(100, 200)
        val socketTimeoutMillis = nextLong(200, 300)
        val maxRetries = nextInt(5, 10)

        // when
        val customSettings = BedrockClientSettings(
            region = BedrockRegions.EU_WEST_1.regionCode,
            endpointUrl = "https://custom.endpoint.com",
            maxRetries = maxRetries,
            enableLogging = true,
            timeoutConfig = ConnectionTimeoutConfig(
                requestTimeoutMillis = requestTimeoutMillis,
                connectTimeoutMillis = connectTimeoutMillis,
                socketTimeoutMillis = socketTimeoutMillis
            )
        )

        val client = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = customSettings,
            clock = KoogClock.System
        )

        // then
        client shouldNotBeNull {
            bedrockClient.config shouldNotBeNull {
                callTimeout shouldBe requestTimeoutMillis.milliseconds
                endpointUrl.toString() shouldBe "https://custom.endpoint.com"
                region shouldBe BedrockRegions.EU_WEST_1.regionCode
                retryStrategy.config.maxAttempts shouldBe maxRetries

                httpClient.config shouldNotBeNull {
                    socketReadTimeout.inWholeMilliseconds shouldBe socketTimeoutMillis
                    socketWriteTimeout.inWholeMilliseconds shouldBe socketTimeoutMillis
                    connectTimeout.inWholeMilliseconds shouldBe connectTimeoutMillis
                }
            }
        }
    }

    @Test
    fun testToolCallGeneration() = runTest {
        val tools = listOf(
            ToolDescriptor(
                name = "get_weather",
                description = "Get current weather for a city",
                requiredParameters = listOf(
                    ToolParameterDescriptor("city", "The city name", ToolParameterType.String)
                ),
                optionalParameters = listOf(
                    ToolParameterDescriptor("units", "Temperature units", ToolParameterType.String)
                )
            )
        )

        val prompt = Prompt.build("test") {
            user("What's the weather in Paris?")
        }

        // Mock client for testing tool call request generation
        val client = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = BedrockClientSettings(region = BedrockRegions.US_EAST_1.regionCode),
            clock = KoogClock.System
        )

        // Verify that Claude Haiku supports tools
        val claudeModel = BedrockModels.AnthropicClaude4_5Haiku
        // This should not throw an exception for models with tool support
        assertFails {
            client.execute(prompt, claudeModel, tools)
        }
    }

    @Execution(ExecutionMode.SAME_THREAD)
    @Test
    fun `can create BedrockLLMClient with moderation guardrails settings`() = runTest {
        val guardrailsSettings = BedrockGuardrailsSettings(
            guardrailIdentifier = "test-guardrail",
            guardrailVersion = "1.0"
        )

        val mockClient = object : BedrockRuntimeClient {
            override suspend fun applyGuardrail(input: ApplyGuardrailRequest): ApplyGuardrailResponse {
                return ApplyGuardrailResponse {
                    action = GuardrailAction.GuardrailIntervened
                    assessments = listOf(
                        GuardrailAssessment {
                            contentPolicy = GuardrailContentPolicyAssessment {
                                filters = listOf(
                                    GuardrailContentFilter {
                                        type = GuardrailContentFilterType.Hate
                                        action = GuardrailContentPolicyAction.None
                                        detected = true
                                        confidence = GuardrailContentFilterConfidence.High
                                    },
                                    GuardrailContentFilter {
                                        type = GuardrailContentFilterType.Sexual
                                        action = GuardrailContentPolicyAction.Blocked
                                        detected = true
                                        confidence = GuardrailContentFilterConfidence.Low
                                    },
                                    GuardrailContentFilter {
                                        type = GuardrailContentFilterType.Misconduct
                                        action = GuardrailContentPolicyAction.None
                                        confidence = GuardrailContentFilterConfidence.Medium
                                        detected = true
                                    }
                                )
                            }
                        }
                    )
                    outputs = emptyList()
                }
            }

            override val config: BedrockRuntimeClient.Config
                get() = throw UnsupportedOperationException("config not implemented in mock client")

            override suspend fun converse(input: ConverseRequest): ConverseResponse =
                throw UnsupportedOperationException("converse not implemented in mock client")

            override suspend fun <T> converseStream(
                input: ConverseStreamRequest,
                block: suspend (ConverseStreamResponse) -> T
            ): T =
                throw UnsupportedOperationException("converseStream not implemented in mock client")

            override suspend fun countTokens(input: CountTokensRequest): CountTokensResponse =
                throw UnsupportedOperationException("countTokens not implemented in mock client")

            override suspend fun getAsyncInvoke(input: GetAsyncInvokeRequest): GetAsyncInvokeResponse =
                throw UnsupportedOperationException("getAsyncInvoke not implemented in mock client")

            override suspend fun invokeModel(input: InvokeModelRequest): InvokeModelResponse =
                throw UnsupportedOperationException("invokeModel not implemented in mock client")

            override suspend fun <T> invokeModelWithBidirectionalStream(
                input: InvokeModelWithBidirectionalStreamRequest,
                block: suspend (InvokeModelWithBidirectionalStreamResponse) -> T
            ): T =
                throw UnsupportedOperationException("invokeModelWithBidirectionalStream not implemented in mock client")

            override suspend fun <T> invokeModelWithResponseStream(
                input: InvokeModelWithResponseStreamRequest,
                block: suspend (InvokeModelWithResponseStreamResponse) -> T
            ): T =
                throw UnsupportedOperationException("invokeModelWithResponseStream not implemented in mock client")

            override suspend fun listAsyncInvokes(input: ListAsyncInvokesRequest): ListAsyncInvokesResponse =
                throw UnsupportedOperationException("listAsyncInvokes not implemented in mock client")

            override suspend fun startAsyncInvoke(input: StartAsyncInvokeRequest): StartAsyncInvokeResponse =
                throw UnsupportedOperationException("startAsyncInvoke not implemented in mock client")

            override fun close() {
                print("closing")
            }
        }

        val client = BedrockLLMClient(
            mockClient,
            moderationGuardrailsSettings = guardrailsSettings
        )

        try {
            val prompt = Prompt.build("test") {
                user("This is a test prompt")
            }
            val model = BedrockModels.AnthropicClaude4Sonnet

            val moderationResult = client.moderate(prompt, model)
            assertEquals(true, moderationResult.isHarmful)
            assertEquals(true, moderationResult.violatesCategory(ModerationCategory.Hate))
            assertEquals(true, moderationResult.violatesCategory(ModerationCategory.Sexual))
            assertEquals(true, moderationResult.violatesCategory(ModerationCategory.Misconduct))
            assertEquals(false, moderationResult.violatesCategory(ModerationCategory.Illicit))
            assertEquals(null, moderationResult.categories[ModerationCategory.Illicit])
        } finally {
            client.close()
        }
    }

    @Test
    fun `moderate method throws exception when moderation guardrails settings are not provided`() = runTest {
        // Create client without moderation guardrails settings
        val client = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = BedrockClientSettings(region = BedrockRegions.US_EAST_1.regionCode),
            clock = KoogClock.System
        )

        val prompt = Prompt.build("test") {
            user("This is a test prompt")
        }
        val model = BedrockModels.AnthropicClaude4Sonnet

        // Verify that moderate method throws an exception because moderationGuardrailsSettings wasn't provided
        assertFailsWith<LLMClientException> {
            client.moderate(prompt, model)
        }
    }

    @Execution(ExecutionMode.SAME_THREAD)
    @Test
    fun `moderate calls guardrails once for Request-only prompts`() = runTest {
        val guardrailsSettings = BedrockGuardrailsSettings(
            guardrailIdentifier = "test-guardrail",
            guardrailVersion = "1.0"
        )

        var applyGuardrailCallCount = 0

        val mockClient = createCountingMockClient { applyGuardrailCallCount++ }

        val client = BedrockLLMClient(
            mockClient,
            moderationGuardrailsSettings = guardrailsSettings
        )

        try {
            // Prompt with only User (Request) message
            val prompt = Prompt.build("test") {
                user("hi")
            }
            val model = BedrockModels.AnthropicClaude4Sonnet

            client.moderate(prompt, model)

            assertEquals(1, applyGuardrailCallCount, "Should call applyGuardrail exactly once for Request-only prompts")
        } finally {
            client.close()
        }
    }

    @Execution(ExecutionMode.SAME_THREAD)
    @Test
    fun `moderate calls guardrails twice for prompts with both Request and Response`() = runTest {
        val guardrailsSettings = BedrockGuardrailsSettings(
            guardrailIdentifier = "test-guardrail",
            guardrailVersion = "1.0"
        )

        var applyGuardrailCallCount = 0

        val mockClient = createCountingMockClient { applyGuardrailCallCount++ }

        val client = BedrockLLMClient(
            mockClient,
            moderationGuardrailsSettings = guardrailsSettings
        )

        try {
            // Prompt with both User (Request) and Assistant (Response) messages
            val prompt = Prompt.build("test") {
                user("What is 2+2?")
                assistant("2+2 equals 4")
            }
            val model = BedrockModels.AnthropicClaude4Sonnet

            client.moderate(prompt, model)

            assertEquals(
                2,
                applyGuardrailCallCount,
                "Should call applyGuardrail exactly twice for prompts with both Request and Response"
            )
        } finally {
            client.close()
        }
    }

    @Execution(ExecutionMode.SAME_THREAD)
    @Test
    fun `moderate calls guardrails once for Response-only prompts`() = runTest {
        val guardrailsSettings = BedrockGuardrailsSettings(
            guardrailIdentifier = "test-guardrail",
            guardrailVersion = "1.0"
        )

        var applyGuardrailCallCount = 0

        val mockClient = createCountingMockClient { applyGuardrailCallCount++ }

        val client = BedrockLLMClient(
            mockClient,
            moderationGuardrailsSettings = guardrailsSettings
        )

        try {
            // Prompt with only Assistant (Response) message
            val prompt = Prompt.build("test") {
                assistant("Hello, how can I help?")
            }
            val model = BedrockModels.AnthropicClaude4Sonnet

            client.moderate(prompt, model)

            assertEquals(
                1,
                applyGuardrailCallCount,
                "Should call applyGuardrail exactly once for Response-only prompts"
            )
        } finally {
            client.close()
        }
    }

    @Execution(ExecutionMode.SAME_THREAD)
    @Test
    fun `converse API includes guardrail config when settings are provided`() = runTest {
        // Note: var is safe here - runTest uses single-threaded dispatcher
        var capturedRequest: ConverseRequest? = null
        val mockClient = createMockBedrockClient(
            onConverse = {
                capturedRequest = it
                defaultConverseResponse()
            },
        )

        val client = BedrockLLMClient(
            mockClient,
            apiMethod = BedrockAPIMethod.Converse,
            moderationGuardrailsSettings = BedrockGuardrailsSettings("test-guardrail-id", "2")
        )

        try {
            client.execute(Prompt.build("test") { user("Hello") }, BedrockModels.AnthropicClaude4Sonnet, emptyList())

            val guardrailConfig = requireNotNull(capturedRequest?.guardrailConfig) { "Guardrail config should be set" }
            assertEquals("test-guardrail-id", guardrailConfig.guardrailIdentifier)
            assertEquals("2", guardrailConfig.guardrailVersion)
        } finally {
            client.close()
        }
    }

    @Execution(ExecutionMode.SAME_THREAD)
    @Test
    fun `converseStream API includes guardrail config when settings are provided`() = runTest {
        // Note: var is safe here - runTest uses single-threaded dispatcher
        var capturedRequest: ConverseStreamRequest? = null
        val mockClient = createMockBedrockClient(
            onConverseStream = {
                capturedRequest = it
                defaultConverseStreamResponse()
            },
        )

        val client = BedrockLLMClient(
            mockClient,
            apiMethod = BedrockAPIMethod.Converse,
            moderationGuardrailsSettings = BedrockGuardrailsSettings("test-guardrail-id", "2")
        )

        try {
            client.executeStreaming(Prompt.build("test") { user("Hello") }, BedrockModels.AnthropicClaude4Sonnet, emptyList()).toList()

            val guardrailConfig = requireNotNull(capturedRequest?.guardrailConfig) { "Guardrail config should be set" }
            assertEquals("test-guardrail-id", guardrailConfig.guardrailIdentifier)
            assertEquals("2", guardrailConfig.guardrailVersion)
            // streamProcessingMode defaults to Sync (synchronous processing)
            assertEquals(GuardrailStreamProcessingMode.Sync, guardrailConfig.streamProcessingMode)
        } finally {
            client.close()
        }
    }

    @Execution(ExecutionMode.SAME_THREAD)
    @Test
    fun `converse API does not include guardrail config when settings are null`() = runTest {
        // Note: var is safe here - runTest uses single-threaded dispatcher
        var capturedRequest: ConverseRequest? = null
        val mockClient = createMockBedrockClient(
            onConverse = {
                capturedRequest = it
                defaultConverseResponse()
            },
        )

        val client = BedrockLLMClient(
            mockClient,
            apiMethod = BedrockAPIMethod.Converse,
            moderationGuardrailsSettings = null,
        )

        try {
            client.execute(Prompt.build("test") { user("Hello") }, BedrockModels.AnthropicClaude4Sonnet, emptyList())

            val request = requireNotNull(capturedRequest) { "Request should have been captured" }
            assertEquals(null, request.guardrailConfig, "Guardrail config should be null")
        } finally {
            client.close()
        }
    }

    @Execution(ExecutionMode.SAME_THREAD)
    @Test
    fun `converseStream API does not include guardrail config when settings are null`() = runTest {
        // Note: var is safe here - runTest uses single-threaded dispatcher
        var capturedRequest: ConverseStreamRequest? = null
        val mockClient = createMockBedrockClient(
            onConverseStream = {
                capturedRequest = it
                defaultConverseStreamResponse()
            },
        )

        val client = BedrockLLMClient(
            mockClient,
            apiMethod = BedrockAPIMethod.Converse,
            moderationGuardrailsSettings = null,
        )

        try {
            client.executeStreaming(Prompt.build("test") { user("Hello") }, BedrockModels.AnthropicClaude4Sonnet, emptyList()).toList()

            val request = requireNotNull(capturedRequest) { "Request should have been captured" }
            assertEquals(null, request.guardrailConfig, "Guardrail config should be null")
        } finally {
            client.close()
        }
    }

    // Helper function to create a counting mock client - delegates to unified mock
    private fun createCountingMockClient(onApplyGuardrail: () -> Unit): BedrockRuntimeClient =
        createMockBedrockClient(
            onApplyGuardrail = {
                onApplyGuardrail()
                defaultGuardrailResponse()
            },
        )

    // Unified mock client with configurable callbacks
    private fun createMockBedrockClient(
        onConverse: (ConverseRequest) -> ConverseResponse = { defaultConverseResponse() },
        onConverseStream: (ConverseStreamRequest) -> ConverseStreamResponse = { defaultConverseStreamResponse() },
        onApplyGuardrail: (ApplyGuardrailRequest) -> ApplyGuardrailResponse = { defaultGuardrailResponse() },
    ): BedrockRuntimeClient = object : BedrockRuntimeClient {
        override suspend fun converse(input: ConverseRequest) = onConverse(input)
        override suspend fun <T> converseStream(input: ConverseStreamRequest, block: suspend (ConverseStreamResponse) -> T): T = block(onConverseStream(input))
        override suspend fun applyGuardrail(input: ApplyGuardrailRequest) = onApplyGuardrail(input)
        override val config: BedrockRuntimeClient.Config get() = throw UnsupportedOperationException()
        override suspend fun countTokens(input: CountTokensRequest) = throw UnsupportedOperationException()
        override suspend fun getAsyncInvoke(input: GetAsyncInvokeRequest) = throw UnsupportedOperationException()
        override suspend fun invokeModel(input: InvokeModelRequest) = throw UnsupportedOperationException()
        override suspend fun <T> invokeModelWithBidirectionalStream(input: InvokeModelWithBidirectionalStreamRequest, block: suspend (InvokeModelWithBidirectionalStreamResponse) -> T): T = throw UnsupportedOperationException()
        override suspend fun <T> invokeModelWithResponseStream(input: InvokeModelWithResponseStreamRequest, block: suspend (InvokeModelWithResponseStreamResponse) -> T): T = throw UnsupportedOperationException()
        override suspend fun listAsyncInvokes(input: ListAsyncInvokesRequest) = throw UnsupportedOperationException()
        override suspend fun startAsyncInvoke(input: StartAsyncInvokeRequest) = throw UnsupportedOperationException()
        override fun close() {}
    }

    private fun defaultConverseResponse() = ConverseResponse {
        output = ConverseOutput.Message(
            BedrockMessage {
                role = ConversationRole.Assistant
                content = listOf(ContentBlock.Text("Hello!"))
            }
        )
        stopReason = StopReason.EndTurn
    }

    private fun defaultConverseStreamResponse() = ConverseStreamResponse {
        stream = kotlinx.coroutines.flow.flow {
            emit(
                ConverseStreamOutput.ContentBlockDelta(
                    ContentBlockDeltaEvent {
                        delta = ContentBlockDelta.Text("Hello!")
                        contentBlockIndex = 0
                    }
                )
            )
            emit(
                ConverseStreamOutput.MessageStop(
                    MessageStopEvent { stopReason = StopReason.EndTurn }
                )
            )
        }
    }

    private fun defaultGuardrailResponse() = ApplyGuardrailResponse {
        action = GuardrailAction.None
        assessments = emptyList()
        outputs = emptyList()
    }

    @Test
    fun `execute throws IllegalArgumentException for TitanEmbedding models`() = runTest {
        val client = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = BedrockClientSettings(region = BedrockRegions.US_EAST_1.regionCode),
            clock = KoogClock.System
        )
        val prompt = Prompt.build("test") {
            user("Get embeddings for this.")
        }
        val titanModel = BedrockModels.Embeddings.AmazonTitanEmbedText
        assertFailsWith<IllegalArgumentException> {
            client.execute(prompt, titanModel, emptyList())
        }
    }

    @Test
    fun `execute throws IllegalArgumentException for Cohere models`() = runTest {
        val client = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = BedrockClientSettings(region = BedrockRegions.US_EAST_1.regionCode),
            clock = KoogClock.System
        )
        val prompt = Prompt.build("test") {
            user("Get Cohere embeddings for this.")
        }
        val cohereModel = BedrockModels.Embeddings.CohereEmbedEnglishV3
        assertFailsWith<IllegalArgumentException> {
            client.execute(prompt, cohereModel, emptyList())
        }
    }

    @Test
    fun `execute throws IllegalArgumentException for model without Completion capability`() = runTest {
        val client = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = BedrockClientSettings(region = BedrockRegions.US_EAST_1.regionCode),
            clock = KoogClock.System
        )
        val noCompletionModel = LLModel(
            provider = LLMProvider.Bedrock,
            id = "some.bedrock.model-without-completion",
            capabilities = listOf(LLMCapability.Embed),
            contextLength = 1024
        )
        val prompt = Prompt.build("test") {
            user("Some input")
        }
        assertFailsWith<IllegalArgumentException> {
            client.execute(prompt, noCompletionModel, emptyList())
        }
    }

    @Test
    fun `executeStreaming throws IllegalArgumentException for TitanEmbedding models`() = runTest {
        val client = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = BedrockClientSettings(region = BedrockRegions.US_EAST_1.regionCode),
            clock = KoogClock.System
        )
        val prompt = Prompt.build("test") {
            user("Get embeddings for this.")
        }
        val titanModel = BedrockModels.Embeddings.AmazonTitanEmbedText
        assertFailsWith<IllegalArgumentException> {
            client.executeStreaming(prompt, titanModel, emptyList()).toList()
        }
    }

    @Test
    fun `executeStreaming throws IllegalArgumentException for Cohere models`() = runTest {
        val client = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = BedrockClientSettings(region = BedrockRegions.US_EAST_1.regionCode),
            clock = KoogClock.System
        )
        val prompt = Prompt.build("test") {
            user("Get Cohere embeddings for this.")
        }
        val cohereModel = BedrockModels.Embeddings.CohereEmbedEnglishV3
        assertFailsWith<IllegalArgumentException> {
            client.executeStreaming(prompt, cohereModel, emptyList()).toList()
        }
    }

    @Test
    fun `executeStreaming throws IllegalArgumentException for model without Completion capability`() = runTest {
        val client = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = BedrockClientSettings(region = BedrockRegions.US_EAST_1.regionCode),
            clock = KoogClock.System
        )
        val noCompletionModel = LLModel(
            provider = LLMProvider.Bedrock,
            id = "some.bedrock.model-without-completion",
            capabilities = listOf(LLMCapability.Embed),
            contextLength = 1024
        )
        val prompt = Prompt.build("test") {
            user("Some input")
        }
        assertFailsWith<IllegalArgumentException> {
            client.executeStreaming(prompt, noCompletionModel, emptyList()).toList()
        }
    }

    @Test
    fun `getBedrockModelFamily returns correct families for known models`() {
        val client = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = BedrockClientSettings(region = BedrockRegions.US_EAST_1.regionCode),
            clock = KoogClock.System
        )

        // Test known model families
        val anthropicModel = LLModel(
            provider = LLMProvider.Bedrock,
            id = "anthropic.claude-3-sonnet-20240229-v1:0",
            capabilities = listOf(LLMCapability.Completion),
            contextLength = 200_000
        )
        assertEquals(BedrockModelFamilies.AnthropicClaude, client.getBedrockModelFamily(anthropicModel))

        val novaModel = LLModel(
            provider = LLMProvider.Bedrock,
            id = "amazon.nova-micro-v1:0",
            capabilities = listOf(LLMCapability.Completion),
            contextLength = 128_000
        )
        assertEquals(BedrockModelFamilies.AmazonNova, client.getBedrockModelFamily(novaModel))

        val llamaModel = LLModel(
            provider = LLMProvider.Bedrock,
            id = "meta.llama3-1-8b-instruct-v1:0",
            capabilities = listOf(LLMCapability.Completion),
            contextLength = 128_000
        )
        assertEquals(BedrockModelFamilies.Meta, client.getBedrockModelFamily(llamaModel))

        val titanModel = LLModel(
            provider = LLMProvider.Bedrock,
            id = "amazon.titan-embed-text-v1",
            capabilities = listOf(LLMCapability.Embed),
            contextLength = 8_192
        )
        assertEquals(BedrockModelFamilies.TitanEmbedding, client.getBedrockModelFamily(titanModel))

        val cohereModel = LLModel(
            provider = LLMProvider.Bedrock,
            id = "cohere.embed-english-v3",
            capabilities = listOf(LLMCapability.Embed),
            contextLength = 512
        )
        assertEquals(BedrockModelFamilies.Cohere, client.getBedrockModelFamily(cohereModel))

        val kimiModel = LLModel(
            provider = LLMProvider.Bedrock,
            id = "moonshot.kimi-k2-thinking",
            capabilities = listOf(LLMCapability.Completion, LLMCapability.Tools),
            contextLength = 256_000
        )
        assertEquals(BedrockModelFamilies.MoonshotKimi, client.getBedrockModelFamily(kimiModel))

        // Kimi K2.5 (moonshotai prefix)
        val kimiK25Model = LLModel(
            provider = LLMProvider.Bedrock,
            id = "moonshotai.kimi-k2.5",
            capabilities = listOf(LLMCapability.Completion, LLMCapability.Tools),
            contextLength = 256_000
        )
        assertEquals(BedrockModelFamilies.MoonshotKimi, client.getBedrockModelFamily(kimiK25Model))

        // Google Gemma
        val gemmaModel = LLModel(
            provider = LLMProvider.Bedrock,
            id = "google.gemma-3-12b-it",
            capabilities = listOf(LLMCapability.Completion),
            contextLength = 128_000
        )
        assertEquals(BedrockModelFamilies.GoogleGemma, client.getBedrockModelFamily(gemmaModel))

        // MiniMax
        val minimaxModel = LLModel(
            provider = LLMProvider.Bedrock,
            id = "minimax.minimax-m2.5",
            capabilities = listOf(LLMCapability.Completion),
            contextLength = 1_000_000
        )
        assertEquals(BedrockModelFamilies.MiniMax, client.getBedrockModelFamily(minimaxModel))

        // OpenAI GPT-OSS
        val gptOssModel = LLModel(
            provider = LLMProvider.Bedrock,
            id = "openai.gpt-oss-120b-1:0",
            capabilities = listOf(LLMCapability.Completion),
            contextLength = 128_000
        )
        assertEquals(BedrockModelFamilies.OpenAI, client.getBedrockModelFamily(gptOssModel))

        // Cohere Embed v4 (with inference profile prefix)
        val cohereV4Model = LLModel(
            provider = LLMProvider.Bedrock,
            id = "us.cohere.embed-v4:0",
            capabilities = listOf(LLMCapability.Embed),
            contextLength = 512
        )
        assertEquals(BedrockModelFamilies.Cohere, client.getBedrockModelFamily(cohereV4Model))
    }

    @Test
    fun `getBedrockModelFamily throws exception for unsupported model without fallback`() {
        val client = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = BedrockClientSettings(region = BedrockRegions.US_EAST_1.regionCode),
            clock = KoogClock.System
        )

        val unsupportedModel = LLModel(
            provider = LLMProvider.Bedrock,
            id = "unsupported.new-model-v1:0",
            capabilities = listOf(LLMCapability.Completion),
            contextLength = 100_000
        )

        val exception = assertFailsWith<LLMClientException> {
            client.getBedrockModelFamily(unsupportedModel)
        }

        assertTrue(exception.message!!.contains("Model unsupported.new-model-v1:0 is not a supported Bedrock model"))
    }

    @Test
    fun `getBedrockModelFamily uses fallback for unsupported model when fallback is configured`() {
        val fallbackFamily = BedrockModelFamilies.AnthropicClaude

        val client = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = BedrockClientSettings(
                region = BedrockRegions.US_EAST_1.regionCode,
                fallbackModelFamily = fallbackFamily
            ),
            clock = KoogClock.System
        )

        val unsupportedModel = LLModel(
            provider = LLMProvider.Bedrock,
            id = "unsupported.new-model-v1:0",
            capabilities = listOf(LLMCapability.Completion),
            contextLength = 100_000
        )

        val result = client.getBedrockModelFamily(unsupportedModel)
        assertEquals(fallbackFamily, result)
    }

    @Test
    fun `getBedrockModelFamily uses different fallback families correctly`() {
        // Test with AnthropicClaude fallback
        val anthropicClient = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = BedrockClientSettings(
                region = BedrockRegions.US_EAST_1.regionCode,
                fallbackModelFamily = BedrockModelFamilies.AnthropicClaude
            ),
            clock = KoogClock.System
        )

        // Test with Meta fallback
        val metaClient = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = BedrockClientSettings(
                region = BedrockRegions.US_EAST_1.regionCode,
                fallbackModelFamily = BedrockModelFamilies.Meta
            ),
            clock = KoogClock.System
        )

        val unsupportedModel = LLModel(
            provider = LLMProvider.Bedrock,
            id = "unsupported.new-model-v1:0",
            capabilities = listOf(LLMCapability.Completion),
            contextLength = 100_000
        )

        assertEquals(BedrockModelFamilies.AnthropicClaude, anthropicClient.getBedrockModelFamily(unsupportedModel))
        assertEquals(BedrockModelFamilies.Meta, metaClient.getBedrockModelFamily(unsupportedModel))
    }

    @Test
    fun `primary constructor accepts fallback parameter`() {
        val mockClient = createCountingMockClient { }
        val fallbackFamily = BedrockModelFamilies.AmazonNova

        val client = BedrockLLMClient(
            bedrockClient = mockClient,
            moderationGuardrailsSettings = null,
            fallbackModelFamily = fallbackFamily,
            clock = KoogClock.System
        )

        val unsupportedModel = LLModel(
            provider = LLMProvider.Bedrock,
            id = "unsupported.new-model-v1:0",
            capabilities = listOf(LLMCapability.Completion),
            contextLength = 100_000
        )

        val result = client.getBedrockModelFamily(unsupportedModel)
        assertEquals(fallbackFamily, result)

        client.close()
    }

    @Test
    fun `fallback model family null by default in settings`() {
        val defaultSettings = BedrockClientSettings()
        assertEquals(null, defaultSettings.fallbackModelFamily)
    }

    @Test
    fun `getBedrockModelFamily requires Bedrock provider`() {
        val client = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = BedrockClientSettings(region = BedrockRegions.US_EAST_1.regionCode),
            clock = KoogClock.System
        )

        val nonBedrockModel = LLModel(
            provider = LLMProvider.Anthropic,
            id = "claude-3-sonnet-20240229",
            capabilities = listOf(LLMCapability.Completion),
            contextLength = 200_000
        )

        assertFailsWith<IllegalArgumentException> {
            client.getBedrockModelFamily(nonBedrockModel)
        }
    }

    @Test
    fun `BedrockClientSettings with fallback model family works correctly`() {
        val fallbackFamily = BedrockModelFamilies.AmazonNova
        val settings = BedrockClientSettings(
            region = BedrockRegions.EU_WEST_1.regionCode,
            endpointUrl = "https://custom.endpoint.com",
            maxRetries = 5,
            enableLogging = true,
            timeoutConfig = ConnectionTimeoutConfig(
                requestTimeoutMillis = 120_000,
                connectTimeoutMillis = 10_000,
                socketTimeoutMillis = 120_000
            ),
            fallbackModelFamily = fallbackFamily
        )

        assertEquals(fallbackFamily, settings.fallbackModelFamily)
        assertEquals(BedrockRegions.EU_WEST_1.regionCode, settings.region)
        assertEquals("https://custom.endpoint.com", settings.endpointUrl)
        assertEquals(5, settings.maxRetries)
        assertEquals(true, settings.enableLogging)
    }

    @Test
    fun `MoonshotKimiK2Thinking model has correct properties`() {
        val model = BedrockModels.MoonshotKimiK2Thinking

        assertEquals("moonshot.kimi-k2-thinking", model.id)
        assertEquals(LLMProvider.Bedrock, model.provider)
        assertEquals(256_000, model.contextLength)
        val capabilities = assertNotNull(model.capabilities)
        assertTrue(capabilities.contains(LLMCapability.Completion))
        assertTrue(capabilities.contains(LLMCapability.Tools))
        assertTrue(capabilities.contains(LLMCapability.Temperature))
    }

    @Test
    fun `Kimi K2 Thinking model requires Converse API for execute`() = runTest {
        val client = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = BedrockClientSettings(
                region = BedrockRegions.US_EAST_1.regionCode,
                apiMethod = BedrockAPIMethod.InvokeModel
            ),
            clock = KoogClock.System
        )

        val prompt = Prompt.build("test") {
            user("Hello, Kimi!")
        }

        val exception = assertFailsWith<LLMClientException> {
            client.execute(prompt, BedrockModels.MoonshotKimiK2Thinking, emptyList())
        }

        assertTrue(exception.message!!.contains("requires the Bedrock Converse API"))
        assertTrue(exception.message!!.contains("BedrockAPIMethod.Converse"))
    }

    @Test
    fun `Kimi K2 Thinking model requires Converse API for executeStreaming`() = runTest {
        val client = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = BedrockClientSettings(
                region = BedrockRegions.US_EAST_1.regionCode,
                apiMethod = BedrockAPIMethod.InvokeModel
            ),
            clock = KoogClock.System
        )

        val prompt = Prompt.build("test") {
            user("Hello, Kimi!")
        }

        val exception = assertFailsWith<LLMClientException> {
            client.executeStreaming(prompt, BedrockModels.MoonshotKimiK2Thinking, emptyList()).toList()
        }

        assertTrue(exception.message!!.contains("requires the Bedrock Converse API"))
        assertTrue(exception.message!!.contains("BedrockAPIMethod.Converse"))
    }

    @Test
    fun `MoonshotKimiK2Thinking model has no inference profile prefix`() {
        val model = BedrockModels.MoonshotKimiK2Thinking

        // The model should NOT have any inference profile prefix
        assertFalse(model.id.startsWith("us."))
        assertFalse(model.id.startsWith("eu."))
        assertFalse(model.id.startsWith("global."))
        assertEquals("moonshot.kimi-k2-thinking", model.id)
    }

    // --- Kimi K2.5 ---

    @Test
    fun `MoonshotKimiK2_5 model has correct properties`() {
        val model = BedrockModels.MoonshotKimiK2_5

        assertEquals("moonshotai.kimi-k2.5", model.id)
        assertEquals(LLMProvider.Bedrock, model.provider)
        assertEquals(256_000, model.contextLength)
        val capabilities = assertNotNull(model.capabilities)
        assertTrue(capabilities.contains(LLMCapability.Completion))
        assertTrue(capabilities.contains(LLMCapability.Tools))
        assertTrue(capabilities.contains(LLMCapability.Temperature))
        assertTrue(capabilities.contains(LLMCapability.Vision.Image))
    }

    @Test
    fun `Kimi K2_5 model requires Converse API for execute`() = runTest {
        val client = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = BedrockClientSettings(
                region = BedrockRegions.US_EAST_1.regionCode,
                apiMethod = BedrockAPIMethod.InvokeModel
            ),
            clock = KoogClock.System
        )

        val prompt = Prompt.build("test") {
            user("Hello!")
        }

        val exception = assertFailsWith<LLMClientException> {
            client.execute(prompt, BedrockModels.MoonshotKimiK2_5, emptyList())
        }

        assertTrue(exception.message!!.contains("requires the Bedrock Converse API"))
    }

    @Test
    fun `Kimi K2_5 model requires Converse API for executeStreaming`() = runTest {
        val client = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = BedrockClientSettings(
                region = BedrockRegions.US_EAST_1.regionCode,
                apiMethod = BedrockAPIMethod.InvokeModel
            ),
            clock = KoogClock.System
        )

        val prompt = Prompt.build("test") {
            user("Hello!")
        }

        val exception = assertFailsWith<LLMClientException> {
            client.executeStreaming(prompt, BedrockModels.MoonshotKimiK2_5, emptyList()).toList()
        }

        assertTrue(exception.message!!.contains("requires the Bedrock Converse API"))
    }

    // --- MiniMax M2.5 ---

    @Test
    fun `MiniMaxM2_5 model has correct properties`() {
        val model = BedrockModels.MiniMaxM2_5

        assertEquals("minimax.minimax-m2.5", model.id)
        assertEquals(LLMProvider.Bedrock, model.provider)
        assertEquals(1_000_000, model.contextLength)
        val capabilities = assertNotNull(model.capabilities)
        assertTrue(capabilities.contains(LLMCapability.Completion))
        assertTrue(capabilities.contains(LLMCapability.Tools))
        assertTrue(capabilities.contains(LLMCapability.ToolChoice))
    }

    @Test
    fun `MiniMax M2_5 model requires Converse API for execute`() = runTest {
        val client = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = BedrockClientSettings(
                region = BedrockRegions.US_EAST_1.regionCode,
                apiMethod = BedrockAPIMethod.InvokeModel
            ),
            clock = KoogClock.System
        )

        val prompt = Prompt.build("test") {
            user("Hello!")
        }

        val exception = assertFailsWith<LLMClientException> {
            client.execute(prompt, BedrockModels.MiniMaxM2_5, emptyList())
        }

        assertTrue(exception.message!!.contains("requires the Bedrock Converse API"))
    }

    @Test
    fun `MiniMax M2_5 model requires Converse API for executeStreaming`() = runTest {
        val client = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = BedrockClientSettings(
                region = BedrockRegions.US_EAST_1.regionCode,
                apiMethod = BedrockAPIMethod.InvokeModel
            ),
            clock = KoogClock.System
        )

        val prompt = Prompt.build("test") {
            user("Hello!")
        }

        val exception = assertFailsWith<LLMClientException> {
            client.executeStreaming(prompt, BedrockModels.MiniMaxM2_5, emptyList()).toList()
        }

        assertTrue(exception.message!!.contains("requires the Bedrock Converse API"))
    }

    // --- GPT-OSS ---

    @Test
    fun `OpenAIGptOss120B model has correct properties`() {
        val model = BedrockModels.OpenAIGptOss120B

        assertEquals("openai.gpt-oss-120b-1:0", model.id)
        assertEquals(LLMProvider.Bedrock, model.provider)
        assertEquals(128_000, model.contextLength)
        val capabilities = assertNotNull(model.capabilities)
        assertTrue(capabilities.contains(LLMCapability.Completion))
        assertTrue(capabilities.contains(LLMCapability.Tools))
        assertTrue(capabilities.contains(LLMCapability.Schema.JSON.Standard))
    }

    @Test
    fun `OpenAIGptOss20B model has correct properties`() {
        val model = BedrockModels.OpenAIGptOss20B

        assertEquals("openai.gpt-oss-20b-1:0", model.id)
        assertEquals(LLMProvider.Bedrock, model.provider)
        assertEquals(128_000, model.contextLength)
        val capabilities = assertNotNull(model.capabilities)
        assertTrue(capabilities.contains(LLMCapability.Completion))
        assertTrue(capabilities.contains(LLMCapability.Tools))
        assertTrue(capabilities.contains(LLMCapability.Schema.JSON.Standard))
    }

    @Test
    fun `GPT-OSS model requires Converse API for execute`() = runTest {
        val client = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = BedrockClientSettings(
                region = BedrockRegions.US_EAST_1.regionCode,
                apiMethod = BedrockAPIMethod.InvokeModel
            ),
            clock = KoogClock.System
        )

        val prompt = Prompt.build("test") {
            user("Hello!")
        }

        val exception = assertFailsWith<LLMClientException> {
            client.execute(prompt, BedrockModels.OpenAIGptOss120B, emptyList())
        }

        assertTrue(exception.message!!.contains("requires the Bedrock Converse API"))
    }

    @Test
    fun `GPT-OSS model requires Converse API for executeStreaming`() = runTest {
        val client = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = BedrockClientSettings(
                region = BedrockRegions.US_EAST_1.regionCode,
                apiMethod = BedrockAPIMethod.InvokeModel
            ),
            clock = KoogClock.System
        )

        val prompt = Prompt.build("test") {
            user("Hello!")
        }

        val exception = assertFailsWith<LLMClientException> {
            client.executeStreaming(prompt, BedrockModels.OpenAIGptOss120B, emptyList()).toList()
        }

        assertTrue(exception.message!!.contains("requires the Bedrock Converse API"))
    }

    // --- Google Gemma 3 ---

    @Test
    fun `GoogleGemma3_4BIt model has correct properties`() {
        val model = BedrockModels.GoogleGemma3_4BIt

        assertEquals("google.gemma-3-4b-it", model.id)
        assertEquals(LLMProvider.Bedrock, model.provider)
        assertEquals(128_000, model.contextLength)
        val capabilities = assertNotNull(model.capabilities)
        assertTrue(capabilities.contains(LLMCapability.Completion))
        assertTrue(capabilities.contains(LLMCapability.Tools))
        assertTrue(capabilities.contains(LLMCapability.Vision.Image))
        assertFalse(capabilities.contains(LLMCapability.Schema.JSON.Standard))
    }

    @Test
    fun `GoogleGemma3_12BIt model has correct properties`() {
        val model = BedrockModels.GoogleGemma3_12BIt

        assertEquals("google.gemma-3-12b-it", model.id)
        assertEquals(LLMProvider.Bedrock, model.provider)
        assertEquals(128_000, model.contextLength)
        val capabilities = assertNotNull(model.capabilities)
        assertTrue(capabilities.contains(LLMCapability.Completion))
        assertTrue(capabilities.contains(LLMCapability.Tools))
        assertTrue(capabilities.contains(LLMCapability.Vision.Image))
        assertTrue(capabilities.contains(LLMCapability.Document))
        assertTrue(capabilities.contains(LLMCapability.Schema.JSON.Standard))
    }

    @Test
    fun `GoogleGemma3_27BIt model has correct properties`() {
        val model = BedrockModels.GoogleGemma3_27BIt

        assertEquals("google.gemma-3-27b-it", model.id)
        assertEquals(LLMProvider.Bedrock, model.provider)
        assertEquals(128_000, model.contextLength)
        val capabilities = assertNotNull(model.capabilities)
        assertTrue(capabilities.contains(LLMCapability.Completion))
        assertTrue(capabilities.contains(LLMCapability.Tools))
        assertTrue(capabilities.contains(LLMCapability.Vision.Image))
        assertTrue(capabilities.contains(LLMCapability.Document))
        assertTrue(capabilities.contains(LLMCapability.Schema.JSON.Standard))
    }

    @Test
    fun `Google Gemma model requires Converse API for execute`() = runTest {
        val client = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = BedrockClientSettings(
                region = BedrockRegions.US_EAST_1.regionCode,
                apiMethod = BedrockAPIMethod.InvokeModel
            ),
            clock = KoogClock.System
        )

        val prompt = Prompt.build("test") {
            user("Hello!")
        }

        val exception = assertFailsWith<LLMClientException> {
            client.execute(prompt, BedrockModels.GoogleGemma3_12BIt, emptyList())
        }

        assertTrue(exception.message!!.contains("requires the Bedrock Converse API"))
    }

    @Test
    fun `Google Gemma model requires Converse API for executeStreaming`() = runTest {
        val client = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = BedrockClientSettings(
                region = BedrockRegions.US_EAST_1.regionCode,
                apiMethod = BedrockAPIMethod.InvokeModel
            ),
            clock = KoogClock.System
        )

        val prompt = Prompt.build("test") {
            user("Hello!")
        }

        val exception = assertFailsWith<LLMClientException> {
            client.executeStreaming(prompt, BedrockModels.GoogleGemma3_27BIt, emptyList()).toList()
        }

        assertTrue(exception.message!!.contains("requires the Bedrock Converse API"))
    }

    // --- Cohere Embed v4 ---

    @Test
    fun `CohereEmbedV4 model has correct properties`() {
        val model = BedrockModels.Embeddings.CohereEmbedV4

        assertEquals("us.cohere.embed-v4:0", model.id)
        assertEquals(LLMProvider.Bedrock, model.provider)
        assertEquals(512, model.contextLength)
        val capabilities = assertNotNull(model.capabilities)
        assertTrue(capabilities.contains(LLMCapability.Embed))
    }

    // --- No inference profile prefix for third-party models ---

    @Test
    fun `new third-party chat models have no inference profile prefix`() {
        val models = listOf(
            BedrockModels.MoonshotKimiK2_5 to "moonshotai.kimi-k2.5",
            BedrockModels.MiniMaxM2_5 to "minimax.minimax-m2.5",
            BedrockModels.OpenAIGptOss120B to "openai.gpt-oss-120b-1:0",
            BedrockModels.OpenAIGptOss20B to "openai.gpt-oss-20b-1:0",
            BedrockModels.GoogleGemma3_4BIt to "google.gemma-3-4b-it",
            BedrockModels.GoogleGemma3_12BIt to "google.gemma-3-12b-it",
            BedrockModels.GoogleGemma3_27BIt to "google.gemma-3-27b-it",
        )

        for ((model, expectedId) in models) {
            assertFalse(model.id.startsWith("us."), "Model ${model.id} should not have US prefix")
            assertFalse(model.id.startsWith("eu."), "Model ${model.id} should not have EU prefix")
            assertFalse(model.id.startsWith("global."), "Model ${model.id} should not have global prefix")
            assertEquals(expectedId, model.id)
        }
    }

    @Test
    fun `CohereEmbedV4 has inference profile prefix`() {
        val model = BedrockModels.Embeddings.CohereEmbedV4
        assertTrue(model.id.startsWith("us."), "Cohere Embed v4 should have US inference profile prefix")
        assertEquals("us.cohere.embed-v4:0", model.id)
    }
}
