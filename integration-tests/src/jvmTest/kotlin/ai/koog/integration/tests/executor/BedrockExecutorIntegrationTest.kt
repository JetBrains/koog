package ai.koog.integration.tests.executor

import ai.koog.integration.tests.utils.MediaTestScenarios.AudioTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.ImageTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.MarkdownTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.TextTestScenario
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.TestUtils.readAwsAccessKeyIdFromEnv
import ai.koog.integration.tests.utils.TestUtils.readAwsSecretAccessKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readAwsSessionTokenFromEnv
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.bedrock.BedrockClientSettings
import ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient
import ai.koog.prompt.executor.llms.all.simpleBedrockExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

@Execution(ExecutionMode.SAME_THREAD)
class BedrockExecutorIntegrationTest : ExecutorIntegrationTestBase() {
    companion object {
        @JvmStatic
        fun bedrockCombinations(): Stream<Arguments> {
            return Models.bedrockModels().map { model -> Arguments.of(model) }
        }

        @JvmStatic
        fun bedrockMarkdownScenarioModelCombinations(): Stream<Arguments> {
            return Models.bedrockModels().flatMap { model ->
                listOf(
                    MarkdownTestScenario.BASIC_MARKDOWN,
                    MarkdownTestScenario.HEADERS,
                    MarkdownTestScenario.TABLES,
                    MarkdownTestScenario.CODE_BLOCKS
                ).map { scenario -> Arguments.of(scenario, model) }.stream()
            }
        }

        @JvmStatic
        fun bedrockImageScenarioModelCombinations(): Stream<Arguments> {
            return Models.bedrockModels()
                .filter { it.capabilities.contains(ai.koog.prompt.llm.LLMCapability.Vision.Image) }
                .flatMap { model ->
                    listOf(
                        ImageTestScenario.BASIC_PNG,
                        ImageTestScenario.BASIC_JPG
                    ).map { scenario -> Arguments.of(scenario, model) }.stream()
                }
        }

        @JvmStatic
        fun bedrockTextScenarioModelCombinations(): Stream<Arguments> {
            return Models.bedrockModels().flatMap { model ->
                listOf(
                    TextTestScenario.BASIC_TEXT,
                    TextTestScenario.LONG_TEXT_5_MB
                ).map { scenario -> Arguments.of(scenario, model) }.stream()
            }
        }

        @JvmStatic
        fun bedrockAudioScenarioModelCombinations(): Stream<Arguments> {
            return Models.bedrockModels().filter { it.capabilities.contains(ai.koog.prompt.llm.LLMCapability.Audio) }
                .flatMap { model ->
                    listOf(
                        AudioTestScenario.BASIC_MP3
                    ).map { scenario -> Arguments.of(scenario, model) }.stream()
                }
        }
    }

    private fun getBedrockExecutor() = simpleBedrockExecutor(
        readAwsAccessKeyIdFromEnv(),
        readAwsSecretAccessKeyFromEnv(),
        readAwsSessionTokenFromEnv(),
    )

    override fun getExecutor(model: LLModel): PromptExecutor = getBedrockExecutor()

