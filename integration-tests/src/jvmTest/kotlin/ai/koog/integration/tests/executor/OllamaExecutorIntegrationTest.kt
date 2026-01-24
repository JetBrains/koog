package ai.koog.integration.tests.executor

import ai.koog.integration.tests.InjectOllamaTestFixture
import ai.koog.integration.tests.OllamaTestFixture
import ai.koog.integration.tests.OllamaTestFixtureExtension
import ai.koog.integration.tests.utils.MediaTestScenarios
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
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.booleans.shouldNotBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64
import java.util.stream.Stream
import kotlin.io.path.pathString
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
        super.integration_testToolWithRequiredParams(model)
    }

    @ParameterizedTest
    @MethodSource("modelParams")
    fun ollama_testToolsWithRequiredOptionalParams(
        model: LLModel
    ) {
        super.integration_testToolWithNotRequiredOptionalParams(model)
    }

    @ParameterizedTest
    @MethodSource("modelParams")
    fun ollama_testToolsWithOptionalParams(model: LLModel) {
        super.integration_testToolWithOptionalParams(model)
    }

    @ParameterizedTest
    @MethodSource("modelParams")
    fun ollama_testToolsWithNoParams(model: LLModel) {
        super.integration_testToolWithNoParams(model)
    }

    @ParameterizedTest
    @MethodSource("modelParams")
    fun ollama_testToolsWithListEnumParams(model: LLModel) {
        super.integration_testToolWithListEnumParams(model)
    }

    @ParameterizedTest
    @MethodSource("modelParams")
    fun ollama_testToolsWithNestedListParams(model: LLModel) {
        super.integration_testToolWithNestedListParams(model)
    }

    @ParameterizedTest
    @MethodSource("modelParams")
    fun ollama_testStructuredDataStreaming(model: LLModel) {
        integration_testMarkdownStructuredDataStreaming(model)
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

        result.isHarmful.shouldBeTrue()
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

        withClue("Question only should not be detected as harmful!") {
            executor.moderate(prompt = questionOnly, model = moderationModel).isHarmful.shouldNotBeTrue()
        }

        withClue("Answer alone should be detected as harmful!") {
            executor.moderate(prompt = answerOnly, model = moderationModel).isHarmful.shouldBeTrue()
        }

        val multiMessageReply = executor.moderate(
            prompt = promptWithMultipleMessages,
            model = moderationModel,
        )

        withClue("Question together with answer must be detected as harmful!") {
            multiMessageReply.isHarmful.shouldBeTrue()
        }

        withClue("Hate must be detected!") {
            multiMessageReply.violatesOneOf(
                ModerationCategory.Hate,
                ModerationCategory.HateThreatening,
            ).shouldBeTrue()
        }
    }

    // Ollama-specific client tests
    @Test
    fun `ollama_test load models`() = runTest(timeout = 600.seconds) {
        val modelCards = client.getModels()

        val modelCard = modelCards.findByNameOrNull(model.id)
        modelCard shouldNotBe null
    }

    @Test
    fun `ollama_test get model`() = runTest(timeout = 600.seconds) {
        client.getModelOrNull(model.id) shouldNotBeNull {
            name shouldBe model.id
            family shouldBe "llama"
            families shouldBe listOf("llama")
            size shouldBe 2019393189
            parameterCount shouldBe 3212749888
            contextLength shouldBe 131072
            embeddingLength shouldBe 3072
            quantizationLevel shouldBe "Q4_K_M"
            capabilities shouldBe listOf(
                Completion,
                Tools,
                Temperature,
                Schema.JSON.Basic,
                Schema.JSON.Standard
            )
        }
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
                ImageTestScenario.LARGE_IMAGE_ANTHROPIC -> {
                    checkExecutorMediaResponse(response)
                    response.content.shouldNotBeBlank()
                }

                ImageTestScenario.CORRUPTED_IMAGE, ImageTestScenario.EMPTY_IMAGE -> {
                    response.content.shouldNotBeBlank()
                }

                ImageTestScenario.LARGE_IMAGE -> {
                    response.content.shouldNotBeBlank()
                }
            }
        } catch (e: Exception) {
            when (scenario) {
                ImageTestScenario.CORRUPTED_IMAGE, ImageTestScenario.EMPTY_IMAGE -> {
                    (e.message?.contains(ollamaException) == true).shouldBeTrue()
                }

                else -> {
                    throw e
                }
            }
        }
    }

    @Test
    fun `ollama_test txt file processing`() = runTest(timeout = 600.seconds) {
        val textFile = MediaTestUtils.createTextFileForScenario(
            MediaTestScenarios.TextTestScenario.BASIC_TEXT,
            testResourcesDir
        )

        val prompt = prompt("text-file-test") {
            system("You are a helpful assistant that can analyze text files.")

            user {
                markdown {
                    +"I'm sending you a text file. Please read its content and summarize it."
                }

                textFile(KtPath(textFile.pathString), "text/plain")
            }
        }

        val response = executor.execute(prompt, model).single()
        response.content.shouldNotBeBlank()
    }
}
