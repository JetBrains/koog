package ai.koog.integration.tests.executor

import ai.koog.integration.tests.utils.MediaTestScenarios.AudioTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.ImageTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.MarkdownTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.TextTestScenario
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.PromptUtils.assistantPromptOfAtLeastLength
import ai.koog.integration.tests.utils.RetryUtils.withRetry
import ai.koog.integration.tests.utils.TestCredentials.readAwsAccessKeyIdFromEnv
import ai.koog.integration.tests.utils.TestCredentials.readAwsBedrockGuardrailIdFromEnv
import ai.koog.integration.tests.utils.TestCredentials.readAwsBedrockGuardrailVersionFromEnv
import ai.koog.integration.tests.utils.TestCredentials.readAwsSecretAccessKeyFromEnv
import ai.koog.integration.tests.utils.TestCredentials.readAwsSessionTokenFromEnv
import ai.koog.integration.tests.utils.tools.CalculatorTool
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.bedrock.BedrockAPIMethod
import ai.koog.prompt.executor.clients.bedrock.BedrockCacheControl
import ai.koog.prompt.executor.clients.bedrock.BedrockClientSettings
import ai.koog.prompt.executor.clients.bedrock.BedrockGuardrailsSettings
import ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.executor.clients.bedrock.converse.BedrockConverseParams
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.params.LLMParams
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.enums.EnumEntries
import kotlin.time.Duration.Companion.seconds

/**
 * Test newer Bedrock Converse API using the same suite of executor tests.
 */
class BedrockConverseApiIntegrationTest : ExecutorIntegrationTestBase() {
    companion object {
        private fun EnumEntries<*>.combineBedrockModels(): Stream<Arguments> {
            return toList()
                .flatMap { scenario ->
                    Models
                        .bedrockModels()
                        .toArray()
                        .map { model -> Arguments.of(scenario, model) }
                }
                .stream()
        }

        @JvmStatic
        fun markdownScenarioModelCombinations(): Stream<Arguments> {
            return MarkdownTestScenario.entries.combineBedrockModels()
        }

        @JvmStatic
        fun imageScenarioModelCombinations(): Stream<Arguments> {
            return ImageTestScenario.entries.combineBedrockModels()
        }

        @JvmStatic
        fun textScenarioModelCombinations(): Stream<Arguments> {
            return TextTestScenario.entries.combineBedrockModels()
        }

        @JvmStatic
        fun audioScenarioModelCombinations(): Stream<Arguments> {
            return AudioTestScenario.entries.combineBedrockModels()
        }

        @JvmStatic
        fun reasoningCapableModels(): Stream<LLModel> {
            return listOf(BedrockModels.AnthropicClaude4_6Sonnet).stream()
        }

        @JvmStatic
        fun allCompletionModels(): Stream<LLModel> {
            return Models.bedrockModels()
        }

        @JvmStatic
        fun cacheCapableModels(): Stream<LLModel> {
            return listOf(BedrockModels.AnthropicClaude4_5Sonnet).stream()
        }
    }

    private val client = run {
        BedrockLLMClient(
            identityProvider = StaticCredentialsProvider {
                this.accessKeyId = readAwsAccessKeyIdFromEnv()
                this.secretAccessKey = readAwsSecretAccessKeyFromEnv()
                readAwsSessionTokenFromEnv()?.let { this.sessionToken = it }
            },
            settings = BedrockClientSettings(
                moderationGuardrailsSettings = BedrockGuardrailsSettings(
                    guardrailIdentifier = readAwsBedrockGuardrailIdFromEnv(),
                    guardrailVersion = readAwsBedrockGuardrailVersionFromEnv()
                ),
                apiMethod = BedrockAPIMethod.Converse,
            )
        )
    }

    private val executor: MultiLLMPromptExecutor = MultiLLMPromptExecutor(client)

