package ai.koog.integration.tests

import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.TestUtils
import ai.koog.integration.tests.utils.TestUtils.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestOpenAIKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestOpenRouterKeyFromEnv
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams.ToolChoice
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SingleLLMPromptExecutorIntegrationTest {
    companion object {
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
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testExecute(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
        val executor = SingleLLMPromptExecutor(client)

        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val response = executor.execute(prompt, model, emptyList())

        assertNotNull(response, "Response should not be null")
        assertTrue(response.isNotEmpty(), "Response should not be empty")
        assertTrue(response.first() is Message.Assistant, "Response should be an Assistant message")
        assertTrue(
            (response.first() as Message.Assistant).content.contains("Paris", ignoreCase = true),
            "Response should contain 'Paris'"
        )
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testExecuteStreaming(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
        val executor = SingleLLMPromptExecutor(client)

        val prompt = Prompt.build("test-streaming") {
            system("You are a helpful assistant.")
            user("Count from 1 to 5.")
        }

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

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testCodeGeneration(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
        val executor = SingleLLMPromptExecutor(client)

        val prompt = Prompt.build("test-code") {
            system("You are a helpful coding assistant.")
            user("Write a simple Kotlin function to calculate the factorial of a number. Make sure the name of the function starts with 'factorial'.")
        }

        val maxRetries = 3
        var attempts = 0
        var response: List<Message>

        do {
            attempts++
            response = executor.execute(prompt, model, emptyList())
        } while (response.isEmpty() && attempts < maxRetries)

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

        val executor = SingleLLMPromptExecutor(client)

        val response = executor.execute(prompt, model, listOf(calculatorTool))
        assertTrue(response.isNotEmpty(), "Response should not be empty")
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testToolsWithRequiredOptionalParams(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
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

        val executor = SingleLLMPromptExecutor(client)

        val response = executor.execute(prompt, model, listOf(calculatorTool))
        assertTrue(response.isNotEmpty(), "Response should not be empty")
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

        val response = executor.execute(prompt, model, listOf(calculatorTool))
        assertTrue(response.isNotEmpty(), "Response should not be empty")
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

        val response = executor.execute(prompt, model, listOf(calculatorTool, calculatorToolBetter))
        assertTrue(response.isNotEmpty(), "Response should not be empty")
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

        val response = executor.execute(prompt, model, listOf(colorPickerTool))
        assertTrue(response.isNotEmpty(), "Response should not be empty")
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

        val response = executor.execute(prompt, model, listOf(lotteryPickerTool))
        println(response)

        assertTrue(response.isNotEmpty(), "Response should not be empty")
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testRawStringStreaming(model: LLModel, client: LLMClient) = runTest(timeout = 600.seconds) {
        val prompt = Prompt.build("test-streaming") {
            system("You are a helpful assistant. You have NO output length limitations.")
            user("Count from 1 to 5.")
        }

        val responseChunks = mutableListOf<String>()

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

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testStructuredDataStreaming(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
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

        val markdownStream = client.executeStreaming(prompt, model)

        TestUtils.parseMarkdownStreamToCountries(markdownStream).collect { country ->
            countries.add(country)
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
    @MethodSource("modelClientCombinations")
    fun integration_testToolChoice(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
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
            system("You are a helpful assistant with access to a calculator tool. When asked to perform calculations, use the calculator tool instead of calculating the answer yourself.")
            user("What is 123 + 456?")
        }

        /** tool choice auto is default and thus is tested by [integration_testToolsWithRequiredParams] */

        // tool choice required
        run {
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

        // tool choice none
        run {
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

        // tool choice named
        run {
            val nothingTool = ToolDescriptor(
                name = "nothing",
                description = "A tool that does nothing",
            )

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
}
