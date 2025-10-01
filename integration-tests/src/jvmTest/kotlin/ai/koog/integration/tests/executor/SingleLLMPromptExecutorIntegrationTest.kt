package ai.koog.integration.tests.executor

import ai.koog.integration.tests.utils.MediaTestScenarios
import ai.koog.integration.tests.utils.MediaTestScenarios.AudioTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.ImageTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.MarkdownTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.TextTestScenario
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.TestUtils.readAwsAccessKeyIdFromEnv
import ai.koog.integration.tests.utils.TestUtils.readAwsSecretAccessKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readAwsSessionTokenFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestGoogleAIKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestOpenAIKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestOpenRouterKeyFromEnv
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.bedrock.BedrockClientSettings
import ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient
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

        val bedrockClientInstance = BedrockLLMClient(
            credentialsProvider = StaticCredentialsProvider {
                this.accessKeyId = readAwsAccessKeyIdFromEnv()
                this.secretAccessKey = readAwsSecretAccessKeyFromEnv()
                readAwsSessionTokenFromEnv()?.let { this.sessionToken = it }
            },
            settings = BedrockClientSettings()
        )

        @JvmStatic
        fun allModels(): Stream<Arguments> {
            return Stream.concat(
                Stream.concat(
                    Models.openAIModels().map { model -> Arguments.of(model) },
                    Models.anthropicModels().map { model -> Arguments.of(model) }
                ),
                Stream.concat(
                    Stream.concat(
                        Models.googleModels().map { model -> Arguments.of(model) },
                        Models.openRouterModels().map { model -> Arguments.of(model) }
                    ),
                    Models.bedrockModels().map { model -> Arguments.of(model) }
                )
            )
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

    override fun getExecutor(model: LLModel): PromptExecutor {
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

            LLMProvider.Bedrock -> bedrockClientInstance

            LLMProvider.Google -> GoogleLLMClient(
                readTestGoogleAIKeyFromEnv()
            )

            else -> throw IllegalArgumentException("Unsupported provider: ${model.provider}")
        }
    }

    @ParameterizedTest
    @MethodSource("allModels")
    override fun integration_testExecute(model: LLModel) {
        super.integration_testExecute(model)
    }

    @ParameterizedTest
    @MethodSource("allModels")
    override fun integration_testExecuteStreaming(model: LLModel) {
        super.integration_testExecuteStreaming(model)
    }

    @ParameterizedTest
    @MethodSource("allModels")
    override fun integration_testToolsWithRequiredParams(model: LLModel) {
        super.integration_testToolsWithRequiredParams(model)
    }

    @ParameterizedTest
    @MethodSource("allModels")
    override fun integration_testToolsWithRequiredOptionalParams(model: LLModel) {
        super.integration_testToolsWithRequiredOptionalParams(model)
    }

    @ParameterizedTest
    @MethodSource("allModels")
    override fun integration_testToolsWithOptionalParams(model: LLModel) {
        super.integration_testToolsWithOptionalParams(model)
    }

    @ParameterizedTest
    @MethodSource("allModels")
    override fun integration_testToolsWithNoParams(model: LLModel) {
        super.integration_testToolsWithNoParams(model)
    }

    @ParameterizedTest
    @MethodSource("allModels")
    override fun integration_testToolsWithListEnumParams(model: LLModel) {
        super.integration_testToolsWithListEnumParams(model)
    }

    @ParameterizedTest
    @MethodSource("allModels")
    override fun integration_testToolsWithNestedListParams(model: LLModel) {
        super.integration_testToolsWithNestedListParams(model)
    }

    @ParameterizedTest
    @MethodSource("allModels")
    override fun integration_testRawStringStreaming(model: LLModel) {
        super.integration_testRawStringStreaming(model)
    }

    @ParameterizedTest
    @MethodSource("allModels")
    override fun integration_testStructuredDataStreaming(model: LLModel) {
        super.integration_testStructuredDataStreaming(model)
    }

    @ParameterizedTest
    @MethodSource("allModels")
    override fun integration_testToolChoiceRequired(model: LLModel) {
        super.integration_testToolChoiceRequired(model)
    }

    @ParameterizedTest
    @MethodSource("allModels")
    override fun integration_testToolChoiceNone(model: LLModel) {
        super.integration_testToolChoiceNone(model)
    }

    @ParameterizedTest
    @MethodSource("allModels")
    override fun integration_testToolChoiceNamed(model: LLModel) {
        super.integration_testToolChoiceNamed(model)
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

    /*
     * Checking just images to make sure the file is uploaded in base64 format
     * */
    @ParameterizedTest
    @MethodSource("allModels")
    override fun integration_testBase64EncodedAttachment(model: LLModel) {
        super.integration_testBase64EncodedAttachment(model)
    }

    /*
     * Checking just images to make sure the file is uploaded by URL
     * */
    @ParameterizedTest
    @MethodSource("allModels")
    override fun integration_testUrlBasedAttachment(model: LLModel) {
        super.integration_testUrlBasedAttachment(model)
    }

    /*
     * Structured native/manual output tests.
     * */

    @ParameterizedTest
    @MethodSource("allModels")
    override fun integration_testStructuredOutputNative(model: LLModel) {
        super.integration_testStructuredOutputNative(model)
    }

    @ParameterizedTest
    @MethodSource("allModels")
    override fun integration_testStructuredOutputNativeWithFixingParser(model: LLModel) {
        super.integration_testStructuredOutputNativeWithFixingParser(model)
    }

    @ParameterizedTest
    @MethodSource("allModels")
    override fun integration_testStructuredOutputManual(model: LLModel) {
        super.integration_testStructuredOutputManual(model)
    }

    @ParameterizedTest
    @MethodSource("allModels")
    override fun integration_testStructuredOutputManualWithFixingParser(model: LLModel) {
        super.integration_testStructuredOutputManualWithFixingParser(model)
    }
}
