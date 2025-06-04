package ai.koog.integration.tests

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.TestUtils
import ai.koog.integration.tests.utils.TestUtils.executeWithRetry
import ai.koog.integration.tests.utils.TestUtils.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestGoogleAIKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestOpenAIKeyFromEnv
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.llms.all.DefaultMultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams.ToolChoice
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class MultipleLLMPromptExecutorIntegrationTest {
    // API keys for testing
    private val geminiApiKey: String get() = readTestGoogleAIKeyFromEnv()
    private val openAIApiKey: String get() = readTestOpenAIKeyFromEnv()
    private val anthropicApiKey: String get() = readTestAnthropicKeyFromEnv()

    // LLM clients
    private val openAIClient get() = OpenAILLMClient(openAIApiKey)
    private val anthropicClient get() = AnthropicLLMClient(anthropicApiKey)
    private val googleClient get() = GoogleLLMClient(geminiApiKey)

    companion object {
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
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testExecute(model: LLModel) = runTest(timeout = 300.seconds) {
        val executor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient, googleClient)

        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val response = executeWithRetry {
            executor.execute(prompt, model, emptyList())
        }

        assertNotNull(response, "Response should not be null")
        assertTrue(response.isNotEmpty(), "Response should not be empty")
        assertTrue(response.first() is Message.Assistant, "Response should be an Assistant message")
        assertTrue(
            (response.first() as Message.Assistant).content.contains("Paris", ignoreCase = true),
            "Response should contain 'Paris'"
        )
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testExecuteStreaming(model: LLModel) = runTest(timeout = 300.seconds) {
        val executor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient, googleClient)

        val prompt = Prompt.build("test-streaming") {
            system("You are a helpful assistant.")
            user("Count from 1 to 5.")
        }

        val responseChunks = executeWithRetry {
            executor.executeStreaming(prompt, model).toList()
        }

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

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testCodeGeneration(model: LLModel) = runTest(timeout = 300.seconds) {
        assumeTrue(model.capabilities.contains(LLMCapability.Tools))

        val executor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient, googleClient)

        val prompt = Prompt.build("test-code") {
            system("You are a helpful coding assistant.")
            user(
                "Write a simple Kotlin function to calculate the factorial of a number. " +
                        "Make sure the name of the function starts with 'factorial'. ONLY generate CODE, no explanations or other texts. " +
                        "The function MUST have a return statement."
            )
        }

        val response = executeWithRetry {
            executor.execute(prompt, model, emptyList())
        }

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

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testToolsWithRequiredParams(model: LLModel) = runTest(timeout = 300.seconds) {
        // ToDo remove after fix
        assumeTrue(model != GoogleModels.Gemini2_5ProPreview0506, "JBAI-14481")
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

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient,
            LLMProvider.Anthropic to anthropicClient,
            LLMProvider.Google to googleClient,
        )

        val response = executeWithRetry {
            executor.execute(prompt, model, listOf(calculatorTool))
        }

        assertTrue(response.isNotEmpty(), "Response should not be empty")
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testToolsWithRequiredOptionalParams(model: LLModel) = runTest(timeout = 300.seconds) {
        // ToDo remove after fix
        assumeTrue(model != GoogleModels.Gemini2_5ProPreview0506, "JBAI-14481")
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

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient,
            LLMProvider.Anthropic to anthropicClient,
            LLMProvider.Google to googleClient,
        )

        val response = executeWithRetry {
            executor.execute(prompt, model, listOf(calculatorTool))
        }

        assertTrue(response.isNotEmpty(), "Response should not be empty")
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testToolsWithOptionalParams(model: LLModel) = runTest(timeout = 300.seconds) {
        // ToDo remove after fix
        assumeTrue(model != GoogleModels.Gemini2_5ProPreview0506, "JBAI-14481")
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

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient,
            LLMProvider.Anthropic to anthropicClient,
            LLMProvider.Google to googleClient,
        )

        val response = executeWithRetry {
            executor.execute(prompt, model, listOf(calculatorTool))
        }

        assertTrue(response.isNotEmpty(), "Response should not be empty")
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testToolsWithNoParams(model: LLModel) = runTest(timeout = 300.seconds) {
        // ToDo remove after fix
        assumeTrue(model != GoogleModels.Gemini2_5ProPreview0506, "JBAI-14481")
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

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient,
            LLMProvider.Anthropic to anthropicClient,
            LLMProvider.Google to googleClient,
        )

        val response = executeWithRetry {
            executor.execute(prompt, model, listOf(calculatorTool, calculatorToolBetter))
        }

        assertTrue(response.isNotEmpty(), "Response should not be empty")
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testToolsWithListEnumParams(model: LLModel) = runTest(timeout = 300.seconds) {
        // ToDo remove after fix
        assumeTrue(model != GoogleModels.Gemini2_5ProPreview0506, "JBAI-14481")
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

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient,
            LLMProvider.Anthropic to anthropicClient,
            LLMProvider.Google to googleClient,
        )

        val response = executeWithRetry {
            executor.execute(prompt, model, listOf(colorPickerTool))
        }

        assertTrue(response.isNotEmpty(), "Response should not be empty")
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testToolsWithNestedListParams(model: LLModel) = runTest(timeout = 300.seconds) {
        // ToDo remove after fix
        assumeTrue(model != GoogleModels.Gemini2_5ProPreview0506, "JBAI-14481")
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

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient,
            LLMProvider.Anthropic to anthropicClient,
            LLMProvider.Google to googleClient,
        )

        val response = executeWithRetry {
            executor.execute(prompt, model, listOf(lotteryPickerTool))
        }

        println(response)

        assertTrue(response.isNotEmpty(), "Response should not be empty")
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testRawStringStreaming(model: LLModel) = runTest(timeout = 600.seconds) {
        val prompt = Prompt.build("test-streaming") {
            system("You are a helpful assistant. You have NO output length limitations.")
            user("Count from 1 to 5.")
        }

        val responseChunks = mutableListOf<String>()
        val client = when (model.provider) {
            is LLMProvider.Anthropic -> anthropicClient
            is LLMProvider.Google -> googleClient
            else -> openAIClient
        }

        executeWithRetry {
            client.executeStreaming(prompt, model).collect { chunk ->
                responseChunks.add(chunk)
                println("Received chunk: $chunk")
            }
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

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testStructuredDataStreaming(model: LLModel) = runTest(timeout = 300.seconds) {
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

        val client = when (model.provider) {
            is LLMProvider.Anthropic -> anthropicClient
            is LLMProvider.Google -> googleClient
            else -> openAIClient
        }

        executeWithRetry {
            val markdownStream = client.executeStreaming(prompt, model)
            TestUtils.parseMarkdownStreamToCountries(markdownStream).collect { country ->
                countries.add(country)
            }
        }

        assertTrue(countries.isNotEmpty(), "Countries list should not be empty")

        countries.forEach { country ->
            println("Country: ${country.name}")
            println("  Capital: ${country.capital}")
            println("  Population: ${country.population}")
            println("  Language: ${country.language}")
            println()
        }
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testToolChoice(model: LLModel) = runTest(timeout = 300.seconds) {
        // ToDo remove after fix
        assumeTrue(model != GoogleModels.Gemini2_5ProPreview0506, "JBAI-14481")
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

        val client = when (model.provider) {
            is LLMProvider.Anthropic -> anthropicClient
            is LLMProvider.Google -> googleClient
            else -> openAIClient
        }

        val prompt = Prompt.build("test-tools") {
            system("You are a helpful assistant with access to a calculator tool. When asked to perform calculations, use the calculator tool instead of calculating the answer yourself.")
            user("What is 123 + 456?")
        }

        /** tool choice auto is default and thus is tested by [integration_testToolsWithRequiredParams] */

        // tool choice required
        run {
            val response = executeWithRetry {
                client.execute(
                    prompt.withParams(
                        prompt.params.copy(
                            toolChoice = ToolChoice.Required
                        )
                    ),
                    model,
                    listOf(calculatorTool)
                )
            }

            assertTrue(response.isNotEmpty(), "Response should not be empty")

            assertTrue(response.first() is Message.Tool.Call)
        }

        // tool choice none
        run {
            val response = executeWithRetry {
                client.execute(
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
            }

            assertTrue(response.isNotEmpty(), "Response should not be empty")

            assertTrue(response.first() is Message.Assistant)
        }

        // tool choice named
        run {
            val nothingTool = ToolDescriptor(
                name = "nothing",
                description = "A tool that does nothing",
            )

            val response = executeWithRetry {
                client.execute(
                    prompt.withParams(
                        prompt.params.copy(
                            toolChoice = ToolChoice.Named(nothingTool.name)
                        )
                    ),
                    model,
                    listOf(calculatorTool, nothingTool)
                )
            }

            assertNotNull(response, "Response should not be null")
            assertTrue(response.isNotEmpty(), "Response should not be empty")

            assertTrue(response.first() is Message.Tool.Call)

            val toolCall = response.first() as Message.Tool.Call
            assertEquals("nothing", toolCall.tool, "Tool name should be 'nothing'")
        }
    }
}
