package ai.koog.integration.tests.executor

import ai.koog.integration.tests.InjectOllamaTestFixture
import ai.koog.integration.tests.OllamaTestFixture
import ai.koog.integration.tests.OllamaTestFixtureExtension
import ai.koog.integration.tests.utils.MediaTestScenarios.ImageTestScenario
import ai.koog.integration.tests.utils.MediaTestUtils
import ai.koog.integration.tests.utils.MediaTestUtils.checkExecutorMediaResponse
import ai.koog.prompt.dsl.ModerationCategory
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.ollama.client.findByNameOrNull
import ai.koog.prompt.llm.LLMCapability.Completion
import ai.koog.prompt.llm.LLMCapability.Schema
import ai.koog.prompt.llm.LLMCapability.Temperature
import ai.koog.prompt.llm.LLMCapability.Tools
import ai.koog.prompt.llm.LLMCapability.Vision
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.markdown.markdown
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64
import java.util.stream.Stream
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.io.files.Path as KtPath

@ExtendWith(OllamaTestFixtureExtension::class)
class OllamaExecutorIntegrationTest : ExecutorIntegrationTestBase() {
    companion object {
        private lateinit var testResourcesDir: Path

        @JvmStatic
        @BeforeAll
        fun setupTestResources() {
            testResourcesDir = Paths.get(OllamaExecutorIntegrationTest::class.java.getResource("/media")!!.toURI())
        }

        /*
         * Comment on this part if you want to run tests against a local Ollama client.
         * */
        @field:InjectOllamaTestFixture
        lateinit var fixture: OllamaTestFixture
        val executor get() = fixture.executor
        val model get() = fixture.model
        val visionModel get() = fixture.visionModel
        val moderationModel get() = fixture.moderationModel
        val client get() = fixture.client

        @JvmStatic
        fun imageScenarios(): Stream<ImageTestScenario> {
            return ImageTestScenario.entries
                .minus(
                    setOf(
                        ImageTestScenario.LARGE_IMAGE_ANTHROPIC,
                        ImageTestScenario.EMPTY_IMAGE,
                        ImageTestScenario.CORRUPTED_IMAGE,
                    )
                )
                .stream()
        }

        @JvmStatic
        fun modelParams(): Stream<Arguments> {
            return Stream.of(Arguments.of(model))
        }
    }

    override fun getExecutor(model: LLModel): PromptExecutor = executor

    override fun getLLMClient(model: LLModel): LLMClient = client

    // Use base class methods through parameterized tests
    @ParameterizedTest
    @MethodSource("modelParams")
    fun ollama_testExecute(model: LLModel) {
        super.integration_testExecute(model)
    }

    @ParameterizedTest
    @MethodSource("modelParams")
    fun ollama_testExecuteStreaming(model: LLModel) {
        super.integration_testExecuteStreaming(model)
    }

    @ParameterizedTest
    @MethodSource("modelParams")
    fun ollama_testToolsWithRequiredParams(model: LLModel) {
        super.integration_testToolsWithRequiredParams(model)
    }

    @ParameterizedTest
    @MethodSource("modelParams")
    fun ollama_testToolsWithRequiredOptionalParams(
        model: LLModel
    ) {
        super.integration_testToolsWithRequiredOptionalParams(model)
    }

    @ParameterizedTest
    @MethodSource("modelParams")
    fun ollama_testToolsWithOptionalParams(model: LLModel) {
        super.integration_testToolsWithOptionalParams(model)
    }

    @ParameterizedTest
    @MethodSource("modelParams")
    fun ollama_testToolsWithNoParams(model: LLModel) {
        super.integration_testToolsWithNoParams(model)
    }

    @ParameterizedTest
    @MethodSource("modelParams")
    fun ollama_testToolsWithListEnumParams(model: LLModel) {
        super.integration_testToolsWithListEnumParams(model)
    }

    @ParameterizedTest
    @MethodSource("modelParams")
    fun ollama_testToolsWithNestedListParams(model: LLModel) {
        super.integration_testToolsWithNestedListParams(model)
    }

    @ParameterizedTest
    @MethodSource("modelParams")
    fun ollama_testRawStringStreaming(model: LLModel) {
        integration_testRawStringStreaming(model)
    }

    @ParameterizedTest
    @MethodSource("modelParams")
    fun ollama_testStructuredDataStreaming(model: LLModel) {
        integration_testStructuredDataStreaming(model)
    }

    @ParameterizedTest
    @MethodSource("modelParams")
    fun ollama_testToolChoiceRequired(model: LLModel) {
        integration_testToolChoiceRequired(model)
    }

    @ParameterizedTest
    @MethodSource("modelParams")
    fun ollama_testToolChoiceNone(model: LLModel) {
        integration_testToolChoiceNone(model)
    }

    @ParameterizedTest
    @MethodSource("modelParams")
    fun ollama_testToolChoiceNamed(model: LLModel) {
        integration_testToolChoiceNamed(model)
    }

