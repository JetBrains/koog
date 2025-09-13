package ai.koog.integration.tests.capabilities

import ai.koog.integration.tests.utils.MediaTestScenarios
import ai.koog.integration.tests.utils.MediaTestUtils.createAudioFileForScenario
import ai.koog.integration.tests.utils.MediaTestUtils.createTextFileForScenario
import ai.koog.integration.tests.utils.MediaTestUtils.createVideoFileForScenario
import ai.koog.integration.tests.utils.MediaTestUtils.getImageFileForScenario
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.RetryUtils.withRetry
import ai.koog.integration.tests.utils.TestUtils.CalculatorTool.calculatorToolDescriptor
import ai.koog.integration.tests.utils.TestUtils.assertExceptionMessageContains
import ai.koog.integration.tests.utils.TestUtils.isValidJson
import ai.koog.integration.tests.utils.TestUtils.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestGoogleAIKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestOpenAIKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.singlePropertyObjectSchema
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIChatParams
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIResponsesParams
import ai.koog.prompt.executor.llms.all.DefaultMultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Attachment
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.params.LLMParams.ToolChoice
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.io.path.pathString
import kotlin.io.path.readBytes
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.io.files.Path as KtPath

private const val EXPECTED_ERROR = "does not support"

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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
                    val tools = calculatorToolDescriptor
                    val prompt = prompt("cap-tools-positive", params = LLMParams(toolChoice = ToolChoice.Required)) {
                        system("You are a helpful assistant with a calculator tool. Always use the tool.")
                        user("Compute 2 + 3.")
                    }
                    withRetry {
                        val responses = executor.execute(prompt, model, listOf(tools))
                        assertTrue(responses.isNotEmpty())
                        assertTrue(responses.any { it is Message.Tool.Call })
                    }
                }

                LLMCapability.Vision.Image -> {
                    val imagePath = getImageFileForScenario(
                        MediaTestScenarios.ImageTestScenario.BASIC_PNG,
                        testResourcesDir
                    )
                    val base64 = Base64.encode(imagePath.readBytes())
                    val prompt = prompt("cap-vision-image-positive") {
                        system("You are a helpful assistant that can describe images.")
                        user {
                            markdown { +"Describe the image in 5-10 words." }
                            attachments {
                                image(
                                    Attachment.Image(
                                        content = AttachmentContent.Binary.Base64(base64),
                                        format = "png",
                                        mimeType = "image/png"
                                    )
                                )
                            }
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
                            attachments {
                                audio(
                                    Attachment.Audio(
                                        AttachmentContent.Binary.Base64(base64),
                                        format = "mp3"
                                    )
                                )
                            }
                        }
                    }
                    withRetry {
                        checkAssistantResponse(prompt, model)
                    }
                }

                LLMCapability.Document -> {
                    val file = createTextFileForScenario(
                        MediaTestScenarios.TextTestScenario.BASIC_TEXT,
                        testResourcesDir
                    )
                    val prompt = prompt("cap-document-positive") {
                        system("You are a helpful assistant that can read attached documents.")
                        user {
                            markdown { +"Summarize the attached text file in 5-10 words." }
                            attachments { textFile(KtPath(file.pathString), "text/plain") }
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
                        val result = executor.moderate(prompt, model)
                        assertNotNull(result)
                        assertFalse(result.isHarmful)
                    }
                }

                LLMCapability.MultipleChoices -> {
                    val prompt = prompt(
                        "cap-multiple-choices-positive",
                        params = LLMParams(numberOfChoices = 2)
                    ) {
                        system("You are a helpful assistant. Provide concise answers.")
                        user("Provide multiple distinct options for a team name.")
                    }
                    withRetry {
                        val choices = executor.executeMultipleChoices(prompt, model, emptyList())
                        assertEquals(2, choices.size, "Expected at least 2 choices, got ${'$'}{choices.size}")
                        choices.forEach { choice ->
                            assertTrue(choice.isNotEmpty(), "Each choice should contain at least one response")
                            val assistant = choice.firstOrNull { it is Message.Assistant }
                            assertNotNull(assistant, "Each choice should contain an assistant message")
                            assertTrue(assistant.content.isNotBlank(), "Assistant content should not be blank")
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
                            attachments {
                                video(
                                    Attachment.Video(
                                        content = AttachmentContent.Binary.Base64(base64),
                                        format = "mp4",
                                        mimeType = "video/mp4",
                                    )
                                )
                            }
                        }
                    }
                    withRetry {
                        checkAssistantResponse(prompt, model)
                    }
                }

                LLMCapability.Embed -> {
                    withRetry {
                        val vector = openAIClient.embed("Provide an embedding for this sentence.", model)
                        assertTrue(vector.isNotEmpty(), "Embedding vector should not be empty")
                        assertTrue(vector.any { it != 0.0 }, "Embedding vector should contain non-zero values")
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
                        val responses = executor.execute(prompt, model)
                        val text = responses.filterIsInstance<Message.Assistant>().joinToString("\n") { it.content }
                        assertTrue(text.isNotBlank())
                        assertTrue(isValidJson(text), "Response should be valid JSON")
                        assertTrue(text.contains("\"x\""), "Response should contain key \"x\"")
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
                        val responses = executor.execute(prompt, model)
                        val text = responses.filterIsInstance<Message.Assistant>().joinToString("\n") { it.content }
                        assertTrue(text.isNotBlank())
                        assertTrue(isValidJson(text), "Response should be valid JSON")
                        assertTrue(text.contains("\"y\""), "Response should contain key \"y\"")
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
                        val ex = assertFails(prompt, model)
                        assertExceptionMessageContains(
                            ex,
                            "EXPECTED_ERROR chat completions",
                            "not a chat completion"
                        )
                    }
                }

                LLMCapability.Tools, LLMCapability.ToolChoice -> {
                    val tools = calculatorToolDescriptor
                    val prompt = prompt("cap-tools-negative", params = LLMParams(toolChoice = ToolChoice.Required)) {
                        system("You are a helpful assistant with a calculator tool. Always use the tool.")
                        user("Compute 2 + 3.")
                    }
                    withRetry {
                        val ex = assertFailsWith<Exception> {
                            executor.execute(prompt, model, listOf(tools))
                        }
                        assertExceptionMessageContains(
                            ex,
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
                            attachments {
                                image(
                                    Attachment.Image(
                                        content = AttachmentContent.Binary.Base64(base64),
                                        format = "png",
                                        mimeType = "image/png"
                                    )
                                )
                            }
                        }
                    }
                    withRetry {
                        val ex = assertFails(prompt, model)
                        assertExceptionMessageContains(
                            ex,
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
                            attachments {
                                audio(
                                    Attachment.Audio(
                                        AttachmentContent.Binary.Base64(base64),
                                        format = "mp3"
                                    )
                                )
                            }
                        }
                    }
                    withRetry {
                        val ex = assertFails(prompt, model)
                        assertExceptionMessageContains(
                            ex,
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
                            attachments { textFile(KtPath(file.pathString), "text/plain") }
                        }
                    }
                    withRetry {
                        val ex = assertFails(prompt, model)
                        assertExceptionMessageContains(
                            ex,
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
                        val ex = assertFailsWith<Exception> {
                            executor.moderate(prompt, model)
                        }
                        assertExceptionMessageContains(
                            ex,
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
                        val ex = assertFailsWith<Throwable> {
                            executor.executeMultipleChoices(prompt, model, emptyList())
                        }
                        assertExceptionMessageContains(
                            ex,
                            "$EXPECTED_ERROR multiple choices",
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
                            attachments {
                                video(
                                    Attachment.Video(
                                        content = AttachmentContent.Binary.Base64(base64),
                                        format = "mp4",
                                        mimeType = "video/mp4",
                                    )
                                )
                            }
                        }
                    }
                    withRetry {
                        val ex = assertFails(prompt, model)
                        assertExceptionMessageContains(
                            ex,
                            "$EXPECTED_ERROR video",
                            "Unsupported attachment type"
                        )
                    }
                }

                LLMCapability.Embed -> {
                    withRetry {
                        val ex = assertFailsWith<Exception> {
                            openAIClient.embed("Provide an embedding for this sentence.", model)
                        }
                        assertExceptionMessageContains(
                            ex,
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
                        val ex = assertFails(prompt, model)
                        assertExceptionMessageContains(
                            ex,
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
                        val ex = assertFails(prompt, model)
                        assertExceptionMessageContains(
                            ex,
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
                        val ex = assertFails(prompt, model)
                        assertExceptionMessageContains(
                            ex,
                            EXPECTED_ERROR,
                            "Unsupported OpenAI API endpoint",
                            "not a chat completion",
                            "Unsupported"
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
                        val ex = assertFails(prompt, model)
                        assertExceptionMessageContains(
                            ex,
                            EXPECTED_ERROR,
                            "Unsupported OpenAI API endpoint",
                            "Unsupported"
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
        val responses = executor.execute(prompt, model)
        val text = responses.filterIsInstance<Message.Assistant>().joinToString("\n") { it.content }
        assertTrue(text.isNotBlank())
    }
}
