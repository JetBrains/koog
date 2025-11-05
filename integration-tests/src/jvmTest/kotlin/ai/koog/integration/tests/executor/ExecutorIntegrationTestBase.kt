package ai.koog.integration.tests.executor

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.integration.tests.utils.MediaTestScenarios.AudioTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.ImageTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.MarkdownTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.TextTestScenario
import ai.koog.integration.tests.utils.MediaTestUtils
import ai.koog.integration.tests.utils.MediaTestUtils.checkExecutorMediaResponse
import ai.koog.integration.tests.utils.MediaTestUtils.checkResponseBasic
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.RetryUtils.withRetry
import ai.koog.integration.tests.utils.TestUtils.CalculatorOperation
import ai.koog.integration.tests.utils.TestUtils.Colors
import ai.koog.integration.tests.utils.TestUtils.Country
import ai.koog.integration.tests.utils.TestUtils.StructuredTest
import ai.koog.integration.tests.utils.TestUtils.StructuredTest.checkResponse
import ai.koog.integration.tests.utils.TestUtils.StructuredTest.getConfigFixingParserNative
import ai.koog.integration.tests.utils.TestUtils.StructuredTest.getConfigNoFixingParserNative
import ai.koog.integration.tests.utils.TestUtils.markdownCountryDefinition
import ai.koog.integration.tests.utils.TestUtils.parseMarkdownStreamToCountries
import ai.koog.integration.tests.utils.getLLMClientForProvider
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
import ai.koog.prompt.streaming.filterTextOnly
import ai.koog.prompt.structure.executeStructured
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
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

    fun createCalculatorTool(): ToolDescriptor {
        return ToolDescriptor(
            name = "calculator",
            description = "A simple calculator that can add, subtract, multiply, and divide two numbers.",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "operation",
                    description = "The operation to perform.",
                    type = ToolParameterType.Enum(CalculatorOperation.entries.map { it.name }.toTypedArray())
                ),
                ToolParameterDescriptor(
                    name = "a",
                    description = "The first argument (number)",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "b",
                    description = "The second argument (number)",
                    type = ToolParameterType.Integer
                )
            )
        )
    }

    fun createCalculatorPrompt() = Prompt.build("test-tools") {
        system(
            "You are a helpful assistant with access to a calculator tool. When asked to perform calculations, use the calculator tool instead of calculating the answer yourself."
        )
        user("What is 123 + 456?")
    }

    open fun integration_testExecute(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        val executor = getExecutor(model)

        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        withRetry(times = 3, testName = "integration_testExecute[${model.id}]") {
            val response = executor.execute(prompt, model, emptyList())
            assertNotNull(response, "Response should not be null")
            assertTrue(response.isNotEmpty(), "Response should not be empty")
            assertTrue(response.first() is Message.Assistant, "Response should be an Assistant message")

            val message = response.first() as Message.Assistant
            assertTrue(
                message.content.contains("Paris", ignoreCase = true),
                "Response should contain 'Paris'"
            )
            assertNotNull(message.metaInfo.inputTokensCount, "Input tokens count should not be null")
            assertNotNull(message.metaInfo.outputTokensCount, "Output tokens count should not be null")
            assertNotNull(message.metaInfo.totalTokensCount, "Total tokens count should not be null")
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
            val responseChunks = executor.executeStreaming(prompt, model)
                .filterTextOnly()
                .toList()
            assertNotNull(responseChunks, "Response chunks should not be null")
            assertTrue(responseChunks.isNotEmpty(), "Response chunks should not be empty")

            val fullResponse = responseChunks.joinToString("")
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

    open fun integration_testToolsWithRequiredParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val calculatorTool = ToolDescriptor(
            name = "calculator",
            description = "A simple calculator that can add, subtract, multiply, and divide two numbers.",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "operation",
                    description = "The operation to perform.",
                    type = ToolParameterType.Enum(CalculatorOperation.entries.map { it.name }.toTypedArray())
                ),
                ToolParameterDescriptor(
                    name = "a",
                    description = "The first argument (number)",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "b",
                    description = "The second argument (number)",
                    type = ToolParameterType.Integer
                )
            )
        )

        val prompt = Prompt.build("test-tools") {
            system("You are a helpful assistant with access to a calculator tool.")
            user("What is 123 + 456?")
        }

        withRetry(times = 3, testName = "integration_testToolsWithRequiredParams[${model.id}]") {
            val executor = getExecutor(model)
            val response = executor.execute(prompt, model, listOf(calculatorTool))
            assertTrue(response.isNotEmpty(), "Response should not be empty")
        }
    }

    open fun integration_testToolsWithRequiredOptionalParams(model: LLModel) =
        runTest(timeout = 300.seconds) {
            Models.assumeAvailable(model.provider)
            assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

            val calculatorTool = ToolDescriptor(
                name = "calculator",
                description = "A simple calculator that can add, subtract, multiply, and divide two numbers.",
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        name = "operation",
                        description = "The operation to perform.",
                        type = ToolParameterType.Enum(
                            CalculatorOperation.entries.map { it.name }
                                .toTypedArray()
                        )
                    ),
                    ToolParameterDescriptor(
                        name = "a",
                        description = "The first argument (number)",
                        type = ToolParameterType.Float
                    ),
                    ToolParameterDescriptor(
                        name = "b",
                        description = "The second argument (number)",
                        type = ToolParameterType.Float
                    )
                ),
                optionalParameters = listOf(
                    ToolParameterDescriptor(
                        name = "comment",
                        description = "Comment to the result (string)",
                        type = ToolParameterType.String
                    )
                )
            )

            val prompt = Prompt.build("test-tools") {
                system {
                    +"You are a helpful assistant with access to a calculator tool."
                    +"Don't use optional params if possible."
                    +"JUST CALL TOOLS. NO QUESTIONS ASKED."
                }
                user("What is 123 + 456?")
            }

            val executor = getExecutor(model)

            withRetry(times = 3, testName = "integration_testToolsWithRequiredOptionalParams[${model.id}]") {
                val response = executor.execute(prompt, model, listOf(calculatorTool))
                assertTrue(response.isNotEmpty(), "Response should not be empty")
            }
        }

    open fun integration_testToolsWithOptionalParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val calculatorTool = ToolDescriptor(
            name = "calculator",
            description = "A simple calculator that can add, subtract, multiply, and divide two numbers.",
            optionalParameters = listOf(
                ToolParameterDescriptor(
                    name = "operation",
                    description = "The operation to perform.",
                    type = ToolParameterType.Enum(CalculatorOperation.entries.map { it.name }.toTypedArray())
                ),
                ToolParameterDescriptor(
                    name = "a",
                    description = "The first argument (number)",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "b",
                    description = "The second argument (number)",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "comment",
                    description = "Comment to the result (string)",
                    type = ToolParameterType.String
                )
            )
        )

        val prompt = Prompt.build("test-tools") {
            system("You are a helpful assistant with access to a calculator tool.")
            user("What is 123 + 456?")
        }

        val executor = getExecutor(model)
        withRetry(times = 3, testName = "integration_testToolsWithOptionalParams[${model.id}]") {
            val response = executor.execute(prompt, model, listOf(calculatorTool))
            assertTrue(response.isNotEmpty(), "Response should not be empty")
        }
    }

    open fun integration_testToolsWithNoParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val calculatorTool = ToolDescriptor(
            name = "calculator",
            description = "A simple calculator that can add, subtract, multiply, and divide two numbers.",
        )

        val calculatorToolBetter = ToolDescriptor(
            name = "calculatorBetter",
            description = "A better calculator that can add, subtract, multiply, and divide two numbers.",
            requiredParameters = emptyList(),
            optionalParameters = emptyList()
        )

        val prompt = Prompt.build("test-tools") {
            system {
                +"You are a helpful assistant with access to calculator tools."
                +"Use the best one."
            }
            user("What is 123 + 456?")
        }

        val executor = getExecutor(model)

        withRetry(times = 3, testName = "integration_testToolsWithNoParams[${model.id}]") {
            val response =
                executor.execute(prompt, model, listOf(calculatorTool, calculatorToolBetter))
            assertTrue(response.isNotEmpty(), "Response should not be empty")
        }
    }

    open fun integration_testToolsWithListEnumParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val colorPickerTool = ToolDescriptor(
            name = "colorPicker",
            description = "A tool that can randomly pick a color from a list of colors.",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "color",
                    description = "The color to be picked.",
                    type = ToolParameterType.List(
                        ToolParameterType.Enum(
                            Colors.entries.map { it.name }
                                .toTypedArray()
                        )
                    )
                )
            )
        )

        val prompt = Prompt.build("test-tools") {
            system {
                +"You are a helpful assistant with access to a color picker tool. "
                +"ALWAYS CALL TOOL FIRST."
            }
            user("Pick me a color!")
        }

        val executor = getExecutor(model)

        withRetry(times = 3, testName = "integration_testToolsWithListEnumParams[${model.id}]") {
            val response = executor.execute(prompt, model, listOf(colorPickerTool))
            assertTrue(response.isNotEmpty(), "Response should not be empty")
        }
    }

    open fun integration_testToolsWithNestedListParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val lotteryPickerTool = ToolDescriptor(
            name = "lotteryPicker",
            description = "A tool that can randomly you some lottery winners and losers",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "Numbers",
                    description = "A list of the numbers for lottery winners and losers from 1 to 100",
                    type = ToolParameterType.List(ToolParameterType.List(ToolParameterType.Integer))
                )
            )
        )

        val prompt = Prompt.build("test-tools") {
            system {
                +"You are a helpful assistant."
                +"JUST CALL TOOLS. NO QUESTIONS ASKED."
            }
            user("Pick me lottery winners and losers! 5 of each")
        }

        val executor = getExecutor(model)

        withRetry(times = 3, testName = "integration_testToolsWithNestedListParams[${model.id}]") {
            val response = executor.execute(prompt, model, listOf(lotteryPickerTool))
            assertTrue(response.isNotEmpty(), "Response should not be empty")
        }
    }

    open fun integration_testToolsWithNullParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val nullGiverTool = ToolDescriptor(
            name = "nullGiver",
            description = "A tool that returns a null value",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "Null",
                    description = "A null",
                    type = ToolParameterType.Null
                )
            )
        )

        val prompt = Prompt.build("test-tools") {
            system {
                +"You are a helpful assistant."
                +"JUST CALL TOOLS. NO QUESTIONS ASKED."
            }
            user("Hi. Call a tool.")
        }

        val executor = getExecutor(model)

        withRetry(times = 3, testName = "integration_testToolsWithNullParams[${model.id}]") {
            val response = executor.execute(prompt, model, listOf(nullGiverTool))
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

        val anyOfTool = ToolDescriptor(
            name = "stringNumberGiver",
            description = "A tool that returns a string or number value",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "anyOfParam",
                    description = "String or number parameter",
                    type = ToolParameterType.AnyOf(
                        types = arrayOf(
                            ToolParameterDescriptor(
                                name = "String",
                                description = "String option",
                                type = ToolParameterType.String
                            ),
                            ToolParameterDescriptor(
                                name = "Number",
                                description = "Number option",
                                type = ToolParameterType.Float
                            )
                        )
                    )
                )
            )
        )

        val prompt = Prompt.build("test-tools", LLMParams(toolChoice = ToolChoice.Required)) {
            system {
                +"You are a helpful assistant."
                +"JUST CALL TOOLS. NO QUESTIONS ASKED."
            }
            user("Hi. Give me a word and a number.")
        }

        val executor = getExecutor(model)

        withRetry(testName = "integration_testToolsWithAnyOfParams[${model.id}]") {
            val response = executor.execute(prompt, model, listOf(anyOfTool))
            assertTrue(response.isNotEmpty(), "Response should not be empty")
            assertTrue(response.any { it is Message.Tool.Call }, "Response should contain a tool call")
        }
    }

    open fun integration_testStructuredDataStreaming(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model != OpenAIModels.CostOptimized.GPT4_1Nano, "Model $model is too small for structured streaming")

        val countries = mutableListOf<Country>()
        val countryDefinition = markdownCountryDefinition()

        val prompt = Prompt.build("test-structured-streaming") {
            system("You are a helpful assistant.")
            user(
                """
                Please provide information about 3 European countries in this format:

                $countryDefinition

                Make sure to follow this exact format with the # for country names and * for details.
                """.trimIndent()
            )
        }

        withRetry(times = 3, testName = "integration_testStructuredDataStreaming[${model.id}]") {
            val markdownStream = getLLMClient(model).executeStreaming(prompt, model)

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

            val prompt =
                if (model.capabilities.contains(LLMCapability.Document) && model.provider != LLMProvider.OpenAI) {
                    prompt("markdown-test-${scenario.name.lowercase()}") {
                        system("You are a helpful assistant that can analyze markdown files.")

                        user {
                            markdown {
                                +"I'm sending you a markdown file with different markdown elements. "
                                +"Please list all the markdown elements used in it and describe its structure clearly."
                            }

                            textFile(KtPath(file.pathString), "text/plain")
                        }
                    }
                } else {
                    prompt("markdown-test-${scenario.name.lowercase()}") {
                        system("You are a helpful assistant that can analyze markdown files.")

                        user(
                            markdown {
                                +"I'm sending you a markdown file with different markdown elements. "
                                +"Please list all the markdown elements used in it and describe its structure clearly."
                                newline()
                                +file.readText()
                            }
                        )
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
                prompt = StructuredTest.prompt,
                model = model,
                config = getConfigNoFixingParserNative(model)
            )

            assertTrue(result.isSuccess, "Structured output should succeed: ${result.exceptionOrNull()}")
            checkResponse(result)
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
                prompt = StructuredTest.prompt,
                model = model,
                config = getConfigFixingParserNative(model)
            )

            assertTrue(result.isSuccess, "Structured output should succeed: ${result.exceptionOrNull()}")
            checkResponse(result)
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
                prompt = StructuredTest.prompt,
                model = model,
                config = StructuredTest.getConfigNoFixingParserManual(model)
            )

            assertTrue(result.isSuccess, "Structured output should succeed: ${result.exceptionOrNull()}")
            checkResponse(result)
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
                prompt = StructuredTest.prompt,
                model = model,
                config = StructuredTest.getConfigFixingParserManual(model)
            )

            assertTrue(result.isSuccess, "Structured output should succeed: ${result.exceptionOrNull()}")
            checkResponse(result)
        }
    }

    open fun integration_testRawStringStreaming(model: LLModel) = runTest(timeout = 600.seconds) {
        Models.assumeAvailable(model.provider)
        if (model.id == OpenAIModels.Audio.GPT4oAudio.id || model.id == OpenAIModels.Audio.GPT4oMiniAudio.id) {
            assumeTrue(false, "https://github.com/JetBrains/koog/issues/231")
        }

        val prompt = Prompt.build("test-streaming") {
            system {
                +"You are a helpful assistant."
                +"You have NO output length limitations."
            }
            user("Count from 1 to 5.")
        }

        val responseChunks = mutableListOf<StreamFrame>()

        withRetry(times = 3, testName = "integration_testRawStringStreaming[${model.id}]") {
            getLLMClient(model).executeStreaming(prompt, model).collect { chunk ->
                responseChunks.add(chunk)
            }

            assertTrue(responseChunks.isNotEmpty(), "Response chunks should not be empty")

            val fullResponse = responseChunks.joinToString("")
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

    open fun integration_testToolChoiceRequired(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(LLMCapability.ToolChoice in model.capabilities, "Model $model does not support tool choice")

        val calculatorTool = createCalculatorTool()
        val prompt = createCalculatorPrompt()

        /** tool choice auto is default and thus is tested by [integration_testToolsWithRequiredParams] */

        withRetry(times = 3, testName = "integration_testToolChoiceRequired[${model.id}]") {
            val response = getLLMClient(model).execute(
                prompt.withParams(
                    prompt.params.copy(
                        toolChoice = ToolChoice.Required
                    )
                ),
                model,
                listOf(calculatorTool)
            )

            assertTrue(response.isNotEmpty(), "Response should not be empty")
            assertTrue(response.first() is Message.Tool.Call)
        }
    }

    open fun integration_testToolChoiceNone(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)

        assumeTrue(model.provider != LLMProvider.Bedrock, "Bedrock API doesn't support 'none' tool choice.")
        assumeTrue(LLMCapability.ToolChoice in model.capabilities, "Model $model does not support tool choice")

        val calculatorTool = createCalculatorTool()
        val prompt = createCalculatorPrompt()

        withRetry(times = 3, testName = "integration_testToolChoiceNone[${model.id}]") {
            val response = getLLMClient(model).execute(
                Prompt.build("test-tools") {
                    system {
                        +"You are a helpful assistant. "
                        +"Do not use calculator tool, it's broken!"
                    }
                    user("What is 123 + 456?")
                }.withParams(
                    prompt.params.copy(
                        toolChoice = ToolChoice.None
                    )
                ),
                model,
                listOf(calculatorTool)
            )

            assertTrue(response.isNotEmpty(), "Response should not be empty")
            assertTrue(response.first() is Message.Assistant)
        }
    }

    open fun integration_testToolChoiceNamed(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)

        assumeTrue(model.capabilities.contains(LLMCapability.ToolChoice), "Model $model does not support tool choice")

        val calculatorTool = createCalculatorTool()
        val prompt = createCalculatorPrompt()

        val nothingTool = ToolDescriptor(
            name = "nothing",
            description = "A tool that does nothing",
        )

        withRetry(times = 3, testName = "integration_testToolChoiceNamed[${model.id}]") {
            val response = getLLMClient(model).execute(
                prompt.withParams(
                    prompt.params.copy(
                        toolChoice = ToolChoice.Named(nothingTool.name)
                    )
                ),
                model,
                listOf(calculatorTool, nothingTool)
            )

            assertNotNull(response, "Response should not be null")
            assertTrue(response.isNotEmpty(), "Response should not be empty")
            assertTrue(
                response.first() is Message.Tool.Call,
                "First message should be a tool call, but was ${response.first().role}"
            )
            val toolCall = response.first() as Message.Tool.Call
            assertEquals("nothing", toolCall.tool, "Tool name should be 'nothing'")
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
