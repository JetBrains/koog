package ai.koog.integration.tests.executor

import ai.koog.integration.tests.utils.MediaTestScenarios
import ai.koog.integration.tests.utils.MediaTestScenarios.AudioTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.ImageTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.MarkdownTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.TextTestScenario
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.TestUtils.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestGoogleAIKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestOpenAIKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestOpenRouterKeyFromEnv
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

@Execution(ExecutionMode.SAME_THREAD)
class SingleLLMPromptExecutorIntegrationTest : ExecutorIntegrationTestBase() {
    companion object {

        @JvmStatic
        fun modelClientCombinations(): Stream<Arguments> {
            val openAIClientInstance = OpenAILLMClient(readTestOpenAIKeyFromEnv())
            val anthropicClientInstance = AnthropicLLMClient(readTestAnthropicKeyFromEnv())
            val googleClientInstance = GoogleLLMClient(readTestGoogleAIKeyFromEnv())
            val openRouterClientInstance = OpenRouterLLMClient(readTestOpenRouterKeyFromEnv())
            val bedrockClientInstance = BedrockLLMClient(
                credentialsProvider = StaticCredentialsProvider {
                    this.accessKeyId = readAwsAccessKeyIdFromEnv()
                    this.secretAccessKey = readAwsSecretAccessKeyFromEnv()
                    readAwsSessionTokenFromEnv()?.let { this.sessionToken = it }
                },
                settings = BedrockClientSettings()
            )

            return Stream.concat(
                Stream.concat(
                    Models.openAIModels().map { model -> Arguments.of(model, openAIClientInstance) },
                    Models.anthropicModels().map { model -> Arguments.of(model, anthropicClientInstance) }
                ),
                Stream.concat(
                    Models.googleModels().map { model -> Arguments.of(model, googleClientInstance) },
                    Models.openRouterModels().map { model -> Arguments.of(model, openRouterClientInstance) }
                )
            )
        }

        @JvmStatic
        fun bedrockCombinations(): Stream<Arguments> {
            val bedrockClientInstance = BedrockLLMClient(
                credentialsProvider = StaticCredentialsProvider {
                    this.accessKeyId = readAwsAccessKeyIdFromEnv()
                    this.secretAccessKey = readAwsSecretAccessKeyFromEnv()
                    readAwsSessionTokenFromEnv()?.let { this.sessionToken = it }
                },
                settings = BedrockClientSettings()
            )

            return Models.bedrockModels().map { model -> Arguments.of(model, bedrockClientInstance) }
        }

        @JvmStatic
        fun markdownScenarioModelCombinations(): Stream<Arguments> {
            return MediaTestScenarios.markdownScenarioModelCombinations()
        }

        @JvmStatic
        fun imageScenarioModelCombinations(): Stream<Arguments> {
            return MediaTestScenarios.imageScenarioModelCombinations()
        }

        @JvmStatic
        fun textScenarioModelCombinations(): Stream<Arguments> {
            return MediaTestScenarios.textScenarioModelCombinations()
        }

        @JvmStatic
        fun audioScenarioModelCombinations(): Stream<Arguments> {
            return MediaTestScenarios.audioScenarioModelCombinations()
        }
    }

    override fun getExecutor(): PromptExecutor {
        // This method will be called by individual test methods
        // We can't return a specific executor here since it depends on the model
        throw UnsupportedOperationException("Use getExecutor(model) instead")
    }

    private fun getExecutor(model: LLModel): SingleLLMPromptExecutor {
        return SingleLLMPromptExecutor(getClient(model))
    }

