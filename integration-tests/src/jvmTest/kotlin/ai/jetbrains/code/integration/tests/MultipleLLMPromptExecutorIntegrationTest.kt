package ai.jetbrains.code.integration.tests

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterType
import ai.jetbrains.code.integration.tests.TestUtils.readTestAnthropicKeyFromEnv
import ai.jetbrains.code.integration.tests.TestUtils.readTestGeminiKeyFromEnv
import ai.jetbrains.code.integration.tests.TestUtils.readTestOpenAIKeyFromEnv
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicModels
import ai.jetbrains.code.prompt.executor.clients.google.GoogleLLMClient
import ai.jetbrains.code.prompt.executor.clients.google.GoogleModels
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAILLMClient
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import ai.jetbrains.code.prompt.executor.llms.MultiLLMPromptExecutor
import ai.jetbrains.code.prompt.executor.llms.all.DefaultMultiLLMPromptExecutor
import ai.jetbrains.code.prompt.llm.LLMProvider
import ai.jetbrains.code.prompt.llm.LLModel
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@Disabled("TODO: pass the `OPEN_AI_API_TEST_KEY`, `ANTHROPIC_API_TEST_KEY`, `GEMINI_API_TEST_KEY`")
class MultipleLLMPromptExecutorIntegrationTest {
    // API keys for testing
    private val geminiApiKey: String get() = readTestGeminiKeyFromEnv()
    private val openAIApiKey: String get() = readTestOpenAIKeyFromEnv()
    private val anthropicApiKey: String get() = readTestAnthropicKeyFromEnv()

    // LLM clients
    private val openAIClient get() = OpenAILLMClient(openAIApiKey)
    private val anthropicClient get() = AnthropicLLMClient(anthropicApiKey)
    private val googleClient get() = GoogleLLMClient(geminiApiKey)

    companion object {
        @JvmStatic
        fun openAIModels(): Stream<LLModel> {
            return Stream.of(
                OpenAIModels.Chat.GPT4o,
                OpenAIModels.Chat.GPT4_1,

                OpenAIModels.Reasoning.GPT4oMini,
                OpenAIModels.Reasoning.O3Mini,
                OpenAIModels.Reasoning.O1Mini,
                OpenAIModels.Reasoning.O3,
                OpenAIModels.Reasoning.O1,

                OpenAIModels.CostOptimized.O4Mini,
                OpenAIModels.CostOptimized.GPT4_1Nano,
                OpenAIModels.CostOptimized.GPT4_1Mini,
            )
        }

        @JvmStatic
        fun anthropicModels(): Stream<LLModel> {
            return Stream.of(
                AnthropicModels.Opus,
                AnthropicModels.Haiku_3,
                AnthropicModels.Haiku_3_5,
                AnthropicModels.Sonnet_3,
                AnthropicModels.Sonnet_3_5,
                AnthropicModels.Sonnet_3_7,
            )
        }

        @JvmStatic
        fun googleModels(): Stream<LLModel> {
            return Stream.of(
                GoogleModels.Gemini1_5Pro,
                GoogleModels.Gemini1_5ProLatest,
                GoogleModels.Gemini1_5Pro001,
                GoogleModels.Gemini1_5Pro002,
                GoogleModels.Gemini2_5ProPreview0506,
                GoogleModels.GeminiProVision,

                GoogleModels.Gemini2_0Flash,
                GoogleModels.Gemini2_0Flash001,
                GoogleModels.Gemini2_0FlashLite,
                GoogleModels.Gemini2_0FlashLite001,
                GoogleModels.Gemini1_5Flash,
                GoogleModels.Gemini1_5FlashLatest,
                GoogleModels.Gemini1_5Flash001,
                GoogleModels.Gemini1_5Flash002,
                GoogleModels.Gemini1_5Flash8B,
                GoogleModels.Gemini1_5Flash8B001,
                GoogleModels.Gemini1_5Flash8BLatest,
                GoogleModels.Gemini2_5FlashPreview0417,
            )
        }
    }

    @ParameterizedTest
    @MethodSource("openAIModels")
    fun integration_testExecuteWithOpenAI(model: LLModel) = runTest {
        val executor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient, googleClient)

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
    @MethodSource("anthropicModels")
    fun integration_testExecuteWithAnthropic(model: LLModel) = runTest {
        val executor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient, googleClient)

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
    @MethodSource("googleModels")
    fun integration_testExecuteWithGoogle(model: LLModel) = runTest {
        val executor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient, googleClient)

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
    @MethodSource("openAIModels")
    fun integration_testExecuteStreamingWithOpenAI(model: LLModel) = runTest {
        val executor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient, googleClient)

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
    @MethodSource("anthropicModels")
    fun integration_testExecuteStreamingWithAnthropic(model: LLModel) = runTest {
        val executor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient, googleClient)

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
    @MethodSource("googleModels")
    fun integration_testExecuteStreamingWithGoogle(model: LLModel) = runTest {
        val executor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient, googleClient)

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
    @MethodSource("openAIModels")
    fun integration_testCodeGenerationWithOpenAI(model: LLModel) = runTest {
        val executor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient, googleClient)

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

