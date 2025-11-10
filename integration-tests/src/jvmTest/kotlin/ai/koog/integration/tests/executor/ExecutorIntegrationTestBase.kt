package ai.koog.integration.tests.executor

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.integration.tests.utils.MediaTestScenarios.AudioTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.ImageTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.MarkdownTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.TextTestScenario
import ai.koog.integration.tests.utils.MediaTestUtils
import ai.koog.integration.tests.utils.MediaTestUtils.checkExecutorMediaResponse
import ai.koog.integration.tests.utils.MediaTestUtils.checkResponseBasic
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.RetryUtils.withRetry
import ai.koog.integration.tests.utils.TestUtils.assertResponseContainsToolCall
import ai.koog.integration.tests.utils.getLLMClientForProvider
import ai.koog.integration.tests.utils.structuredOutput.Country
import ai.koog.integration.tests.utils.structuredOutput.checkWeatherStructuredOutputResponse
import ai.koog.integration.tests.utils.structuredOutput.countryStructuredOutputPrompt
import ai.koog.integration.tests.utils.structuredOutput.getConfigFixingParserManual
import ai.koog.integration.tests.utils.structuredOutput.getConfigFixingParserNative
import ai.koog.integration.tests.utils.structuredOutput.getConfigNoFixingParserManual
import ai.koog.integration.tests.utils.structuredOutput.getConfigNoFixingParserNative
import ai.koog.integration.tests.utils.structuredOutput.parseMarkdownStreamToCountries
import ai.koog.integration.tests.utils.structuredOutput.weatherStructuredOutputPrompt
import ai.koog.integration.tests.utils.tools.CalculatorTool
import ai.koog.integration.tests.utils.tools.LotteryTool
import ai.koog.integration.tests.utils.tools.PickColorFromListTool
import ai.koog.integration.tests.utils.tools.PickColorTool
import ai.koog.integration.tests.utils.tools.PriceCalculatorTool
import ai.koog.integration.tests.utils.tools.SimplePriceCalculatorTool
import ai.koog.integration.tests.utils.tools.calculatorPrompt
import ai.koog.integration.tests.utils.tools.calculatorPromptNotRequiredOptionalParams
import ai.koog.integration.tests.utils.tools.calculatorToolDescriptorOptionalParams
import ai.koog.prompt.dsl.ModerationCategory
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLMEmbeddingProvider
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.params.LLMParams.ToolChoice
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.executeStructured
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64
import kotlin.io.path.pathString
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.io.files.Path as KtPath

abstract class ExecutorIntegrationTestBase {
    private val testScope = TestScope()

    @AfterEach
    fun cleanup() {
        testScope.cancel()
    }

    companion object {
        protected lateinit var testResourcesDir: Path

        @JvmStatic
        @BeforeAll
        fun setupTestResourcesBase() {
            testResourcesDir =
                Paths.get(ExecutorIntegrationTestBase::class.java.getResource("/media")!!.toURI())
        }
    }

    abstract fun getExecutor(model: LLModel): PromptExecutor

    open fun getLLMClient(model: LLModel): LLMClient = getLLMClientForProvider(model.provider)

    open fun integration_testExecute(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        val executor = getExecutor(model)

        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        withRetry(times = 3, testName = "integration_testExecute[${model.id}]") {
            val response = executor.execute(prompt, model)
            assertNotNull(response, "Response should not be null")
            assertTrue(response.isNotEmpty(), "Response should not be empty")
            assertTrue(response.any { it is Message.Assistant }, "Response should be an Assistant message")

            val assistantMessage = response.first { it is Message.Assistant }
            assertTrue(
                assistantMessage.content.contains("Paris", ignoreCase = true),
                "Response should contain 'Paris'"
            )
            assertNotNull(assistantMessage.metaInfo.inputTokensCount, "Input tokens count should not be null")
            assertNotNull(assistantMessage.metaInfo.outputTokensCount, "Output tokens count should not be null")
            assertNotNull(assistantMessage.metaInfo.totalTokensCount, "Total tokens count should not be null")
        }
    }

