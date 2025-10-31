package ai.koog.integration.tests.executor

import ai.koog.integration.tests.utils.MediaTestScenarios
import ai.koog.integration.tests.utils.MediaTestScenarios.AudioTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.ImageTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.MarkdownTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.TextTestScenario
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.getLLMClientForProvider
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class MultipleLLMPromptExecutorIntegrationTest : ExecutorIntegrationTestBase() {

    companion object {
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

        @JvmStatic
        fun moderationModels(): Stream<Arguments> {
            return Models.moderationModels().map { model -> Arguments.of(model) }
        }

        @JvmStatic
        fun embeddingModels(): Stream<Arguments> {
            return Models.embeddingModels().map { model -> Arguments.of(model) }
        }

        @JvmStatic
        fun allCompletionModels(): Stream<Arguments> {
            return Models.allCompletionModels().map { model -> Arguments.of(model) }
        }
    }

    private val executor: MultiLLMPromptExecutor = run {
        val providers = allCompletionModels()
            .toList()
            .map { it.get().single() as LLModel }
            .map { it.provider }
            .distinct()

        val clients = providers.associateWith { getLLMClientForProvider(it) }

        MultiLLMPromptExecutor(clients)
    }

    override fun getExecutor(model: LLModel): PromptExecutor = executor

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
    override fun integration_testToolsWithRequiredParams(model: LLModel) {
        super.integration_testToolsWithRequiredParams(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testToolsWithRequiredOptionalParams(model: LLModel) {
        super.integration_testToolsWithRequiredOptionalParams(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testToolsWithOptionalParams(model: LLModel) {
        super.integration_testToolsWithOptionalParams(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testToolsWithNoParams(model: LLModel) {
        super.integration_testToolsWithNoParams(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testToolsWithListEnumParams(model: LLModel) {
        super.integration_testToolsWithListEnumParams(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testToolsWithNestedListParams(model: LLModel) {
        super.integration_testToolsWithNestedListParams(model)
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
    override fun integration_testRawStringStreaming(model: LLModel) {
        super.integration_testRawStringStreaming(model)
    }

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testStructuredDataStreaming(model: LLModel) {
        super.integration_testStructuredDataStreaming(model)
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

    @ParameterizedTest
    @MethodSource("allCompletionModels")
    override fun integration_testUrlBasedAttachment(model: LLModel) {
        super.integration_testUrlBasedAttachment(model)
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
    @MethodSource("embeddingModels")
    override fun integration_testEmbed(model: LLModel) {
        super.integration_testEmbed(model)
    }

    @ParameterizedTest
    @MethodSource("moderationModels")
    override fun integration_testSingleMessageModeration(model: LLModel) {
        super.integration_testSingleMessageModeration(model)
    }

    @ParameterizedTest
    @MethodSource("moderationModels")
    override fun integration_testMultipleMessagesModeration(model: LLModel) {
        super.integration_testMultipleMessagesModeration(model)
    }
}
