package ai.koog.integration.tests.capabilities

import ai.koog.integration.tests.utils.MediaTestScenarios
import ai.koog.integration.tests.utils.MediaTestUtils.createAudioFileForScenario
import ai.koog.integration.tests.utils.MediaTestUtils.createTextFileForScenario
import ai.koog.integration.tests.utils.MediaTestUtils.createVideoFileForScenario
import ai.koog.integration.tests.utils.MediaTestUtils.getImageFileForScenario
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.RetryUtils.withRetry
import ai.koog.integration.tests.utils.TestCredentials.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.utils.TestCredentials.readTestGoogleAIKeyFromEnv
import ai.koog.integration.tests.utils.TestCredentials.readTestOpenAIKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.assertExceptionMessageContains
import ai.koog.integration.tests.utils.TestUtils.isValidJson
import ai.koog.integration.tests.utils.TestUtils.singlePropertyObjectSchema
import ai.koog.integration.tests.utils.tools.SimpleCalculatorTool
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIChatParams
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openai.OpenAIResponsesParams
import ai.koog.prompt.executor.llms.all.DefaultMultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.params.LLMParams.ToolChoice
import io.kotest.inspectors.shouldForAny
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.io.path.pathString
import kotlin.io.path.readBytes
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
import kotlinx.io.files.Path as KtPath

private const val EXPECTED_ERROR = "does not support"

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.CONCURRENT)
class ModelCapabilitiesIntegrationTest {
    private lateinit var openAIClient: OpenAILLMClient
    private lateinit var anthropicClient: AnthropicLLMClient
    private lateinit var googleClient: GoogleLLMClient
    private lateinit var executor: DefaultMultiLLMPromptExecutor
    private lateinit var testResourcesDir: Path

    @BeforeAll
    fun setup() {
        val openAIKey = readTestOpenAIKeyFromEnv()
        val anthropicKey = readTestAnthropicKeyFromEnv()
        val googleKey = readTestGoogleAIKeyFromEnv()

        openAIClient = OpenAILLMClient(openAIKey)
        anthropicClient = AnthropicLLMClient(anthropicKey)
        googleClient = GoogleLLMClient(googleKey)
        executor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient, googleClient)