    open fun integration_testExecuteStreaming(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        if (model.id == OpenAIModels.Audio.GPT4oAudio.id || model.id == OpenAIModels.Audio.GPT4oMiniAudio.id) {
            assumeTrue(false, "https://github.com/JetBrains/koog/issues/231")
        }

        val executor = getExecutor(model)

        val prompt = Prompt.build("test-streaming") {
            system("You are a helpful assistant.")
            user("Count from 1 to 5.")
        }

        withRetry(times = 3, testName = "integration_testExecuteStreaming[${model.id}]") {
            val messageBuilder = StringBuilder()
            val endMessages = mutableListOf<StreamFrame.End>()
            val toolMessages = mutableListOf<StreamFrame.ToolCall>()
            executor.executeStreaming(prompt, model).collect {
                when (it) {
                    is StreamFrame.Append -> messageBuilder.append(it.text)
                    is StreamFrame.End -> endMessages.add(it)
                    is StreamFrame.ToolCall -> toolMessages.add(it)
                }
            }
            assertTrue(messageBuilder.isNotEmpty(), "Response message should not be empty")
            assertTrue(toolMessages.isEmpty(), "Response should not contain any tools be empty")
            assertEquals(endMessages.size, 1, "Response should contain single end message")

            val fullResponse = messageBuilder.toString()
            assertTrue(
                fullResponse.contains("1") &&
                    fullResponse.contains("2") &&
                    fullResponse.contains("3") &&
                    fullResponse.contains("4") &&
                    fullResponse.contains("5"),
                "Full response should contain numbers 1 through 5"
            )
        }
    }

    open fun integration_testToolWithRequiredParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")
        val executor = getExecutor(model)

