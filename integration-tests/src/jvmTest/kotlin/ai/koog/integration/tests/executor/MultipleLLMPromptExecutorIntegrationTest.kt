package ai.koog.integration.tests.executor

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.integration.tests.utils.MediaTestScenarios
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
import ai.koog.integration.tests.utils.TestUtils.StructuredTest.getConfigFixingParserManual
import ai.koog.integration.tests.utils.TestUtils.markdownCountryDefinition
import ai.koog.integration.tests.utils.TestUtils.parseMarkdownStreamToCountries
import ai.koog.integration.tests.utils.TestUtils.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestGoogleAIKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestOpenAIKeyFromEnv
import ai.koog.integration.tests.utils.annotations.Retry
import ai.koog.prompt.dsl.ModerationCategory
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.llms.all.DefaultMultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Attachment
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams.ToolChoice
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.filterTextOnly
import ai.koog.prompt.structure.executeStructured
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64
import java.util.stream.Stream
import kotlin.io.path.pathString
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.io.files.Path as KtPath

class MultipleLLMPromptExecutorIntegrationTest {

    companion object {
        private lateinit var testResourcesDir: Path

        @JvmStatic
        @BeforeAll
        fun setupTestResources() {
            testResourcesDir =
                Paths.get(MultipleLLMPromptExecutorIntegrationTest::class.java.getResource("/media")!!.toURI())
        }

        @JvmStatic
        fun openAIModels(): Stream<LLModel> {
            return Models.openAIModels()
        }

        @JvmStatic
        fun anthropicModels(): Stream<LLModel> {
            return Models.anthropicModels()
        }

        @JvmStatic
        fun googleModels(): Stream<LLModel> {
            return Models.googleModels()
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

    // API keys for testing
    private val openAIApiKey: String get() = readTestOpenAIKeyFromEnv()
    private val anthropicApiKey: String get() = readTestAnthropicKeyFromEnv()
    private val googleApiKey: String get() = readTestGoogleAIKeyFromEnv()

    // LLM clients
    private val openAIClient get() = OpenAILLMClient(openAIApiKey)
    private val anthropicClient get() = AnthropicLLMClient(anthropicApiKey)
    private val googleClient get() = GoogleLLMClient(googleApiKey)
    val executor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient, googleClient)

    private fun createCalculatorTool(): ToolDescriptor {
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

    private fun getClientForModel(model: LLModel) = when (model.provider) {
        is LLMProvider.Anthropic -> anthropicClient
        is LLMProvider.Google -> googleClient
        else -> openAIClient
    }

    private fun createCalculatorPrompt() = prompt("test-tools") {
        system(
            "You are a helpful assistant with access to a calculator tool. When asked to perform calculations, use the calculator tool instead of calculating the answer yourself."
        )
        user("What is 123 + 456?")
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testExecute(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)

        val prompt = prompt("test-prompt") {
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
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testExecuteStreaming(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        if (model.id == OpenAIModels.Audio.GPT4oAudio.id || model.id == OpenAIModels.Audio.GPT4oMiniAudio.id) {
            assumeTrue(false, "There is no text response for audio models.")
        }

        val prompt = prompt("test-streaming") {
            system("You are a helpful assistant.")
            user("Count from 1 to 5.")
        }

        withRetry(times = 3, testName = "integration_testExecuteStreaming[${model.id}]") {
            val responseChunks = executor.executeStreaming(prompt, model)
                .filterTextOnly()
                .toList()

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
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testToolsWithRequiredParams(model: LLModel) = runTest(timeout = 300.seconds) {
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

        val prompt = prompt("test-tools") {
            system("You are a helpful assistant with access to a calculator tool.")
            user("What is 123 + 456?")
        }

        withRetry(times = 3, testName = "integration_testToolsWithRequiredParams[${model.id}]") {
            val executor = MultiLLMPromptExecutor(
                LLMProvider.OpenAI to openAIClient,
                LLMProvider.Anthropic to anthropicClient,
                LLMProvider.Google to googleClient,
            )

            val response = executor.execute(prompt, model, listOf(calculatorTool))

            assertTrue(response.isNotEmpty(), "Response should not be empty")
        }
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testToolsWithRequiredOptionalParams(model: LLModel) = runTest(timeout = 300.seconds) {
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

        val prompt = prompt("test-tools") {
            system(
                "You are a helpful assistant with access to a calculator tool. Don't use optional params if possible. ALWAYS CALL TOOL FIRST."
            )
            user("What is 123 + 456?")
        }

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient,
            LLMProvider.Anthropic to anthropicClient,
            LLMProvider.Google to googleClient,
        )

        withRetry(times = 3, testName = "integration_testToolsWithRequiredParams[${model.id}]") {
            val response = executor.execute(prompt, model, listOf(calculatorTool))
            assertTrue(response.isNotEmpty(), "Response should not be empty")
        }
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testToolsWithOptionalParams(model: LLModel) = runTest(timeout = 300.seconds) {
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

        val prompt = prompt("test-tools") {
            system("You are a helpful assistant with access to a calculator tool.")
            user("What is 123 + 456?")
        }

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient,
            LLMProvider.Anthropic to anthropicClient,
            LLMProvider.Google to googleClient,
        )

        withRetry(times = 3, testName = "integration_testToolsWithOptionalParams[${model.id}]") {
            val response = executor.execute(prompt, model, listOf(calculatorTool))
            assertTrue(response.isNotEmpty(), "Response should not be empty")
        }
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testToolsWithNoParams(model: LLModel) = runTest(timeout = 300.seconds) {
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

        val prompt = prompt("test-tools") {
            system("You are a helpful assistant with access to calculator tools. Use the best one.")
            user("What is 123 + 456?")
        }

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient,
            LLMProvider.Anthropic to anthropicClient,
            LLMProvider.Google to googleClient,
        )

        withRetry(times = 3, testName = "integration_testToolsWithNoParams[${model.id}]") {
            val response = executor.execute(prompt, model, listOf(calculatorTool, calculatorToolBetter))
            assertTrue(response.isNotEmpty(), "Response should not be empty")
        }
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testToolsWithListEnumParams(model: LLModel) = runTest(timeout = 300.seconds) {
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

        val prompt = prompt("test-tools") {
            system("You are a helpful assistant with access to a color picker tool. ALWAYS CALL TOOL FIRST.")
            user("Pick me a color!")
        }

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient,
            LLMProvider.Anthropic to anthropicClient,
            LLMProvider.Google to googleClient,
        )

        withRetry(times = 3, testName = "integration_testToolsWithNoParams[${model.id}]") {
            val response = executor.execute(prompt, model, listOf(colorPickerTool))
            assertTrue(response.isNotEmpty(), "Response should not be empty")
        }
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testToolsWithNestedListParams(model: LLModel) = runTest(timeout = 300.seconds) {
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

        val prompt = prompt("test-tools") {
            system("You are a helpful assistant. ALWAYS CALL TOOL FIRST.")
            user("Pick me lottery winners and losers! 5 of each")
        }

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient,
            LLMProvider.Anthropic to anthropicClient,
            LLMProvider.Google to googleClient,
        )

        withRetry {
            val response = executor.execute(prompt, model, listOf(lotteryPickerTool))
            assertTrue(response.isNotEmpty(), "Response should not be empty")
        }
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testRawStringStreaming(model: LLModel) = runTest(timeout = 600.seconds) {
        Models.assumeAvailable(model.provider)
        if (model.id == OpenAIModels.Audio.GPT4oAudio.id || model.id == OpenAIModels.Audio.GPT4oMiniAudio.id) {
            assumeTrue(false, "There is no text response for audio models.")
        }
        val prompt = prompt("test-streaming") {
            system("You are a helpful assistant. You have NO output length limitations.")
            user("Count from 1 to 5.")
        }

        val responseChunks = mutableListOf<StreamFrame>()
        val client = when (model.provider) {
            is LLMProvider.Anthropic -> anthropicClient
            is LLMProvider.Google -> googleClient
            else -> openAIClient
        }

        withRetry(times = 3, testName = "integration_testRawStringStreaming[${model.id}]") {
            client.executeStreaming(prompt, model).collect { chunk ->
                responseChunks.add(chunk)
                println("Received chunk: $chunk")
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
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testStructuredDataStreaming(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model != OpenAIModels.CostOptimized.GPT4_1Nano, "Model $model is too small for structured streaming")

        val countries = mutableListOf<Country>()
        val countryDefinition = markdownCountryDefinition()

        val prompt = prompt("test-structured-streaming") {
            system("You are a helpful assistant.")
            user(
                """
                Please provide information about 3 European countries in this format:

                $countryDefinition

                Make sure to follow this exact format with the # for country names and * for details.
                """.trimIndent()
            )
        }

        val client = when (model.provider) {
            is LLMProvider.Anthropic -> anthropicClient
            is LLMProvider.Google -> googleClient
            else -> openAIClient
        }

        withRetry(times = 3, testName = "integration_testStructuredDataStreaming[${model.id}]") {
            val markdownStream = client.executeStreaming(prompt, model)
            parseMarkdownStreamToCountries(markdownStream).collect { country ->
                countries.add(country)
            }

            assertTrue(countries.isNotEmpty(), "Countries list should not be empty")
        }
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testToolChoiceRequired(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val calculatorTool = createCalculatorTool()
        val client = getClientForModel(model)
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
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testToolChoiceNone(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val calculatorTool = createCalculatorTool()
        val client = getClientForModel(model)
        val prompt = createCalculatorPrompt()

        withRetry(times = 3, testName = "integration_testToolChoiceNone[${model.id}]") {
            val response =
                client.execute(
                    prompt("test-tools") {
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
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testToolChoiceNamed(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val calculatorTool = createCalculatorTool()
        val client = getClientForModel(model)
        val prompt = createCalculatorPrompt()

        val nothingTool = ToolDescriptor(
            name = "nothing",
            description = "A tool that does nothing",
        )

        withRetry(times = 3, testName = "integration_testToolChoiceNamed[${model.id}]") {
            val response =
                client.execute(
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
    @MethodSource("markdownScenarioModelCombinations")
    fun integration_testMarkdownProcessingBasic(
        scenario: MarkdownTestScenario,
        model: LLModel
    ) =
        runTest(timeout = 300.seconds) {
            Models.assumeAvailable(model.provider)
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

                            attachments {
                                textFile(KtPath(file.pathString), "text/plain")
                            }
                        }
                    }
                } else {
                    prompt("markdown-test-${scenario.name.lowercase()}") {
                        system("You are a helpful assistant that can analyze markdown files.")

                        user {
                            markdown {
                                +"I'm sending you a markdown file with different markdown elements. "
                                +"Please list all the markdown elements used in it and describe its structure clearly."
                                newline()
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

    @ParameterizedTest
    @MethodSource("imageScenarioModelCombinations")
    fun integration_testImageProcessing(scenario: ImageTestScenario, model: LLModel) =
        runTest(timeout = 300.seconds) {
            Models.assumeAvailable(model.provider)
            assumeTrue(model.capabilities.contains(LLMCapability.Vision.Image), "Model must support vision capability")
            val imageFile = MediaTestUtils.getImageFileForScenario(scenario, testResourcesDir)
            val prompt = prompt("image-test-${scenario.name.lowercase()}") {
                system("You are a helpful assistant that can analyze images.")

                user {
                    markdown {
                        +"I'm sending you an image. Please describe what you see in it and identify the image format if possible."
                    }

                    attachments {
                        when (scenario) {
                            ImageTestScenario.LARGE_IMAGE, ImageTestScenario.LARGE_IMAGE_ANTHROPIC -> {
                                image(
                                    Attachment.Image(
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
            Models.assumeAvailable(model.provider)

            val file = MediaTestUtils.createTextFileForScenario(scenario, testResourcesDir)

            val prompt =
                if (model.capabilities.contains(LLMCapability.Document) && model.provider != LLMProvider.OpenAI) {
                    prompt("text-test-${scenario.name.lowercase()}") {
                        system("You are a helpful assistant that can analyze and process text.")

                        user {
                            markdown {
                                +"I'm sending you a text file. Please analyze it and summarize its content."
                            }

                            attachments {
                                textFile(KtPath(file.pathString), "text/plain")
                            }
                        }
                    }
                } else {
                    prompt("text-test-${scenario.name.lowercase()}") {
                        system("You are a helpful assistant that can analyze and process text.")

                        user {
                            markdown {
                                +"I'm sending you a text file. Please analyze it and summarize its content."
                                newline()
                                +file.readText()
                            }
                        }
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

    @ParameterizedTest
    @MethodSource("audioScenarioModelCombinations")
    fun integration_testAudioProcessingBasic(scenario: AudioTestScenario, model: LLModel) =
        runTest(timeout = 300.seconds) {
            Models.assumeAvailable(model.provider)
            assumeTrue(
                model.capabilities.contains(LLMCapability.Audio),
                "Model must support audio capability"
            )

            val audioFile = MediaTestUtils.createAudioFileForScenario(scenario, testResourcesDir)

            val prompt = prompt("audio-test-${scenario.name.lowercase()}") {
                system("You are a helpful assistant that can analyze audio files.")

                user {
                    markdown {
                        +"I'm sending you an audio file. Please tell me a couple of words about it."
                    }

                    attachments {
                        audio(KtPath(audioFile.pathString))
                    }
                }
            }

            withRetry {
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

    /*
     * Checking just images to make sure the file is uploaded in base64 format
     * */
    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testBase64EncodedAttachment(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(
            model.capabilities.contains(LLMCapability.Vision.Image),
            "Model must support vision capability"
        )

        // Skip audio-only models
        assumeTrue(
            !model.id.contains("audio", ignoreCase = true),
            "Audio-only models are not supported for this test"
        )

        val imageFile = MediaTestUtils.getImageFileForScenario(ImageTestScenario.BASIC_PNG, testResourcesDir)
        val imageBytes = imageFile.readBytes()

        val tempImageFile = testResourcesDir.resolve("small.png")

        tempImageFile.writeBytes(imageBytes)
        val prompt = prompt("base64-encoded-attachments-test") {
            system("You are a helpful assistant that can analyze different types of media files.")

            user {
                markdown {
                    +"I'm sending you an image. Please analyze it and tell me about its content."
                }

                attachments {
                    image(KtPath(tempImageFile.pathString))
                }
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

    /*
     * Checking just images to make sure the file is uploaded by URL
     * */
    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels")
    fun integration_testUrlBasedAttachment(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
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

                attachments {
                    image(imageUrl)
                }
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

    @Test
    fun integration_testOpenAISingleMessageModeration() = runTest {
        val prompt = prompt("test-harmful-content") {
            user(String(Base64.getDecoder().decode("SG93IGNhbiBJIGhhcm0gc29tZW9uZT8="))) // For testing purposes
        }

        val result = executor.moderate(prompt = prompt, model = OpenAIModels.Moderation.Omni)

        println(result)

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
    fun integration_testOpenAIMultipleMessagesModeration() = runTest {
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
            !executor.moderate(prompt = questionOnly, model = OpenAIModels.Moderation.Omni).isHarmful
        ) { "Question only should not be detected as harmful!" }

        assert(
            !executor.moderate(prompt = answerOnly, model = OpenAIModels.Moderation.Omni).isHarmful
        ) { "Answer alone should not be detected as harmful!" }

        val multiMessageReply = executor.moderate(
            prompt = promptWithMultipleMessages,
            model = OpenAIModels.Moderation.Omni
        )

        assert(multiMessageReply.isHarmful) { "Question together with answer must be detected as harmful!" }

        assert(
            multiMessageReply.violatesOneOf(
                ModerationCategory.Illicit,
                ModerationCategory.IllicitViolent,
                ModerationCategory.Violence
            )
        ) { "Violence must be detected!" }
    }

    @Retry
    @Test
    fun integration_testMultipleSystemMessages() = runBlocking {
        Models.assumeAvailable(LLMProvider.OpenAI)
        Models.assumeAvailable(LLMProvider.Anthropic)
        Models.assumeAvailable(LLMProvider.Google)

        val openAIClient = OpenAILLMClient(openAIApiKey)
        val anthropicClient = AnthropicLLMClient(anthropicApiKey)
        val googleClient = GoogleLLMClient(googleApiKey)

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient,
            LLMProvider.Anthropic to anthropicClient,
            LLMProvider.Google to googleClient
        )

        val prompt = prompt("multiple-system-messages-test") {
            system("You are a helpful assistant.")
            user("Hi")
            system("You can handle multiple system messages.")
            user("Respond with a short message.")
        }

        val modelOpenAI = OpenAIModels.CostOptimized.GPT4oMini
        val modelAnthropic = AnthropicModels.Haiku_3_5
        val modelGemini = GoogleModels.Gemini2_0Flash

        val responseOpenAI = executor.execute(prompt, modelOpenAI).single()
        val responseAnthropic = executor.execute(prompt, modelAnthropic).single()
        val responseGemini = executor.execute(prompt, modelGemini).single()

        assertTrue(responseOpenAI.content.isNotEmpty(), "OpenAI response should not be empty")
        assertTrue(responseAnthropic.content.isNotEmpty(), "Anthropic response should not be empty")
        assertTrue(responseGemini.content.isNotEmpty(), "Gemini response should not be empty")
        println("OpenAI Response: ${responseOpenAI.content}")
        println("Anthropic Response: ${responseAnthropic.content}")
        println("Gemini Response: ${responseGemini.content}")
    }

    /*
     * Structured native/manual output tests.
     * */

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testStructuredOutputNative(model: LLModel) = runTest {
        assumeTrue(
            model.capabilities.contains(LLMCapability.Schema.JSON.Standard),
            "Model does not support Standard JSON Schema"
        )

        withRetry {
            val result = executor.executeStructured(
                prompt = StructuredTest.prompt,
                model = model,
                config = StructuredTest.getConfigNoFixingParserNative(model)
            )

            assertTrue(result.isSuccess, "Structured output should succeed: ${result.exceptionOrNull()}")
            checkResponse(result)
        }
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testStructuredOutputNativeWithFixingParser(model: LLModel) = runTest {
        assumeTrue(
            model.capabilities.contains(LLMCapability.Schema.JSON.Standard),
            "Model does not support Standard JSON Schema"
        )

        withRetry {
            val result = executor.executeStructured(
                prompt = StructuredTest.prompt,
                model = model,
                config = StructuredTest.getConfigFixingParserNative(model)
            )

            assertTrue(result.isSuccess, "Structured output should succeed: ${result.exceptionOrNull()}")
            checkResponse(result)
        }
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testStructuredOutputManual(model: LLModel) = runTest {
        assumeTrue(
            model.provider !== LLMProvider.Google,
            "Google models fail to return manually requested structured output without fixing"
        )

        assumeTrue(
            model.capabilities.contains(LLMCapability.Schema.JSON.Standard),
            "Model does not support Standard JSON Schema"
        )

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

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testStructuredOutputManualWithFixingParser(model: LLModel) = runTest {
        assumeTrue(
            model !== GoogleModels.Gemini2_0FlashLite,
            "Gemini Flash Lite 2.0 model fail to return manually requested structured output"
        )

        assumeTrue(
            model.capabilities.contains(LLMCapability.Schema.JSON.Standard),
            "Model does not support Standard JSON Schema"
        )

        withRetry(6) {
            val result = executor.executeStructured(
                prompt = StructuredTest.prompt,
                model = model,
                config = getConfigFixingParserManual(model)
            )

            assertTrue(result.isSuccess, "Structured output should succeed: ${result.exceptionOrNull()}")
            checkResponse(result)
        }
    }
}