    override fun getClient(model: LLModel): LLMClient {
        return BedrockLLMClient(
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = readAwsAccessKeyIdFromEnv()
                secretAccessKey = readAwsSecretAccessKeyFromEnv()
                sessionToken = readAwsSessionTokenFromEnv()
            },
            settings = BedrockClientSettings()
        )
    }

    @ParameterizedTest
    @MethodSource("bedrockCombinations")
    fun integration_testSimpleBedrockExecutor(model: LLModel) {
        integration_testExecute(model)
    }

    @ParameterizedTest
    @MethodSource("bedrockCombinations")
    fun integration_testExecuteStreamingBedrock(model: LLModel) {
        integration_testExecuteStreaming(model)
    }

    @ParameterizedTest
    @MethodSource("bedrockCombinations")
    fun integration_testToolsWithRequiredParamsBedrock(model: LLModel) {
        integration_testToolsWithRequiredParams(model)
    }

    @ParameterizedTest
    @MethodSource("bedrockCombinations")
    fun integration_testToolsWithRequiredOptionalParamsBedrock(model: LLModel) {
        integration_testToolsWithRequiredOptionalParams(model)
    }

    @ParameterizedTest
    @MethodSource("bedrockCombinations")
    fun integration_testToolsWithOptionalParamsBedrock(model: LLModel) {
        integration_testToolsWithOptionalParams(model)
    }

    @ParameterizedTest
    @MethodSource("bedrockCombinations")
    fun integration_testToolsWithNoParamsBedrock(model: LLModel) {
        integration_testToolsWithNoParams(model)
    }

    @ParameterizedTest
    @MethodSource("bedrockCombinations")
    fun integration_testToolsWithListEnumParamsBedrock(model: LLModel) {
        integration_testToolsWithListEnumParams(model)
    }

    @ParameterizedTest
    @MethodSource("bedrockCombinations")
    fun integration_testToolsWithNestedListParamsBedrock(model: LLModel) {
        integration_testToolsWithNestedListParams(model)
    }

    @ParameterizedTest
    @MethodSource("bedrockCombinations")
    fun integration_testRawStringStreamingBedrock(model: LLModel) {
        integration_testRawStringStreaming(model)
    }

    @ParameterizedTest
    @MethodSource("bedrockCombinations")
    fun integration_testStructuredDataStreamingBedrock(model: LLModel) {
        integration_testStructuredDataStreaming(model)
    }

    @ParameterizedTest
    @MethodSource("bedrockCombinations")
    fun integration_testToolChoiceRequiredBedrock(model: LLModel) {
        integration_testToolChoiceRequired(model)
    }

    @ParameterizedTest
    @MethodSource("bedrockCombinations")
    fun integration_testToolChoiceNoneBedrock(model: LLModel) {
        integration_testToolChoiceNone(model)
    }

    @ParameterizedTest
    @MethodSource("bedrockCombinations")
    fun integration_testToolChoiceNamedBedrock(model: LLModel) {
        integration_testToolChoiceNamed(model)
    }

    @ParameterizedTest
    @MethodSource("bedrockCombinations")
    fun integration_testBase64EncodedAttachmentBedrock(model: LLModel) {
        integration_testBase64EncodedAttachment(model)
    }

    @ParameterizedTest
    @MethodSource("bedrockCombinations")
    fun integration_testUrlBasedAttachmentBedrock(model: LLModel) {
        integration_testUrlBasedAttachment(model)
    }

    @ParameterizedTest
    @MethodSource("bedrockCombinations")
    fun integration_testStructuredOutputNativeBedrock(model: LLModel) {
        integration_testStructuredOutputNative(model)
    }

    @ParameterizedTest
    @MethodSource("bedrockCombinations")
    fun integration_testStructuredOutputNativeWithFixingParserBedrock(model: LLModel) {
        integration_testStructuredOutputNativeWithFixingParser(model)
    }

    @ParameterizedTest
    @MethodSource("bedrockCombinations")
    fun integration_testStructuredOutputManualBedrock(model: LLModel) {
        integration_testStructuredOutputManual(model)
    }

    @ParameterizedTest
    @MethodSource("bedrockCombinations")
    fun integration_testStructuredOutputManualWithFixingParserBedrock(model: LLModel) {
        integration_testStructuredOutputManualWithFixingParser(model)
    }

    @ParameterizedTest
    @MethodSource("bedrockMarkdownScenarioModelCombinations")
    fun integration_testMarkdownProcessingBasicBedrock(scenario: MarkdownTestScenario, model: LLModel) {
        integration_testMarkdownProcessingBasic(scenario, model)
    }

    @ParameterizedTest
    @MethodSource("bedrockImageScenarioModelCombinations")
    fun integration_testImageProcessingBedrock(scenario: ImageTestScenario, model: LLModel) {
        integration_testImageProcessing(scenario, model)
    }

    @ParameterizedTest
    @MethodSource("bedrockTextScenarioModelCombinations")
    fun integration_testTextProcessingBasicBedrock(scenario: TextTestScenario, model: LLModel) {
        integration_testTextProcessingBasic(scenario, model)
    }

    @ParameterizedTest
    @MethodSource("bedrockAudioScenarioModelCombinations")
    fun integration_testAudioProcessingBasicBedrock(scenario: AudioTestScenario, model: LLModel) {
        integration_testAudioProcessingBasic(scenario, model)
    }
}
