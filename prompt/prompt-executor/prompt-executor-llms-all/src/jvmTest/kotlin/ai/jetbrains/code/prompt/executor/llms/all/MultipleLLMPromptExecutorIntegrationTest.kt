package ai.jetbrains.code.prompt.executor.llms.all

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterType
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicModels
import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAILLMClient
import ai.jetbrains.code.prompt.executor.llms.MultiLLMPromptExecutor
import ai.jetbrains.code.prompt.llm.LLMProvider
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class MultipleLLMPromptExecutorIntegrationTest {

    // API keys for testing
    private val openAIApiKey: String get() = readTestOpenAIKeyFromEnv()
    private val anthropicApiKey: String get() = readTestAnthropicKeyFromEnv()


    @Disabled("TODO: pass the `OPEN_AI_API_TEST_KEY` and `ANTHROPIC_API_TEST_KEY`")
    @Test
    fun testExecuteWithOpenAI() = runTest {
        val openAIClient = OpenAILLMClient(openAIApiKey)
        val anthropicClient = AnthropicLLMClient(anthropicApiKey)

        val executor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient)

        val model = OpenAIModels.Chat.GPT4o
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

    @Disabled("TODO: pass the `OPEN_AI_API_TEST_KEY` and `ANTHROPIC_API_TEST_KEY`")
    @Test
    fun testExecuteWithAnthropic() = runTest {
        val openAIClient = OpenAILLMClient(openAIApiKey)
        val anthropicClient = AnthropicLLMClient(anthropicApiKey)

        val executor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient)

        val model = AnthropicModels.Sonnet_3_7
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

    @Disabled("TODO: pass the `OPEN_AI_API_TEST_KEY` and `ANTHROPIC_API_TEST_KEY`")
    @Test
    fun testExecuteStreamingWithOpenAI() = runTest {
        val openAIClient = OpenAILLMClient(openAIApiKey)
        val anthropicClient = AnthropicLLMClient(anthropicApiKey)

        val executor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient)

        val model = OpenAIModels.Chat.GPT4o
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

    @Disabled("TODO: pass the `OPEN_AI_API_TEST_KEY` and `ANTHROPIC_API_TEST_KEY`")
    @Test
    fun testExecuteStreamingWithAnthropic() = runTest {
        val openAIClient = OpenAILLMClient(openAIApiKey)
        val anthropicClient = AnthropicLLMClient(anthropicApiKey)

        val executor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient)

        val model = AnthropicModels.Sonnet_3_7
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

    @Disabled("TODO: pass the `OPEN_AI_API_TEST_KEY` and `ANTHROPIC_API_TEST_KEY`")
    @Test
    fun testCodeGenerationWithOpenAI() = runTest {
        val openAIClient = OpenAILLMClient(openAIApiKey)
        val anthropicClient = AnthropicLLMClient(anthropicApiKey)

        val executor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient)

        val model = OpenAIModels.Chat.GPT4o
        val prompt = Prompt.build("test-code") {
            system("You are a helpful coding assistant.")
            user("Write a simple Kotlin function to calculate the factorial of a number.")
        }

        val response = executor.execute(prompt, model, emptyList())

        assertNotNull(response, "Response should not be null")
        assertTrue(response.isNotEmpty(), "Response should not be empty")
        assertTrue(response.first() is Message.Assistant, "Response should be an Assistant message")

        val content = (response.first() as Message.Assistant).content
        assertTrue(content.contains("fun factorial"), "Response should contain a factorial function")
        assertTrue(content.contains("return"), "Response should contain a return statement")
    }

    @Disabled("TODO: pass the `OPEN_AI_API_TEST_KEY` and `ANTHROPIC_API_TEST_KEY`")
    @Test
    fun testCodeGenerationWithAnthropic() = runTest {
        val openAIClient = OpenAILLMClient(openAIApiKey)
        val anthropicClient = AnthropicLLMClient(anthropicApiKey)

        val executor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient)

        val model = AnthropicModels.Sonnet_3_7
        val prompt = Prompt.build("test-code") {
            system("You are a helpful coding assistant.")
            user("Write a simple Kotlin function to calculate the factorial of a number.")
        }

        val response = executor.execute(prompt, model, emptyList())

        assertNotNull(response, "Response should not be null")
        assertTrue(response.isNotEmpty(), "Response should not be empty")
        assertTrue(response.first() is Message.Assistant, "Response should be an Assistant message")

        val content = (response.first() as Message.Assistant).content
        assertTrue(content.contains("fun factorial"), "Response should contain a factorial function")
        assertTrue(content.contains("return"), "Response should contain a return statement")
    }

    @Disabled("TODO: pass the `OPEN_AI_API_TEST_KEY` and `ANTHROPIC_API_TEST_KEY`")
    @Test
    fun `test execute tools with required parameters`() = runTest {
        val openAIClient = OpenAILLMClient(openAIApiKey)
        val anthropicClient = AnthropicLLMClient(anthropicApiKey)

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

        val modelOpenAI = OpenAIModels.Chat.GPT4o
        val promptOpenAI = Prompt.build("test-tools") {
            system("You are a helpful assistant with access to a calculator tool.")
            user("What is 123 + 456?")
        }

        val modelAnthropic = AnthropicModels.Sonnet_3_7
        val promptAnthropic = Prompt.build("test-tools") {
            system("You are a helpful assistant with access to a calculator tool.")
            user("What is 123 + 456?")
        }

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient, LLMProvider.Anthropic to anthropicClient
        )
        val responseOpenAI = executor.execute(promptOpenAI, modelOpenAI, listOf(calculatorTool))
        val responseAnthropic = executor.execute(promptAnthropic, modelAnthropic, listOf(calculatorTool))
        assertTrue(responseOpenAI.isNotEmpty(), "Response should not be empty")
        assertTrue(responseAnthropic.isNotEmpty(), "Response should not be empty")
    }

    @Disabled
    @Test
    fun `test execute tools with required and optional parameters`() = runTest {
        val openAIClient = OpenAILLMClient(openAIApiKey)
        val anthropicClient = AnthropicLLMClient(anthropicApiKey)

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

        val modelOpenAI = OpenAIModels.Chat.GPT4o
        val promptOpenAI = Prompt.build("test-tools") {
            system("You are a helpful assistant with access to a calculator tool. ALWAYS CALL TOOL FIRST.")
            user("What is 12,3 + 45,,6?")
        }

        val modelAnthropic = AnthropicModels.Sonnet_3_7
        val promptAnthropic = Prompt.build("test-tools") {
            system("You are a helpful assistant with access to a calculator tool. Don't use optional params if possible. ALWAYS CALL TOOL FIRST.")
            user("What is 1 23 + 456,.1?")
        }

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient, LLMProvider.Anthropic to anthropicClient
        )
        val responseOpenAI = executor.execute(promptOpenAI, modelOpenAI, listOf(calculatorTool))
        val responseAnthropic = executor.execute(promptAnthropic, modelAnthropic, listOf(calculatorTool))
        assertTrue(responseOpenAI.isNotEmpty(), "Response should not be empty")
        assertTrue(responseAnthropic.isNotEmpty(), "Response should not be empty")
    }

    @Disabled
    @Test
    fun `test execute tools with optional parameters`() = runTest {
        val openAIClient = OpenAILLMClient(openAIApiKey)
        val anthropicClient = AnthropicLLMClient(anthropicApiKey)

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

        val modelOpenAI = OpenAIModels.Chat.GPT4o
        val promptOpenAI = Prompt.build("test-tools") {
            system("You are a helpful assistant with access to a calculator tool.")
            user("What is 123 + 456?")
        }

        val modelAnthropic = AnthropicModels.Sonnet_3_7
        val promptAnthropic = Prompt.build("test-tools") {
            system("You are a helpful assistant with access to a calculator tool. Don't use optional params if possible.")
            user("What is 123 + 456?")
        }


        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient, LLMProvider.Anthropic to anthropicClient
        )
        val responseOpenAI = executor.execute(promptOpenAI, modelOpenAI, listOf(calculatorTool))
        val responseAnthropic = executor.execute(promptAnthropic, modelAnthropic, listOf(calculatorTool))
        assertTrue(responseOpenAI.isNotEmpty(), "Response should not be empty")
        assertTrue(responseAnthropic.isNotEmpty(), "Response should not be empty")
    }

    @Disabled
    @Test
    fun `test execute tools with no parameters`() = runTest {
        val openAIClient = OpenAILLMClient(openAIApiKey)
        val anthropicClient = AnthropicLLMClient(anthropicApiKey)

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

        val modelOpenAI = OpenAIModels.Chat.GPT4o
        val promptOpenAI = Prompt.build("test-tools") {
            system("You are a helpful assistant with access to calculator tools. Use the best one.")
            user("What is 123 + 456?")
        }

        val modelAnthropic = AnthropicModels.Sonnet_3_7
        val promptAnthropic = Prompt.build("test-tools") {
            system("You are a helpful assistant with access to calculator tools. Use the best one.")
            user("What is 123 + 456?")
        }

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient, LLMProvider.Anthropic to anthropicClient
        )
        val responseOpenAI = executor.execute(promptOpenAI, modelOpenAI, listOf(calculatorTool, calculatorToolBetter))
        val responseAnthropic =
            executor.execute(promptAnthropic, modelAnthropic, listOf(calculatorTool, calculatorToolBetter))
        assertTrue(responseOpenAI.isNotEmpty(), "Response should not be empty")
        assertTrue(responseAnthropic.isNotEmpty(), "Response should not be empty")
    }

    @Disabled
    @Test
    fun `test execute tools with list enum parameter`() = runTest {
        val openAIClient = OpenAILLMClient(openAIApiKey)
        val anthropicClient = AnthropicLLMClient(anthropicApiKey)

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

        val modelOpenAI = OpenAIModels.Chat.GPT4o
        val promptOpenAI = Prompt.build("test-tools") {
            system("You are a helpful assistant with access to a color picker tool. ALWAYS CALL TOOL FIRST.")
            user("Pick me a color!")
        }

        val modelAnthropic = AnthropicModels.Sonnet_3_7
        val promptAnthropic = Prompt.build("test-tools") {
            system("You are a helpful assistant with access to a color picker tool. ALWAYS CALL TOOL FIRST.")
            user("Pick me a color!")
        }

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient, LLMProvider.Anthropic to anthropicClient
        )
        val responseOpenAI = executor.execute(promptOpenAI, modelOpenAI, listOf(colorPickerTool))
        val responseAnthropic = executor.execute(promptAnthropic, modelAnthropic, listOf(colorPickerTool))

        assertTrue(responseOpenAI.isNotEmpty(), "Response should not be empty")
        assertTrue(responseAnthropic.isNotEmpty(), "Response should not be empty")
    }

    @Disabled
    @Test
    fun `test execute tools with list of lists parameter`() = runTest {
        val openAIClient = OpenAILLMClient(openAIApiKey)
        val anthropicClient = AnthropicLLMClient(anthropicApiKey)

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

        val modelOpenAI = OpenAIModels.Chat.GPT4o
        val promptOpenAI = Prompt.build("test-tools") {
            system("You are a helpful assistant. ALWAYS CALL TOOL FIRST.")
            user("Pick me lottery winners and losers! 5 of each")
        }

        val modelAnthropic = AnthropicModels.Sonnet_3_7
        val promptAnthropic = Prompt.build("test-tools") {
            system("You are a helpful assistant. ALWAYS CALL TOOL FIRST.")
            user("Pick me lottery winners and losers! 5 of each")
        }

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient, LLMProvider.Anthropic to anthropicClient
        )
        val responseOpenAI = executor.execute(promptOpenAI, modelOpenAI, listOf(lotteryPickerTool))
        println(responseOpenAI)
        val responseAnthropic = executor.execute(promptAnthropic, modelAnthropic, listOf(lotteryPickerTool))
        println(responseAnthropic)

        assertTrue(responseOpenAI.isNotEmpty(), "Response should not be empty")
        assertTrue(responseAnthropic.isNotEmpty(), "Response should not be empty")
    }

    @Disabled
    @Test
    fun `test openai client with streaming API raw string`() = runTest(timeout = 600.seconds) {
        val openAIClient = OpenAILLMClient(openAIApiKey)

        val model = OpenAIModels.Chat.GPT4o
        val prompt = Prompt.build("test-streaming") {
            system("You are a helpful assistant. You have NO output length limitations.")
            user("Please provide information about 200 countries.")
        }

        val responseChunks = mutableListOf<String>()
        openAIClient.executeStreaming(prompt, model).collect { chunk ->
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

    @Disabled
    @Test
    fun `test anthropic client with streaming API raw string`() = runTest(timeout = 600.seconds) {
        val anthropicClient = AnthropicLLMClient(anthropicApiKey)

        val model = AnthropicModels.Sonnet_3_7
        val prompt = Prompt.build("test-streaming") {
            system("You are a helpful assistant. You have NO output length limitations.")
            user("Please provide information about 200 countries.")
        }

        val responseChunks = mutableListOf<String>()
        anthropicClient.executeStreaming(prompt, model).collect { chunk ->
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

    @Disabled
    @Test
    fun `test openai client with streaming API structured data`() = runTest {
        val openAIClient = OpenAILLMClient(openAIApiKey)
        val countries = mutableListOf<TestUtils.Country>()
        val countryDefinition = TestUtils().markdownCountryDefinition()

        val model = OpenAIModels.Chat.GPT4o
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

        val markdownStream = openAIClient.executeStreaming(prompt, model)

        TestUtils().parseMarkdownStreamToCountries(markdownStream).collect { country ->
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

    @Disabled
    @Test
    fun `test anthropic client with streaming API structured data`() = runTest {
        val anthropicClient = AnthropicLLMClient(anthropicApiKey)
        val countries = mutableListOf<TestUtils.Country>()
        val countryDefinition = TestUtils().markdownCountryDefinition()

        val model = AnthropicModels.Sonnet_3_7
        val prompt = Prompt.build("test-structured-streaming") {
            system("You are a helpful assistant.")
            user(
                """
                Please provide information about 30 European countries in this format:

                $countryDefinition

                Make sure to follow this exact format with the # for country names and * for details.
            """.trimIndent()
            )
        }

        val markdownStream = anthropicClient.executeStreaming(prompt, model)

        TestUtils().parseMarkdownStreamToCountries(markdownStream).collect { country ->
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
}
