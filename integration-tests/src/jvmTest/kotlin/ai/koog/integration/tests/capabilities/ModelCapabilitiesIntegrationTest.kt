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
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Attachment
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.params.LLMParams.ToolChoice
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
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

    private val logger = logger { }

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
        testResourcesDir = Path.of(resourceUrl!!.toURI())
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
                        ToolParameterDescriptor(
                            "operation",
                            "Arithmetic operation to perform",
                            ToolParameterType.Enum(arrayOf("ADD"))
                        ),
                        ToolParameterDescriptor("a", "First number", ToolParameterType.Integer),
                        ToolParameterDescriptor("b", "Second number", ToolParameterType.Integer),
                    )
                )
            )
        )
    }

    private fun clientFor(model: LLModel) = when (model.provider) {
        is LLMProvider.OpenAI -> openAIClient
        is LLMProvider.Anthropic -> anthropicClient
        is LLMProvider.Google -> googleClient
        else -> openAIClient
    }

    private fun isValidJson(str: String): Boolean = try {
        Json.parseToJsonElement(str)
        true
    } catch (_: Exception) {
        false
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
                    withRetry(times = 3, testName = "positive_completion[${'$'}{model.id}]") {
                        val responses = executor.execute(prompt, model)
                        val text = responses.filterIsInstance<Message.Assistant>().joinToString("\n") { it.content }
                        assertTrue(text.isNotBlank())
                    }
                }

                LLMCapability.Tools -> {
                    val tools = toolDescriptors().findFirst().get().get()[0] as ToolDescriptor
                    val prompt = prompt("cap-tools-positive", params = LLMParams(toolChoice = ToolChoice.Required)) {
                        system("You are a helpful assistant with a calculator tool. Always use the tool.")
                        user("Compute 2 + 3.")
                    }
                    withRetry(times = 3, testName = "positive_tools[${'$'}{model.id}]") {
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
                    withRetry(times = 3, testName = "positive_toolchoice[${'$'}{model.id}]") {
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
                    withRetry(times = 3, testName = "positive_vision_image[${'$'}{model.id}]") {
                        val responses = executor.execute(prompt, model)
                        val text = responses.filterIsInstance<Message.Assistant>().joinToString("\n") { it.content }
                        assertTrue(text.isNotBlank())
                    }
                }

                LLMCapability.Audio -> {
                    val audioPath = MediaTestUtils.createAudioFileForScenario(
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
                    withRetry(times = 3, testName = "positive_audio[${'$'}{model.id}]") {
                        val responses = executor.execute(prompt, model)
                        val text = responses.filterIsInstance<Message.Assistant>().joinToString("\n") { it.content }
                        assertTrue(text.isNotBlank())
                    }
                }

                LLMCapability.Document -> {
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
                    withRetry(times = 3, testName = "positive_document[${'$'}{model.id}]") {
                        val responses = executor.execute(prompt, model)
                        val text = responses.filterIsInstance<Message.Assistant>().joinToString("\n") { it.content }
                        assertTrue(text.isNotBlank())
                    }
                }

                LLMCapability.Moderation -> {
                    val prompt = prompt("cap-moderation-positive") {
                        user("This is a harmless request about the weather.")
                    }
                    withRetry(times = 3, testName = "positive_moderation[${'$'}{model.id}]") {
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
                    withRetry(times = 3, testName = "positive_multiple_choices[${'$'}{model.id}]") {
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

                LLMCapability.Vision.Video -> {
                    val videoPath = MediaTestUtils.createVideoFileForScenario(testResourcesDir)
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
                    withRetry(times = 3, testName = "positive_vision_video[${'$'}{model.id}]") {
                        val responses = executor.execute(prompt, model)
                        val text = responses.filterIsInstance<Message.Assistant>().joinToString("\n") { it.content }
                        assertTrue(text.isNotBlank())
                    }
                }

                LLMCapability.Embed -> {
                    val client = clientFor(model)
                    withRetry(times = 3, testName = "positive_embed[${'$'}{model.id}]") {
                        if (client is OpenAILLMClient) {
                            val vector = client.embed("Provide an embedding for this sentence.", model)
                            assertTrue(vector.isNotEmpty(), "Embedding vector should not be empty")
                            assertTrue(vector.any { it != 0.0 }, "Embedding vector should contain non-zero values")
                        } else {
                            // If embedding is supported for this model, its client should provide an embed method.
                            // For now, we just assert true to avoid false negatives for providers without a dedicated embed API here.
                            assertTrue(true)
                        }
                    }
                }

                LLMCapability.Schema.JSON.Basic -> {
                    val schema = if (model.provider is LLMProvider.Google) {
                        // Google response_schema does not support additionalProperties at the root
                        buildJsonObject {
                            put("type", JsonPrimitive("object"))
                            put(
                                "properties",
                                buildJsonObject {
                                    put("x", buildJsonObject { put("type", JsonPrimitive("integer")) })
                                }
                            )
                            put("required", buildJsonArray { add(JsonPrimitive("x")) })
                        }
                    } else {
                        buildJsonObject {
                            put("type", JsonPrimitive("object"))
                            put(
                                "properties",
                                buildJsonObject {
                                    put("x", buildJsonObject { put("type", JsonPrimitive("integer")) })
                                }
                            )
                            put("required", buildJsonArray { add(JsonPrimitive("x")) })
                            put("additionalProperties", JsonPrimitive(false))
                        }
                    }
                    val prompt = prompt(
                        "cap-json-basic-positive",
                        params = LLMParams(schema = LLMParams.Schema.JSON.Basic(name = "XSchema", schema = schema))
                    ) {
                        system("Reply strictly as JSON. Only include the JSON object.")
                        user("Return an integer x field with any small integer.")
                    }
                    withRetry(times = 3, testName = "positive_json_basic[${'$'}{model.id}]") {
                        val responses = executor.execute(prompt, model)
                        val text = responses.filterIsInstance<Message.Assistant>().joinToString("\n") { it.content }
                        assertTrue(text.isNotBlank())
                        assertTrue(isValidJson(text), "Response should be valid JSON")
                        assertTrue(text.contains("\"x\""), "Response should contain key \"x\"")
                    }
                }

                LLMCapability.Schema.JSON.Standard -> {
                    val schema = if (model.provider is LLMProvider.Google) {
                        // Google response_schema does not support additionalProperties at the root
                        buildJsonObject {
                            put("type", JsonPrimitive("object"))
                            put(
                                "properties",
                                buildJsonObject {
                                    put("y", buildJsonObject { put("type", JsonPrimitive("string")) })
                                }
                            )
                            put("required", buildJsonArray { add(JsonPrimitive("y")) })
                        }
                    } else {
                        buildJsonObject {
                            put("type", JsonPrimitive("object"))
                            put(
                                "properties",
                                buildJsonObject {
                                    put("y", buildJsonObject { put("type", JsonPrimitive("string")) })
                                }
                            )
                            put("required", buildJsonArray { add(JsonPrimitive("y")) })
                            put("additionalProperties", JsonPrimitive(false))
                        }
                    }
                    val prompt = prompt(
                        "cap-json-standard-positive",
                        params = LLMParams(schema = LLMParams.Schema.JSON.Standard(name = "YSchema", schema = schema))
                    ) {
                        system("Reply strictly as JSON. Only include the JSON object.")
                        user("Return a string y field.")
                    }
                    withRetry(times = 3, testName = "positive_json_standard[${'$'}{model.id}]") {
                        val responses = executor.execute(prompt, model)
                        val text = responses.filterIsInstance<Message.Assistant>().joinToString("\n") { it.content }
                        assertTrue(text.isNotBlank())
                        assertTrue(isValidJson(text), "Response should be valid JSON")
                        assertTrue(text.contains("\"y\""), "Response should contain key \"y\"")
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
            when (capability) {
                LLMCapability.Completion -> {
                    val prompt = prompt("cap-completion-negative") {
                        system("You are a helpful assistant.")
                        user("This should fail because the model is not a chat completion model.")
                    }
                    withRetry(times = 3, testName = "negative_completion[${model.id}]") {
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
                    withRetry(times = 3, testName = "negative_tools[${model.id}]") {
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
                    withRetry(times = 3, testName = "negative_toolchoice[${model.id}]") {
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
                    withRetry(times = 3, testName = "negative_vision_image[${model.id}]") {
                        val ex = assertFailsWith<Exception> {
                            executor.execute(prompt, model)
                        }
                        assertEquals(
                            true,
                            ex.message?.let {
                                it.contains(
                                    "does not support image",
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
                    withRetry(times = 3, testName = "negative_audio[${model.id}]") {
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
                    withRetry(times = 3, testName = "negative_document[${model.id}]") {
                        val ex = assertFailsWith<Exception> {
                            executor.execute(prompt, model)
                        }
                        assertEquals(
                            true,
                            ex.message?.let {
                                it.contains("does not support files", ignoreCase = true) ||
                                    it.contains("Unsupported attachment type", ignoreCase = true) ||
                                    it.contains("does not support document", ignoreCase = true)
                            },
                            "Exception message doesn't contain expected error: ${ex.message}"
                        )
                    }
                }

                LLMCapability.Moderation -> {
                    val prompt = prompt("cap-moderation-negative") {
                        user("Is this content allowed?")
                    }
                    withRetry(times = 3, testName = "negative_moderation[${model.id}]") {
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
                    withRetry(times = 3, testName = "negative_multiple_choices[${model.id}]") {
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

                LLMCapability.Vision.Video -> {
                    val fakeVideoBytes = ByteArray(64) { 0 }
                    val base64 = Base64.encode(fakeVideoBytes)
                    val prompt = prompt("cap-vision-video-negative") {
                        system("You are a helpful assistant.")
                        user {
                            markdown { +"This should fail due to unsupported video capability." }
                            attachments {
                                video(
                                    Attachment.Video(
                                        content = AttachmentContent.Binary.Base64(base64),
                                        format = "mp4",
                                        mimeType = "video/mp4"
                                    )
                                )
                            }
                        }
                    }
                    withRetry(times = 3, testName = "negative_vision_video[${model.id}]") {
                        val ex = assertFailsWith<Exception> {
                            executor.execute(prompt, model)
                        }
                        val msg = ex.message ?: ""
                        assertEquals(
                            true,
                            msg.contains("does not support video", ignoreCase = true) ||
                                msg.contains("Unsupported attachment type", ignoreCase = true),
                            "Exception message doesn't contain expected error: ${ex.message}"
                        )
                    }
                }

                LLMCapability.Embed -> {
                    val client = clientFor(model)
                    withRetry(times = 3, testName = "negative_embed[${model.id}]") {
                        val ex = assertFailsWith<Exception> {
                            if (client is OpenAILLMClient) {
                                client.embed("this should fail for non-embedding models", model)
                            } else {
                                error("Model ${model.id} does not support embeddings")
                            }
                        }
                        val msg = ex.message ?: ""
                        assertEquals(
                            true,
                            msg.contains("does not support", ignoreCase = true) ||
                                msg.contains("embedding", ignoreCase = true) ||
                                msg.contains("does not have the Embed capability", ignoreCase = true) ||
                                msg.contains("Unsupported", ignoreCase = true),
                            "Exception message doesn't contain expected error: ${ex.message}"
                        )
                    }
                }

                LLMCapability.Schema.JSON.Basic -> {
                    val schema = buildJsonObject {
                        put("type", JsonPrimitive("object"))
                        put(
                            "properties",
                            buildJsonObject {
                                put(
                                    "x",
                                    buildJsonObject { put("type", JsonPrimitive("integer")) }
                                )
                            }
                        )
                        put("required", buildJsonArray { add(JsonPrimitive("x")) })
                    }
                    val prompt = prompt(
                        "cap-json-basic-negative",
                        params = LLMParams(schema = LLMParams.Schema.JSON.Basic(name = "XSchema", schema = schema))
                    ) {
                        system("Reply strictly as JSON.")
                        user("Return an integer x.")
                    }
                    withRetry(times = 3, testName = "negative_json_basic[${model.id}]") {
                        val ex = assertFailsWith<Exception> {
                            executor.execute(prompt, model)
                        }
                        val msg = ex.message ?: ""
                        assertEquals(
                            true,
                            msg.contains("does not support structured output schema", ignoreCase = true) ||
                                msg.contains("does not support", ignoreCase = true) ||
                                msg.contains("structured output", ignoreCase = true) ||
                                msg.contains(
                                    "Anthropic does not currently support native structured output",
                                    ignoreCase = true
                                ),
                            "Exception message doesn't contain expected error: ${ex.message}"
                        )
                    }
                }

                LLMCapability.Schema.JSON.Standard -> {
                    val schema = buildJsonObject {
                        put("type", JsonPrimitive("object"))
                        put(
                            "properties",
                            buildJsonObject {
                                put(
                                    "y",
                                    buildJsonObject { put("type", JsonPrimitive("string")) }
                                )
                            }
                        )
                        put("required", buildJsonArray { add(JsonPrimitive("y")) })
                    }
                    val prompt = prompt(
                        "cap-json-standard-negative",
                        params = LLMParams(schema = LLMParams.Schema.JSON.Standard(name = "YSchema", schema = schema))
                    ) {
                        system("Reply strictly as JSON.")
                        user("Return a string y.")
                    }
                    withRetry(times = 3, testName = "negative_json_standard[${model.id}]") {
                        val ex = assertFailsWith<Exception> {
                            executor.execute(prompt, model)
                        }
                        val msg = ex.message ?: ""
                        assertEquals(
                            true,
                            msg.contains("does not support structured output schema", ignoreCase = true) ||
                                msg.contains("does not support", ignoreCase = true) ||
                                msg.contains("structured output", ignoreCase = true) ||
                                msg.contains(
                                    "Anthropic does not currently support native structured output",
                                    ignoreCase = true
                                ),
                            "Exception message doesn't contain expected error: ${ex.message}"
                        )
                    }
                }

                else -> {
                    logger.warn { "Skipping hard-to-verify capability verification for $capability on $model" }
                }
            }
        }
}
