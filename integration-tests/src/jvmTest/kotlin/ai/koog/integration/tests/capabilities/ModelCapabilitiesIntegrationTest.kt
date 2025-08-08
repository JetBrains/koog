package ai.koog.integration.tests.capabilities

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.integration.tests.utils.MediaTestScenarios
import ai.koog.integration.tests.utils.MediaTestUtils
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.RetryUtils.withRetry
import ai.koog.integration.tests.utils.TestUtils
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.all.DefaultMultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutorExt.execute
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

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ModelCapabilitiesIntegrationTest {
    private lateinit var openAIClient: OpenAILLMClient
    private lateinit var anthropicClient: AnthropicLLMClient
    private lateinit var googleClient: GoogleLLMClient
    private lateinit var executor: DefaultMultiLLMPromptExecutor
    private lateinit var testResourcesDir: Path

    @BeforeAll
    fun setup() {
        val openAIKey = TestUtils.readTestOpenAIKeyFromEnv()
        val anthropicKey = TestUtils.readTestAnthropicKeyFromEnv()
        val googleKey = TestUtils.readTestGoogleAIKeyFromEnv()

        openAIClient = OpenAILLMClient(openAIKey)
        anthropicClient = AnthropicLLMClient(anthropicKey)
        googleClient = GoogleLLMClient(googleKey)
        executor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient, googleClient)

        val resourceUrl = this::class.java.getResource("/media")
        require(resourceUrl != null) { "Test resources directory '/media' not found on classpath" }
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
            LLMCapability.Speculation,
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.MultipleChoices,
            LLMCapability.Vision.Image,
            LLMCapability.Vision.Video,
            LLMCapability.Audio,
            LLMCapability.Document,
            LLMCapability.Embed,
            LLMCapability.Completion,
            LLMCapability.PromptCaching,
            LLMCapability.Moderation,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard
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

        @JvmStatic
        fun toolDescriptors(): Stream<Arguments> = Stream.of(
            Arguments.of(
                ToolDescriptor(
                    name = "calculator",
                    description = "Perform basic arithmetic",
                    requiredParameters = listOf(
                        ToolParameterDescriptor("operation", "Operation", ToolParameterType.Enum(arrayOf("ADD"))),
                        ToolParameterDescriptor("a", "First number", ToolParameterType.Integer),
                        ToolParameterDescriptor("b", "Second number", ToolParameterType.Integer),
                    )
                )
            )
        )
    }

    private fun assumeProvider(model: LLModel) {
        Models.assumeAvailable(model.provider)
    }

    private fun clientFor(model: LLModel) = when (model.provider) {
        is LLMProvider.OpenAI -> openAIClient
        is LLMProvider.Anthropic -> anthropicClient
        is LLMProvider.Google -> googleClient
        else -> openAIClient
    }

    @ParameterizedTest
    @MethodSource("positiveModelCapabilityCombinations")
    @OptIn(ExperimentalEncodingApi::class)
    fun integration_positiveCapabilityShouldWork(model: LLModel, capability: LLMCapability) =
        runTest(timeout = 300.seconds) {
            assumeProvider(model)

            when (capability) {
                LLMCapability.Completion -> {
                    val prompt = prompt("cap-completion-positive") {
                        system("You are a helpful assistant.")
                        user("Say hello in one short sentence.")
                    }
                    withRetry(times = 2, testName = "positive_completion[${'$'}{model.id}]") {
                        val resp = executor.execute(prompt, model)
                        assertTrue(resp.content.isNotBlank())
                        assertTrue(resp is Message.Assistant)
                    }
                }

                LLMCapability.Tools -> {
                    val tools = toolDescriptors().findFirst().get().get()[0] as ToolDescriptor
                    val prompt = prompt("cap-tools-positive", params = LLMParams(toolChoice = ToolChoice.Required)) {
                        system("You are a helpful assistant with a calculator tool. Always use the tool.")
                        user("Compute 2 + 3.")
                    }
                    withRetry(times = 2, testName = "positive_tools[${'$'}{model.id}]") {
                        val client = clientFor(model)
                        val responses = client.execute(prompt, model, listOf(tools))
                        assertTrue(responses.isNotEmpty())
                        assertTrue(responses.any { it is Message.Tool.Call } || responses.any { it is Message.Assistant })
                    }
                }

                LLMCapability.ToolChoice -> {
                    val tools = toolDescriptors().findFirst().get().get()[0] as ToolDescriptor
                    val prompt =
                        prompt("cap-toolchoice-positive", params = LLMParams(toolChoice = ToolChoice.Required)) {
                            system("You are a helpful assistant with tools. Always choose to use a tool when required.")
                            user("Compute 2 + 3.")
                        }
                    withRetry(times = 2, testName = "positive_toolchoice[${'$'}{model.id}]") {
                        val client = clientFor(model)
                        val responses = client.execute(prompt, model, listOf(tools))
                        assertTrue(responses.isNotEmpty())
                        assertTrue(responses.any { it is Message.Tool.Call } || responses.any { it is Message.Assistant })
                    }
                }

                LLMCapability.Vision.Image -> {
                    val imagePath = MediaTestUtils.getImageFileForScenario(
                        MediaTestScenarios.ImageTestScenario.BASIC_PNG,
                        testResourcesDir
                    )
                    val base64 = Base64.encode(imagePath.readBytes())
                    val prompt = prompt("cap-vision-image-positive") {
                        system("You are a helpful assistant that can describe images.")
                        user {
                            markdown { +"Describe the image succinctly." }
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
                    withRetry(times = 2, testName = "positive_vision_image[${'$'}{model.id}]") {
                        val resp = executor.execute(prompt, model)
                        assertTrue(resp.content.isNotBlank())
                        assertTrue(resp is Message.Assistant)
                    }
                }

                LLMCapability.Audio -> {
                    val audioPath = MediaTestUtils.createAudioFileForScenario(
                        MediaTestScenarios.AudioTestScenario.BASIC_WAV,
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
                                        format = "wav"
                                    )
                                )
                            }
                        }
                    }
                    withRetry(times = 2, testName = "positive_audio[${'$'}{model.id}]") {
                        val resp = executor.execute(prompt, model)
                        assertTrue(resp.content.isNotBlank())
                    }
                }

                LLMCapability.Document -> {
                    // Skip Google and OpenAI models due to known bug with PlainText attachments (documented in BUG.md)
                    if (model.provider is LLMProvider.Google || model.provider is LLMProvider.OpenAI) {
                        return@runTest
                    }

                    val file = MediaTestUtils.createTextFileForScenario(
                        MediaTestScenarios.TextTestScenario.BASIC_TEXT,
                        testResourcesDir
                    )
                    val prompt = prompt("cap-document-positive") {
                        system("You are a helpful assistant that can read attached documents.")
                        user {
                            markdown { +"Summarize the attached text file in one sentence." }
                            attachments { textFile(KtPath(file.pathString), "text/plain") }
                        }
                    }
                    withRetry(times = 2, testName = "positive_document[${'$'}{model.id}]") {
                        val resp = executor.execute(prompt, model)
                        assertTrue(resp.content.isNotBlank())
                    }
                }

                LLMCapability.Moderation -> {
                    val prompt = prompt("cap-moderation-positive") {
                        user("This is a harmless request about the weather.")
                    }
                    withRetry(times = 2, testName = "positive_moderation[${'$'}{model.id}]") {
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
                        user("Name a popular programming language.")
                    }
                    withRetry(times = 2, testName = "positive_multiple_choices[${'$'}{model.id}]") {
                        val client = clientFor(model)
                        val choices = client.executeMultipleChoices(prompt, model, emptyList())
                        assertTrue(choices.size >= 2, "Expected at least 2 choices, got ${'$'}{choices.size}")
                        choices.forEach { choice ->
                            assertTrue(choice.isNotEmpty(), "Each choice should contain at least one response")
                            val assistant = choice.firstOrNull { it is Message.Assistant } as? Message.Assistant
                            assertNotNull(assistant, "Each choice should contain an assistant message")
                            assertTrue(assistant.content.isNotBlank(), "Assistant content should not be blank")
                        }
                    }
                }

                else -> {
                    // skip other hard-to-verify capabilities
                }
            }
        }

    @ParameterizedTest
    @MethodSource("negativeModelCapabilityCombinations")
    @OptIn(ExperimentalEncodingApi::class)
    fun integration_negativeCapabilityShouldFail(model: LLModel, capability: LLMCapability) =
        runTest(timeout = 300.seconds) {
            assumeProvider(model)

            when (capability) {
                LLMCapability.Completion -> {
                    val prompt = prompt("cap-completion-negative") {
                        system("You are a helpful assistant.")
                        user("This should fail because the model is not a chat completion model.")
                    }
                    withRetry(times = 2, testName = "negative_completion[${model.id}]") {
                        val ex = assertFailsWith<Exception> {
                            executor.execute(prompt, model)
                        }
                        assertEquals(
                            true,
                            ex.message?.contains("does not support chat completions", ignoreCase = true),
                            "Exception message doesn't contain expected error: ${ex.message}"
                        )
                    }
                }

                LLMCapability.Tools -> {
                    val tools = toolDescriptors().findFirst().get().get()[0] as ToolDescriptor
                    val prompt = prompt("cap-tools-negative", params = LLMParams(toolChoice = ToolChoice.Required)) {
                        system("You are a helpful assistant with tools.")
                        user("Try to use a tool.")
                    }
                    withRetry(times = 2, testName = "negative_tools[${model.id}]") {
                        val ex = assertFailsWith<Exception> {
                            clientFor(model).execute(prompt, model, listOf(tools))
                        }
                        assertEquals(
                            true,
                            ex.message?.contains("does not support tools", ignoreCase = true),
                            "Exception message doesn't contain expected error: ${ex.message}"
                        )
                    }
                }

                LLMCapability.ToolChoice -> {
                    val tools = toolDescriptors().findFirst().get().get()[0] as ToolDescriptor
                    val prompt =
                        prompt("cap-toolchoice-negative", params = LLMParams(toolChoice = ToolChoice.Required)) {
                            system("You are a helpful assistant with tools.")
                            user("Try to use a tool.")
                        }
                    withRetry(times = 2, testName = "negative_toolchoice[${model.id}]") {
                        val ex = assertFailsWith<Exception> {
                            clientFor(model).execute(prompt, model, listOf(tools))
                        }
                        val msg = ex.message ?: ""
                        assertEquals(
                            true,
                            msg.contains("does not support tool choice", ignoreCase = true) ||
                                msg.contains("does not support tools", ignoreCase = true) ||
                                msg.contains("toolchoice", ignoreCase = true) ||
                                msg.contains("tool choice is not supported", ignoreCase = true),
                            "Exception message doesn't contain expected error: ${ex.message}"
                        )
                    }
                }

                LLMCapability.Vision.Image -> {
                    val imagePath = MediaTestUtils.getImageFileForScenario(
                        MediaTestScenarios.ImageTestScenario.BASIC_PNG,
                        testResourcesDir
                    )
                    val base64 = Base64.encode(imagePath.readBytes())
                    val prompt = prompt("cap-vision-image-negative") {
                        system("You are a helpful assistant.")
                        user {
                            markdown { +"This should fail due to unsupported image capability." }
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
                    withRetry(times = 2, testName = "negative_vision_image[${model.id}]") {
                        val ex = assertFailsWith<Exception> {
                            executor.execute(prompt, model)
                        }
                        assertEquals(
                            true,
                            ex.message?.let {
                                it.contains(
                                    "does not support images",
                                    ignoreCase = true
                                ) ||
                                    it.contains("Unsupported attachment type", ignoreCase = true)
                            },
                            "Exception message doesn't contain expected error: ${ex.message}"
                        )
                    }
                }

                LLMCapability.Audio -> {
                    val audioPath = MediaTestUtils.createAudioFileForScenario(
                        MediaTestScenarios.AudioTestScenario.BASIC_WAV,
                        testResourcesDir
                    )
                    val base64 = Base64.encode(audioPath.readBytes())
                    val prompt = prompt("cap-audio-negative") {
                        system("You are a helpful assistant.")
                        user {
                            markdown { +"This should fail because audio is unsupported." }
                            attachments {
                                audio(
                                    Attachment.Audio(
                                        AttachmentContent.Binary.Base64(base64),
                                        format = "wav"
                                    )
                                )
                            }
                        }
                    }
                    withRetry(times = 2, testName = "negative_audio[${model.id}]") {
                        val ex = assertFailsWith<Exception> {
                            executor.execute(prompt, model)
                        }
                        assertEquals(
                            true,
                            ex.message?.let {
                                it.contains(
                                    "does not support audio",
                                    ignoreCase = true
                                ) ||
                                    it.contains("Unsupported attachment type", ignoreCase = true)
                            },
                            "Exception message doesn't contain expected error: ${ex.message}"
                        )
                    }
                }

                LLMCapability.Document -> {
                    val file = MediaTestUtils.createTextFileForScenario(
                        MediaTestScenarios.TextTestScenario.BASIC_TEXT,
                        testResourcesDir
                    )
                    val prompt = prompt("cap-document-negative") {
                        system("You are a helpful assistant.")
                        user {
                            markdown { +"This should fail due to file attachment on unsupported model." }
                            attachments { textFile(KtPath(file.pathString), "text/plain") }
                        }
                    }
                    withRetry(times = 2, testName = "negative_document[${model.id}]") {
                        val ex = assertFailsWith<Exception> {
                            executor.execute(prompt, model)
                        }
                        assertEquals(
                            true,
                            ex.message?.let {
                                it.contains(
                                    "does not support files",
                                    ignoreCase = true
                                ) ||
                                    it.contains("Unsupported attachment type", ignoreCase = true) ||
                                    it.contains("does not support documents", ignoreCase = true)
                            },
                            "Exception message doesn't contain expected error: ${ex.message}"
                        )
                    }
                }

                LLMCapability.Moderation -> {
                    val prompt = prompt("cap-moderation-negative") {
                        user("Is this content allowed?")
                    }
                    withRetry(times = 2, testName = "negative_moderation[${model.id}]") {
                        val ex = assertFailsWith<Exception> {
                            executor.moderate(prompt, model)
                        }
                        val msg = ex.message ?: ""
                        assertEquals(
                            true,
                            msg.contains("does not support moderation", ignoreCase = true) ||
                                msg.contains("Moderation is not supported by", ignoreCase = true),
                            "Exception message doesn't contain expected error: ${ex.message}"
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
                    withRetry(times = 2, testName = "negative_multiple_choices[${model.id}]") {
                        val ex = assertFailsWith<Throwable> {
                            val client = clientFor(model)
                            client.executeMultipleChoices(prompt, model, emptyList())
                        }
                        val msg = ex.message ?: ""
                        assertEquals(
                            true,
                            msg.contains("does not support multiple choices", ignoreCase = true) ||
                                msg.contains("Not implemented for this client", ignoreCase = true),
                            "Exception message doesn't contain expected error: ${ex.message}"
                        )
                    }
                }

                else -> {
                    // skip other hard-to-verify capabilities
                }
            }
        }
}