        val resourceUrl = this::class.java.getResource("/media") ?: error("Resource folder '/media' not found.")
        testResourcesDir = Path.of(resourceUrl.toURI())
    }

    companion object {
        @JvmStatic
        fun allModels(): Stream<LLModel> = Stream.of(
            Models.openAIModels(),
            Models.anthropicModels(),
            Models.googleModels(),
        ).flatMap { it }

        private val allCapabilities = listOf(
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.MultipleChoices,
            LLMCapability.Vision.Image,
            LLMCapability.Vision.Video,
            LLMCapability.Audio,
            LLMCapability.Document,
            LLMCapability.Embed,
            LLMCapability.Completion,
            LLMCapability.Moderation,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
            LLMCapability.PromptCaching,
            LLMCapability.OpenAIEndpoint.Completions,
            LLMCapability.OpenAIEndpoint.Responses,
        )

        @JvmStatic
        fun positiveModelCapabilityCombinations(): Stream<Arguments> =
            allModels().flatMap { model ->
                model.capabilities.stream().map { capability ->
                    Arguments.of(model, capability)
                }
            }

        @JvmStatic
        fun negativeModelCapabilityCombinations(): Stream<Arguments> =
            allModels().flatMap { model ->
                allCapabilities.stream()
                    .filter { capability -> !model.capabilities.contains(capability) }
                    .map { capability -> Arguments.of(model, capability) }
            }
    }

    @ParameterizedTest
    @MethodSource("positiveModelCapabilityCombinations")
    @OptIn(ExperimentalEncodingApi::class)
    fun integration_positiveCapabilityShouldWork(model: LLModel, capability: LLMCapability) =
        runTest(timeout = 300.seconds) {
            when (capability) {
                LLMCapability.Completion -> {
                    val prompt = prompt("cap-completion-positive") {
                        system("You are a helpful assistant.")
                        user("Say hello in one short sentence.")
                    }
                    withRetry {
                        checkAssistantResponse(prompt, model)
                    }
                }

                LLMCapability.Tools, LLMCapability.ToolChoice -> {
                    val tools = SimpleCalculatorTool.descriptor
                    val prompt = prompt("cap-tools-positive", params = LLMParams(toolChoice = ToolChoice.Required)) {
                        system("You are a helpful assistant.")
                        user("Compute 2 + 3.")
                    }
                    withRetry {
                        executor.execute(prompt, model, listOf(tools))
                            .shouldNotBeEmpty()
                            .shouldForAny { it is Message.Tool.Call }
                    }
                }

                LLMCapability.Vision.Image -> {
                    val imagePath = testResourcesDir.resolve("basic.jpeg")
                    val base64 = Base64.encode(imagePath.readBytes())
                    val prompt = prompt("cap-vision-image-positive") {
                        system("You are a helpful assistant that can describe images.")
                        user {
                            markdown { +"Describe the image in 5-10 words." }
                            image(
                                ContentPart.Image(
                                    content = AttachmentContent.Binary.Base64(base64),
                                    format = "jpeg",
                                    mimeType = "image/jpeg"
                                )
                            )
                        }
                    }
                    withRetry {
                        checkAssistantResponse(prompt, model)
                    }
                }

                LLMCapability.Audio -> {
                    val audioPath = createAudioFileForScenario(
                        MediaTestScenarios.AudioTestScenario.BASIC_MP3,
                        testResourcesDir
                    )
                    val base64 = Base64.encode(audioPath.readBytes())
                    val prompt = prompt("cap-audio-positive") {
                        system("You are a helpful assistant that can transcribe audio.")
                        user {
                            markdown { +"Transcribe the attached audio in 5-10 words." }
                            audio(
                                ContentPart.Audio(
                                    AttachmentContent.Binary.Base64(base64),
                                    format = "mp3"
                                )
                            )
                        }
                    }
                    withRetry {
                        checkAssistantResponse(prompt, model)
                    }
                }

                LLMCapability.Document -> {
                    // KG-620 GPT-5.1-Codex fails to process the text input file
                    assumeTrue(
                        model != OpenAIModels.Chat.GPT5_1Codex,
                        "Skipping document capability test for ${model.id}, see KG-620"
                    )

                    val file = createTextFileForScenario(
                        MediaTestScenarios.TextTestScenario.BASIC_TEXT,
                        testResourcesDir
                    )
                    val prompt = prompt("cap-document-positive") {
                        system("You are a helpful assistant that can read attached documents.")
                        user {
                            markdown { +"Summarize the attached text file in 5-10 words." }
                            textFile(KtPath(file.pathString), "text/plain")
                        }
                    }
                    withRetry {
                        checkAssistantResponse(prompt, model)
                    }
                }

                LLMCapability.Moderation -> {
                    val prompt = prompt("cap-moderation-positive") {
                        user("This is a harmless request about the weather.")
                    }
                    withRetry {
                        executor.moderate(prompt, model) shouldNotBeNull {
                            this.isHarmful.shouldBeFalse()
                        }
                    }
                }

                LLMCapability.MultipleChoices -> {
                    assumeTrue(
                        model.provider !is LLMProvider.Google,
                        "https://github.com/googleapis/python-genai/issues/1723"
                    )
                    val prompt = prompt(
                        "cap-multiple-choices-positive",
                        params = LLMParams(numberOfChoices = 2)
                    ) {
                        system("You are a helpful assistant. Provide concise answers.")
                        user("Provide multiple distinct options for a team name.")
                    }
                    withRetry {
                        with(executor.executeMultipleChoices(prompt, model, emptyList())) {
                            size shouldBe 2
                            forEach { choice ->
                                choice
                                    .shouldNotBeEmpty()
                                    .shouldForAny { it is Message.Assistant && it.content.isNotBlank() }
                            }
                        }
                    }
                }

                LLMCapability.Vision.Video -> {
                    val videoPath = createVideoFileForScenario(testResourcesDir)
                    val base64 = Base64.encode(videoPath.readBytes())
                    val prompt = prompt("cap-vision-video-positive") {
                        system("You are a helpful assistant that can analyze short videos.")
                        user {
                            markdown { +"Describe in 5-10 words what you can infer from the attached video." }
                            video(
                                ContentPart.Video(
                                    content = AttachmentContent.Binary.Base64(base64),
                                    format = "mp4",
                                    mimeType = "video/mp4",
                                )
                            )
                        }
                    }
                    withRetry {
                        checkAssistantResponse(prompt, model)
                    }
                }

                LLMCapability.Embed -> {
                    withRetry {
                        val vector = openAIClient.embed("Provide an embedding for this sentence.", model)
                        vector.shouldNotBeEmpty().shouldForAny { it != 0.0 }
                    }
                }

                LLMCapability.Schema.JSON.Basic -> {
                    val schema = singlePropertyObjectSchema(model.provider, "x", "integer")
                    val prompt = prompt(
                        "cap-json-basic-positive",
                        params = LLMParams(schema = LLMParams.Schema.JSON.Basic(name = "XSchema", schema = schema))
                    ) {
                        system("Reply strictly as JSON. Only include the JSON object.")
                        user("Return an integer x field with any small integer.")
                    }
                    withRetry {
                        with(
                            executor.execute(prompt, model).filterIsInstance<Message.Assistant>()
                                .joinToString("\n") { it.content }
                        ) {
                            shouldNotBeBlank()
                            isValidJson(this).shouldBeTrue()
                            shouldContain("\"x\"")
                        }
                    }
                }

                LLMCapability.Schema.JSON.Standard -> {
                    val schema = singlePropertyObjectSchema(model.provider, "y", "string")
                    val prompt = prompt(
                        "cap-json-standard-positive",
                        params = LLMParams(schema = LLMParams.Schema.JSON.Standard(name = "YSchema", schema = schema))
                    ) {
                        system("Reply strictly as JSON. Only include the JSON object.")
                        user("Return a string y field.")
                    }
                    withRetry {
                        with(
                            executor.execute(prompt, model).filterIsInstance<Message.Assistant>()
                                .joinToString("\n") { it.content }
                        ) {
                            shouldNotBeBlank()
                            shouldContain("\"y\"")
                            isValidJson(this).shouldBeTrue()
                        }
                    }
                }

                LLMCapability.PromptCaching -> {
                    val prompt = prompt("cap-prompt-caching-positive") {
                        system("You are a helpful assistant. Consider this a cached-system setup.")
                        user("Say hello in one short sentence.")
                    }
                    withRetry {
                        checkAssistantResponse(prompt, model)
                    }
                }

                LLMCapability.OpenAIEndpoint.Completions -> {
                    assumeTrue(model.provider is LLMProvider.OpenAI)
                    val prompt = prompt("cap-openai-endpoint-completions-positive", params = OpenAIChatParams()) {
                        system("You are a helpful assistant.")
                        user("Say hello in one short sentence.")
                    }
                    withRetry {
                        checkAssistantResponse(prompt, model)
                    }
                }

                LLMCapability.OpenAIEndpoint.Responses -> {
                    assumeTrue(model.provider is LLMProvider.OpenAI)
                    val prompt =
                        prompt("cap-openai-endpoint-responses-positive", params = OpenAIResponsesParams()) {
                            system("You are a helpful assistant.")
                            user("Say hello in one short sentence.")
                        }
                    withRetry {
                        checkAssistantResponse(prompt, model)
                    }
                }

                else -> {
                    assumeTrue(false, "Skipping hard-to-verify capability verification for $capability on ${model.id}")
                }
            }
        }

    @ParameterizedTest
    @MethodSource("negativeModelCapabilityCombinations")
    @OptIn(ExperimentalEncodingApi::class)
    fun integration_negativeCapabilityShouldFail(model: LLModel, capability: LLMCapability) =
        runTest(timeout = 300.seconds) {
            when (capability) {
                LLMCapability.Completion -> {
                    val prompt = prompt("cap-completion-negative") {
                        system("You are a helpful assistant.")
                        user("Say hello in one short sentence.")
                    }
                    withRetry {
                        assertExceptionMessageContains(
                            assertFails(prompt, model),
                            "$EXPECTED_ERROR chat completions",
                            "not a chat completion"
                        )
                    }
                }

                LLMCapability.Tools, LLMCapability.ToolChoice -> {
                    val tools = SimpleCalculatorTool.descriptor
                    val prompt = prompt("cap-tools-negative", params = LLMParams(toolChoice = ToolChoice.Required)) {
                        system("You are a helpful assistant with a calculator tool. Always use the tool.")
                        user("Compute 2 + 3.")
                    }
                    withRetry {
                        assertExceptionMessageContains(
                            assertFailsWith<Exception> {
                                executor.execute(prompt, model, listOf(tools))
                            },
                            "$EXPECTED_ERROR tools"
                        )
                    }
                }

                LLMCapability.Vision.Image -> {
                    val imagePath = getImageFileForScenario(
                        MediaTestScenarios.ImageTestScenario.BASIC_PNG,
                        testResourcesDir
                    )
                    val base64 = Base64.encode(imagePath.readBytes())
                    val prompt = prompt("cap-vision-image-negative") {
                        system("You are a helpful assistant that can describe images.")
                        user {
                            markdown { +"Describe the image in 5-10 words." }
                            image(
                                ContentPart.Image(
                                    content = AttachmentContent.Binary.Base64(base64),
                                    format = "png",
                                    mimeType = "image/png"
                                )
                            )
                        }
                    }
                    withRetry {
                        assertExceptionMessageContains(
                            assertFails(prompt, model),
                            "$EXPECTED_ERROR image",
                            "Unsupported attachment type"
                        )
                    }
                }

                LLMCapability.Audio -> {
                    val audioPath = createAudioFileForScenario(
                        MediaTestScenarios.AudioTestScenario.BASIC_MP3,
                        testResourcesDir
                    )
                    val base64 = Base64.encode(audioPath.readBytes())
                    val prompt = prompt("cap-audio-negative") {
                        system("You are a helpful assistant that can transcribe audio.")
                        user {
                            markdown { +"Transcribe the attached audio in 5-10 words." }
                            audio(
                                ContentPart.Audio(
                                    AttachmentContent.Binary.Base64(base64),
                                    format = "mp3"
                                )
                            )
                        }
                    }
                    withRetry {
                        assertExceptionMessageContains(
                            assertFails(prompt, model),
                            "$EXPECTED_ERROR audio",
                            "Unsupported attachment type"
                        )
                    }
                }

                LLMCapability.Document -> {
                    val file = createTextFileForScenario(
                        MediaTestScenarios.TextTestScenario.BASIC_TEXT,
                        testResourcesDir
                    )
                    val prompt = prompt("cap-document-negative") {
                        system("You are a helpful assistant that can read attached documents.")
                        user {
                            markdown { +"Summarize the attached text file in 5-10 words." }
                            textFile(KtPath(file.pathString), "text/plain")
                        }
                    }
                    withRetry {
                        assertExceptionMessageContains(
                            assertFails(prompt, model),
                            "$EXPECTED_ERROR files",
                            "Unsupported attachment type",
                            "$EXPECTED_ERROR document"
                        )
                    }
                }

                LLMCapability.Moderation -> {
                    val prompt = prompt("cap-moderation-negative") {
                        user("This is a harmless request about the weather.")
                    }
                    withRetry {
                        assertExceptionMessageContains(
                            assertFailsWith<Exception> {
                                executor.moderate(prompt, model)
                            },
                            "$EXPECTED_ERROR moderation",
                            "Moderation is not supported by"
                        )
                    }
                }

                LLMCapability.MultipleChoices -> {
                    val prompt = prompt(
                        "cap-multiple-choices-negative",
                        params = LLMParams(numberOfChoices = 3)
                    ) {
                        system("You are a helpful assistant.")
                        user("Provide multiple distinct options for a team name.")
                    }
                    withRetry {
                        assertExceptionMessageContains(
                            assertFailsWith<Throwable> {
                                executor.executeMultipleChoices(prompt, model, emptyList())
                            },
                            "$EXPECTED_ERROR multiple choices",
                            "$EXPECTED_ERROR ${LLMCapability.MultipleChoices.id}",
                            "Not implemented for this client"
                        )
                    }
                }

                LLMCapability.Vision.Video -> {
                    val videoPath = createVideoFileForScenario(testResourcesDir)
                    val base64 = Base64.encode(videoPath.readBytes())
                    val prompt = prompt("cap-vision-video-positive") {
                        system("You are a helpful assistant that can analyze short videos.")
                        user {
                            markdown { +"Describe in 5-10 words what you can infer from the attached video." }
                            video(
                                ContentPart.Video(
                                    content = AttachmentContent.Binary.Base64(base64),
                                    format = "mp4",
                                    mimeType = "video/mp4",
                                )
                            )
                        }
                    }
                    withRetry {
                        assertExceptionMessageContains(
                            assertFails(prompt, model),
                            "$EXPECTED_ERROR video",
                            "Unsupported attachment type"
                        )
                    }
                }

                LLMCapability.Embed -> {
                    withRetry {
                        assertExceptionMessageContains(
                            assertFailsWith<Exception> {
                                openAIClient.embed("Provide an embedding for this sentence.", model)
                            },
                            EXPECTED_ERROR,
                            "embedding",
                            "does not have the Embed capability",
                            "Unsupported"
                        )
                    }
                }

                LLMCapability.Schema.JSON.Basic -> {
                    val schema = singlePropertyObjectSchema(model.provider, "x", "integer")
                    val prompt = prompt(
                        "cap-json-basic-negative",
                        params = LLMParams(schema = LLMParams.Schema.JSON.Basic(name = "XSchema", schema = schema))
                    ) {
                        system("Reply strictly as JSON. Only include the JSON object.")
                        user("Return an integer x field with any small integer.")
                    }
                    withRetry {
                        assertExceptionMessageContains(
                            assertFails(prompt, model),
                            "$EXPECTED_ERROR structured output schema",
                            EXPECTED_ERROR,
                            "structured output",
                            "Anthropic does not currently support native structured output"
                        )
                    }
                }

                LLMCapability.Schema.JSON.Standard -> {
                    val schema = singlePropertyObjectSchema(model.provider, "y", "string")
                    val prompt = prompt(
                        "cap-json-standard-negative",
                        params = LLMParams(schema = LLMParams.Schema.JSON.Standard(name = "YSchema", schema = schema))
                    ) {
                        system("Reply strictly as JSON. Only include the JSON object.")
                        user("Return a string y field.")
                    }
                    withRetry {
                        assertExceptionMessageContains(
                            assertFails(prompt, model),
                            "$EXPECTED_ERROR structured output schema",
                            EXPECTED_ERROR,
                            "structured output",
                            "Anthropic does not currently support native structured output"
                        )
                    }
                }

                LLMCapability.OpenAIEndpoint.Completions -> {
                    assumeTrue(model.provider is LLMProvider.OpenAI)
                    val prompt = prompt("cap-openai-endpoint-completions-negative", params = OpenAIChatParams()) {
                        system("You are a helpful assistant.")
                        user("Say hello in one short sentence.")
                    }
                    withRetry {
                        assertExceptionMessageContains(
                            assertFails(prompt, model),
                            "$EXPECTED_ERROR ${LLMCapability.OpenAIEndpoint.Completions.id}",
                        )
                    }
                }

                LLMCapability.OpenAIEndpoint.Responses -> {
                    assumeTrue(model.provider is LLMProvider.OpenAI)
                    val prompt = prompt("cap-openai-endpoint-responses-negative", params = OpenAIResponsesParams()) {
                        system("You are a helpful assistant.")
                        user("Say hello in one short sentence.")
                    }
                    withRetry {
                        assertExceptionMessageContains(
                            assertFails(prompt, model),
                            "$EXPECTED_ERROR ${LLMCapability.OpenAIEndpoint.Responses.id}",
                        )
                    }
                }

                else -> {
                    assumeTrue(false, "Skipping hard-to-verify capability verification for $capability on ${model.id}")
                }
            }
        }

    private suspend fun assertFails(prompt: Prompt, model: LLModel): Exception = assertFailsWith<Exception> {
        executor.execute(prompt, model)
    }

    private suspend fun checkAssistantResponse(prompt: Prompt, model: LLModel) {
        executor
            .execute(prompt, model)
            .filterIsInstance<Message.Assistant>()
            .joinToString("\n") { it.content }
            .shouldNotBeBlank()
    }
}