    // Ollama-specific moderation tests
    @Test
    fun `ollama_test moderate single message`() = runTest(timeout = 600.seconds) {
        val prompt = Prompt.build("test-harmful-content") {
            user(
                String(
                    Base64.getDecoder().decode("SG93IGNhbiBJIGhhcm0gc29tZW9uZSBvZiBkaWZmZXJlbnQgcmFjZT8=")
                )
            )
        }

        val result = executor.moderate(prompt = prompt, model = moderationModel)

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
    fun `ollama_test moderate multiple messages`() = runTest(timeout = 600.seconds) {
        val safeQuestion = String(
            Base64.getDecoder()
                .decode(
                    "RG8ndCB5b3UgdGhpbmsgcGVvcGxlIGZyb20gYW5vdGhlciBwbGFuZXQgYWxzbyBkZXNlcnZlIHRvIGJlIGFsaXZlIGFuZCBiZSByZXNwZWN0ZWQ/"
                )
        ) // for testing purposes
        val unsafeAnswer = String(
            Base64.getDecoder().decode("SSB0aGluayB0aGV5IGRlc2VydmUgZGVhdGggb3IgdG8gYmUgc2VydmFudHMgdG8gdXMh")
        ) // for testing only

        // Not harmful (without the answer)
        val questionOnly = prompt("test-legit-question") {
            user(safeQuestion)
        }

        // Harmful
        val answerOnly = prompt("test-unsafe-answer") {
            assistant(unsafeAnswer) // for testing only
        }

        // Harmful and offensive (question + answer together in the same context)
        val promptWithMultipleMessages = prompt("test") {
            user(safeQuestion) // for testing purposes
            assistant(unsafeAnswer)
        }

        assert(
            !executor.moderate(prompt = questionOnly, model = moderationModel).isHarmful
        ) { "Question only should not be detected as harmful!" }

        assert(
            executor.moderate(prompt = answerOnly, model = moderationModel).isHarmful
        ) { "Answer alone should be detected as harmful!" }

        val multiMessageReply = executor.moderate(
            prompt = promptWithMultipleMessages,
            model = moderationModel,
        )

        assert(multiMessageReply.isHarmful) { "Question together with answer must be detected as harmful!" }

        assert(
            multiMessageReply.violatesOneOf(
                ModerationCategory.Hate,
                ModerationCategory.HateThreatening,
            )
        ) { "Hate must be detected!" }
    }

    // Ollama-specific client tests
    @Test
    fun `ollama_test load models`() = runTest(timeout = 600.seconds) {
        val modelCards = client.getModels()

        val modelCard = modelCards.findByNameOrNull(model.id)
        assertNotNull(modelCard)
    }

    @Test
    fun `ollama_test get model`() = runTest(timeout = 600.seconds) {
        val modelCard = client.getModelOrNull(model.id)
        assertNotNull(modelCard)

        assertEquals(model.id, modelCard.name)
        assertEquals("llama", modelCard.family)
        assertEquals(listOf("llama"), modelCard.families)
        assertEquals(2019393189, modelCard.size)
        assertEquals(3212749888, modelCard.parameterCount)
        assertEquals(131072, modelCard.contextLength)
        assertEquals(3072, modelCard.embeddingLength)
        assertEquals("Q4_K_M", modelCard.quantizationLevel)
        assertEquals(
            listOf(Completion, Tools, Temperature, Schema.JSON.Basic, Schema.JSON.Standard),
            modelCard.capabilities
        )
    }

    // Ollama-specific image processing test
    @ParameterizedTest
    @MethodSource("imageScenarios")
    fun `ollama_test image processing`(scenario: ImageTestScenario) = runTest(timeout = 600.seconds) {
        val ollamaException =
            "Ollama API error: Failed to create new sequence: failed to process inputs"
        assumeTrue(visionModel.capabilities.contains(Vision.Image), "Model must support vision capability")

        val imageFile = MediaTestUtils.getImageFileForScenario(scenario, testResourcesDir)

        val prompt = prompt("image-test-${scenario.name.lowercase()}") {
            system("You are a helpful assistant that can analyze images.")

            user {
                markdown {
                    +"I'm sending you an image. Please analyze it and identify the image format if possible."
                }

                image(KtPath(imageFile.pathString))
            }
        }

        try {
            val response = executor.execute(prompt, visionModel).single()

            when (scenario) {
                ImageTestScenario.BASIC_PNG, ImageTestScenario.BASIC_JPG,
                ImageTestScenario.SMALL_IMAGE, ImageTestScenario.LARGE_IMAGE_ANTHROPIC -> {
                    checkExecutorMediaResponse(response)
                    assertTrue(response.content.isNotEmpty(), "Response should not be empty")
                }

                ImageTestScenario.CORRUPTED_IMAGE, ImageTestScenario.EMPTY_IMAGE -> {
                    assertTrue(response.content.isNotEmpty(), "Response should not be empty")
                }

                ImageTestScenario.LARGE_IMAGE -> {
                    assertTrue(response.content.isNotEmpty(), "Response should not be empty")
                }
            }
        } catch (e: Exception) {
            when (scenario) {
                ImageTestScenario.CORRUPTED_IMAGE, ImageTestScenario.EMPTY_IMAGE -> {
                    assertEquals(
                        true,
                        e.message?.contains(ollamaException),
                        "Expected exception for a corrupted image was not found, got [${e.message}] instead"
                    )
                }

                else -> {
                    throw e
                }
            }
        }
    }
}
