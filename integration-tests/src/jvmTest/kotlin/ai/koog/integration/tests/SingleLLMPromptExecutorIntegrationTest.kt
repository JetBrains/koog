package ai.koog.integration.tests

import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.TestUtils
import ai.koog.integration.tests.utils.TestUtils.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestOpenAIKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestOpenRouterKeyFromEnv
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
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
import org.junit.jupiter.api.BeforeEach
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

            val markdownFile = File(testResourcesDir, "test.md")
            markdownFile.writeText(
                """
                # Test Markdown File

                This is a test markdown file for integration testing.

                ## Features
                - Support for markdown files
                - Integration with LLM models
                - Testing capabilities
                
                ## Usage
                - Run the `integration_test` Gradle task to run the tests.
                - Run the `integrationTest` Maven goal to run the tests.
                
                ## License
                This project is licensed under the Apache License 2.0.
            """.trimIndent()
            )

            val textFile = File(testResourcesDir, "test.txt")
            textFile.writeText("This is a simple text file for testing document handling.")

            val imageFile = File(testResourcesDir, "test.png")
            imageFile.writeBytes(
                byteArrayOf(
                    -119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13,
                    73, 72, 68, 82, 0, 0, 0, 1, 0, 0, 0, 1, 8, 6,
                    0, 0, 0, 31, 21, -60, -119, 0, 0, 0, 10, 73, 68, 65,
                    84, 120, -100, 99, 0, 1, 0, 0, 5, 0, 1, 13, 10, 45,
                    -76, 0, 0, 0, 0, 73, 69, 78, 68, -82, 66, 96, -126
                )
            )

            val audioFile = File(testResourcesDir, "test.wav")
            audioFile.writeBytes(
                byteArrayOf(
                    82, 73, 70, 70, 36, 0, 0, 0, 87, 65, 86, 69, 102, 109, 116, 32,
                    16, 0, 0, 0, 1, 0, 1, 0, 68, -84, 0, 0, -120, 88, 1, 0,
                    2, 0, 16, 0, 100, 97, 116, 97, 0, 0, 0, 0
                )
            )

            // Create a simple PDF file for testing
            val pdfFile = File(testResourcesDir, "test.pdf")
            pdfFile.writeText(
                """%PDF-1.4
                        1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj
                        2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj
                        3 0 obj<</Type/Page/Contents 4 0 R>>endobj
                        4 0 obj<</Length 44>>stream
                        BT/F1 12 Tf 100 700 Td(Test PDF for Koog)Tj ET
                        endstream endobj
                        xref 0 5
                        0000000000 65535 f 
                        0000000010 00000 n 
                        0000000074 00000 n 
                        0000000142 00000 n 
                        0000000210 00000 n 
                        trailer<</Size 5/Root 1 0 R>>startxref 300 %%EOF""".trimIndent()
            )
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

        // combinations for tests with media processing
        @JvmStatic
        fun modelClientCombinationsMedia(): Stream<Arguments> {
            val openAIClient = OpenAILLMClient(readTestOpenAIKeyFromEnv())
            val anthropicClient = AnthropicLLMClient(readTestAnthropicKeyFromEnv())
            val googleClient = GoogleLLMClient(readTestGoogleAIKeyFromEnv())
            val openRouterClient = OpenRouterLLMClient(readTestOpenRouterKeyFromEnv())

            return Stream.concat(
                Models.openAIModels()
                    .filter { model ->
                        model.capabilities.contains(LLMCapability.Vision.Image) ||
                                model.capabilities.contains(LLMCapability.Vision.Video) ||
                                model.capabilities.contains(LLMCapability.Audio) ||
                                model.capabilities.contains(LLMCapability.Document)
                    }
                    .map { model -> Arguments.of(model, openAIClient) },

                Models.anthropicModels()
                    .filter { model ->
                        model.capabilities.contains(LLMCapability.Vision.Image)
                    }
                    .map { model -> Arguments.of(model, anthropicClient) }
            ).let { stream ->
                Stream.concat(
                    stream,
                    Models.googleModels()
                        .filter { model ->
                            model.capabilities.contains(LLMCapability.Vision.Image) ||
                                    model.capabilities.contains(LLMCapability.Vision.Video) ||
                                    model.capabilities.contains(LLMCapability.Audio)
                        }
                        .map { model -> Arguments.of(model, googleClient) }
                )
            }.let { stream ->
                Stream.concat(
                    stream,
                    Models.openRouterModels()
                        .filter { model ->
                            model.capabilities.contains(LLMCapability.Vision.Image)
                        }
                        .map { model -> Arguments.of(model, openRouterClient) }
                )
            }
        }
    }

    @BeforeEach
    fun setup() {
        assertTrue(testResourcesDir.exists(), "Test resources directory should exist")
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

            // Combine all chunks to check the full response
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

    // Common helper methods for tool choice tests
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

    @ParameterizedTest
    @MethodSource("modelClientCombinationsMedia")
    fun integration_testMarkdownDocument(model: LLModel, client: LLMClient) = runTest(timeout = 60.seconds) {
        assumeTrue(
            model.capabilities.contains(LLMCapability.Vision.Image),
            "Model must support image vision capability"
        )
        assumeTrue(
            model.provider != LLMProvider.OpenAI,
            "File format md not supported for OpenAI"
        )

        val markdownFile = File(testResourcesDir, "test.md")
        assertTrue(markdownFile.exists(), "Markdown test file should exist")

        val executor = SingleLLMPromptExecutor(client)

        val prompt = prompt("markdown-test") {
            system("You are a helpful assistant that can analyze markdown files.")

            user {
                markdown {
                    +"I'm sending you a markdown file. Please analyze it and tell me what sections it contains."
                }

                attachments {
                    document(markdownFile.absolutePath)
                }
            }
        }

        val response = executor.execute(prompt, model)

        assertNotNull(response, "Response should not be null")
        assertNotNull(response.content, "Response content should not be null")
        assertTrue(response.content.isNotEmpty(), "Response content should not be empty")

        assertTrue(
            response.content.contains("Features", ignoreCase = true),
            "Response should mention the 'Features' section from the markdown file"
        )
        assertTrue(
            response.content.contains("Usage", ignoreCase = true),
            "Response should mention the 'Usage' section from the markdown file"
        )
        assertTrue(
            response.content.contains("License", ignoreCase = true),
            "Response should mention the 'License' section from the markdown file"
        )
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinationsMedia")
    fun integration_testImageFile(model: LLModel, client: LLMClient) = runTest(timeout = 60.seconds) {
        assumeTrue(model.capabilities.contains(LLMCapability.Vision.Image), "Model must support vision capability")

        val imageFile = File(testResourcesDir, "test.png")
        assertTrue(imageFile.exists(), "Image test file should exist")

        val executor = SingleLLMPromptExecutor(client)

        val prompt = prompt("image-test") {
            system("You are a helpful assistant that can analyze images.")

            user {
                markdown {
                    +"I'm sending you an image. Please describe what you see in it."
                }

                attachments {
                    image(imageFile.absolutePath)
                }
            }
        }

        val response = executor.execute(prompt, model)

        assertNotNull(response, "Response should not be null")
        assertNotNull(response.content, "Response content should not be null")
        assertTrue(response.content.isNotEmpty(), "Response content should not be empty")
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinationsMedia")
    fun integration_testTextDocument(model: LLModel, client: LLMClient) = runTest(timeout = 60.seconds) {
        assumeTrue(
            model.capabilities.contains(LLMCapability.Vision.Image),
            "Model must support image vision capability"
        )
        assumeTrue(
            model.provider != LLMProvider.OpenAI,
            "File format md not supported for OpenAI"
        )

        val textFile = File(testResourcesDir, "test.txt")
        assertTrue(textFile.exists(), "Text test file should exist")

        val executor = SingleLLMPromptExecutor(client)

        val prompt = prompt("text-document-test") {
            system("You are a helpful assistant that can analyze text documents.")

            user {
                markdown {
                    +"I'm sending you a text file. Please summarize its content."
                }

                attachments {
                    document(textFile.absolutePath)
                }
            }
        }

        val response = executor.execute(prompt, model)

        assertNotNull(response, "Response should not be null")
        assertNotNull(response.content, "Response content should not be null")
        assertTrue(response.content.isNotEmpty(), "Response content should not be empty")

        assertTrue(
            response.content.contains("document", ignoreCase = true),
            "Response should mention 'document' from the text file"
        )
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinationsMedia")
    fun integration_testPDFDocument(model: LLModel, client: LLMClient) = runTest(timeout = 60.seconds) {
        assumeTrue(model.capabilities.contains(LLMCapability.Vision.Image), "Model must support vision capability")

        val pdfFile = File(testResourcesDir, "test.pdf")
        assertTrue(pdfFile.exists(), "PDF test file should exist")

        val executor = SingleLLMPromptExecutor(client)

        val prompt = prompt("pdf-document-test") {
            system("You are a helpful assistant that can analyze PDF documents.")

            user {
                markdown {
                    +"I'm sending you a PDF file. Please analyze it and tell me what it contains."
                }

                attachments {
                    document(pdfFile.absolutePath)
                }
            }
        }

        val response = executor.execute(prompt, model)

        assertNotNull(response, "Response should not be null")
        assertNotNull(response.content, "Response content should not be null")
        assertTrue(response.content.isNotEmpty(), "Response content should not be empty")

        assertTrue(
            response.content.contains("PDF", ignoreCase = true) ||
                    response.content.contains("document", ignoreCase = true),
            "Response should mention the PDF document"
        )
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinationsMedia")
    fun integration_testAudioFile(model: LLModel, client: LLMClient) = runTest(timeout = 60.seconds) {
        assumeTrue(model.id.contains("audio"), "Model must support audio capability")

        val audioFile = File(testResourcesDir, "test.wav")
        assertTrue(audioFile.exists(), "Audio test file should exist")

        val executor = SingleLLMPromptExecutor(client)

        val prompt = prompt("audio-test") {
            system("You are a helpful assistant that can analyze audio files.")

            user {
                markdown {
                    +"I'm sending you an audio file. Please analyze it and tell me what you hear."
                }

                attachments {
                    audio(audioFile.readBytes(), "wav")
                }
            }
        }

        val response = executor.execute(prompt, model)

        assertNotNull(response, "Response should not be null")
        assertNotNull(response.content, "Response content should not be null")
        assertTrue(response.content.isNotEmpty(), "Response content should not be empty")
    }

    @Test
    fun integration_testMultipleMediaTypes() = runTest(timeout = 60.seconds) {
        val model = OpenAIModels.Chat.GPT4o
        val client = OpenAILLMClient(readTestOpenAIKeyFromEnv())

        assumeTrue(model.capabilities.contains(LLMCapability.Vision.Image), "Model must support vision capability")

        val pdfFile = File(testResourcesDir, "test.pdf")
        val imageFile = File(testResourcesDir, "test.png")

        assertTrue(pdfFile.exists(), "PDF test file should exist")
        assertTrue(imageFile.exists(), "Image test file should exist")

        val executor = SingleLLMPromptExecutor(client)

        val prompt = prompt("multiple-media-test") {
            system("You are a helpful assistant that can analyze different types of media files.")

            user {
                markdown {
                    +"I'm sending you a markdown file and an image. Please analyze both and tell me about their content."
                }

                attachments {
                    document(pdfFile.absolutePath)
                    image(imageFile.absolutePath)
                }
            }
        }

        val response = executor.execute(prompt, model)

        assertNotNull(response, "Response should not be null")
        assertNotNull(response.content, "Response content should not be null")
        assertTrue(response.content.isNotEmpty(), "Response content should not be empty")

        assertTrue(
            response.content.contains("markdown", ignoreCase = true) ||
                    response.content.contains("document", ignoreCase = true),
            "Response should mention the markdown file"
        )
    }
}
