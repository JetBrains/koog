package ai.koog.integration.tests.executor

import ai.koog.integration.tests.utils.MediaTestScenarios.AudioTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.ImageTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.MarkdownTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.TextTestScenario
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.TestCredentials.readTestGoogleAIKeyFromEnv
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.google.GoogleParams
import ai.koog.prompt.executor.clients.google.genai.GoogleGenaiLLMClient
import ai.koog.prompt.executor.clients.google.models.GoogleThinkingConfig
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import com.google.genai.Client
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junitpioneer.jupiter.ExpectedToFail
import java.util.stream.Stream
import kotlin.enums.EnumEntries

/**
 * Test for [Google Genai SDK](https://github.com/googleapis/java-genai/)
 * using the same suite of executor tests.
 */
class GoogleGenaiIntegrationTest : ExecutorIntegrationTestBase() {
    companion object {
        private fun EnumEntries<*>.combineGoogleModels(): Stream<Arguments> {
            return toList().flatMap { scenario ->
                Stream.of(GoogleModels.Gemini2_5Pro).toArray().map { model -> Arguments.of(scenario, model) }
            }.stream()
        }

        @JvmStatic
        fun markdownScenarioModelCombinations(): Stream<Arguments> {
            return MarkdownTestScenario.entries.combineGoogleModels()
        }

        @JvmStatic
        fun imageScenarioModelCombinations(): Stream<Arguments> {
            return ImageTestScenario.entries.combineGoogleModels()
        }

        @JvmStatic
        fun textScenarioModelCombinations(): Stream<Arguments> {
            return TextTestScenario.entries.combineGoogleModels()
        }

        @JvmStatic
        fun audioScenarioModelCombinations(): Stream<Arguments> {
            return AudioTestScenario.entries.combineGoogleModels()
        }

        @JvmStatic
        fun reasoningCapableModels(): Stream<LLModel> {
            return listOf(GoogleModels.Gemini3_Flash_Preview).stream()
        }

        @JvmStatic
        fun allCompletionModels(): Stream<LLModel> {
            return Models.googleModels()
        }
    }

    private val client = GoogleGenaiLLMClient(
        Client.builder()
            .apiKey(readTestGoogleAIKeyFromEnv())
            .vertexAI(false)
            .build()
    )

    private val executor: MultiLLMPromptExecutor = MultiLLMPromptExecutor(client)

    override fun getLLMClient(model: LLModel): LLMClient {
        require(model.provider == LLMProvider.Google) { "Model ${model.id} is not a Google model" }

        return client
    }

    override fun getExecutor(model: LLModel): PromptExecutor = executor

    override fun createReasoningParams(model: LLModel): LLMParams {
        require(model in reasoningCapableModels().toArray()) {
            "Model ${model.id} is not a reasoning capable model"
        }

        return GoogleParams(
            temperature = 0.01,
            thinkingConfig = GoogleThinkingConfig(includeThoughts = true)
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
    override fun integration_testToolWithOptionalParams(model: LLModel) {
        super.integration_testToolWithOptionalParams(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testToolWithNotRequiredOptionalParams(model: LLModel) {
        super.integration_testToolWithNotRequiredOptionalParams(model)
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

    @Disabled
    @ExpectedToFail("flaky! tune timeouts!")
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

    @ExpectedToFail("flaky! tune timeouts!")
    @Disabled
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

    @Disabled
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
}
