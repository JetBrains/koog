package ai.koog.integration.tests.executor

import ai.koog.integration.tests.utils.MediaTestScenarios.AudioTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.ImageTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.MarkdownTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.TextTestScenario
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.TestCredentials.readTestOpenAIKeyFromEnv
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.OpenAILLMProvider
import ai.koog.spring.ai.chat.SpringAiLLMClient
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import java.util.stream.Stream
import kotlin.enums.EnumEntries

/**
 * Test Spring AI LLM client integration
 */
class SpringAILLMClientV2IntegrationTest : ExecutorIntegrationTestBase() {
    companion object {
        private fun EnumEntries<*>.combineSpringAiModels(): Stream<Arguments> {
            return toList()
                .flatMap { scenario ->
                    Models
                        .springAiModels()
                        .toArray()
                        .map { model -> Arguments.of(scenario, model) }
                }
                .stream()
        }

        @JvmStatic
        fun markdownScenarioModelCombinations(): Stream<Arguments> {
            return MarkdownTestScenario.entries.combineSpringAiModels()
        }

        @JvmStatic
        fun imageScenarioModelCombinations(): Stream<Arguments> {
            return ImageTestScenario.entries.combineSpringAiModels()
        }

        @JvmStatic
        fun textScenarioModelCombinations(): Stream<Arguments> {
            return TextTestScenario.entries.combineSpringAiModels()
        }

        @JvmStatic
        fun audioScenarioModelCombinations(): Stream<Arguments> {
            return AudioTestScenario.entries.combineSpringAiModels()
        }

        @JvmStatic
        fun reasoningCapableModels(): Stream<LLModel> {
            return Models.springAiModels()
        }

        @JvmStatic
        fun allCompletionModels(): Stream<LLModel> {
            return Models.springAiModels()
        }
    }

    private val openAiClient: SpringAiLLMClient = run {
        val chatModel = OpenAiChatModel.builder()
            .options(
                OpenAiChatOptions.builder()
                    .apiKey(readTestOpenAIKeyFromEnv())
                    .build()
            )
            .build()

        SpringAiLLMClient.builder()
            .chatModel(chatModel)
            .provider(OpenAILLMProvider)
            .build()
    }

    private val executor: MultiLLMPromptExecutor = MultiLLMPromptExecutor(
        OpenAILLMProvider to openAiClient
    )

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
}