        withRetry(times = 3, testName = "integration_testToolWithRequiredParams[${model.id}]") {
            val response = executor.execute(calculatorPrompt, model, listOf(CalculatorTool.descriptor))
            assertTrue(response.isNotEmpty(), "Response should not be empty")
            assertResponseContainsToolCall(response, CalculatorTool.name)
        }
    }

    open fun integration_testToolWithNotRequiredOptionalParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")
        val executor = getExecutor(model)

        withRetry(times = 3, testName = "integration_testToolWithNotRequiredOptionalParams[${model.id}]") {
            val response = executor.execute(
                calculatorPromptNotRequiredOptionalParams,
                model,
                listOf(calculatorToolDescriptorOptionalParams)
            )
            assertTrue(response.isNotEmpty(), "Response should not be empty")
            assertResponseContainsToolCall(response, CalculatorTool.name)
        }
    }

    open fun integration_testToolWithOptionalParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val executor = getExecutor(model)
        withRetry(times = 3, testName = "integration_testToolWithOptionalParams[${model.id}]") {
            val response = executor.execute(calculatorPrompt, model, listOf(calculatorToolDescriptorOptionalParams))
            assertTrue(response.isNotEmpty(), "Response should not be empty")
            assertResponseContainsToolCall(response, CalculatorTool.name)
        }
    }

    open fun integration_testToolWithNoParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val prompt = Prompt.build("test-tools") {
            system {
                +"You are a helpful assistant with access to a color picker tool. "
                +"ALWAYS CALL TOOL!!!"
            }
            user("Picker random color for me!")
        }

        val executor = getExecutor(model)

        withRetry(times = 3, testName = "integration_testToolWithNoParams[${model.id}]") {
            val response = executor.execute(prompt, model, listOf(PickColorTool.descriptor))
            assertTrue(response.isNotEmpty(), "Response should not be empty")
            assertResponseContainsToolCall(response, PickColorTool.name)
        }
    }

    open fun integration_testToolWithListEnumParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val prompt = Prompt.build("test-tools") {
            system {
                +"You are a helpful assistant with access to a color picker tool. "
                +"ALWAYS CALL TOOL!!!"
            }
            user("Pick me a color from red, green, orange!")
        }

        val executor = getExecutor(model)

        withRetry(times = 3, testName = "integration_testToolWithListEnumParams[${model.id}]") {
            val response = executor.execute(prompt, model, listOf(PickColorFromListTool.descriptor))
            assertTrue(response.isNotEmpty(), "Response should not be empty")
            assertResponseContainsToolCall(response, PickColorFromListTool.name)
        }
    }

    open fun integration_testToolWithNestedListParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val prompt = Prompt.build("test-tools") {
            system {
                +"You are a helpful assistant with lottery tool. You MUST always call tools!!!"
            }
            user("Select winners from lottery tickets [10, 42, 43, 51, 22] and [34, 12, 4, 53, 99]")
        }

        val executor = getExecutor(model)

        withRetry(times = 3, testName = "integration_testToolWithNestedListParams[${model.id}]") {
            val response = executor.execute(prompt, model, listOf(LotteryTool.descriptor))
            assertTrue(response.isNotEmpty(), "Response should not be empty")
            assertResponseContainsToolCall(response, LotteryTool.name)
        }
    }

    open fun integration_testToolsWithNullParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.provider != LLMProvider.Anthropic, "Anthropic does not support anyOf")
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")
        assumeTrue(model.provider != LLMProvider.MistralAI, "MistralAI returns json array which we are failing to parse. Remove after KG-535 fix")

        val prompt = Prompt.build("test-tools") {
            system {
                +"You are a helpful assistant with tokens price calculator tool."
                +"JUST CALL TOOLS. NO QUESTIONS ASKED."
            }
            user("Calculate price of 10 tokens if I pay 0.003 euro. Discount is not provided to set null.")
        }

        val executor = getExecutor(model)

        withRetry(times = 3, testName = "integration_testToolsWithNullParams[${model.id}]") {
            val response = executor.execute(prompt, model, listOf(SimplePriceCalculatorTool.descriptor))
            assertTrue(response.isNotEmpty(), "Response should not be empty")
            assertTrue(
                response.first { it is Message.Tool.Call }.content.contains("null"),
                "Tool call response should contain null"
            )
        }
    }

    open fun integration_testToolsWithAnyOfParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.provider != LLMProvider.Anthropic, "Anthropic does not support anyOf")
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val prompt = Prompt.build("test-tools", LLMParams(toolChoice = ToolChoice.Required)) {
            system {
                +"You are a helpful assistant with tokens price calculator tool."
                +"JUST CALL TOOLS. NO QUESTIONS ASKED."
            }
            user("Calculate price of 10 tokens if I pay 0.003 euro for token with 10% discount.")
        }

        val executor = getExecutor(model)

        withRetry(testName = "integration_testToolsWithAnyOfParams[${model.id}]") {
            val response = executor.execute(prompt, model, listOf(PriceCalculatorTool.descriptor))
            assertTrue(response.isNotEmpty(), "Response should not be empty")
            assertResponseContainsToolCall(response, PriceCalculatorTool.name)
        }
    }

    open fun integration_testMarkdownStructuredDataStreaming(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model != OpenAIModels.CostOptimized.GPT4_1Nano, "Model $model is too small for structured streaming")

        withRetry(times = 3, testName = "integration_testStructuredDataStreaming[${model.id}]") {
            val markdownStream = getLLMClient(model).executeStreaming(countryStructuredOutputPrompt, model)
            val countries = mutableListOf<Country>()

            parseMarkdownStreamToCountries(markdownStream).collect { country ->
                countries.add(country)
            }

            assertTrue(countries.isNotEmpty(), "Countries list should not be empty")
        }
    }

    open fun integration_testMarkdownProcessingBasic(
        scenario: MarkdownTestScenario,
        model: LLModel
    ) =
        runTest(timeout = 10.minutes) {
            Models.assumeAvailable(model.provider)
            val executor = getExecutor(model)

            val file = MediaTestUtils.createMarkdownFileForScenario(scenario, testResourcesDir)

            val prompt = prompt("markdown-test-${scenario.name.lowercase()}") {
                system("You are a helpful assistant that can analyze markdown files.")

                user {
                    markdown {
                        +"I'm sending you a markdown file with different markdown elements. "
                        +"Please list all the markdown elements used in it and describe its structure clearly."
                    }

                    if (model.capabilities.contains(LLMCapability.Document) && model.provider != LLMProvider.OpenAI) {
                        textFile(KtPath(file.pathString), "text/plain")
                    } else {
                        markdown {
                            +file.readText()
                        }
                    }
                }
            }

            withRetry {
                try {
                    val response = executor.execute(prompt, model).single()
                    when (scenario) {
                        MarkdownTestScenario.MALFORMED_SYNTAX,
                        MarkdownTestScenario.MATH_NOTATION,
                        MarkdownTestScenario.BROKEN_LINKS,
                        MarkdownTestScenario.IRREGULAR_TABLES -> {
                            checkResponseBasic(response)
                        }

                        else -> {
                            checkExecutorMediaResponse(response)
                        }
                    }
                } catch (e: Exception) {
                    when (scenario) {
                        MarkdownTestScenario.EMPTY_MARKDOWN -> {
                            when (model.provider) {
                                LLMProvider.Google -> {
                                    println("Expected exception for ${scenario.name.lowercase()} image: ${e.message}")
                                }
                            }
                        }

                        else -> {
                            throw e
                        }
                    }
                }
            }
        }

    open fun integration_testImageProcessing(scenario: ImageTestScenario, model: LLModel) =
        runTest(timeout = 300.seconds) {
            Models.assumeAvailable(model.provider)
            assumeTrue(model.capabilities.contains(LLMCapability.Vision.Image), "Model must support vision capability")

            val executor = getExecutor(model)

            val imageFile = MediaTestUtils.getImageFileForScenario(scenario, testResourcesDir)

            val prompt = prompt("image-test-${scenario.name.lowercase()}") {
                system("You are a helpful assistant that can analyze images.")

                user {
                    markdown {
                        +"I'm sending you an image. Please analyze it and identify the image format if possible."
                    }

                    when (scenario) {
                        ImageTestScenario.LARGE_IMAGE, ImageTestScenario.LARGE_IMAGE_ANTHROPIC -> {
                            image(
                                ContentPart.Image(
                                    content = AttachmentContent.Binary.Bytes(imageFile.readBytes()),
                                    format = "jpg",
                                    mimeType = "image/jpeg"
                                )
                            )
                        }

                        else -> {
                            image(KtPath(imageFile.pathString))
                        }
                    }
                }
            }

            withRetry {
                try {
                    val response = executor.execute(prompt, model).single()
                    checkExecutorMediaResponse(response)
                } catch (e: Exception) {
                    // For some edge cases, exceptions are expected
                    when (scenario) {
                        ImageTestScenario.LARGE_IMAGE_ANTHROPIC, ImageTestScenario.LARGE_IMAGE -> {
                            assertEquals(
                                e.message?.contains("400 Bad Request"),
                                true,
                                "Expected exception for a large image [400 Bad Request] was not found, got [${e.message}] instead"
                            )
                            assertEquals(
                                e.message?.contains("image exceeds"),
                                true,
                                "Expected exception for a large image [image exceeds] was not found, got [${e.message}] instead"
                            )
                        }

                        ImageTestScenario.CORRUPTED_IMAGE, ImageTestScenario.EMPTY_IMAGE -> {
                            assertEquals(
                                e.message?.contains("400 Bad Request"),
                                true,
                                "Expected exception for a corrupted image [400 Bad Request] was not found, got [${e.message}] instead"
                            )
                            if (model.provider == LLMProvider.Anthropic) {
                                assertEquals(
                                    e.message?.contains("Could not process image"),
                                    true,
                                    "Expected exception for a corrupted image [Could not process image] was not found, got [${e.message}] instead"
                                )
                            } else if (model.provider == LLMProvider.OpenAI) {
                                assertEquals(
                                    e.message?.contains(
                                        "You uploaded an unsupported image. Please make sure your image is valid."
                                    ),
                                    true,
                                    "Expected exception for a corrupted image [You uploaded an unsupported image. Please make sure your image is valid..] was not found, got [${e.message}] instead"
                                )
                            }
                        }

                        else -> {
                            throw e
                        }
                    }
                }
            }
        }

    open fun integration_testTextProcessingBasic(scenario: TextTestScenario, model: LLModel) =
        runTest(timeout = 300.seconds) {
            Models.assumeAvailable(model.provider)

            val executor = getExecutor(model)

            val file = MediaTestUtils.createTextFileForScenario(scenario, testResourcesDir)

            val prompt =
                if (model.capabilities.contains(LLMCapability.Document) && model.provider != LLMProvider.OpenAI) {
                    prompt("text-test-${scenario.name.lowercase()}") {
                        system("You are a helpful assistant that can analyze and process text.")

                        user {
                            markdown {
                                +"I'm sending you a text file. Please analyze it and summarize its content."
                            }

                            textFile(KtPath(file.pathString), "text/plain")
                        }
                    }
                } else {
                    prompt("text-test-${scenario.name.lowercase()}") {
                        system("You are a helpful assistant that can analyze and process text.")

                        user(
                            markdown {
                                +"I'm sending you a text file. Please analyze it and summarize its content."
                                newline()
                                +file.readText()
                            }
                        )
                    }
                }

            withRetry {
                try {
                    val response = executor.execute(prompt, model).single()
                    checkExecutorMediaResponse(response)
                } catch (e: Exception) {
                    when (scenario) {
                        TextTestScenario.EMPTY_TEXT -> {
                            if (model.provider == LLMProvider.Google) {
                                assertEquals(
                                    e.message?.contains("400 Bad Request"),
                                    true,
                                    "Expected exception for empty text [400 Bad Request] was not found, got [${e.message}] instead"
                                )
                                assertEquals(
                                    e.message?.contains(
                                        "Unable to submit request because it has an empty inlineData parameter. Add a value to the parameter and try again."
                                    ),
                                    true,
                                    "Expected exception for empty text [Unable to submit request because it has an empty inlineData parameter. Add a value to the parameter and try again] was not found, got [${e.message}] instead"
                                )
                            }
                        }

                        TextTestScenario.LONG_TEXT_5_MB -> {
                            if (model.provider == LLMProvider.Anthropic) {
                                assertEquals(
                                    e.message?.contains("400 Bad Request"),
                                    true,
                                    "Expected exception for long text [400 Bad Request] was not found, got [${e.message}] instead"
                                )
                                assertEquals(
                                    e.message?.contains("prompt is too long"),
                                    true,
                                    "Expected exception for long text [prompt is too long:] was not found, got [${e.message}] instead"
                                )
                            } else if (model.provider == LLMProvider.Google) {
                                throw e
                            }
                        }

                        else -> {
                            throw e
                        }
                    }
                }
            }
        }

    open fun integration_testAudioProcessingBasic(scenario: AudioTestScenario, model: LLModel) =
        runTest(timeout = 300.seconds) {
            Models.assumeAvailable(model.provider)
            assumeTrue(
                model.capabilities.contains(LLMCapability.Audio),
                "Model must support audio capability"
            )

            val executor = getExecutor(model)

            val audioFile = MediaTestUtils.createAudioFileForScenario(scenario, testResourcesDir)

            val prompt = prompt("audio-test-${scenario.name.lowercase()}") {
                system("You are a helpful assistant.")

                user {
                    text("I'm sending you an audio file. Please tell me a couple of words about it.")
                    audio(KtPath(audioFile.pathString))
                }
            }

            withRetry(times = 3, testName = "integration_testAudioProcessingBasic[${model.id}]") {
                try {
                    val response = executor.execute(prompt, model).single()
                    checkExecutorMediaResponse(response)
                } catch (e: Exception) {
                    if (scenario == AudioTestScenario.CORRUPTED_AUDIO) {
                        assertEquals(
                            e.message?.contains("400 Bad Request"),
                            true,
                            "Expected exception for empty text [400 Bad Request] was not found, got [${e.message}] instead"
                        )
                        if (model.provider == LLMProvider.OpenAI) {
                            assertEquals(
                                e.message?.contains("This model does not support the format you provided."),
                                true,
                                "Expected exception for corrupted audio [This model does not support the format you provided.]"
                            )
                        } else if (model.provider == LLMProvider.Google) {
                            assertEquals(
                                e.message?.contains("Request contains an invalid argument."),
                                true,
                                "Expected exception for corrupted audio [Request contains an invalid argument.]"
                            )
                        }
                    } else {
                        throw e
                    }
                }
            }
        }

    open fun integration_testBase64EncodedAttachment(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        val executor = getExecutor(model)

        assumeTrue(
            model.capabilities.contains(LLMCapability.Vision.Image),
            "Model must support vision capability"
        )

        val imageFile = MediaTestUtils.getImageFileForScenario(ImageTestScenario.BASIC_PNG, testResourcesDir)
        val imageBytes = imageFile.readBytes()

        val tempImageFile = testResourcesDir.resolve("small.png")

        tempImageFile.writeBytes(imageBytes)
        val prompt = prompt("base64-encoded-attachments-test") {
            system("You are a helpful assistant that can analyze different types of media files.")

            user {
                markdown {
                    +"I'm sending you an image. Please analyze them and tell me about their content."
                }

                image(KtPath(tempImageFile.pathString))
            }
        }

        withRetry {
            val response = executor.execute(prompt, model).single()
            checkExecutorMediaResponse(response)

            assertTrue(
                response.content.contains("image", ignoreCase = true),
                "Response should mention the image"
            )
        }
    }

    open fun integration_testUrlBasedAttachment(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.provider !== LLMProvider.Google, "Google models do not support URL attachments")
        val executor = getExecutor(model)

        assumeTrue(
            model.capabilities.contains(LLMCapability.Vision.Image),
            "Model must support vision capability"
        )

        val imageUrl =
            "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c3/Python-logo-notext.svg/1200px-Python-logo-notext.svg.png"

        val prompt = prompt("url-based-attachments-test") {
            system("You are a helpful assistant that can analyze images.")

            user {
                markdown {
                    +"I'm sending you an image from a URL. Please analyze it and tell me about its content."
                }

                image(imageUrl)
            }
        }

        withRetry {
            val response = executor.execute(prompt, model).single()
            checkExecutorMediaResponse(response)

            assertTrue(
                response.content.contains("image", ignoreCase = true) ||
                    response.content.contains("python", ignoreCase = true) ||
                    response.content.contains("logo", ignoreCase = true),
                "Response should mention the image content"
            )
        }
    }

    open fun integration_testStructuredOutputNative(model: LLModel) = runTest {
        assumeTrue(
            model.capabilities.contains(LLMCapability.Schema.JSON.Standard),
            "Model does not support Standard JSON Schema"
        )

        val executor = getExecutor(model)

        withRetry {
            val result = executor.executeStructured(
                prompt = weatherStructuredOutputPrompt,
                model = model,
                config = getConfigNoFixingParserNative(model)
            )

            assertTrue(result.isSuccess, "Structured output should succeed: ${result.exceptionOrNull()}")
            checkWeatherStructuredOutputResponse(result)
        }
    }

    open fun integration_testStructuredOutputNativeWithFixingParser(model: LLModel) = runTest {
        assumeTrue(
            model.capabilities.contains(LLMCapability.Schema.JSON.Standard),
            "Model does not support Standard JSON Schema"
        )

        val executor = getExecutor(model)

        withRetry {
            val result = executor.executeStructured(
                prompt = weatherStructuredOutputPrompt,
                model = model,
                config = getConfigFixingParserNative(model)
            )

            assertTrue(result.isSuccess, "Structured output should succeed: ${result.exceptionOrNull()}")
            checkWeatherStructuredOutputResponse(result)
        }
    }

    open fun integration_testStructuredOutputManual(model: LLModel) = runTest {
        assumeTrue(
            model.provider !== LLMProvider.Google,
            "Google models fail to return manually requested structured output without fixing"
        )
        if (model.provider == LLMProvider.OpenRouter) {
            assumeTrue(
                model.id.contains("gemini"),
                "Google models fail to return manually requested structured output without fixing"
            )
        }

        val executor = getExecutor(model)

        withRetry {
            val result = executor.executeStructured(
                prompt = weatherStructuredOutputPrompt,
                model = model,
                config = getConfigNoFixingParserManual(model)
            )

            assertTrue(result.isSuccess, "Structured output should succeed: ${result.exceptionOrNull()}")
            checkWeatherStructuredOutputResponse(result)
        }
    }

    open fun integration_testStructuredOutputManualWithFixingParser(model: LLModel) = runTest {
        assumeFalse(
            (model.id.contains("flash-lite")),
            "Gemini Flash Lite models fail to return manually requested structured output"
        )
        val executor = getExecutor(model)

        withRetry(6) {
            val result = executor.executeStructured(
                prompt = weatherStructuredOutputPrompt,
                model = model,
                config = getConfigFixingParserManual(model)
            )

            assertTrue(result.isSuccess, "Structured output should succeed: ${result.exceptionOrNull()}")
            checkWeatherStructuredOutputResponse(result)
        }
    }

    open fun integration_testToolChoiceRequired(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(LLMCapability.ToolChoice in model.capabilities, "Model $model does not support tool choice")

        val prompt = calculatorPrompt

        /** tool choice auto is default and thus is tested by [integration_testToolWithRequiredParams] */

        withRetry(times = 3, testName = "integration_testToolChoiceRequired[${model.id}]") {
            val response = getLLMClient(model).execute(
                prompt.withParams(
                    prompt.params.copy(
                        toolChoice = ToolChoice.Required
                    )
                ),
                model,
                listOf(CalculatorTool.descriptor)
            )

            assertTrue(response.isNotEmpty(), "Response should not be empty")
            assertResponseContainsToolCall(response, CalculatorTool.descriptor.name)
        }
    }

    open fun integration_testToolChoiceNone(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)

        assumeTrue(model.provider != LLMProvider.Bedrock, "Bedrock API doesn't support 'none' tool choice.")
        assumeTrue(LLMCapability.ToolChoice in model.capabilities, "Model $model does not support tool choice")
        assumeTrue(model.provider != LLMProvider.MistralAI, "MistralAI returns json array which we are failing to parse. Remove after KG-535 fix")

        val prompt = Prompt.build("test-calculator-tool") {
            system("You are a helpful assistant.")
            user("What is 2 + 2?")
        }

        withRetry(times = 3, testName = "integration_testToolChoiceNone[${model.id}]") {
            val response = getLLMClient(model).execute(
                prompt.withParams(
                    prompt.params.copy(
                        toolChoice = ToolChoice.None
                    )
                ),
                model,
                listOf(CalculatorTool.descriptor)
            )

            assertTrue(response.isNotEmpty(), "Response should not be empty")
            assertTrue(response.none { it is Message.Tool.Call }, "Response should not contain tool calls")
        }
    }

    open fun integration_testToolChoiceNamed(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)

        assumeTrue(model.capabilities.contains(LLMCapability.ToolChoice), "Model $model does not support tool choice")

        val nothingTool = ToolDescriptor(
            name = "nothing",
            description = "A tool that does nothing",
        )

        val prompt = calculatorPrompt

        withRetry(times = 3, testName = "integration_testToolChoiceNamed[${model.id}]") {
            val response = getLLMClient(model).execute(
                prompt.withParams(
                    prompt.params.copy(
                        toolChoice = ToolChoice.Named(nothingTool.name)
                    )
                ),
                model,
                listOf(CalculatorTool.descriptor, nothingTool)
            )

            assertTrue(response.isNotEmpty(), "Response should not be empty")
            assertResponseContainsToolCall(response, nothingTool.name)
        }
    }

    open fun integration_testEmbed(model: LLModel) = runTest {
        val client = getLLMClient(model)
        if (client !is LLMEmbeddingProvider) {
            return@runTest
        }
        val testText = "integration test embedding"
        val embedding = client.embed(testText, model)
        assertNotNull(embedding, "Embedding should not be null")
        assertTrue(embedding.isNotEmpty(), "Embedding should not be empty for model ${model.id}")
        assertTrue(embedding.size > 100, "Embedding should have a reasonable number of dimensions")
        embedding.forEach { value ->
            assertTrue(value.isFinite(), "Embedding values should be finite")
        }
    }

    open fun integration_testMultipleSystemMessages(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)

        val prompt = prompt("multiple-system-messages-test") {
            system("You are a helpful assistant.")
            user("Hi")
            system("You can handle multiple system messages.")
            user("Respond with a short message.")
        }
        val response = getLLMClient(model).execute(prompt, model).single()
        assertEquals(Message.Role.Assistant, response.role, "First message should be an assistant message")
    }

    open fun integration_testSingleMessageModeration(model: LLModel) = runTest(timeout = 300.seconds) {
        assumeTrue(model.capabilities.contains(LLMCapability.Moderation), "Model $model does not support moderation")
        val client = getLLMClient(model)

        val prompt = prompt("test-harmful-content") {
            user(String(Base64.getDecoder().decode("SG93IGNhbiBJIGhhcm0gc29tZW9uZT8="))) // For testing purposes
        }

        val result = client.moderate(prompt = prompt, model = model)

        assertTrue(result.isHarmful, "Harmful content should be detected!")
        assert(
            result.violatesOneOf(
                ModerationCategory.Illicit,
                ModerationCategory.IllicitViolent,
                ModerationCategory.Violence
            )
        ) { "Violence or crime must be detected!" }
    }

    open fun integration_testMultipleMessagesModeration(model: LLModel) = runTest(timeout = 300.seconds) {
        assumeTrue(model.capabilities.contains(LLMCapability.Moderation), "Model $model does not support moderation")
        val client = getLLMClient(model)

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
            !client.moderate(prompt = questionOnly, model = model).isHarmful
        ) { "Question only should not be detected as harmful!" }

        assert(
            !client.moderate(prompt = answerOnly, model = model).isHarmful
        ) { "Answer alone should not be detected as harmful!" }

        val multiMessageReply = client.moderate(
            prompt = promptWithMultipleMessages,
            model = model
        )

        assert(multiMessageReply.isHarmful) { "Question together with answer must be detected as harmful!" }
    }

    open fun integration_testGetModels(provider: LLMProvider): Unit = runBlocking {
        val client = getLLMClientForProvider(provider)
        val models = client.models()
        assertTrue(models.isNotEmpty(), "Models list should not be empty")
    }
}