    override fun getClient(model: LLModel): LLMClient {
        return when (model.provider) {
            LLMProvider.Anthropic -> AnthropicLLMClient(
                readTestAnthropicKeyFromEnv()
            )

            LLMProvider.OpenAI -> OpenAILLMClient(
                readTestOpenAIKeyFromEnv()
            )

            LLMProvider.OpenRouter -> OpenRouterLLMClient(
                readTestOpenRouterKeyFromEnv()
            )

            LLMProvider.Bedrock -> BedrockLLMClient(
                credentialsProvider = StaticCredentialsProvider {
                    this.accessKeyId = readAwsAccessKeyIdFromEnv()
                    this.secretAccessKey = readAwsSecretAccessKeyFromEnv()
                    readAwsSessionTokenFromEnv()?.let { this.sessionToken = it }
                },
                settings = BedrockClientSettings()
            )

            LLMProvider.Google -> GoogleLLMClient(
                readTestGoogleAIKeyFromEnv()
            )

            else -> throw IllegalArgumentException("Unsupported provider: ${model.provider}")
        }
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testExecute(model: LLModel, client: LLMClient) {
        val testBase = object : ExecutorIntegrationTestBase() {
            override fun getExecutor(): PromptExecutor = getExecutor(model)
            override fun getClient(model: LLModel): LLMClient = client
        }
        testBase.integration_testExecute(model)
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testExecuteStreaming(model: LLModel, client: LLMClient) {
        val testBase = object : ExecutorIntegrationTestBase() {
            override fun getExecutor(): PromptExecutor = getExecutor(model)
            override fun getClient(model: LLModel): LLMClient = client
        }
        testBase.integration_testExecuteStreaming(model)
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testToolsWithRequiredParams(model: LLModel, client: LLMClient) {
        val testBase = object : ExecutorIntegrationTestBase() {
            override fun getExecutor(): PromptExecutor = getExecutor(model)
            override fun getClient(model: LLModel): LLMClient = client
        }
        testBase.integration_testToolsWithRequiredParams(model)
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testToolsWithRequiredOptionalParams(model: LLModel, client: LLMClient) {
        val testBase = object : ExecutorIntegrationTestBase() {
            override fun getExecutor(): PromptExecutor = getExecutor(model)
            override fun getClient(model: LLModel): LLMClient = client
        }
        testBase.integration_testToolsWithRequiredOptionalParams(model)
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testToolsWithOptionalParams(model: LLModel, client: LLMClient) {
        val testBase = object : ExecutorIntegrationTestBase() {
            override fun getExecutor(): PromptExecutor = getExecutor(model)
            override fun getClient(model: LLModel): LLMClient = client
        }
        testBase.integration_testToolsWithOptionalParams(model)
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testToolsWithNoParams(model: LLModel, client: LLMClient) {
        val testBase = object : ExecutorIntegrationTestBase() {
            override fun getExecutor(): PromptExecutor = getExecutor(model)
            override fun getClient(model: LLModel): LLMClient = client
        }
        testBase.integration_testToolsWithNoParams(model)
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testToolsWithListEnumParams(model: LLModel, client: LLMClient) {
        val testBase = object : ExecutorIntegrationTestBase() {
            override fun getExecutor(): PromptExecutor = getExecutor(model)
            override fun getClient(model: LLModel): LLMClient = client
        }
        testBase.integration_testToolsWithListEnumParams(model)
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testToolsWithNestedListParams(model: LLModel, client: LLMClient) {
        val testBase = object : ExecutorIntegrationTestBase() {
            override fun getExecutor(): PromptExecutor = getExecutor(model)
            override fun getClient(model: LLModel): LLMClient = client
        }
        testBase.integration_testToolsWithNestedListParams(model)
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testRawStringStreaming(model: LLModel, client: LLMClient) {
        val testBase = object : ExecutorIntegrationTestBase() {
            override fun getExecutor(): PromptExecutor = getExecutor(model)
            override fun getClient(model: LLModel): LLMClient = client
        }
        testBase.integration_testRawStringStreaming(model)
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testStructuredDataStreaming(model: LLModel, client: LLMClient) {
        val testBase = object : ExecutorIntegrationTestBase() {
            override fun getExecutor(): PromptExecutor = getExecutor(model)
            override fun getClient(model: LLModel): LLMClient = client
        }
        testBase.integration_testStructuredDataStreaming(model)
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testToolChoiceRequired(model: LLModel, client: LLMClient) {
        val testBase = object : ExecutorIntegrationTestBase() {
            override fun getExecutor(): PromptExecutor = getExecutor(model)
            override fun getClient(model: LLModel): LLMClient = client
        }
        testBase.integration_testToolChoiceRequired(model)
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testToolChoiceNone(model: LLModel, client: LLMClient) {
        val testBase = object : ExecutorIntegrationTestBase() {
            override fun getExecutor(): PromptExecutor = getExecutor(model)
            override fun getClient(model: LLModel): LLMClient = client
        }
        testBase.integration_testToolChoiceNone(model)
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testToolChoiceNamed(model: LLModel, client: LLMClient) {
        val testBase = object : ExecutorIntegrationTestBase() {
            override fun getExecutor(): PromptExecutor = getExecutor(model)
            override fun getClient(model: LLModel): LLMClient = client
        }
        testBase.integration_testToolChoiceNamed(model)
    }

    /*
     * IMPORTANT about the testing approach!
     * The number of combinations between specific executors and media types will make tests slower.
     * The compatibility of each LLM profile with the media processing is covered in the E2E agents tests.
     * Therefore, in the scope of the executor tests, we'll check one executor of each provider
     * to decrease the number of possible combinations and to avoid redundant checks.*/

    @ParameterizedTest
    @MethodSource("markdownScenarioModelCombinations")
    override fun integration_testMarkdownProcessingBasic(
        scenario: MarkdownTestScenario,
        model: LLModel
    ) {
        val testBase = object : ExecutorIntegrationTestBase() {
            override fun getExecutor(): PromptExecutor = this@SingleLLMPromptExecutorIntegrationTest.getExecutor(model)
            override fun getClient(model: LLModel): LLMClient =
                this@SingleLLMPromptExecutorIntegrationTest.getClient(model)
        }
        testBase.integration_testMarkdownProcessingBasic(scenario, model)
    }

    @ParameterizedTest
    @MethodSource("imageScenarioModelCombinations")
    override fun integration_testImageProcessing(scenario: ImageTestScenario, model: LLModel) {
        val testBase = object : ExecutorIntegrationTestBase() {
            override fun getExecutor(): PromptExecutor = this@SingleLLMPromptExecutorIntegrationTest.getExecutor(model)
            override fun getClient(model: LLModel): LLMClient =
                this@SingleLLMPromptExecutorIntegrationTest.getClient(model)
        }
        testBase.integration_testImageProcessing(scenario, model)
    }

    @ParameterizedTest
    @MethodSource("textScenarioModelCombinations")
    override fun integration_testTextProcessingBasic(scenario: TextTestScenario, model: LLModel) {
        val testBase = object : ExecutorIntegrationTestBase() {
            override fun getExecutor(): PromptExecutor = this@SingleLLMPromptExecutorIntegrationTest.getExecutor(model)
            override fun getClient(model: LLModel): LLMClient =
                this@SingleLLMPromptExecutorIntegrationTest.getClient(model)
        }
        testBase.integration_testTextProcessingBasic(scenario, model)
    }

    @ParameterizedTest
    @MethodSource("audioScenarioModelCombinations")
    override fun integration_testAudioProcessingBasic(scenario: AudioTestScenario, model: LLModel) {
        val testBase = object : ExecutorIntegrationTestBase() {
            override fun getExecutor(): PromptExecutor = this@SingleLLMPromptExecutorIntegrationTest.getExecutor(model)
            override fun getClient(model: LLModel): LLMClient =
                this@SingleLLMPromptExecutorIntegrationTest.getClient(model)
        }
        testBase.integration_testAudioProcessingBasic(scenario, model)
    }

    /*
     * Checking just images to make sure the file is uploaded in base64 format
     * */
    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testBase64EncodedAttachment(model: LLModel, client: LLMClient) {
        val testBase = object : ExecutorIntegrationTestBase() {
            override fun getExecutor(): PromptExecutor = getExecutor(model)
            override fun getClient(model: LLModel): LLMClient = client
        }
        testBase.integration_testBase64EncodedAttachment(model)
    }

    /*
     * Checking just images to make sure the file is uploaded by URL
     * */
    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testUrlBasedAttachment(model: LLModel, client: LLMClient) {
        val testBase = object : ExecutorIntegrationTestBase() {
            override fun getExecutor(): PromptExecutor = getExecutor(model)
            override fun getClient(model: LLModel): LLMClient = client
        }
        testBase.integration_testUrlBasedAttachment(model)
    }

    /*
     * Structured native/manual output tests.
     * */

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testStructuredOutputNative(model: LLModel, client: LLMClient) {
        val testBase = object : ExecutorIntegrationTestBase() {
            override fun getExecutor(): PromptExecutor = getExecutor(model)
            override fun getClient(model: LLModel): LLMClient = client
        }
        testBase.integration_testStructuredOutputNative(model)
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testStructuredOutputNativeWithFixingParser(model: LLModel, client: LLMClient) {
        val testBase = object : ExecutorIntegrationTestBase() {
            override fun getExecutor(): PromptExecutor = getExecutor(model)
            override fun getClient(model: LLModel): LLMClient = client
        }
        testBase.integration_testStructuredOutputNativeWithFixingParser(model)
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testStructuredOutputManual(model: LLModel, client: LLMClient) {
        val testBase = object : ExecutorIntegrationTestBase() {
            override fun getExecutor(): PromptExecutor = getExecutor(model)
            override fun getClient(model: LLModel): LLMClient = client
        }
        testBase.integration_testStructuredOutputManual(model)
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testStructuredOutputManualWithFixingParser(model: LLModel, client: LLMClient) {
        val testBase = object : ExecutorIntegrationTestBase() {
            override fun getExecutor(): PromptExecutor = getExecutor(model)
            override fun getClient(model: LLModel): LLMClient = client
        }
        testBase.integration_testStructuredOutputManualWithFixingParser(model)
    }
}