    private fun JsonObject.validateRequestWasCachedCorrectly() {
        this.shouldNotBeNull()
        val cacheRead = this["cacheReadInputTokens"]?.jsonPrimitive?.intOrNull ?: 0
        val cacheWrite = this["cacheWriteInputTokens"]?.jsonPrimitive?.intOrNull ?: 0
        withClue("Cache read or cache write should be greater than 0 if the cache point was added correctly") {
            (cacheRead > 0 || cacheWrite > 0).shouldBeTrue()
        }
    }

    override fun getLLMClient(model: LLModel): LLMClient {
        require(model.provider == LLMProvider.Bedrock) { "Model ${model.id} is not a Bedrock model" }

        return client
    }

    override fun getExecutor(model: LLModel): PromptExecutor = executor

    override fun createReasoningParams(model: LLModel): LLMParams {
        require(model in reasoningCapableModels().toArray()) {
            "Model ${model.id} is not a reasoning capable model"
        }

        return BedrockConverseParams(
            additionalProperties = mapOf(
                // Anthropic-specific reasoning config
                "reasoning_config" to buildJsonObject {
                    put("type", "enabled")
                    put("budget_tokens", 1024)
                }
            )
        )
    }

    @ParameterizedTest
    @MethodSource("markdownScenarioModelCombinations")
    override fun integration_testMarkdownProcessingBasic(
        scenario: MarkdownTestScenario,
        model: LLModel
    ) {
        super.integration_testMarkdownProcessingBasic(scenario, model)
    }

    @ParameterizedTest
    @MethodSource("imageScenarioModelCombinations")
    override fun integration_testImageProcessing(scenario: ImageTestScenario, model: LLModel) {
        super.integration_testImageProcessing(scenario, model)
    }

    @ParameterizedTest
    @MethodSource("textScenarioModelCombinations")
    override fun integration_testTextProcessingBasic(scenario: TextTestScenario, model: LLModel) {
        super.integration_testTextProcessingBasic(scenario, model)
    }

    @ParameterizedTest
    @MethodSource("audioScenarioModelCombinations")
    override fun integration_testAudioProcessingBasic(scenario: AudioTestScenario, model: LLModel) {
        super.integration_testAudioProcessingBasic(scenario, model)
    }

