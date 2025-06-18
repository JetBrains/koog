package ai.koog.integration.tests

import ai.koog.integration.tests.utils.MediaTestScenarios
import ai.koog.integration.tests.utils.MediaTestUtils
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.TestUtils
import ai.koog.integration.tests.utils.TestUtils.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestOpenAIKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestOpenRouterKeyFromEnv
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.integration.tests.utils.MediaTestScenarios.ImageTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.MarkdownTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.TextTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.AudioTestScenario
import ai.koog.integration.tests.utils.MediaTestUtils.checkExecutorMediaResponse
import ai.koog.integration.tests.utils.MediaTestUtils.checkResponseBasic
import ai.koog.integration.tests.utils.RetryUtils.withRetry
import ai.koog.integration.tests.utils.TestUtils.readTestGoogleAIKeyFromEnv
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutorExt.execute
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams.ToolChoice
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SingleLLMPromptExecutorIntegrationTest {
    companion object {
        private lateinit var testResourcesDir: File

        @JvmStatic
        @BeforeAll
        fun setupTestResources() {
            testResourcesDir = File("src/jvmTest/resources/media")
            testResourcesDir.mkdirs()
            assertTrue(testResourcesDir.exists(), "Test resources directory should exist")
        }

        // combinations for usual universal tests
        @JvmStatic
        fun modelClientCombinations(): Stream<Arguments> {
            val openAIClientInstance = OpenAILLMClient(readTestOpenAIKeyFromEnv())
            val anthropicClientInstance = AnthropicLLMClient(readTestAnthropicKeyFromEnv())
            val openRouterClientInstance = OpenRouterLLMClient(readTestOpenRouterKeyFromEnv())

            return Stream.concat(
                Models.openAIModels().map { model -> Arguments.of(model, openAIClientInstance) },
                Models.anthropicModels().map { model -> Arguments.of(model, anthropicClientInstance) }
                // Will enable when there're models that support tool calls
                /*Models.openRouterModels().map { model -> Arguments.of(model, openRouterClientInstance) }*/
            )
        }

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
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testExecute(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
        val executor = SingleLLMPromptExecutor(client)

        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        withRetry(times = 3, testName = "integration_testExecute[${model.id}]") {
            val response = executor.execute(prompt, model, emptyList())
            assertNotNull(response, "Response should not be null")
            assertTrue(response.isNotEmpty(), "Response should not be empty")
            assertTrue(response.first() is Message.Assistant, "Response should be an Assistant message")
            assertTrue(
                (response.first() as Message.Assistant).content.contains("Paris", ignoreCase = true),
                "Response should contain 'Paris'"
            )
        }
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testExecuteStreaming(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
        if (model.id == OpenAIModels.Audio.GPT4oAudio.id || model.id == OpenAIModels.Audio.GPT4oMiniAudio.id) {
            assumeTrue(false, "https://github.com/JetBrains/koog/issues/231")
        }

        val executor = SingleLLMPromptExecutor(client)

        val prompt = Prompt.build("test-streaming") {
            system("You are a helpful assistant.")
            user("Count from 1 to 5.")
        }

        withRetry(times = 3, testName = "integration_testExecuteStreaming[${model.id}]") {
            val responseChunks = executor.executeStreaming(prompt, model).toList()
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

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testCodeGeneration(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
        val executor = SingleLLMPromptExecutor(client)

        val prompt = Prompt.build("test-code") {
            system("You are a helpful coding assistant.")
            user("Write a simple Kotlin function to calculate the factorial of a number. Make sure the name of the function starts with 'factorial'.")
        }

        var response: List<Message>

        withRetry(times = 3, testName = "integration_testCodeGeneration[${model.id}]") {
            response = executor.execute(prompt, model, emptyList())

            assertNotNull(response, "Response should not be null")
            assertTrue(response.isNotEmpty(), "Response should not be empty")
            assertTrue(response.first() is Message.Assistant, "Response should be an Assistant message")

            val content = (response.first() as Message.Assistant).content
            assertTrue(
                content.contains("fun factorial"),
                "Response should contain a factorial function. Response: $response. Content: $content"
            )
            assertTrue(content.contains("return"), "Response should contain a return statement")
        }
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testToolsWithRequiredParams(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val calculatorTool = ToolDescriptor(
            name = "calculator",
            description = "A simple calculator that can add, subtract, multiply, and divide two numbers.",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "operation",
                    description = "The operation to perform.",
                    type = ToolParameterType.Enum(TestUtils.CalculatorOperation.entries.map { it.name }.toTypedArray())
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
            val executor = SingleLLMPromptExecutor(client)
            val response = executor.execute(prompt, model, listOf(calculatorTool))
            assertTrue(response.isNotEmpty(), "Response should not be empty")
        }
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testToolsWithRequiredOptionalParams(model: LLModel, client: LLMClient) =
        runTest(timeout = 300.seconds) {
            assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

            val calculatorTool = ToolDescriptor(
                name = "calculator",
                description = "A simple calculator that can add, subtract, multiply, and divide two numbers.",
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        name = "operation",
                        description = "The operation to perform.",
                        type = ToolParameterType.Enum(TestUtils.CalculatorOperation.entries.map { it.name }
                            .toTypedArray())
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
                system("You are a helpful assistant with access to a calculator tool. Don't use optional params if possible. ALWAYS CALL TOOL FIRST.")
                user("What is 123 + 456?")
            }

            val executor = SingleLLMPromptExecutor(client)

            withRetry(times = 3, testName = "integration_testToolsWithRequiredOptionalParams[${model.id}]") {
                val response = executor.execute(prompt, model, listOf(calculatorTool))
                assertTrue(response.isNotEmpty(), "Response should not be empty")
            }
        }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testToolsWithOptionalParams(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val calculatorTool = ToolDescriptor(
            name = "calculator",
            description = "A simple calculator that can add, subtract, multiply, and divide two numbers.",
            optionalParameters = listOf(
                ToolParameterDescriptor(
                    name = "operation",
                    description = "The operation to perform.",
                    type = ToolParameterType.Enum(TestUtils.CalculatorOperation.entries.map { it.name }.toTypedArray())
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

        val executor = SingleLLMPromptExecutor(client)
        withRetry(times = 3, testName = "integration_testToolsWithOptionalParams[${model.id}]") {
            val response = executor.execute(prompt, model, listOf(calculatorTool))
            assertTrue(response.isNotEmpty(), "Response should not be empty")
        }
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testToolsWithNoParams(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
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
            system("You are a helpful assistant with access to calculator tools. Use the best one.")
            user("What is 123 + 456?")
        }

        val executor = SingleLLMPromptExecutor(client)

        withRetry(times = 3, testName = "integration_testToolsWithNoParams[${model.id}]") {
            val response =
                executor.execute(prompt, model, listOf(calculatorTool, calculatorToolBetter))
            assertTrue(response.isNotEmpty(), "Response should not be empty")
        }
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testToolsWithListEnumParams(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val colorPickerTool = ToolDescriptor(
            name = "colorPicker",
            description = "A tool that can randomly pick a color from a list of colors.",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "color",
                    description = "The color to be picked.",
                    type = ToolParameterType.List(ToolParameterType.Enum(TestUtils.Colors.entries.map { it.name }
                        .toTypedArray()))
                )
            )
        )

        val prompt = Prompt.build("test-tools") {
            system("You are a helpful assistant with access to a color picker tool. ALWAYS CALL TOOL FIRST.")
            user("Pick me a color!")
        }

        val executor = SingleLLMPromptExecutor(client)

        withRetry(times = 3, testName = "integration_testToolsWithListEnumParams[${model.id}]") {
            val response = executor.execute(prompt, model, listOf(colorPickerTool))
            assertTrue(response.isNotEmpty(), "Response should not be empty")
        }
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testToolsWithNestedListParams(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
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
            system("You are a helpful assistant. ALWAYS CALL TOOL FIRST.")
            user("Pick me lottery winners and losers! 5 of each")
        }

        val executor = SingleLLMPromptExecutor(client)

        withRetry(times = 3, testName = "integration_testToolsWithNestedListParams[${model.id}]") {
            val response = executor.execute(prompt, model, listOf(lotteryPickerTool))
            assertTrue(response.isNotEmpty(), "Response should not be empty")
        }
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testRawStringStreaming(model: LLModel, client: LLMClient) = runTest(timeout = 600.seconds) {
        if (model.id == OpenAIModels.Audio.GPT4oAudio.id || model.id == OpenAIModels.Audio.GPT4oMiniAudio.id) {
            assumeTrue(false, "https://github.com/JetBrains/koog/issues/231")
        }

        val prompt = Prompt.build("test-streaming") {
            system("You are a helpful assistant. You have NO output length limitations.")
            user("Count from 1 to 5.")
        }

        val responseChunks = mutableListOf<String>()

        withRetry(times = 3, testName = "integration_testRawStringStreaming[${model.id}]") {
            client.executeStreaming(prompt, model).collect { chunk ->
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

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testStructuredDataStreaming(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
        if (model.id == OpenAIModels.Audio.GPT4oAudio.id || model.id == OpenAIModels.Audio.GPT4oMiniAudio.id) {
            assumeTrue(false, "https://github.com/JetBrains/koog/issues/231")
        }

        val countries = mutableListOf<TestUtils.Country>()
        val countryDefinition = TestUtils.markdownCountryDefinition()

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
            val markdownStream = client.executeStreaming(prompt, model)

            TestUtils.parseMarkdownStreamToCountries(markdownStream).collect { country ->
                countries.add(country)
            }

            assertTrue(countries.isNotEmpty(), "Countries list should not be empty")
        }
    }

    private fun createCalculatorTool(): ToolDescriptor {
        return ToolDescriptor(
            name = "calculator",
            description = "A simple calculator that can add, subtract, multiply, and divide two numbers.",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "operation",
                    description = "The operation to perform.",
                    type = ToolParameterType.Enum(TestUtils.CalculatorOperation.entries.map { it.name }.toTypedArray())
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

    private fun createCalculatorPrompt() = Prompt.build("test-tools") {
        system("You are a helpful assistant with access to a calculator tool. When asked to perform calculations, use the calculator tool instead of calculating the answer yourself.")
        user("What is 123 + 456?")
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testToolChoiceRequired(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val calculatorTool = createCalculatorTool()
        val prompt = createCalculatorPrompt()

        /** tool choice auto is default and thus is tested by [integration_testToolsWithRequiredParams] */

        withRetry(times = 3, testName = "integration_testToolChoiceRequired[${model.id}]") {
            val response = client.execute(
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

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testToolChoiceNone(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val calculatorTool = createCalculatorTool()
        val prompt = createCalculatorPrompt()

        withRetry(times = 3, testName = "integration_testToolChoiceNone[${model.id}]") {
            val response = client.execute(
                Prompt.build("test-tools") {
                    system("You are a helpful assistant. Do not use calculator tool, it's broken!")
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

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testToolChoiceNamed(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val calculatorTool = createCalculatorTool()
        val prompt = createCalculatorPrompt()

        val nothingTool = ToolDescriptor(
            name = "nothing",
            description = "A tool that does nothing",
        )

        withRetry(times = 3, testName = "integration_testToolChoiceNamed[${model.id}]") {
            val response = client.execute(
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
            assertTrue(response.first() is Message.Tool.Call)
            val toolCall = response.first() as Message.Tool.Call
            assertEquals("nothing", toolCall.tool, "Tool name should be 'nothing'")
        }
    }

    /*
    * IMPORTANT about the testing approach!
    * The number of combinations between specific executors and media types will make tests slower.
    * The compatibility of each LLM profile with the media processing is covered in the E2E agents tests.
    * Therefore, in the scope of the executor tests, we'll check one executor of each provider
    * to decrease the number of possible combinations and to avoid redundant checks.*/

    private fun getClient(model: LLModel): LLMClient {
        return if (model.provider == LLMProvider.Anthropic) AnthropicLLMClient(
            readTestAnthropicKeyFromEnv()
        ) else if (model.provider == LLMProvider.OpenAI) OpenAILLMClient(
            readTestOpenAIKeyFromEnv()
        ) else GoogleLLMClient(
            readTestGoogleAIKeyFromEnv()
        )
    }

    @ParameterizedTest
    @MethodSource("markdownScenarioModelCombinations")
    fun integration_testMarkdownProcessingBasic(
        scenario: MarkdownTestScenario,
        model: LLModel
    ) =
        runTest(timeout = 60.seconds) {
            assumeTrue(model.provider != LLMProvider.OpenAI, "File format md not supported for OpenAI")

            val client = getClient(model)
            val executor = SingleLLMPromptExecutor(client)

            val file = MediaTestUtils.createMarkdownFileForScenario(scenario, testResourcesDir)

            val prompt = prompt("markdown-test-${scenario.name.lowercase()}") {
                system("You are a helpful assistant that can analyze markdown files.")

                user {
                    markdown {
                        "I'm sending you a markdown file with different markdown elements. "
                        +"Please list all the markdown elements used in it and describe its structure clearly."
                    }

                    attachments {
                        document(file.absolutePath)
                    }
                }
            }

            try {
                val response = executor.execute(prompt, model)
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

    @ParameterizedTest
    @MethodSource("imageScenarioModelCombinations")
    fun integration_testImageProcessing(scenario: ImageTestScenario, model: LLModel) =
        runTest(timeout = 60.seconds) {
            assumeTrue(model.capabilities.contains(LLMCapability.Vision.Image), "Model must support vision capability")

            val client = getClient(model)
            val executor = SingleLLMPromptExecutor(client)

            val imageFile = MediaTestUtils.getImageFileForScenario(scenario, testResourcesDir)

            val prompt = prompt("image-test-${scenario.name.lowercase()}") {
                system("You are a helpful assistant that can analyze images.")

                user {
                    markdown {
                        +"I'm sending you an image. Please analyze it and identify the image format if possible."
                    }

                    attachments {
                        image(imageFile.absolutePath)
                    }
                }
            }

            withRetry {
                try {
                    val response = executor.execute(prompt, model)
                    checkExecutorMediaResponse(response)
                } catch (e: Exception) {
                    // For some edge cases, exceptions are expected
                    when (scenario) {
                        ImageTestScenario.LARGE_IMAGE_ANTHROPIC, ImageTestScenario.LARGE_IMAGE -> {
                            assertTrue(
                                e.message?.contains("400 Bad Request") == true,
                                "Expected exception for a large image [400 Bad Request] was not found, got [${e.message}] instead"
                            )
                            assertTrue(
                                e.message?.contains("image exceeds") == true,
                                "Expected exception for a large image [image exceeds] was not found, got [${e.message}] instead"
                            )
                        }

                        ImageTestScenario.CORRUPTED_IMAGE, ImageTestScenario.EMPTY_IMAGE -> {
                            assertTrue(
                                e.message?.contains("400 Bad Request") == true,
                                "Expected exception for a corrupted image [400 Bad Request] was not found, got [${e.message}] instead"
                            )
                            if (model.provider == LLMProvider.Anthropic) {
                                assertTrue(
                                    e.message?.contains("Could not process image") == true,
                                    "Expected exception for a corrupted image [Could not process image] was not found, got [${e.message}] instead"
                                )
                            } else if (model.provider == LLMProvider.OpenAI) {
                                assertTrue(
                                    e.message?.contains("You uploaded an unsupported image. Please make sure your image is valid.") == true,
                                    "Expected exception for a corrupted image [You uploaded an unsupported image. Please make sure your image is valid.] was not found, got [${e.message}] instead"
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

    @ParameterizedTest
    @MethodSource("textScenarioModelCombinations")
    fun integration_testTextProcessingBasic(scenario: TextTestScenario, model: LLModel) =
        runTest(timeout = 300.seconds) {
            assumeTrue(model.provider != LLMProvider.OpenAI, "File format txt not supported for OpenAI")

            val client = getClient(model)
            val executor = SingleLLMPromptExecutor(client)

            val file = MediaTestUtils.createTextFileForScenario(scenario, testResourcesDir)

            val prompt = prompt("text-test-${scenario.name.lowercase()}") {
                system("You are a helpful assistant that can analyze and process text.")

                user {
                    markdown {
                        "I'm sending you a text file. Please analyze it and summarize its content."
                    }

                    attachments {
                        document(file.absolutePath)
                    }
                }
            }

            withRetry {
                try {
                    val response = executor.execute(prompt, model)
                    if (scenario == TextTestScenario.CORRUPTED_TEXT) {
                        checkResponseBasic(response)
                    } else {
                        checkExecutorMediaResponse(response)
                    }
                } catch (e: Exception) {
                    when (scenario) {
                        TextTestScenario.EMPTY_TEXT -> {
                            if (model.provider == LLMProvider.Google) {
                                assertTrue(
                                    e.message?.contains("400 Bad Request") == true,
                                    "Expected exception for empty text [400 Bad Request] was not found, got [${e.message}] instead"
                                )
                                assertTrue(
                                    e.message?.contains("Unable to submit request because it has an empty inlineData parameter. Add a value to the parameter and try again.") == true,
                                    "Expected exception for empty text [Unable to submit request because it has an empty inlineData parameter. Add a value to the parameter and try again] was not found, got [${e.message}] instead"
                                )
                            }
                        }

                        TextTestScenario.LONG_TEXT_5_MB -> {
                            if (model.provider == LLMProvider.Anthropic) {
                                assertTrue(
                                    e.message?.contains("400 Bad Request") == true,
                                    "Expected exception for long text [400 Bad Request] was not found, got [${e.message}] instead"
                                )
                                assertTrue(
                                    e.message?.contains("prompt is too long") == true,
                                    "Expected exception for long text [prompt is too long:] was not found, got [${e.message}] instead"
                                )
                            } else if (model.provider == LLMProvider.Google) {
                                throw e
                            }
                        }

                        TextTestScenario.LONG_TEXT_20_MB -> {
                            assertTrue(
                                e.message?.contains("400 Bad Request") == true,
                                "Expected exception for long text [400 Bad Request] was not found, got [${e.message}] instead"
                            )
                            if (model.provider == LLMProvider.Anthropic) {
                                assertTrue(
                                    e.message?.contains("prompt is too long") == true,
                                    "Expected exception for long text [prompt is too long] was not found, got [${e.message}] instead"
                                )
                            } else if (model.provider == LLMProvider.Google) {
                                assertTrue(
                                    e.message?.contains("exceeds the maximum number of tokens allowed") == true,
                                    "Expected exception for long text [exceeds the maximum number of tokens allowed] was not found, got [${e.message}] instead"
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

    @ParameterizedTest
    @MethodSource("audioScenarioModelCombinations")
    fun integration_testAudioProcessingBasic(scenario: AudioTestScenario, model: LLModel) =
        runTest(timeout = 60.seconds) {
            assumeTrue(
                model.capabilities.contains(LLMCapability.Audio),
                "Model must support audio capability"
            )

            val client = getClient(model)
            val executor = SingleLLMPromptExecutor(client)

            val audioFile = MediaTestUtils.createAudioFileForScenario(scenario, testResourcesDir)

            val prompt = prompt("audio-test-${scenario.name.lowercase()}") {
                system("You are a helpful assistant.")

                user {
                    markdown {
                        "I'm sending you an audio file. Please tell me a couple of words about it."
                    }

                    attachments {
                        when (scenario) {
                            AudioTestScenario.BASIC_WAV, AudioTestScenario.BIG_AUDIO, AudioTestScenario.CORRUPTED_AUDIO -> {
                                audio(audioFile.readBytes(), "wav")
                            }

                            AudioTestScenario.BASIC_MP3 -> {
                                audio(audioFile.readBytes(), "mp3")
                            }
                        }
                    }
                }
            }

            withRetry(times = 3, testName = "integration_testAudioProcessingBasic[${model.id}]") {
                try {
                    val response = executor.execute(prompt, model)
                    checkExecutorMediaResponse(response)
                } catch (e: Exception) {
                    if (scenario == AudioTestScenario.CORRUPTED_AUDIO) {
                        assertTrue(
                            e.message?.contains("400 Bad Request") == true,
                            "Expected exception for empty text [400 Bad Request] was not found, got [${e.message}] instead"
                        )
                        if (model.provider == LLMProvider.OpenAI) {
                            assertTrue(
                                e.message?.contains("This model does not support the format you provided.") == true,
                                "Expected exception for corrupted audio [This model does not support the format you provided.]"
                            )
                        } else if (model.provider == LLMProvider.Google) {
                            assertTrue(
                                e.message?.contains("Request contains an invalid argument.") == true,
                                "Expected exception for corrupted audio [Request contains an invalid argument.]"
                            )
                        }
                    } else {
                        throw e
                    }
                }
            }
        }

    @Test
    fun integration_testMultipleMediaTypes() = runTest(timeout = 60.seconds) {
        val model = OpenAIModels.Chat.GPT4o
        val client = OpenAILLMClient(readTestOpenAIKeyFromEnv())
        val executor = SingleLLMPromptExecutor(client)

        assumeTrue(
            model.capabilities.contains(LLMCapability.Vision.Image),
            "Model must support vision capability"
        )

        val pdfFile = File(testResourcesDir, "test.pdf")
        val imageFile = File(testResourcesDir, "test.png")

        assertTrue(imageFile.exists(), "Image test file should exist")
        assertTrue(pdfFile.exists(), "PDF test file should exist")

        val prompt = prompt("multiple-media-test") {
            system("You are a helpful assistant that can analyze different types of media files.")

            user {
                markdown {
                    +"I'm sending you a PDF file and an image. Please analyze both and tell me about their content."
                }

                attachments {
                    document(pdfFile.absolutePath)
                    image(imageFile.absolutePath)
                }
            }
        }

        val response = executor.execute(prompt, model)
        checkExecutorMediaResponse(response)

        assertTrue(
            response.content.contains("image", ignoreCase = true) ||
                    response.content.contains("PDF", ignoreCase = true),
            "Response should mention both image & PDF files"
        )
    }
}