    @ParameterizedTest
    @MethodSource("anthropicModels")
    fun integration_testCodeGenerationWithAnthropic(model: LLModel) = runTest {
        val executor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient, googleClient)

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

    @ParameterizedTest
    @MethodSource("googleModels")
    fun integration_testCodeGenerationWithGoogle(model: LLModel) = runTest {
        val executor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient, googleClient)

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

    @ParameterizedTest
    @MethodSource("openAIModels")
    fun integration_testToolsWithRequiredParamsOpenAI(model: LLModel) = runTest {
        // o1-mini doesn't support tools
        assumeTrue(model != OpenAIModels.Reasoning.O1Mini)

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
            LLMProvider.OpenAI to openAIClient, LLMProvider.Anthropic to anthropicClient
        )
        val response = executor.execute(prompt, model, listOf(calculatorTool))
        assertTrue(response.isNotEmpty(), "Response should not be empty")
    }

    @ParameterizedTest
    @MethodSource("anthropicModels")
    fun integration_testToolsWithRequiredParamsAnthropic(model: LLModel) = runTest {
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
        val response = executor.execute(prompt, model, listOf(calculatorTool))
        assertTrue(response.isNotEmpty(), "Response should not be empty")
    }

    @ParameterizedTest
    @MethodSource("googleModels")
    fun integration_testToolsWithRequiredParamsGoogle(model: LLModel) = runTest {
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
        val response = executor.execute(prompt, model, listOf(calculatorTool))
        assertTrue(response.isNotEmpty(), "Response should not be empty")
    }

    @ParameterizedTest
    @MethodSource("openAIModels")
    fun integration_testToolsWithRequiredOptionalParamsOpenAI(model: LLModel) = runTest {
        // o1-mini doesn't support tools
        assumeTrue(model != OpenAIModels.Reasoning.O1Mini)

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
            system("You are a helpful assistant with access to a calculator tool. ALWAYS CALL TOOL FIRST.")
            user("What is 12,3 + 45,,6?")
        }

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient,
            LLMProvider.Anthropic to anthropicClient,
            LLMProvider.Google to googleClient,
        )
        val response = executor.execute(prompt, model, listOf(calculatorTool))
        assertTrue(response.isNotEmpty(), "Response should not be empty")
    }

    @ParameterizedTest
    @MethodSource("anthropicModels")
    fun integration_testToolsWithRequiredOptionalParamsAnthropic(model: LLModel) = runTest {
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
            user("What is 1 23 + 456,.1?")
        }

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient,
            LLMProvider.Anthropic to anthropicClient,
            LLMProvider.Google to googleClient,
        )
        val response = executor.execute(prompt, model, listOf(calculatorTool))
        assertTrue(response.isNotEmpty(), "Response should not be empty")
    }

    @ParameterizedTest
    @MethodSource("googleModels")
    fun integration_testToolsWithRequiredOptionalParamsGoogle(model: LLModel) = runTest {
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
            user("What is 1 23 + 456,.1?")
        }

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient,
            LLMProvider.Anthropic to anthropicClient,
            LLMProvider.Google to googleClient,
        )
        val response = executor.execute(prompt, model, listOf(calculatorTool))
        assertTrue(response.isNotEmpty(), "Response should not be empty")
    }

    @ParameterizedTest
    @MethodSource("openAIModels")
    fun integration_testToolsWithOptionalParamsOpenAI(model: LLModel) = runTest {
        // o1-mini doesn't support tools
        assumeTrue(model != OpenAIModels.Reasoning.O1Mini)

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
        val response = executor.execute(prompt, model, listOf(calculatorTool))
        assertTrue(response.isNotEmpty(), "Response should not be empty")
    }

    @ParameterizedTest
    @MethodSource("anthropicModels")
    fun integration_testToolsWithOptionalParamsAnthropic(model: LLModel) = runTest {
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
            system("You are a helpful assistant with access to a calculator tool. Don't use optional params if possible.")
            user("What is 123 + 456?")
        }

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient,
            LLMProvider.Anthropic to anthropicClient,
            LLMProvider.Google to googleClient,
        )
        val response = executor.execute(prompt, model, listOf(calculatorTool))
        assertTrue(response.isNotEmpty(), "Response should not be empty")
    }

    @ParameterizedTest
    @MethodSource("googleModels")
    fun integration_testToolsWithOptionalParamsGoogle(model: LLModel) = runTest {
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
            system("You are a helpful assistant with access to a calculator tool. Don't use optional params if possible.")
            user("What is 123 + 456?")
        }

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient,
            LLMProvider.Anthropic to anthropicClient,
            LLMProvider.Google to googleClient,
        )
        val response = executor.execute(prompt, model, listOf(calculatorTool))
        assertTrue(response.isNotEmpty(), "Response should not be empty")
    }

    @ParameterizedTest
    @MethodSource("openAIModels")
    fun integration_testToolsWithNoParamsOpenAI(model: LLModel) = runTest {
        // o1-mini doesn't support tools
        assumeTrue(model != OpenAIModels.Reasoning.O1Mini)

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
        val response = executor.execute(prompt, model, listOf(calculatorTool, calculatorToolBetter))
        assertTrue(response.isNotEmpty(), "Response should not be empty")
    }

    @ParameterizedTest
    @MethodSource("anthropicModels")
    fun integration_testToolsWithNoParamsAnthropic(model: LLModel) = runTest {
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
        val response = executor.execute(prompt, model, listOf(calculatorTool, calculatorToolBetter))
        assertTrue(response.isNotEmpty(), "Response should not be empty")
    }

    @ParameterizedTest
    @MethodSource("googleModels")
    fun integration_testToolsWithNoParamsGoogle(model: LLModel) = runTest {
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
        val response = executor.execute(prompt, model, listOf(calculatorTool, calculatorToolBetter))
        assertTrue(response.isNotEmpty(), "Response should not be empty")
    }

    @ParameterizedTest
    @MethodSource("openAIModels")
    fun integration_testToolsWithListEnumParamsOpenAI(model: LLModel) = runTest {
        // o1-mini doesn't support tools
        assumeTrue(model != OpenAIModels.Reasoning.O1Mini)

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
        val response = executor.execute(prompt, model, listOf(colorPickerTool))
        assertTrue(response.isNotEmpty(), "Response should not be empty")
    }

    @ParameterizedTest
    @MethodSource("anthropicModels")
    fun integration_testToolsWithListEnumParamsAnthropic(model: LLModel) = runTest {
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
        val response = executor.execute(prompt, model, listOf(colorPickerTool))
        assertTrue(response.isNotEmpty(), "Response should not be empty")
    }

    @ParameterizedTest
    @MethodSource("googleModels")
    fun integration_testToolsWithListEnumParamsGoogle(model: LLModel) = runTest {
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
        val response = executor.execute(prompt, model, listOf(colorPickerTool))
        assertTrue(response.isNotEmpty(), "Response should not be empty")
    }

    @ParameterizedTest
    @MethodSource("openAIModels")
    fun integration_testToolsWithNestedListParamsOpenAI(model: LLModel) = runTest {
        // o1-mini doesn't support tools
        assumeTrue(model != OpenAIModels.Reasoning.O1Mini)

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
        val response = executor.execute(prompt, model, listOf(lotteryPickerTool))
        println(response)

        assertTrue(response.isNotEmpty(), "Response should not be empty")
    }

    @ParameterizedTest
    @MethodSource("anthropicModels")
    fun integration_testToolsWithNestedListParamsAnthropic(model: LLModel) = runTest {
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
        val response = executor.execute(prompt, model, listOf(lotteryPickerTool))
        println(response)

        assertTrue(response.isNotEmpty(), "Response should not be empty")
    }

    @ParameterizedTest
    @MethodSource("googleModels")
    fun integration_testToolsWithNestedListParamsGoogle(model: LLModel) = runTest {
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
        val response = executor.execute(prompt, model, listOf(lotteryPickerTool))
        println(response)

        assertTrue(response.isNotEmpty(), "Response should not be empty")
    }

    @ParameterizedTest
    @MethodSource("openAIModels")
    fun integration_testOpenAIRawStringStreaming(model: LLModel) = runTest(timeout = 600.seconds) {
        val prompt = Prompt.build("test-streaming") {
            system("You are a helpful assistant. You have NO output length limitations.")
            user("Count from 1 to 5.")
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

    @ParameterizedTest
    @MethodSource("anthropicModels")
    fun integration_testAnthropicRawStringStreaming(model: LLModel) = runTest(timeout = 600.seconds) {
        val prompt = Prompt.build("test-streaming") {
            system("You are a helpful assistant. You have NO output length limitations.")
            user("Count from 1 to 5.")
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

    @ParameterizedTest
    @MethodSource("googleModels")
    fun integration_testGoogleRawStringStreaming(model: LLModel) = runTest(timeout = 600.seconds) {
        val prompt = Prompt.build("test-streaming") {
            system("You are a helpful assistant. You have NO output length limitations.")
            user("Count from 1 to 5.")
        }

        val responseChunks = mutableListOf<String>()
        googleClient.executeStreaming(prompt, model).collect { chunk ->
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
    @MethodSource("openAIModels")
    fun integration_testOpenAIStructuredDataStreaming(model: LLModel) = runTest {
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

        val markdownStream = openAIClient.executeStreaming(prompt, model)

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
    @MethodSource("anthropicModels")
    fun integration_testAnthropicStructuredDataStreaming(model: LLModel) = runTest {
        val countries = mutableListOf<TestUtils.Country>()
        val countryDefinition = TestUtils.markdownCountryDefinition()

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
    @MethodSource("googleModels")
    fun integration_testGoogleStructuredDataStreaming(model: LLModel) = runTest {
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

        val markdownStream = googleClient.executeStreaming(prompt, model)

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
}
