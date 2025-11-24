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
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.bedrockruntime.model.ApplyGuardrailRequest
import aws.sdk.kotlin.services.bedrockruntime.model.ApplyGuardrailResponse
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseRequest
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseResponse
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseStreamRequest
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseStreamResponse
import aws.sdk.kotlin.services.bedrockruntime.model.GetAsyncInvokeRequest
import aws.sdk.kotlin.services.bedrockruntime.model.GetAsyncInvokeResponse
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailAction
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailAssessment
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailContentFilter
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailContentFilterConfidence
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailContentFilterType
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailContentPolicyAction
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailContentPolicyAssessment
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelRequest
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelResponse
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelWithBidirectionalStreamRequest
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelWithBidirectionalStreamResponse
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelWithResponseStreamResponse
import aws.sdk.kotlin.services.bedrockruntime.model.ListAsyncInvokesRequest
import aws.sdk.kotlin.services.bedrockruntime.model.ListAsyncInvokesResponse
import aws.sdk.kotlin.services.bedrockruntime.model.StartAsyncInvokeRequest
import aws.sdk.kotlin.services.bedrockruntime.model.StartAsyncInvokeResponse
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BedrockLLMClientTest {
    @Test
    fun `can create BedrockLLMClient`() {
        val client = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = BedrockClientSettings(region = BedrockRegions.US_EAST_1.regionCode),
            clock = Clock.System
        )

        assertNotNull(client)
    }

    @Test
    fun `can create BedrockLLMClient with API key`() {
        val client = BedrockLLMClient(
            identityProvider = StaticBearerTokenProvider(token = "test-token"),
            settings = BedrockClientSettings(region = BedrockRegions.US_EAST_1.regionCode),
            clock = Clock.System
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
        assertTrue(exception.message!!.contains("withInferencePrefix() can only be used with Bedrock models"))
        assertTrue(exception.message!!.contains("model provider is Anthropic"))
    }

    @Test
    fun `client configuration options work correctly`() {
        val customSettings = BedrockClientSettings(
            region = BedrockRegions.EU_WEST_1.regionCode,
            endpointUrl = "https://custom.endpoint.com",
            maxRetries = 5,
            enableLogging = true,
            timeoutConfig = ConnectionTimeoutConfig(
                requestTimeoutMillis = 120_000,
                connectTimeoutMillis = 10_000,
                socketTimeoutMillis = 120_000
            )
        )

        val client = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = customSettings,
            clock = Clock.System
        )

        assertNotNull(client)
        assertEquals(BedrockRegions.EU_WEST_1.regionCode, customSettings.region)
        assertEquals("https://custom.endpoint.com", customSettings.endpointUrl)
        assertEquals(5, customSettings.maxRetries)
        assertEquals(true, customSettings.enableLogging)
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
            clock = Clock.System
        )

        // Verify that older Claude models don't support tools
        val olderClaudeModel = BedrockModels.AnthropicClaude21
        assertFails {
            client.execute(prompt, olderClaudeModel, tools)
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

            override suspend fun <T> converseStream(input: ConverseStreamRequest, block: suspend (ConverseStreamResponse) -> T): T =
                throw UnsupportedOperationException("converseStream not implemented in mock client")

            override suspend fun getAsyncInvoke(input: GetAsyncInvokeRequest): GetAsyncInvokeResponse =
                throw UnsupportedOperationException("getAsyncInvoke not implemented in mock client")

            override suspend fun invokeModel(input: InvokeModelRequest): InvokeModelResponse =
                throw UnsupportedOperationException("invokeModel not implemented in mock client")

            override suspend fun <T> invokeModelWithBidirectionalStream(input: InvokeModelWithBidirectionalStreamRequest, block: suspend (InvokeModelWithBidirectionalStreamResponse) -> T): T =
                throw UnsupportedOperationException("invokeModelWithBidirectionalStream not implemented in mock client")

            override suspend fun <T> invokeModelWithResponseStream(input: InvokeModelWithResponseStreamRequest, block: suspend (InvokeModelWithResponseStreamResponse) -> T): T =
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
            val model = BedrockModels.AnthropicClaude3Sonnet

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
            clock = Clock.System
        )

        val prompt = Prompt.build("test") {
            user("This is a test prompt")
        }
        val model = BedrockModels.AnthropicClaude3Sonnet

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
            val model = BedrockModels.AnthropicClaude3Sonnet

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
            val model = BedrockModels.AnthropicClaude3Sonnet

            client.moderate(prompt, model)

            assertEquals(2, applyGuardrailCallCount, "Should call applyGuardrail exactly twice for prompts with both Request and Response")
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
            val model = BedrockModels.AnthropicClaude3Sonnet

            client.moderate(prompt, model)

            assertEquals(1, applyGuardrailCallCount, "Should call applyGuardrail exactly once for Response-only prompts")
        } finally {
            client.close()
        }
    }

    // Helper function to create a counting mock client
    private fun createCountingMockClient(onApplyGuardrail: () -> Unit): BedrockRuntimeClient {
        return object : BedrockRuntimeClient {
            override suspend fun applyGuardrail(input: ApplyGuardrailRequest): ApplyGuardrailResponse {
                onApplyGuardrail()
                return ApplyGuardrailResponse {
                    action = GuardrailAction.None
                    assessments = emptyList()
                    outputs = emptyList()
                }
            }

            override val config: BedrockRuntimeClient.Config
                get() = throw UnsupportedOperationException("config not implemented in mock client")

            override suspend fun converse(input: ConverseRequest): ConverseResponse =
                throw UnsupportedOperationException("converse not implemented in mock client")

            override suspend fun <T> converseStream(input: ConverseStreamRequest, block: suspend (ConverseStreamResponse) -> T): T =
                throw UnsupportedOperationException("converseStream not implemented in mock client")

            override suspend fun getAsyncInvoke(input: GetAsyncInvokeRequest): GetAsyncInvokeResponse =
                throw UnsupportedOperationException("getAsyncInvoke not implemented in mock client")

            override suspend fun invokeModel(input: InvokeModelRequest): InvokeModelResponse =
                throw UnsupportedOperationException("invokeModel not implemented in mock client")

            override suspend fun <T> invokeModelWithBidirectionalStream(input: InvokeModelWithBidirectionalStreamRequest, block: suspend (InvokeModelWithBidirectionalStreamResponse) -> T): T =
                throw UnsupportedOperationException("invokeModelWithBidirectionalStream not implemented in mock client")

            override suspend fun <T> invokeModelWithResponseStream(input: InvokeModelWithResponseStreamRequest, block: suspend (InvokeModelWithResponseStreamResponse) -> T): T =
                throw UnsupportedOperationException("invokeModelWithResponseStream not implemented in mock client")

            override suspend fun listAsyncInvokes(input: ListAsyncInvokesRequest): ListAsyncInvokesResponse =
                throw UnsupportedOperationException("listAsyncInvokes not implemented in mock client")

            override suspend fun startAsyncInvoke(input: StartAsyncInvokeRequest): StartAsyncInvokeResponse =
                throw UnsupportedOperationException("startAsyncInvoke not implemented in mock client")

            override fun close() {
                print("closing")
            }
        }
    }

    @Test
    fun `execute throws IllegalArgumentException for TitanEmbedding models`() = runTest {
        val client = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = BedrockClientSettings(region = BedrockRegions.US_EAST_1.regionCode),
            clock = Clock.System
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
            clock = Clock.System
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
    fun `execute throws LLMClientException for model without Completion capability`() = runTest {
        val client = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = BedrockClientSettings(region = BedrockRegions.US_EAST_1.regionCode),
            clock = Clock.System
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
        assertFailsWith<LLMClientException> {
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
            clock = Clock.System
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
            clock = Clock.System
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
    fun `executeStreaming throws LLMClientException for model without Completion capability`() = runTest {
        val client = BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                accessKeyId = "test-key"
                secretAccessKey = "test-secret"
            },
            settings = BedrockClientSettings(region = BedrockRegions.US_EAST_1.regionCode),
            clock = Clock.System
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
        assertFailsWith<LLMClientException> {
            client.executeStreaming(prompt, noCompletionModel, emptyList()).toList()
        }
    }
}