    // Core integration test methods
    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testExecute(model: LLModel) {
        super.integration_testExecute(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testExecuteStreaming(model: LLModel) {
        super.integration_testExecuteStreaming(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testExecuteStreamingWithTools(model: LLModel) {
        super.integration_testExecuteStreamingWithTools(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testToolWithRequiredParams(model: LLModel) {
        super.integration_testToolWithRequiredParams(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testToolWithNotRequiredOptionalParams(model: LLModel) {
        super.integration_testToolWithNotRequiredOptionalParams(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testToolWithOptionalParams(model: LLModel) {
        super.integration_testToolWithOptionalParams(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testToolWithNoParams(model: LLModel) {
        super.integration_testToolWithNoParams(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testToolWithListEnumParams(model: LLModel) {
        super.integration_testToolWithListEnumParams(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testToolWithNestedListParams(model: LLModel) {
        super.integration_testToolWithNestedListParams(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testToolsWithNullParams(model: LLModel) {
        super.integration_testToolsWithNullParams(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testToolsWithAnyOfParams(model: LLModel) {
        super.integration_testToolsWithAnyOfParams(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testAssistantMultiPartRoundTrip(model: LLModel) {
        super.integration_testAssistantMultiPartRoundTrip(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testToolCallResultCorrelationById(model: LLModel) {
        super.integration_testToolCallResultCorrelationById(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testMarkdownStructuredDataStreaming(model: LLModel) {
        super.integration_testMarkdownStructuredDataStreaming(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testToolChoiceRequired(model: LLModel) {
        super.integration_testToolChoiceRequired(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testToolChoiceNone(model: LLModel) {
        super.integration_testToolChoiceNone(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testToolChoiceNamed(model: LLModel) {
        super.integration_testToolChoiceNamed(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testBase64EncodedAttachment(model: LLModel) {
        super.integration_testBase64EncodedAttachment(model)
    }

    @Disabled("Converse API supports only S3 url attachments")
    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testUrlBasedAttachment(model: LLModel) {
        super.integration_testUrlBasedAttachment(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testAttachmentTextRoundTrip(model: LLModel) {
        super.integration_testAttachmentTextRoundTrip(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testStructuredOutputNative(model: LLModel) {
        super.integration_testStructuredOutputNative(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testStructuredOutputNativeWithFixingParser(model: LLModel) {
        super.integration_testStructuredOutputNativeWithFixingParser(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testStructuredOutputManual(model: LLModel) {
        super.integration_testStructuredOutputManual(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testStructuredOutputManualWithFixingParser(model: LLModel) {
        super.integration_testStructuredOutputManualWithFixingParser(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testMultipleSystemMessages(model: LLModel) {
        super.integration_testMultipleSystemMessages(model)
    }

    @ParameterizedTest
    @MethodSource("reasoningCapableModels")
    override fun integration_testReasoningCapability(model: LLModel) {
        super.integration_testReasoningCapability(model)
    }

    @ParameterizedTest
    @MethodSource("reasoningCapableModels")
    override fun integration_testReasoningMultiStep(model: LLModel) {
        super.integration_testReasoningMultiStep(model)
    }

    @ParameterizedTest
    @MethodSource("reasoningCapableModels")
    override fun integration_testReasoningPreservationRoundTrip(model: LLModel) {
        super.integration_testReasoningPreservationRoundTrip(model)
    }

    @ParameterizedTest
    @MethodSource("cacheCapableModels")
    fun integration_testCacheControlOnSystemMessage(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)

        val prompt = Prompt.build("test-cache-system") {
            // Caching requires a minimum prompt length to work.
            system(assistantPromptOfAtLeastLength(1600), BedrockCacheControl.Default)
            user("What is the capital of France?")
        }

        withRetry(times = 3, testName = "integration_testCacheControlOnSystemMessage[${model.id}]") {
            val result = getExecutor(model).execute(prompt, model)
            result.shouldNotBeNull()
            result.parts.filterIsInstance<MessagePart.Text>().firstOrNull().shouldNotBeNull {
                text.lowercase().shouldContain("paris")
            }
            result.metaInfo.metadata.shouldNotBeNull().validateRequestWasCachedCorrectly()
        }
    }

    @ParameterizedTest
    @MethodSource("cacheCapableModels")
    fun integration_testCacheControlOnUserMessage(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)

        val prompt = Prompt.build("test-cache-user") {
            // Caching requires a minimum prompt length to work.
            system(assistantPromptOfAtLeastLength(1600))
            user("What is the capital of France?", BedrockCacheControl.Default)
        }

        withRetry(times = 3, testName = "integration_testCacheControlOnUserMessage[${model.id}]") {
            val result = getExecutor(model).execute(prompt, model)
            result.shouldNotBeNull()
            result.parts.filterIsInstance<MessagePart.Text>().firstOrNull().shouldNotBeNull {
                text.lowercase().shouldContain("paris")
            }
            result.metaInfo.metadata.shouldNotBeNull().validateRequestWasCachedCorrectly()
        }
    }

    @ParameterizedTest
    @MethodSource("cacheCapableModels")
    fun integration_testCacheControlOnToolDefinition(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities?.contains(LLMCapability.Tools) ?: false, "Model $model does not support tools")

        val cachedDescriptor = CalculatorTool.descriptor.withCacheControl(BedrockCacheControl.Default).copy(
            // Caching requires a minimum prompt length to work - in the case of tools, this appears to apply specifically to the tool section
            // rather than the prompt as a whole.
            description = assistantPromptOfAtLeastLength(1600, CalculatorTool.descriptor.description)
        )
        val prompt = Prompt.build("test-cache-tool") {
            system("You are a helpful assistant with a calculator tool. You MUST call the calculator tool!!!.")
            user("What is 123 + 456?")
        }

        withRetry(times = 3, testName = "integration_testCacheControlOnToolDefinition[${model.id}]") {
            val result = getExecutor(model).execute(prompt, model, listOf(cachedDescriptor))
            result.shouldNotBeNull()
            result.metaInfo.metadata.shouldNotBeNull().validateRequestWasCachedCorrectly()
        }
    }
}
