package ai.koog.integration.tests.executor

import ai.koog.integration.tests.utils.MediaTestScenarios
import ai.koog.integration.tests.utils.MediaTestScenarios.AudioTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.ImageTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.MarkdownTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.TextTestScenario
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.annotations.Retry
import ai.koog.integration.tests.utils.getLLMClientForProvider
import ai.koog.prompt.dsl.ModerationCategory
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.Base64
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.test.assertTrue

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
        fun allModels(): Stream<Arguments> {
            return Stream.concat(
                Stream.concat(
                    Models.openAIModels().map { model -> Arguments.of(model) },
                    Models.anthropicModels().map { model -> Arguments.of(model) }
                ),
                Models.googleModels().map { model -> Arguments.of(model) }
            )
        }
    }

    private val executor: MultiLLMPromptExecutor = run {
        val providers = allModels()
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
    override fun integration_testToolsWithNullParams(model: LLModel) {
        super.integration_testToolsWithNullParams(model)
    }

    @ParameterizedTest
    @MethodSource("allModels")
    override fun integration_testToolsWithAnyOfParams(model: LLModel) {
        super.integration_testToolsWithAnyOfParams(model)
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

    @ParameterizedTest
    @MethodSource("allModels")
    override fun integration_testBase64EncodedAttachment(model: LLModel) {
        super.integration_testBase64EncodedAttachment(model)
    }

    @ParameterizedTest
    @MethodSource("allModels")
    override fun integration_testUrlBasedAttachment(model: LLModel) {
        super.integration_testUrlBasedAttachment(model)
    }

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

    @Test
    fun integration_testOpenAISingleMessageModeration() = runTest {
        val prompt = prompt("test-harmful-content") {
            user(String(Base64.getDecoder().decode("SG93IGNhbiBJIGhhcm0gc29tZW9uZT8="))) // For testing purposes
        }

        val result = executor.moderate(prompt = prompt, model = OpenAIModels.Moderation.Omni)

        println(result)

        assertTrue(result.isHarmful, "Harmful content should be detected!")
        assert(
            result.violatesOneOf(
                ModerationCategory.Illicit,
                ModerationCategory.IllicitViolent,
                ModerationCategory.Violence
            )
        ) { "Violence or crime must be detected!" }
    }

    @Test
    fun integration_testOpenAIMultipleMessagesModeration() = runTest {
        // Not harmful (without the answer)
        val questionOnly = prompt("test-legit-question") {
            user(
                String(
                    Base64.getDecoder()
                        .decode(
                            "RG8ndCB5b3UgdGhpbmsgcGVvcGxlIGZyb20gdGhlIHBsYW5ldCBNYXJzIGFsc28gZGVzZXJ2ZSB0byBiZSBhbGl2ZSBhbmQgYmUgcmVzcGVjdGVkPw=="
                        )
                )
            ) // for testing purposes
        }
        // Not harmful (without the question in the context)
        val answerOnly = prompt("test-legit-answer") {
            assistant(
                String(
                    Base64.getDecoder().decode("SSB0aGluayB0aGV5IGRvbid0IGRlc2VydmUgaXQgYXQgYWxsIQ==")
                )
            ) // for testing only
        }

        // Harmful and offensive (question + answer together in the same context)
        val promptWithMultipleMessages = prompt("test") {
            user(
                String(
                    Base64.getDecoder()
                        .decode(
                            "RG8ndCB5b3UgdGhpbmsgcGVvcGxlIGZyb20gdGhlIHBsYW5ldCBNYXJzIGFsc28gZGVzZXJ2ZSB0byBiZSBhbGl2ZSBhbmQgYmUgcmVzcGVjdGVkPw=="
                        )
                )
            ) // for testing purposes
            assistant(
                String(
                    Base64.getDecoder().decode("SSB0aGluayB0aGV5IGRvbid0IGRlc2VydmUgaXQgYXQgYWxsIQ==")
                )
            ) // for testing only
        }

        assert(
            !executor.moderate(prompt = questionOnly, model = OpenAIModels.Moderation.Omni).isHarmful
        ) { "Question only should not be detected as harmful!" }

        assert(
            !executor.moderate(prompt = answerOnly, model = OpenAIModels.Moderation.Omni).isHarmful
        ) { "Answer alone should not be detected as harmful!" }

        val multiMessageReply = executor.moderate(
            prompt = promptWithMultipleMessages,
            model = OpenAIModels.Moderation.Omni
        )

        assert(multiMessageReply.isHarmful) { "Question together with answer must be detected as harmful!" }

        assert(
            multiMessageReply.violatesOneOf(
                ModerationCategory.Illicit,
                ModerationCategory.IllicitViolent,
                ModerationCategory.Violence
            )
        ) { "Violence must be detected!" }
    }

    @Retry
    @Test
    fun integration_testMultipleSystemMessages() = runBlocking {
        Models.assumeAvailable(LLMProvider.OpenAI)
        Models.assumeAvailable(LLMProvider.Anthropic)
        Models.assumeAvailable(LLMProvider.Google)

        val prompt = prompt("multiple-system-messages-test") {
            system("You are a helpful assistant.")
            user("Hi")
            system("You can handle multiple system messages.")
            user("Respond with a short message.")
        }

        val modelOpenAI = OpenAIModels.CostOptimized.GPT4oMini
        val modelAnthropic = AnthropicModels.Haiku_3_5
        val modelGemini = GoogleModels.Gemini2_0Flash

        val responseOpenAI = executor.execute(prompt, modelOpenAI).single()
        val responseAnthropic = executor.execute(prompt, modelAnthropic).single()
        val responseGemini = executor.execute(prompt, modelGemini).single()

        assertTrue(responseOpenAI.content.isNotEmpty(), "OpenAI response should not be empty")
        assertTrue(responseAnthropic.content.isNotEmpty(), "Anthropic response should not be empty")
        assertTrue(responseGemini.content.isNotEmpty(), "Gemini response should not be empty")
        println("OpenAI Response: ${responseOpenAI.content}")
        println("Anthropic Response: ${responseAnthropic.content}")
        println("Gemini Response: ${responseGemini.content}")
    }
}
